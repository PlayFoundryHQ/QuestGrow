"""Phase C integration validation — the MVP.md acceptance checklist, end to end
through the full stack (auth + HTTP API + notifications + persistence).

One scenario walks items 1–12 and the cross-cutting requirements in order,
against a `SqliteRepository` (the D1 backend) so persistence is exercised too.
This is the "integration validation passes" gate for Phase C.
"""

from __future__ import annotations

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import AuthService, EventSink, QuestGrowService, SqliteRepository  # noqa: E402
from questgrow.api import create_app  # noqa: E402

D1, D2, D3 = "2026-08-03", "2026-08-04", "2026-08-05"


@pytest.fixture
def stack():
    svc = QuestGrowService(repo=SqliteRepository(":memory:"), events=EventSink(),
                           advancement_threshold=8)
    auth = AuthService(svc)
    return TestClient(create_app(svc, auth=auth)), svc


def _parent(client) -> dict:
    client.post("/auth/signup", json={"email": "mum@x.com", "password": "pw123456", "pin": "2468"})
    s = client.post("/auth/login", json={"email": "mum@x.com", "password": "pw123456"}).json()
    p = client.post("/auth/unlock", json={"session_token": s["session_token"], "pin": "2468"}).json()
    return {"Authorization": f"Bearer {p['parent_token']}"}


def test_mvp_acceptance_end_to_end(stack):
    client, svc = stack
    ph = _parent(client)

    # cross-cutting: parent gate — a wrong PIN yields no parent token
    s2 = client.post("/auth/login", json={"email": "mum@x.com", "password": "pw123456"}).json()
    assert client.post("/auth/unlock",
                       json={"session_token": s2["session_token"], "pin": "0000"}).status_code == 403

    # 1. parent creates a child profile (+ 11: age band → adaptation; override a dimension)
    assert client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "3-4"},
                       headers=ph).status_code == 200
    client.patch("/children/mia", json={"adaptation_overrides": {"quests_shown_at_once": "2"}},
                 headers=ph)
    client.post("/children", json={"child_id": "leo", "name": "Leo", "age_band": "7-8"}, headers=ph)  # multi-child

    # 2. parent configures quests (starter templates one-tap + a custom one) + 10 (edit)
    seeded = client.post("/quests/seed-starters", headers=ph).json()
    assert any(q["quest_id"] == "teeth" for q in seeded)
    client.post("/quests", json={"quest_id": "read", "title": "Read a book", "icon": "📖",
                                 "points": 15}, headers=ph)
    client.put("/quests/read/schedule", json={"recurrence": "daily"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "teeth"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "read"}, headers=ph)
    # 12 / no verification flag: assignment defaults to PARENT_GUIDED
    assert svc.repo.get_child_quest("mia", "teeth").ownership_stage.value == "PARENT_GUIDED"

    ctok = client.post("/auth/child-token", json={"child_id": "mia"}, headers=ph).json()["child_token"]
    ch = {"Authorization": f"Bearer {ctok}"}

    # 3. child sees daily visual quests, tuned to age band (item 11: complexityProfile)
    client.post("/clock/materialise", json={"day": D1}, headers=ph)
    today = client.get(f"/me/today?day={D1}", headers=ch).json()
    assert {i["quest_id"] for i in today["items"]} == {"teeth", "read"}
    assert today["complexity_profile"]["band"] == "3-4"
    assert today["complexity_profile"]["quests_shown_at_once"] == 2      # parent override honoured
    assert today["complexity_profile"]["text_style"] == "icon_only"
    # INV-8: nothing stage-ish in the child payload
    assert "stage" not in str(today).lower() and "ownership" not in str(today).lower()

    # 4. child marks completion — PARENT_GUIDED → pending ("waiting for grown-up")
    r = client.post("/me/quests/teeth/complete", json={"day": D1, "note": ""}, headers=ch)
    assert r.json()["state"] == "pending"

    # 5. pending shows in the parent approvals queue; opt in to notifications first
    client.put("/account/notifications", json={"enabled": True}, headers=ph)
    q = client.get("/children/mia/approvals", headers=ph).json()
    assert [i["quest_id"] for i in q] == ["teeth"]
    # 6. no points yet — nothing verified
    assert client.get("/me/progress?week_start=2026-08-03", headers=ch).json()["lifetime_achievement"] == 0
    # approve → verified + one ledger entry + celebration + parent notification
    client.post("/children/mia/quests/teeth/approve", json={"day": D1}, headers=ph)
    prog = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    assert prog["lifetime_achievement"] == 10
    # 7. immediate celebration available to the child poll
    cel = client.get("/me/celebrations", headers=ch).json()
    assert cel and cel[-1]["points_awarded"] == 10
    notes = client.get("/children/mia/notifications", headers=ph).json()
    assert notes and "Mia" in notes[-1]["text"] and "streak" not in notes[-1]["text"].lower()

    # 6 (idempotency): replaying the child intent does not double-award
    client.post("/me/quests/teeth/complete", json={"day": D1, "note": ""}, headers=ch)
    from questgrow import LedgerKind
    assert len([e for e in svc.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]) == 1

    # 8. daily progress indicator
    dash = client.get(f"/children/mia/dashboard?day={D1}&week_start=2026-08-03", headers=ph).json()
    assert (dash["verified"], dash["pending"], dash["total"]) == (1, 0, 2)

    # 5 (advancement suggestion) + regression is neutral:
    # move teeth to CHILD_PARTICIPATED, rack up completions, get a suggestion, accept it
    client.put("/children/mia/quests/teeth/ownership", json={"target": "CHILD_PARTICIPATED"},
               headers=ph)
    for d in ("2026-08-04", "2026-08-05", "2026-08-06", "2026-08-07", "2026-08-08",
              "2026-08-09", "2026-08-10", "2026-08-11"):
        client.post("/clock/materialise", json={"day": d}, headers=ph)
        client.post("/me/quests/teeth/complete", json={"day": d, "note": ""}, headers=ch)
    sug = client.get("/children/mia/suggestions", headers=ph).json()
    assert sug and sug[0]["to_stage"] == "CHILD_OWNED"
    plan = client.post("/children/mia/quests/teeth/suggestion/accept", headers=ph).json()
    assert plan["direction"] == "advance"
    # regress back — neutral, no error, no negative signal, counter resets
    reg = client.put("/children/mia/quests/teeth/ownership", json={"target": "PARENT_GUIDED"},
                     headers=ph).json()
    assert reg["direction"] == "regress"
    assert svc.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 0

    # 9. weekly progress = progressive consistency (a count, never a streak)
    wk = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    assert wk["week_active_days"] >= 4
    assert "streak" not in str(wk).lower()

    # 10. rewards: define + redeem (self-service) affects Spendable Balance only
    client.post("/rewards", json={"reward_id": "sticker", "name": "Sticker pack", "icon": "🏷️",
                                  "cost": 20, "mode": "self_service"}, headers=ph)
    before = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    red = client.post("/me/rewards/sticker/redeem", headers=ch).json()
    assert red["state"] == "granted"
    after = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    assert after["spendable_balance"] == before["spendable_balance"] - 20
    assert after["lifetime_achievement"] == before["lifetime_achievement"]   # unchanged (monotonic)

    # cross-cutting trust boundary: the child token cannot touch parent state
    assert client.post("/quests", json={"quest_id": "x", "title": "x", "icon": "x"},
                       headers=ch).status_code == 403
    assert client.get("/children/mia/approvals", headers=ch).status_code == 403

    # persistence: state survives a fresh service object on the same DB handle
    live = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    svc2 = QuestGrowService(repo=svc.repo, events=EventSink())
    assert svc2.lifetime_achievement(child_id="mia") == live["lifetime_achievement"]
    assert svc2.spendable_balance(child_id="mia") == live["spendable_balance"]
    assert svc2.repo.get_child_quest("mia", "teeth").ownership_stage.value == "PARENT_GUIDED"

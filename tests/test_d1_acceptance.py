"""D1 — end-to-end MVP acceptance run.

One test per numbered scenario in
`docs/product-delivery/MVP.md` → "MVP acceptance (the loop works end to end)"
(1–10), plus the cross-cutting MVP requirements and the out-of-scope negative
checks. Every scenario is driven through the full stack — `AuthService` +
FastAPI + notifications + `SqliteRepository` (the D1 backend) — i.e. exactly
what the reference clients drive.

Where a scenario has a purely visual component (scenario 8's *visible* age-band
change; celebration animation) this suite verifies the data/contract that the
client renders from and the D1 report records the visual check as pending.
"""

from __future__ import annotations

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import (  # noqa: E402
    AuthService,
    EventSink,
    LedgerKind,
    QuestGrowService,
    SqliteRepository,
)
from questgrow.api import _WEBCLIENT_DIR, create_app  # noqa: E402

DAYS = [f"2026-08-{d:02d}" for d in range(3, 25)]  # Mon 2026-08-03 onward


@pytest.fixture
def fam():
    """A reference family: account (notifications opt-in), one child (mia,
    age 5-6), five quests on a daily schedule, all assigned (→ PARENT_GUIDED).
    Returns (client, svc, parent_headers, child_headers)."""
    svc = QuestGrowService(repo=SqliteRepository(":memory:"), events=EventSink(),
                           advancement_threshold=8)
    client = TestClient(create_app(svc, auth=AuthService(svc)))
    client.post("/auth/signup", json={"email": "mum@x.com", "password": "pw123456", "pin": "2468"})
    s = client.post("/auth/login", json={"email": "mum@x.com", "password": "pw123456"}).json()
    pt = client.post("/auth/unlock", json={"session_token": s["session_token"], "pin": "2468"}).json()
    ph = {"Authorization": f"Bearer {pt['parent_token']}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    for qid, title, icon, pts in [
        ("teeth", "Brush teeth", "🪥", 10), ("dressed", "Get dressed", "👕", 10),
        ("tidy", "Tidy toys", "🧸", 10), ("read", "Read a book", "📖", 15),
        ("plants", "Water plants", "🪴", 5),
    ]:
        client.post("/quests", json={"quest_id": qid, "title": title, "icon": icon, "points": pts},
                    headers=ph)
        client.put(f"/quests/{qid}/schedule", json={"recurrence": "daily"}, headers=ph)
        client.post("/children/mia/quests", json={"quest_id": qid}, headers=ph)
    ctok = client.post("/auth/child-token", json={"child_id": "mia"}, headers=ph).json()["child_token"]
    return client, svc, ph, {"Authorization": f"Bearer {ctok}"}


def _materialise(client, ph, day):
    client.post("/clock/materialise", json={"day": day}, headers=ph)


# ------------------------------------------------------------------ #
# Scenario 1                                                          #
# ------------------------------------------------------------------ #
def test_s1_parent_sets_up_child_and_quests_some_requiring_verification(fam):
    client, svc, ph, ch = fam
    cqs = svc.repo.child_quests_of("mia")
    assert len(cqs) == 5                                   # 3–5 quests
    # every assigned quest requires verification (PARENT_GUIDED → REQUIRES_APPROVAL)
    assert all(cq.ownership_stage.value == "PARENT_GUIDED" for cq in cqs)
    _materialise(client, ph, DAYS[0])
    items = client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()["items"]
    assert {i["quest_id"] for i in items} == {"teeth", "dressed", "tidy", "read", "plants"}
    assert all(i["waits_for_grownup"] for i in items)


# ------------------------------------------------------------------ #
# Scenario 2                                                          #
# ------------------------------------------------------------------ #
def test_s2_child_sees_today_does_one_marks_it(fam):
    client, svc, ph, ch = fam
    _materialise(client, ph, DAYS[0])
    today = client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()
    assert len(today["items"]) == 5
    r = client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    assert r.status_code == 200


# ------------------------------------------------------------------ #
# Scenario 3                                                          #
# ------------------------------------------------------------------ #
def test_s3_parent_guided_pends_child_owned_verifies_immediately(fam):
    client, svc, ph, ch = fam
    client.put("/children/mia/quests/read/ownership", json={"target": "CHILD_OWNED"}, headers=ph)
    _materialise(client, ph, DAYS[0])

    pending = client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    assert pending.json()["state"] == "pending"
    assert client.get("/me/celebrations", headers=ch).json() == []          # nothing yet

    imm = client.post("/me/quests/read/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    assert imm.json()["state"] == "verified"
    cel = client.get("/me/celebrations", headers=ch).json()
    assert len(cel) == 1 and cel[0]["points_awarded"] == 15                  # celebrate now


# ------------------------------------------------------------------ #
# Scenario 4                                                          #
# ------------------------------------------------------------------ #
def test_s4_parent_approves_pending_child_sees_celebration_and_progress(fam):
    client, svc, ph, ch = fam
    client.put("/account/notifications", json={"enabled": True}, headers=ph)
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)

    q = client.get("/children/mia/approvals", headers=ph).json()
    assert [i["quest_id"] for i in q] == ["teeth"]
    before = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()["lifetime_achievement"]

    client.post("/children/mia/quests/teeth/approve", json={"day": DAYS[0]}, headers=ph)
    after = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()["lifetime_achievement"]
    assert after == before + 10                                             # progress increments
    assert client.get("/me/celebrations", headers=ch).json()[-1]["points_awarded"] == 10
    assert client.get("/children/mia/notifications", headers=ph).json()      # quiet parent note


# ------------------------------------------------------------------ #
# Scenario 5                                                          #
# ------------------------------------------------------------------ #
def test_s5_not_yet_returns_to_available_with_no_penalty(fam):
    client, svc, ph, ch = fam
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/tidy/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    led_before = list(svc.repo.all_ledger())

    client.post("/children/mia/quests/tidy/not-yet", json={"day": DAYS[0], "note": "give it another go"},
                headers=ph)
    item = next(i for i in client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()["items"]
                if i["quest_id"] == "tidy")
    assert item["state"] == "available"                                     # back to available
    assert svc.repo.all_ledger() == led_before                             # no points delta
    # child surface shows no negative signal
    assert "not_yet" not in str(client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()).lower()


# ------------------------------------------------------------------ #
# Scenario 6                                                          #
# ------------------------------------------------------------------ #
def test_s6_daily_reflects_only_verified_weekly_updates(fam):
    client, svc, ph, ch = fam
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)  # pending
    d = client.get(f"/children/mia/dashboard?day={DAYS[0]}&week_start=2026-08-03", headers=ph).json()
    assert d["verified"] == 0 and d["pending"] == 1                         # daily = verified only

    client.post("/children/mia/quests/teeth/approve", json={"day": DAYS[0]}, headers=ph)
    d = client.get(f"/children/mia/dashboard?day={DAYS[0]}&week_start=2026-08-03", headers=ph).json()
    assert d["verified"] == 1
    assert d["week_active_days"] == 1
    # a second active day moves the weekly view
    _materialise(client, ph, DAYS[1])
    client.post("/me/quests/teeth/complete", json={"day": DAYS[1], "note": ""}, headers=ch)
    client.post("/children/mia/quests/teeth/approve", json={"day": DAYS[1]}, headers=ph)
    d = client.get(f"/children/mia/dashboard?day={DAYS[1]}&week_start=2026-08-03", headers=ph).json()
    assert d["week_active_days"] == 2


# ------------------------------------------------------------------ #
# Scenario 7                                                          #
# ------------------------------------------------------------------ #
def test_s7_child_mode_cannot_change_authoritative_state_even_tampered(fam):
    client, svc, ph, ch = fam
    # points / ledger
    assert client.post("/children/mia/adjustments", json={"amount": 100}, headers=ch).status_code == 403
    # quests
    assert client.post("/quests", json={"quest_id": "x", "title": "x", "icon": "x"},
                       headers=ch).status_code == 403
    assert client.patch("/quests/teeth", json={"points": 999}, headers=ch).status_code == 403
    # rewards
    assert client.post("/rewards", json={"reward_id": "r", "name": "r", "icon": "r", "cost": 0,
                                         "mode": "self_service"}, headers=ch).status_code == 403
    # ownership stage
    assert client.put("/children/mia/quests/teeth/ownership", json={"target": "CHILD_OWNED"},
                      headers=ch).status_code == 403
    # settings / notifications
    assert client.put("/account/notifications", json={"enabled": True}, headers=ch).status_code == 403
    # a forged token is refused outright
    assert client.get(f"/me/today?day={DAYS[0]}",
                      headers={"Authorization": "Bearer forged"}).status_code == 401
    # nothing leaked through
    assert svc.repo.all_ledger() == []


# ------------------------------------------------------------------ #
# Scenario 8                                                          #
# ------------------------------------------------------------------ #
def test_s8_age_band_changes_text_quantity_density_and_reward_presentation(fam):
    client, svc, ph, ch = fam
    _materialise(client, ph, DAYS[0])

    def profile(band):
        client.patch("/children/mia", json={"age_band": band}, headers=ph)
        return client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()["complexity_profile"]

    young, mid, old = profile("3-4"), profile("5-6"), profile("7-8")
    # text amount
    assert (young["text_style"], mid["text_style"], old["text_style"]) == (
        "icon_only", "short_label", "short_sentence")
    # quests per screen
    assert young["quests_shown_at_once"] < mid["quests_shown_at_once"] < old["quests_shown_at_once"]
    # reward presentation
    assert young["reward_presentation"] != old["reward_presentation"]
    # per-dimension parent override still wins
    client.patch("/children/mia", json={"adaptation_overrides": {"text_style": "short_sentence"}},
                 headers=ph)
    client.patch("/children/mia", json={"age_band": "3-4"}, headers=ph)
    p = client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()["complexity_profile"]
    assert p["text_style"] == "short_sentence" and p["quests_shown_at_once"] == 3   # band default kept


# ------------------------------------------------------------------ #
# Scenario 9                                                          #
# ------------------------------------------------------------------ #
def test_s9_advancement_suggestion_after_eight_then_immediate_verify_same_points(fam):
    client, svc, ph, ch = fam
    for day in DAYS[:8]:                                    # 8 consecutive eligible completions
        _materialise(client, ph, day)
        client.post("/me/quests/teeth/complete", json={"day": day, "note": ""}, headers=ch)
        client.post("/children/mia/quests/teeth/approve", json={"day": day}, headers=ph)

    sug = client.get("/children/mia/suggestions", headers=ph).json()
    assert len(sug) == 1
    assert sug[0]["quest_id"] == "teeth"
    assert sug[0]["from_stage"] == "PARENT_GUIDED" and sug[0]["to_stage"] == "CHILD_PARTICIPATED"

    points_before = 10  # teeth quest points
    client.post("/children/mia/quests/teeth/suggestion/accept", headers=ph)
    _materialise(client, ph, DAYS[8])
    r = client.post("/me/quests/teeth/complete", json={"day": DAYS[8], "note": ""}, headers=ch)
    assert r.json()["state"] == "verified"                  # now verifies immediately
    earns = [e for e in svc.repo.ledger_for("mia") if e.kind is LedgerKind.EARN
             and e.source.startswith("completion:teeth")]
    assert {e.points for e in earns} == {points_before}     # points value unchanged


# ------------------------------------------------------------------ #
# Scenario 10                                                         #
# ------------------------------------------------------------------ #
def test_s10_regression_to_parent_guided_no_negative_signal_no_points_change(fam):
    client, svc, ph, ch = fam
    client.put("/children/mia/quests/teeth/ownership", json={"target": "CHILD_OWNED"}, headers=ph)
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    lifetime_before = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()["lifetime_achievement"]

    plan = client.put("/children/mia/quests/teeth/ownership", json={"target": "PARENT_GUIDED"},
                      headers=ph).json()
    assert plan["direction"] == "regress"                   # accepted, no error

    after = client.get("/me/progress?week_start=2026-08-03", headers=ch).json()
    assert after["lifetime_achievement"] == lifetime_before  # no points change
    today = client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()
    blob = str(today).lower()
    for negative in ("downgrade", "regress", "lost", "back", "fail", "stage"):
        assert negative not in blob                         # child sees nothing of it


# ================================================================== #
# Cross-cutting MVP requirements                                       #
# ================================================================== #
def test_cross_positive_only_child_states_are_a_safe_subset(fam):
    client, svc, ph, ch = fam
    client.put("/children/mia/quests/read/ownership", json={"target": "CHILD_OWNED"}, headers=ph)
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/teeth/complete", json={"day": DAYS[0], "note": ""}, headers=ch)  # pending
    client.post("/me/quests/read/complete", json={"day": DAYS[0], "note": ""}, headers=ch)   # verified
    client.post("/children/mia/quests/dressed/not-yet", json={"day": DAYS[0], "note": ""}, headers=ph)
    states = {i["state"] for i in client.get(f"/me/today?day={DAYS[0]}", headers=ch).json()["items"]}
    assert states <= {"available", "pending", "verified"}   # never expired/not_yet/failed to the child


def test_cross_ownership_never_a_kpi_in_any_response_or_schema(fam):
    client, *_ = fam
    schema = str(client.get("/openapi.json").json()).lower()
    for banned in ("owned_count", "owned_pct", "ownership_score", "independence_level",
                   "% owned", "readiness_score"):
        assert banned not in schema


def test_cross_quiet_by_default_notifications_opt_in(fam):
    client, svc, ph, ch = fam
    client.put("/children/mia/quests/read/ownership", json={"target": "CHILD_OWNED"}, headers=ph)
    _materialise(client, ph, DAYS[0])
    client.post("/me/quests/read/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    # no opt-in yet → no parent notification, but the child still celebrates
    assert client.get("/children/mia/notifications", headers=ph).json() == []
    assert client.get("/me/celebrations", headers=ch).json()


def test_cross_offline_tolerant_marking_is_idempotent(fam):
    client, svc, ph, ch = fam
    client.put("/children/mia/quests/read/ownership", json={"target": "CHILD_OWNED"}, headers=ph)
    _materialise(client, ph, DAYS[0])
    for _ in range(4):   # a client replaying a queued intent on reconnect
        client.post("/me/quests/read/complete", json={"day": DAYS[0], "note": ""}, headers=ch)
    earns = [e for e in svc.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1


def test_cross_accessibility_baseline_present_in_child_client():
    html = (_WEBCLIENT_DIR / "child.html").read_text()
    assert "prefers-reduced-motion" in html                 # reduced-motion
    assert "speechSynthesis" in html and 'aria-label="Hear' in html  # audio narration + labels
    assert "min-width: 44px" in html or "min-height: 44px" in html   # target size
    assert 'class="cue"' in html                            # state as text, not colour alone


# ================================================================== #
# Explicitly out of scope — assert absence                            #
# ================================================================== #
def test_oos_no_parent_managed_assignment_path(fam):
    client, svc, ph, ch = fam
    # assignment always lands PARENT_GUIDED; there is no API arg to start elsewhere
    client.post("/quests", json={"quest_id": "new", "title": "n", "icon": "n"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "new"}, headers=ph)
    assert svc.repo.get_child_quest("mia", "new").ownership_stage.value == "PARENT_GUIDED"


def test_oos_no_photo_evidence_or_marketplace_or_multiparent_endpoints(fam):
    client, *_ = fam
    paths = client.get("/openapi.json").json()["paths"]
    joined = " ".join(paths).lower()
    for absent in ("photo", "evidence", "marketplace", "template-library", "caregiver",
                   "verifier", "invite"):
        assert absent not in joined

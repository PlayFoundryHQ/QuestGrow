"""C2 — HTTP API contract tests.

Replays AC-1, AC-2, AC-5, AC-8, AC-9, AC-11, AC-13 through HTTP, checks that
forged / cross-scope requests get 403, and scans the generated OpenAPI schema
for the INV-8 boundary (no stage / ownership-progress field on any child
response model).
"""

from __future__ import annotations

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import EventSink, InMemoryRepository, OwnershipStage, QuestGrowService  # noqa: E402
from questgrow.api import TokenStore, create_app  # noqa: E402

DAY = "2026-08-03"


@pytest.fixture
def ctx():
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)
    tokens = TokenStore()
    app = create_app(svc, tokens)
    client = TestClient(app)
    svc.create_account("acct-1")
    ptok = tokens.issue_parent("acct-1")
    ph = {"Authorization": f"Bearer {ptok}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    client.post("/quests", json={"quest_id": "teeth", "title": "Brush", "icon": "🪥", "points": 10},
                headers=ph)
    client.put("/quests/teeth/schedule", json={"recurrence": "daily"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "teeth"}, headers=ph)
    ctok = tokens.issue_child("mia")
    ch = {"Authorization": f"Bearer {ctok}"}
    return client, svc, tokens, ph, ch


def test_ac1_ac2_immediate_completion_and_single_earn(ctx):
    client, svc, tokens, ph, ch = ctx
    import questgrow
    svc.set_ownership_stage(questgrow.ParentScope("acct-1"),
                            child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_PARTICIPATED)
    client.post("/clock/materialise", json={"day": DAY}, headers=ph)

    r = client.post("/me/quests/teeth/complete", json={"day": DAY}, headers=ch)
    assert r.status_code == 200 and r.json()["state"] == "verified"      # AC-1
    assert len(svc.events.celebrations_for("mia")) == 1

    # AC-2: replay the child intent — still exactly one earn
    client.post("/me/quests/teeth/complete", json={"day": DAY}, headers=ch)
    from questgrow import LedgerKind
    earns = [e for e in svc.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1


def test_ac8_today_payload(ctx):
    client, svc, tokens, ph, ch = ctx
    client.post("/clock/materialise", json={"day": DAY}, headers=ph)
    r = client.get(f"/me/today?day={DAY}", headers=ch)
    body = r.json()
    assert body["items"][0]["quest_id"] == "teeth"
    assert body["items"][0]["waits_for_grownup"] is True                 # PARENT_GUIDED
    assert body["lifetime_achievement"] == 0
    assert body["complexity_profile"]["band"] == "5-6"


def test_ac9_weekly_consistency(ctx):
    client, svc, tokens, ph, ch = ctx
    import questgrow
    svc.set_ownership_stage(questgrow.ParentScope("acct-1"), child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    for d in ("2026-08-03", "2026-08-04", "2026-08-05"):
        client.post("/clock/materialise", json={"day": d}, headers=ph)
        client.post("/me/quests/teeth/complete", json={"day": d}, headers=ch)
    r = client.get("/me/progress?week_start=2026-08-03", headers=ch)
    assert r.json()["week_active_days"] == 3


def test_ac11_parent_managed_rejects_child_self_mark(ctx):
    client, svc, tokens, ph, ch = ctx
    import questgrow
    svc.set_ownership_stage(questgrow.ParentScope("acct-1"), child_id="mia", quest_id="teeth",
                            target=OwnershipStage.PARENT_MANAGED)
    client.post("/clock/materialise", json={"day": DAY}, headers=ph)
    r = client.post("/me/quests/teeth/complete", json={"day": DAY}, headers=ch)
    assert r.status_code == 409                                          # ContractViolation → 409


def test_ac13_advancement_suggestion_via_http(ctx):
    client, svc, tokens, ph, ch = ctx
    import questgrow
    svc.set_ownership_stage(questgrow.ParentScope("acct-1"), child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_PARTICIPATED)
    for n in range(8):
        d = f"2026-08-{3 + n:02d}"
        client.post("/clock/materialise", json={"day": d}, headers=ph)
        client.post("/me/quests/teeth/complete", json={"day": d}, headers=ch)
    r = client.get("/children/mia/suggestions", headers=ph)
    assert r.status_code == 200
    assert r.json()[0]["to_stage"] == "CHILD_OWNED"


def test_ac5_scope_enforcement(ctx):
    client, svc, tokens, ph, ch = ctx
    # child token cannot hit a parent endpoint
    assert client.post("/quests", json={"quest_id": "x", "title": "x", "icon": "x"},
                       headers=ch).status_code == 403
    assert client.get("/children/mia/approvals", headers=ch).status_code == 403
    # parent token cannot act as the child
    assert client.get(f"/me/today?day={DAY}", headers=ph).status_code == 403
    # forged token
    assert client.get(f"/me/today?day={DAY}",
                      headers={"Authorization": "Bearer nope"}).status_code == 401
    # cross-account parent cannot touch another account's child
    other = tokens.issue_parent("acct-2")
    r = client.post("/children/mia/adjustments", json={"amount": 5},
                    headers={"Authorization": f"Bearer {other}"})
    assert r.status_code in (403, 404)


def test_openapi_child_responses_have_no_stage_or_progress_aggregate(ctx):
    client, *_ = ctx
    schema = client.get("/openapi.json").json()
    child_models = ("TodayOut", "TodayItemOut", "CompletionOut", "ProgressOut", "ComplexityProfile")
    for name in child_models:
        props = schema["components"]["schemas"][name].get("properties", {})
        low = " ".join(props).lower()
        for banned in ("ownership", "stage", "independence", "readiness", "owned_count",
                       "level"):
            assert banned not in low, f"{name}.{banned}"
    # whole schema: no ownership-progress aggregate name
    blob = str(schema).lower()
    for banned in ("owned_count", "owned_pct", "independence_level", "ownership_score"):
        assert banned not in blob

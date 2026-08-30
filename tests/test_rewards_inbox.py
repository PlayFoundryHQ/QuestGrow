"""Phase M — child reward catalogue (`GET /v1/me/rewards`) and the parent-side
redemption inbox (`GET /v1/redemptions`). Both additive, read-only."""

from __future__ import annotations

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import EventSink, InMemoryRepository, OwnershipStage, QuestGrowService  # noqa: E402
from questgrow.api import TokenStore, create_app  # noqa: E402

DAY = "2026-08-03"


@pytest.fixture
def ctx():
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink())
    tokens = TokenStore()
    app = create_app(svc, tokens)
    client = TestClient(app)
    svc.create_account("acct-1")
    ph = {"Authorization": f"Bearer {tokens.issue_parent('acct-1')}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    client.post("/quests", json={"quest_id": "teeth", "title": "Brush", "icon": "🪥", "points": 10},
                headers=ph)
    client.put("/quests/teeth/schedule", json={"recurrence": "daily"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "teeth"}, headers=ph)
    svc.set_ownership_stage(__import__("questgrow").ParentScope("acct-1"),
                            child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    ch = {"Authorization": f"Bearer {tokens.issue_child('mia')}"}
    # earn 30 spendable points over three days
    for d in ("2026-08-03", "2026-08-04", "2026-08-05"):
        client.post("/clock/materialise", json={"day": d}, headers=ph)
        client.post("/me/quests/teeth/complete", json={"day": d, "note": ""}, headers=ch)
    return client, svc, tokens, ph, ch


def test_me_rewards_lists_catalogue_with_affordability_and_pending(ctx):
    client, svc, tokens, ph, ch = ctx
    client.post("/rewards", json={"reward_id": "ice", "name": "Ice cream", "icon": "🍦",
                                  "cost": 20, "mode": "parent_confirmed"}, headers=ph)
    client.post("/rewards", json={"reward_id": "park", "name": "Big trip", "icon": "🎡",
                                  "cost": 200, "mode": "parent_confirmed"}, headers=ph)

    body = client.get("/me/rewards", headers=ch).json()
    assert body["spendable_balance"] == 30
    by_id = {r["reward_id"]: r for r in body["rewards"]}
    assert by_id["ice"]["affordable"] is True and by_id["ice"]["pending"] is False
    assert by_id["park"]["affordable"] is False

    # child asks to spend -> pending, and the catalogue reflects it
    client.post("/me/rewards/ice/redeem", headers=ch)
    assert client.get("/me/rewards", headers=ch).json()["rewards"]
    assert {r["reward_id"]: r["pending"] for r in
            client.get("/me/rewards", headers=ch).json()["rewards"]}["ice"] is True


def test_redemption_inbox_then_grant_clears_it(ctx):
    client, svc, tokens, ph, ch = ctx
    client.post("/rewards", json={"reward_id": "ice", "name": "Ice cream", "icon": "🍦",
                                  "cost": 20, "mode": "parent_confirmed"}, headers=ph)
    client.post("/me/rewards/ice/redeem", headers=ch)

    inbox = client.get("/redemptions", headers=ph).json()
    assert len(inbox) == 1
    assert inbox[0]["child_name"] == "Mia"
    assert inbox[0]["reward_name"] == "Ice cream"
    assert inbox[0]["cost"] == 20

    assert client.post(f"/redemptions/{inbox[0]['id']}/grant", headers=ph).json()["state"] == "granted"
    assert client.get("/redemptions", headers=ph).json() == []
    assert client.get("/me/progress?week_start=2026-08-03", headers=ch).json()["spendable_balance"] == 10


def test_me_rewards_requires_a_child_token(ctx):
    client, svc, tokens, ph, ch = ctx
    assert client.get("/me/rewards", headers=ph).status_code == 403
    assert client.get("/redemptions", headers=ch).status_code == 403

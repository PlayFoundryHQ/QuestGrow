"""Routine assignment lifecycle — list + unassign (the parent-side inverse of
``assign_quest``). Domain-level checks plus the HTTP surface.

Rules exercised:
  * unassign drops the ChildQuest link and future/today unresolved occurrences;
  * verified/pending occurrences and every ledger entry are kept (INV-12/13);
  * unassign is parent-only and account-scoped (INV-5/18);
  * re-assigning after an unassign starts a fresh plan at the default stage.
"""

from __future__ import annotations

from datetime import timedelta

import pytest

from questgrow import (
    AuthorizationError,
    ChildScope,
    EventSink,
    InMemoryRepository,
    NotFound,
    OwnershipStage,
    ParentScope,
    QuestGrowService,
    QuestSchedule,
    Recurrence,
)
from questgrow.enums import InstanceState
from conftest import DAY, force_stage

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow.api import TokenStore, create_app  # noqa: E402


@pytest.fixture
def svc() -> QuestGrowService:
    return QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)


@pytest.fixture
def parent() -> ParentScope:
    return ParentScope(account_id="acct-1")


@pytest.fixture
def world(svc: QuestGrowService, parent: ParentScope) -> QuestGrowService:
    svc.create_account("acct-1")
    svc.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc.create_quest(parent, quest_id="teeth", title="Brush", icon="🪥", points=10)
    svc.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc.assign_quest(parent, child_id="mia", quest_id="teeth")
    return svc


def test_list_child_quests_returns_assigned_with_display(world, parent):
    rows = world.list_child_quests(parent, child_id="mia")
    assert len(rows) == 1
    cq, q = rows[0]
    assert cq.quest_id == "teeth"
    assert (q.title, q.icon, q.points) == ("Brush", "🪥", 10)


def test_unassign_removes_link_and_stops_materialising(world, parent):
    world.materialise_day(DAY)
    world.unassign_quest(parent, child_id="mia", quest_id="teeth")

    assert world.repo.get_child_quest("mia", "teeth") is None
    assert world.list_child_quests(parent, child_id="mia") == []
    # a fresh materialise does not bring it back
    world.materialise_day(DAY + timedelta(days=1))
    later = [i for i in world.repo.instances_of("mia") if i.on_date > DAY]
    assert all(i.state is InstanceState.EXPIRED or i.quest_id != "teeth" for i in later)


def test_unassign_keeps_earned_ledger_and_verified_history(world, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    world.submit_completion(ChildScope("mia"), child_id="mia", quest_id="teeth",
                            day=DAY)
    lifetime_before = world.lifetime_achievement(child_id="mia")
    assert lifetime_before == 10

    world.unassign_quest(parent, child_id="mia", quest_id="teeth")

    assert world.lifetime_achievement(child_id="mia") == lifetime_before          # INV-13
    assert len(world.repo.ledger_for("mia")) == 1                        # append-only
    verified = [i for i in world.repo.instances_of("mia")
                if i.quest_id == "teeth" and i.state is InstanceState.VERIFIED]
    assert len(verified) == 1                                           # history kept


def test_unassign_unknown_is_not_found(world, parent):
    with pytest.raises(NotFound):
        world.unassign_quest(parent, child_id="mia", quest_id="nope")


def test_unassign_is_parent_only_and_account_scoped(world, parent):
    with pytest.raises(AuthorizationError):
        world.unassign_quest(ChildScope("mia"), child_id="mia", quest_id="teeth")

    world.create_account("acct-2")
    other = ParentScope(account_id="acct-2")
    with pytest.raises(AuthorizationError):
        world.unassign_quest(other, child_id="mia", quest_id="teeth")


def test_reassign_after_unassign_starts_fresh(world, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.repo.get_child_quest("mia", "teeth").consecutive_ok_count = 4

    world.unassign_quest(parent, child_id="mia", quest_id="teeth")
    cq = world.assign_quest(parent, child_id="mia", quest_id="teeth")

    assert cq.ownership_stage is OwnershipStage.PARENT_GUIDED
    assert cq.consecutive_ok_count == 0


# --- HTTP surface -----------------------------------------------------------

@pytest.fixture
def http():
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)
    tokens = TokenStore()
    client = TestClient(create_app(svc, tokens))
    svc.create_account("acct-1")
    ph = {"Authorization": f"Bearer {tokens.issue_parent('acct-1')}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    client.post("/quests", json={"quest_id": "teeth", "title": "Brush", "icon": "🪥", "points": 10},
                headers=ph)
    client.put("/quests/teeth/schedule", json={"recurrence": "daily"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "teeth"}, headers=ph)
    return client, tokens, ph


def test_http_list_then_delete(http):
    client, tokens, ph = http
    listed = client.get("/v1/children/mia/quests", headers=ph)
    assert listed.status_code == 200
    body = listed.json()
    assert body == [{
        "quest_id": "teeth", "title": "Brush", "icon": "🪥", "points": 10,
        "ownership_stage": "PARENT_GUIDED",
    }]

    gone = client.delete("/v1/children/mia/quests/teeth", headers=ph)
    assert gone.status_code == 200 and gone.json() == {"ok": True}
    assert client.get("/v1/children/mia/quests", headers=ph).json() == []

    # second delete → 404
    assert client.delete("/v1/children/mia/quests/teeth", headers=ph).status_code == 404


def test_http_unassign_needs_parent_scope(http):
    client, tokens, ph = http
    ch = {"Authorization": f"Bearer {tokens.issue_child('mia')}"}
    assert client.delete("/v1/children/mia/quests/teeth", headers=ch).status_code == 403

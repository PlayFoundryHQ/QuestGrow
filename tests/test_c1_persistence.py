"""C1 — persistence backend + domain gaps.

* repo parity: the full AC/INV suite already runs against ``InMemoryRepository``
  via ``conftest``; here we replay a representative end-to-end flow against
  ``SqliteRepository`` and assert identical observable results.
* new domain surface: ``edit_reward``, ``set_child_profile``,
  ``daily_progress``, ``seed_starter_quests``, and the ``complexityProfile``
  wired into ``today()`` (with the INV-8 no-stage/level guarantee).
* schema scan: no stored ``balance`` / ``streak`` / ``verification_required``
  / ``independence_level`` column.
"""

from __future__ import annotations

from datetime import date

import pytest

from questgrow import (
    ChildScope,
    EventSink,
    InMemoryRepository,
    InstanceState,
    LedgerKind,
    OwnershipStage,
    ParentScope,
    QuestGrowService,
    QuestSchedule,
    Recurrence,
    RedemptionMode,
    SqliteRepository,
)
from questgrow.sqlite_repository import SCHEMA

DAY = date(2026, 8, 3)


def _service(repo):
    return QuestGrowService(repo=repo, events=EventSink(), advancement_threshold=8)


def _seed_family(svc: QuestGrowService, parent: ParentScope):
    svc.create_account("acct-1")
    svc.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc.create_quest(parent, quest_id="teeth", title="Brush teeth", icon="🪥", points=10)
    svc.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc.assign_quest(parent, child_id="mia", quest_id="teeth")


@pytest.fixture(params=["memory", "sqlite"])
def repo(request):
    return InMemoryRepository() if request.param == "memory" else SqliteRepository(":memory:")


# --- repo parity ---------------------------------------------------------
def test_end_to_end_flow_parity(repo):
    parent = ParentScope("acct-1")
    child = ChildScope("mia")
    svc = _service(repo)
    _seed_family(svc, parent)

    svc.materialise_day(DAY)
    inst = svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.PENDING                      # PARENT_GUIDED → approval

    svc.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    payload = svc.today(child, child_id="mia", day=DAY)
    assert payload.lifetime_achievement == 10
    assert payload.spendable_balance == 10
    assert [i.state for i in payload.items] == ["verified"]

    earns = [e for e in repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1
    # idempotency holds on the concrete backend
    assert repo.append_ledger(earns[0]) is False
    assert len([e for e in repo.ledger_for("mia") if e.kind is LedgerKind.EARN]) == 1

    cq = repo.get_child_quest("mia", "teeth")
    assert cq.ownership_stage is OwnershipStage.PARENT_GUIDED
    assert cq.consecutive_ok_count == 1


def test_suggestion_and_transition_parity(repo):
    parent = ParentScope("acct-1")
    child = ChildScope("mia")
    svc = _service(repo)
    _seed_family(svc, parent)
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_PARTICIPATED)

    for d in (date(2026, 8, 3 + n) for n in range(8)):
        svc.materialise_day(d)
        svc.submit_completion(child, child_id="mia", quest_id="teeth", day=d)

    sugg = svc.advancement_suggestions(parent, child_id="mia")
    assert len(sugg) == 1 and sugg[0].to_stage is OwnershipStage.CHILD_OWNED
    svc.accept_advancement_suggestion(parent, child_id="mia", quest_id="teeth")
    assert repo.get_child_quest("mia", "teeth").ownership_stage is OwnershipStage.CHILD_OWNED
    assert svc.advancement_suggestions(parent, child_id="mia") == []


# --- new domain surface ------------------------------------------------
def test_edit_reward(repo):
    parent = ParentScope("acct-1")
    svc = _service(repo)
    _seed_family(svc, parent)
    svc.create_reward(parent, reward_id="ice", name="Ice cream", icon="🍦", cost=50,
                      mode=RedemptionMode.PARENT_CONFIRMED)
    r = svc.edit_reward(parent, reward_id="ice", name="Gelato", cost=40)
    assert (r.name, r.cost) == ("Gelato", 40)
    assert repo.get_reward("ice").name == "Gelato"
    with pytest.raises(Exception):
        svc.edit_reward(parent, reward_id="ice", cost=-1)


def test_set_child_profile(repo):
    parent = ParentScope("acct-1")
    svc = _service(repo)
    _seed_family(svc, parent)
    c = svc.set_child_profile(parent, child_id="mia", avatar="🦊", age_band="3-4",
                              adaptation_overrides={"audio_narration": "always"})
    assert c.avatar == "🦊"
    got = repo.get_child("mia")
    assert got.age_band == "3-4"
    assert got.adaptation_overrides == {"audio_narration": "always"}


def test_daily_progress(repo):
    parent = ParentScope("acct-1")
    child = ChildScope("mia")
    svc = _service(repo)
    _seed_family(svc, parent)
    svc.materialise_day(DAY)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    dp = svc.daily_progress(parent, child_id="mia", day=DAY)
    assert (dp.total, dp.pending, dp.verified) == (1, 1, 0)
    svc.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    dp = svc.daily_progress(parent, child_id="mia", day=DAY)
    assert (dp.total, dp.pending, dp.verified) == (1, 0, 1)


def test_seed_starter_quests(repo):
    parent = ParentScope("acct-1")
    svc = _service(repo)
    svc.create_account("acct-1")
    created = svc.seed_starter_quests(parent)
    assert len(created) >= 3
    # idempotent — a second seed creates nothing
    assert svc.seed_starter_quests(parent) == []
    assert repo.get_schedule("teeth") is not None


def test_complexity_profile_in_today_and_no_stage_or_level(repo):
    parent = ParentScope("acct-1")
    child = ChildScope("mia")
    svc = _service(repo)
    _seed_family(svc, parent)
    svc.set_child_profile(parent, child_id="mia", age_band="3-4",
                          adaptation_overrides={"quests_shown_at_once": "2"})
    svc.materialise_day(DAY)
    payload = svc.today(child, child_id="mia", day=DAY)
    prof = payload.complexity_profile
    assert prof is not None
    assert prof.band == "3-4"
    assert prof.text_style == "icon_only"            # band default
    assert prof.quests_shown_at_once == 2            # parent override, cast to int
    # INV-8: nothing stage/level-ish anywhere in the rendering config
    blob = repr(prof).lower()
    for banned in ("ownership", "stage", "independence", "level", "parent_guided"):
        assert banned not in blob


# --- schema scan -------------------------------------------------------
def test_schema_has_no_drifting_stored_value_column():
    low = SCHEMA.lower()
    for banned in ("balance", "streak", "verification_required", "independence",
                   "ownership_level", "lifetime_points", "spendable", "owned_routine_count"):
        assert banned not in low, banned

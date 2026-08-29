"""Acceptance criteria AC-1 … AC-15 — TECHNICAL_MODEL.md §9.

Each test name and docstring cites the AC it executes.
"""

from __future__ import annotations

from datetime import date, timedelta

import pytest

from questgrow import (
    AuthorizationError,
    ChildScope,
    ContractViolation,
    InstanceState,
    LedgerKind,
    OwnershipStage,
    ParentScope,
    QuestSchedule,
    Recurrence,
    RedemptionMode,
)
from conftest import DAY, add_quest_at, force_stage


# --- AC-1 -------------------------------------------------------------------
def test_ac1_parent_guided_completion_pends_with_no_ledger_or_celebration(world, child):
    world.materialise_day(DAY)
    inst = world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.PENDING
    assert world.repo.ledger_for("mia") == []
    assert world.events.celebrations_for("mia") == []


# --- AC-2 -----------------------------------------------------------------
def test_ac2_child_owned_completion_verifies_immediately_with_one_earn_and_celebration(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    inst = world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.VERIFIED
    earns = [e for e in world.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1 and earns[0].points == 10
    assert len(world.events.celebrations_for("mia")) == 1


# --- AC-3 ---------------------------------------------------------------
def test_ac3_threshold_reached_no_parent_action_stage_unchanged_one_suggestion(world, child, parent):
    # 8 consecutive verified completions at PARENT_GUIDED (approve each)
    d = DAY
    for _ in range(8):
        world.materialise_day(d)
        world.submit_completion(child, child_id="mia", quest_id="teeth", day=d)
        world.approve(parent, child_id="mia", quest_id="teeth", day=d)
        d += timedelta(days=1)
    cq = world.repo.get_child_quest("mia", "teeth")
    assert cq.consecutive_ok_count == 8
    assert cq.ownership_stage is OwnershipStage.PARENT_GUIDED  # unchanged — system never advances
    sugg = world.advancement_suggestions(parent, child_id="mia")
    assert len(sugg) == 1
    assert sugg[0].to_stage is OwnershipStage.CHILD_PARTICIPATED
    # firing again does not create a second outstanding suggestion
    world.materialise_day(d)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=d)
    world.approve(parent, child_id="mia", quest_id="teeth", day=d)
    assert len(world.advancement_suggestions(parent, child_id="mia")) == 1


# --- AC-4 -------------------------------------------------------------
def test_ac4_regression_no_ledger_delta_no_child_event_counter_reset_reversible(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)  # one earn
    ledger_before = list(world.repo.all_ledger())
    celebrations_before = len(world.events.celebrations())
    world.repo.get_child_quest("mia", "teeth").consecutive_ok_count = 3

    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.PARENT_GUIDED)

    assert world.repo.all_ledger() == ledger_before          # no ledger delta
    assert len(world.events.celebrations()) == celebrations_before  # no new child event
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 0  # reset
    # reversible: can advance again
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_OWNED)
    assert world.repo.get_child_quest("mia", "teeth").ownership_stage is OwnershipStage.CHILD_OWNED


# --- AC-5 -----------------------------------------------------------
def test_ac5_forged_child_scope_writes_are_rejected(world, child, parent):
    world.materialise_day(DAY)
    # (a) child cannot set ownership_stage
    with pytest.raises(AuthorizationError):
        world.set_ownership_stage(child, child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_OWNED)
    # (b) child cannot write a ledger entry (no such method; adjustment is parent-only)
    with pytest.raises(AuthorizationError):
        world.apply_adjustment(child, child_id="mia", amount=100)
    # (c) child cannot force state=verified: submit at PARENT_GUIDED only ever yields pending
    inst = world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.PENDING
    # (d) child cannot act for another child (INV-18)
    other = ChildScope(child_id="not-mia")
    with pytest.raises(AuthorizationError):
        world.submit_completion(other, child_id="mia", quest_id="teeth", day=DAY)


# --- AC-6 ---------------------------------------------------------
def test_ac6_lifetime_achievement_never_decreases_over_earn_redeem_adjustment(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.create_reward(parent, reward_id="story", name="Bedtime story", icon="📖", cost=5,
                        mode=RedemptionMode.SELF_SERVICE)
    seq = []
    d = DAY
    for i in range(6):
        world.materialise_day(d)
        world.submit_completion(child, child_id="mia", quest_id="teeth", day=d)  # +10 earn
        seq.append(world.lifetime_achievement(child_id="mia"))
        if i == 2:
            world.redeem_reward(child, child_id="mia", reward_id="story")       # -5 redeem
            seq.append(world.lifetime_achievement(child_id="mia"))
        if i == 4:
            world.apply_adjustment(parent, child_id="mia", amount=3)            # +3 adjustment
            seq.append(world.lifetime_achievement(child_id="mia"))
        d += timedelta(days=1)
    assert seq == sorted(seq)                     # monotonic non-decreasing
    assert world.lifetime_achievement(child_id="mia") == 60   # 6 * 10 earn; redeem/adjust ignored
    assert world.spendable_balance(child_id="mia") == 60 - 5 + 3


# --- AC-7 -----------------------------------------------------
def test_ac7_points_value_is_stage_independent(world, child, parent):
    # complete once at PARENT_GUIDED
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    # advance to CHILD_OWNED, complete again next day
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    d2 = DAY + timedelta(days=1)
    world.materialise_day(d2)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=d2)
    earns = [e for e in world.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert [e.points for e in earns] == [10, 10]


# --- AC-8 -----------------------------------------------
def test_ac8_no_ownership_progress_aggregate_anywhere(world, child, parent):
    add_quest_at(world, parent, child_id="mia", quest_id="bed", stage=OwnershipStage.CHILD_OWNED)
    add_quest_at(world, parent, child_id="mia", quest_id="tidy", stage=OwnershipStage.CHILD_PARTICIPATED)
    add_quest_at(world, parent, child_id="mia", quest_id="read", stage=OwnershipStage.PARENT_MANAGED)
    world.materialise_day(DAY)
    payload = world.today(child, child_id="mia", day=DAY)
    blob = repr(payload).lower()
    for banned in ("owned_count", "owned_pct", "independence", "ownership_score", "% owned", "n of m"):
        assert banned not in blob
    assert not hasattr(payload, "ownership_percentage")
    # service exposes no such method
    assert not any("owned" in n and "count" in n for n in dir(world))


# --- AC-9 ---------------------------------------
def test_ac9_child_today_payload_has_no_stage_label_or_verdict(world, child, parent):
    add_quest_at(world, parent, child_id="mia", quest_id="bed", stage=OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    payload = world.today(child, child_id="mia", day=DAY)
    for item in payload.items:
        assert not hasattr(item, "ownership_stage")
        blob = repr(item).lower()
        for banned in ("parent_guided", "child_owned", "child_participated", "parent_managed",
                       "level", "ready", "verdict", "stage"):
            assert banned not in blob
        # only the two-mode bit is exposed
        assert isinstance(item.waits_for_grownup, bool)


# --- AC-10 -----------------------------
def test_ac10_parent_review_is_non_blocking(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_PARTICIPATED)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    ledger_before = list(world.repo.all_ledger())
    celeb_before = len(world.events.celebrations())

    world.create_parent_review(parent, child_id="mia", quest_id="teeth", day=DAY,
                               note="left the tap running", flagged=True)

    inst = world._get_instance("teeth", "mia", DAY)
    assert inst.state is InstanceState.VERIFIED               # unchanged
    assert world.repo.all_ledger() == ledger_before           # ledger unchanged
    assert len(world.events.celebrations()) == celeb_before    # celebration not reversed


# --- AC-11 -------------------
def test_ac11_parent_managed_has_no_child_self_mark_path(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.PARENT_MANAGED)
    world.materialise_day(DAY)
    with pytest.raises(ContractViolation):
        world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    # only a parent record transitions the instance
    inst = world.record_completion(parent, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.VERIFIED


# --- AC-12 -----------
def test_ac12_completion_delivered_twice_yields_one_earn(world, child, parent):
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)  # replay
    # also force the internal award path to run twice
    inst = world._get_instance("teeth", "mia", DAY)
    cq = world.repo.get_child_quest("mia", "teeth")
    world._award_earn(inst, cq)
    earns = [e for e in world.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1


# --- AC-13 ---
def test_ac13_parent_skips_stages_in_one_action_naming_bypassed(world, parent):
    plan = world.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                                     target=OwnershipStage.CHILD_OWNED)
    assert plan.direction == "advance"
    assert plan.bypassed == (OwnershipStage.CHILD_PARTICIPATED,)
    cq = world.repo.get_child_quest("mia", "teeth")
    assert cq.ownership_stage is OwnershipStage.CHILD_OWNED
    assert cq.consecutive_ok_count == 0
    last_audit = world.repo.audit_entries()[-1]
    assert last_audit.actor == "parent:acct-1"
    assert last_audit.action == "ownership_advance"
    assert last_audit.after == "CHILD_OWNED"


# --- AC-14 -
def test_ac14_expired_occurrence_is_neutral_for_counter(world, child, parent):
    cq = world.repo.get_child_quest("mia", "teeth")
    cq.consecutive_ok_count = 5
    world.materialise_day(DAY)                       # instance created, left available
    world.end_of_day(DAY)                            # available -> expired
    assert world._get_instance("teeth", "mia", DAY).state is InstanceState.EXPIRED
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 5


# --- IL-1 (issue #18): pending grace window --------------------------------
def test_il1_pending_grace_window(world, child, parent):
    """A PARENT_GUIDED completion the child marked is NOT swept the same day;
    it expires silently one day past the occurrence date if the parent has not
    acted; and a mid-window parent approval still works and fires the
    celebration. TECHNICAL_MODEL §4 / PARENT_CHILD_MODEL."""
    cq = world.repo.get_child_quest("mia", "teeth")
    cq.consecutive_ok_count = 4

    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)  # -> pending
    world.end_of_day(DAY)                            # same day: pending survives
    assert world._get_instance("teeth", "mia", DAY).state is InstanceState.PENDING
    assert world.events.celebrations_for("mia") == []          # no negative or positive signal yet

    # parent can still approve within the grace window
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    assert world._get_instance("teeth", "mia", DAY).state is InstanceState.VERIFIED
    assert len(world.events.celebrations_for("mia")) == 1
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 5

    # a DIFFERENT pending instance left unresolved expires silently at D+1
    d2 = DAY + timedelta(days=1)
    world.materialise_day(d2)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=d2)   # -> pending
    world.end_of_day(d2)                             # same day: survives
    assert world._get_instance("teeth", "mia", d2).state is InstanceState.PENDING
    world.end_of_day(d2 + timedelta(days=1))         # one day past: silent expiry
    assert world._get_instance("teeth", "mia", d2).state is InstanceState.EXPIRED
    assert len(world.events.celebrations_for("mia")) == 1      # no new signal from expiry
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 5  # expired is neutral


# --- IL-5 (issue backlog → C1): quest-version instance lookup regression -----
def test_il5_quest_edit_midday_keeps_instance_addressable_and_no_duplicate(world, child, parent):
    """create quest → materialise today's instance → edit quest (new version)
    → the pre-edit instance must stay completable, and re-materialising the
    day must NOT create a duplicate same-day instance. QUEST_MODEL: instances
    keep the version they were created under."""
    world.materialise_day(DAY)
    n_before = len(world.repo.all_instances())

    world.edit_quest(parent, quest_id="teeth", title="Brush teeth well")   # -> version 2

    inst = world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.PENDING                              # still addressable

    world.materialise_day(DAY)                                             # must be a no-op for this (child, quest, day)
    assert len(world.repo.all_instances()) == n_before                     # no duplicate at v2


# --- AC-15
def test_ac15_any_stage_transition_resets_counter(world, parent):
    cq = world.repo.get_child_quest("mia", "teeth")
    cq.consecutive_ok_count = 7
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                             target=OwnershipStage.CHILD_PARTICIPATED)  # advance
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 0
    world.repo.get_child_quest("mia", "teeth").consecutive_ok_count = 4
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                             target=OwnershipStage.PARENT_GUIDED)       # regress
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 0

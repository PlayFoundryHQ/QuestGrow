"""Invariants INV-1 … INV-18 — TECHNICAL_MODEL.md §8.

Structural invariants (INV-1, INV-4, INV-9) are asserted by scanning the
dataclass fields / public surface. Behavioural invariants are exercised
directly.
"""

from __future__ import annotations

import dataclasses
import inspect
from datetime import date, timedelta

import pytest

import questgrow.entities as entities
from questgrow import (
    AuthorizationError,
    ChildScope,
    ContractViolation,
    InstanceState,
    LedgerKind,
    OwnershipStage,
    ParentScope,
    VerificationBehaviour,
    verification_behaviour,
)
from questgrow.projections import TodayItem, TodayPayload
from conftest import DAY, add_quest_at, force_stage

_BANNED_FIELD_SUBSTRINGS = (
    "independence", "ownership_level", "owned_count", "owned_routine",
    "streak", "verification_required", "self_mark", "balance_cache",
)


def _all_dataclasses():
    for name, obj in vars(entities).items():
        if dataclasses.is_dataclass(obj) and isinstance(obj, type):
            yield name, obj


# --- INV-1 : no child-level independence/ownership level anywhere ----------
def test_inv1_no_independence_or_ownership_level_field():
    for name, dc in _all_dataclasses():
        for f in dataclasses.fields(dc):
            low = f.name.lower()
            assert "independence" not in low, f"{name}.{f.name}"
            assert low != "ownership_level", f"{name}.{f.name}"
    # Child specifically carries no such attribute
    assert not any("independen" in f.name.lower() for f in dataclasses.fields(entities.Child))


# --- INV-2 : ownership_stage only via a (child, quest) pairing ------------
def test_inv2_ownership_stage_only_on_child_quest():
    holders = [
        name for name, dc in _all_dataclasses()
        if any(f.name == "ownership_stage" for f in dataclasses.fields(dc))
    ]
    assert holders == ["ChildQuest"]
    assert {f.name for f in dataclasses.fields(entities.ChildQuest)} >= {"child_id", "quest_id", "ownership_stage"}


# --- INV-3 : ownership_stage is a closed 4-value vocabulary --------------
def test_inv3_ownership_stage_enum_is_the_four_stages():
    assert [s.value for s in OwnershipStage] == [
        "PARENT_MANAGED", "PARENT_GUIDED", "CHILD_PARTICIPATED", "CHILD_OWNED",
    ]
    with pytest.raises(ValueError):
        OwnershipStage("SOMETHING_ELSE")


# --- INV-4 : verification is derived, never a stored flag ---------------
def test_inv4_no_verification_flag_and_derivation_is_total():
    for name, dc in _all_dataclasses():
        for f in dataclasses.fields(dc):
            low = f.name.lower()
            assert "verification_required" not in low
            assert "self_mark" not in low
    mapping = {s: verification_behaviour(s) for s in OwnershipStage}
    assert mapping == {
        OwnershipStage.PARENT_MANAGED: VerificationBehaviour.PARENT_RECORDS,
        OwnershipStage.PARENT_GUIDED: VerificationBehaviour.REQUIRES_APPROVAL,
        OwnershipStage.CHILD_PARTICIPATED: VerificationBehaviour.IMMEDIATE,
        OwnershipStage.CHILD_OWNED: VerificationBehaviour.IMMEDIATE,
    }


# --- INV-5 : ownership_stage writable only by parent scope --------------
def test_inv5_child_and_server_cannot_write_ownership_stage(world):
    child = ChildScope("mia")
    with pytest.raises(AuthorizationError):
        world.set_ownership_stage(child, child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_OWNED)
    from questgrow.scope import ServerScope
    with pytest.raises(AuthorizationError):
        world.set_ownership_stage(ServerScope("sweep"), child_id="mia", quest_id="teeth",
                                  target=OwnershipStage.CHILD_OWNED)


# --- INV-6 : no autonomous transition; every one is parent-audited ------
def test_inv6_every_stage_change_has_a_parent_audit_entry_and_sweep_never_changes_stage(world, parent):
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_PARTICIPATED)
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.PARENT_GUIDED)
    stage_audits = [a for a in world.repo.audit_entries() if a.action.startswith("ownership_")]
    assert len(stage_audits) == 2
    assert all(a.actor.startswith("parent:") for a in stage_audits)
    # the scheduled sweep must not change ownership_stage
    before = world.repo.get_child_quest("mia", "teeth").ownership_stage
    world.materialise_day(DAY)
    world.end_of_day(DAY)
    world.end_of_day(DAY + timedelta(days=30))
    assert world.repo.get_child_quest("mia", "teeth").ownership_stage is before
    # the suggestion evaluator never mutates ownership_stage either
    cq = world.repo.get_child_quest("mia", "teeth")
    cq.consecutive_ok_count = 99
    world._maybe_suggest_advancement(cq)
    assert cq.ownership_stage is before


# --- INV-7 : regression is reversible and produces no negative artifact --
def test_inv7_regression_reversible_no_artifact(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    ledger = list(world.repo.all_ledger())
    events = len(world.events.celebrations())
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.PARENT_MANAGED)
    world.set_ownership_stage(parent, child_id="mia", quest_id="teeth", target=OwnershipStage.CHILD_OWNED)
    assert world.repo.all_ledger() == ledger
    assert len(world.events.celebrations()) == events
    inst = world._get_instance("teeth", "mia", DAY)
    assert not hasattr(inst, "failure_flag") and not hasattr(inst, "downgraded")


# --- INV-8 : child surface exposes no stage/level/verdict ---------------
def test_inv8_child_facing_types_carry_no_stage(world, child, parent):
    add_quest_at(world, parent, child_id="mia", quest_id="bed", stage=OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    payload = world.today(child, child_id="mia", day=DAY)
    assert "ownership_stage" not in {f.name for f in dataclasses.fields(TodayItem)}
    assert "ownership_stage" not in {f.name for f in dataclasses.fields(TodayPayload)}
    assert "ownership_stage" not in repr(payload)


# --- INV-9 : no ownership-progress aggregate stored or exposed ----------
def test_inv9_no_aggregate_field_or_projection():
    from questgrow import projections
    for name, obj in vars(projections).items():
        if dataclasses.is_dataclass(obj) and isinstance(obj, type):
            for f in dataclasses.fields(obj):
                low = f.name.lower()
                assert not any(b in low for b in ("owned_count", "owned_pct", "independence", "ownership_score"))
    # no projection function computes such a thing
    fn_names = [n for n, o in vars(projections).items() if inspect.isfunction(o)]
    assert not any("owned" in n or "independence" in n for n in fn_names)


# --- INV-10 : verified only via the three legal paths -------------------
def test_inv10_verified_only_via_legal_paths(world, child, parent):
    world.materialise_day(DAY)
    inst = world._get_instance("teeth", "mia", DAY)
    # no public setter for state; forging via a child request stays pending
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.PENDING
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    assert inst.state is InstanceState.VERIFIED
    # server/system cannot verify
    from questgrow.scope import ServerScope
    d2 = DAY + timedelta(days=1)
    world.materialise_day(d2)
    with pytest.raises(AuthorizationError):
        world.approve(ServerScope("x"), child_id="mia", quest_id="teeth", day=d2)


# --- INV-11 : exactly one earn per verified completion -----------------
def test_inv11_one_earn_per_verified_completion(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    # a verified instance cannot be re-submitted, and replaying the internal
    # award (retry / at-least-once delivery) still yields exactly one entry
    with pytest.raises(ContractViolation):
        world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    inst = world._get_instance("teeth", "mia", DAY)
    cq = world.repo.get_child_quest("mia", "teeth")
    world._award_earn(inst, cq)
    world._award_earn(inst, cq)
    earns = [e for e in world.repo.ledger_for("mia") if e.kind is LedgerKind.EARN]
    assert len(earns) == 1


# --- INV-12 : ledger append-only, server-written -----------------------
def test_inv12_ledger_is_append_only_and_not_client_writable(world):
    repo = world.repo
    assert not hasattr(repo, "update_ledger")
    assert not hasattr(repo, "delete_ledger")
    child = ChildScope("mia")
    with pytest.raises(AuthorizationError):
        world.apply_adjustment(child, child_id="mia", amount=5)


# --- INV-13 : Lifetime Achievement monotonic; redeem/adjust never reduce it
def test_inv13_lifetime_monotonic(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.create_reward(parent, reward_id="p", name="Park", icon="🌳", cost=8,
                        mode=__import__("questgrow").RedemptionMode.SELF_SERVICE)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    world.materialise_day(DAY + timedelta(days=1))
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY + timedelta(days=1))
    la_before = world.lifetime_achievement(child_id="mia")
    world.redeem_reward(child, child_id="mia", reward_id="p")
    world.apply_adjustment(parent, child_id="mia", amount=2)
    assert world.lifetime_achievement(child_id="mia") == la_before  # unchanged by redeem+adjust
    assert world.spendable_balance(child_id="mia") == la_before - 8 + 2


# --- INV-14 : points a function of the quest, not the stage ------------
def test_inv14_points_function_of_quest_only(world, child, parent):
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    world.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    for stage in (OwnershipStage.CHILD_PARTICIPATED, OwnershipStage.CHILD_OWNED):
        force_stage(world, parent, "mia", "teeth", stage)
        d = DAY + timedelta(days=1 + stage.rank)
        world.materialise_day(d)
        world.submit_completion(child, child_id="mia", quest_id="teeth", day=d)
    assert {e.points for e in world.repo.ledger_for("mia") if e.kind is LedgerKind.EARN} == {10}


# --- INV-15 : ParentReview never changes verified/ledger/celebration ---
def test_inv15_parent_review_non_blocking(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_PARTICIPATED)
    world.materialise_day(DAY)
    world.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    snap_ledger = list(world.repo.all_ledger())
    snap_events = list(world.events.celebrations())
    for flagged in (False, True):
        world.create_parent_review(parent, child_id="mia", quest_id="teeth", day=DAY,
                                   note="n", flagged=flagged)
    assert world._get_instance("teeth", "mia", DAY).state is InstanceState.VERIFIED
    assert world.repo.all_ledger() == snap_ledger
    assert world.events.celebrations() == snap_events


# --- INV-16 : consecutive_ok_count never exposed; expired is neutral ---
def test_inv16_counter_not_in_child_or_parent_read_models(world, child, parent):
    world.materialise_day(DAY)
    payload = world.today(child, child_id="mia", day=DAY)
    assert "consecutive_ok_count" not in repr(payload)
    approvals = world.approvals_queue(parent, child_id="mia")
    assert all("consecutive_ok_count" not in repr(i) for i in approvals)
    # expired outcome leaves the counter unchanged
    cq = world.repo.get_child_quest("mia", "teeth")
    cq.consecutive_ok_count = 6
    world.end_of_day(DAY)
    assert world.repo.get_child_quest("mia", "teeth").consecutive_ok_count == 6


# --- INV-17 : parent capability identical at every stage ---------------
def test_inv17_parent_capability_invariant_across_stages(world, child, parent):
    # the parent can approve / regress / adjust regardless of the current stage
    for stage in OwnershipStage:
        add_quest_at(world, parent, child_id="mia", quest_id=f"q_{stage.name}", stage=stage)
    for stage in OwnershipStage:
        qid = f"q_{stage.name}"
        # parent may always set the stage and always apply an adjustment
        world.set_ownership_stage(parent, child_id="mia", quest_id=qid, target=OwnershipStage.PARENT_GUIDED)
        world.apply_adjustment(parent, child_id="mia", amount=1, reason=qid)
    # child capability identical too: never allowed to set a stage
    for stage in OwnershipStage:
        with pytest.raises(AuthorizationError):
            world.set_ownership_stage(child, child_id="mia", quest_id=f"q_{stage.name}",
                                      target=OwnershipStage.CHILD_OWNED)


# --- INV-18 : child may write intent only, for own childId -------------
def test_inv18_child_intent_only_own_child(world):
    mia = ChildScope("mia")
    notmia = ChildScope("intruder")
    world.materialise_day(DAY)
    with pytest.raises(AuthorizationError):
        world.submit_completion(notmia, child_id="mia", quest_id="teeth", day=DAY)
    with pytest.raises(AuthorizationError):
        world.redeem_reward(notmia, child_id="mia", reward_id="whatever")
    with pytest.raises(AuthorizationError):
        world.apply_adjustment(mia, child_id="mia", amount=1)  # not an intent op


# --- INV-2 : re-assigning an already-assigned quest is idempotent -------
# (assign_quest must not silently discard the child's ownership stage or
#  progress count — INV-2 durable ownership state, DECISION-017)
def test_assign_quest_is_idempotent_preserves_stage_and_progress(world, child, parent):
    force_stage(world, parent, "mia", "teeth", OwnershipStage.CHILD_OWNED)
    world.repo.get_child_quest("mia", "teeth").consecutive_ok_count = 5

    again = world.assign_quest(parent, child_id="mia", quest_id="teeth")

    assert again.ownership_stage is OwnershipStage.CHILD_OWNED
    assert again.consecutive_ok_count == 5
    cq = world.repo.get_child_quest("mia", "teeth")
    assert cq.ownership_stage is OwnershipStage.CHILD_OWNED
    assert cq.consecutive_ok_count == 5

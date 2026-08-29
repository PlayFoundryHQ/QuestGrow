"""Ownership stage service — TECHNICAL_MODEL §3 (state machine) and the
``consecutive_ok_count`` rules of §4.

Pure logic only; all mutation is applied by ``QuestGrowService`` after a
parent-scope check (INV-5, INV-6). The advancement *suggestion* is derived,
never a stored transition (§7, DECISION-008).
"""

from __future__ import annotations

from dataclasses import dataclass

from .enums import OwnershipStage
from .errors import ContractViolation

# Effect of an occurrence outcome on ``consecutive_ok_count`` — TECHNICAL_MODEL §4 table.
#   completed  -> +1
#   not_yet    -> reset to 0        (DECISION-009)
#   expired    -> no effect         (DECISION-018)
#   non-scheduled day -> no effect  (DECISION-009)
#   any stage transition -> reset   (TOQ-2)  [applied in QuestGrowService, not here]


def counter_after_completed(current: int) -> int:
    return current + 1


def counter_after_not_yet(current: int) -> int:
    return 0


def counter_after_expired(current: int) -> int:
    """DECISION-018: an expired scheduled occurrence is neutral."""
    return current


def counter_after_transition(current: int) -> int:
    """TOQ-2: any ownership_stage transition resets the counter."""
    return 0


@dataclass(frozen=True)
class TransitionPlan:
    """The result of validating a requested ``ownership_stage`` change.
    ``bypassed`` is the ordered list of stages skipped on a multi-stage
    forward move (DECISION-017) — the confirmation UI must name them.
    """

    from_stage: OwnershipStage
    to_stage: OwnershipStage
    direction: str          # "advance" | "regress" | "noop"
    bypassed: tuple[OwnershipStage, ...]


def plan_transition(current: OwnershipStage, target: OwnershipStage) -> TransitionPlan:
    """Validate a parent-requested stage change.

    * Forward movement may skip stages (DECISION-017).
    * Regression may move to any earlier stage (OWNERSHIP_MODEL §7).
    * Same-stage is a no-op (not an error).
    * ``target`` must be a real ``OwnershipStage`` (INV-3) — guaranteed by the
      type, re-checked defensively.
    """

    if not isinstance(target, OwnershipStage):  # pragma: no cover - defensive
        raise ContractViolation(f"not an OwnershipStage: {target!r}")

    if target == current:
        return TransitionPlan(current, target, "noop", ())

    if target.is_later_than(current):
        # every stage strictly between current and target is bypassed
        lo, hi = current.rank + 1, target.rank
        bypassed = tuple(s for s in OwnershipStage if lo <= s.rank < hi)
        return TransitionPlan(current, target, "advance", bypassed)

    return TransitionPlan(current, target, "regress", ())


def default_stage_for_age_band(age_band: str) -> OwnershipStage:
    """Server-side derivation of the default ``ownership_stage`` at quest
    assignment (§3, TOQ-9).

    DECISION-019 (MVP is an on-ramp): the MVP default is ``PARENT_GUIDED`` for
    **every** quest, regardless of age band. ``PARENT_MANAGED`` stays a valid
    contract stage but is not produced here in MVP.

    (Post-MVP this function would branch on ``age_band``; the branch point is
    kept explicit so the change is localised.)
    """

    return OwnershipStage.PARENT_GUIDED


def should_suggest_advancement(stage: OwnershipStage, counter: int, threshold: int) -> OwnershipStage | None:
    """Derived signal (§4). Returns the single next stage to suggest, or None.

    Only stages that *have* a next stage drive a suggestion (§4): at
    ``CHILD_OWNED`` the counter is inert. ``PARENT_MANAGED`` counter behaviour
    is deferred with that stage's experience — it never suggests in MVP.
    """

    if stage is OwnershipStage.PARENT_MANAGED:
        return None
    nxt = stage.next_stage
    if nxt is None:
        return None
    return nxt if counter >= threshold else None

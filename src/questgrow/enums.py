"""Closed vocabularies from TECHNICAL_MODEL.md §2–§4.

Every enum here is a *contract* constraint:

- ``OwnershipStage``     — TECHNICAL_MODEL §3 "States"; INV-3.
- ``InstanceState``      — TECHNICAL_MODEL §4 "QuestInstance state machine".
- ``VerificationBehaviour`` — the range of the pure function in §4; INV-4.
- ``LedgerKind``         — §2 (LedgerEntry), §6.
- ``RedemptionState``    — §6 redemption modes.
- ``Recurrence``         — §2 (QuestSchedule).
- ``Actor``              — §5 actor matrix columns.

There is deliberately **no** ``verification_required`` / ``self_mark_preauthorized``
value anywhere (INV-4): verification is *derived*, never stored.
"""

from __future__ import annotations

from enum import StrEnum


class OwnershipStage(StrEnum):
    """TECHNICAL_MODEL §3. Ordered least → most child responsibility.

    The ordering defines "earlier"/"later" for advancement and regression; it
    is **not** a ranking of better/worse (DECISION-010).
    """

    PARENT_MANAGED = "PARENT_MANAGED"
    PARENT_GUIDED = "PARENT_GUIDED"
    CHILD_PARTICIPATED = "CHILD_PARTICIPATED"
    CHILD_OWNED = "CHILD_OWNED"

    @property
    def rank(self) -> int:
        return _STAGE_ORDER.index(self)

    @property
    def next_stage(self) -> "OwnershipStage | None":
        i = self.rank
        return _STAGE_ORDER[i + 1] if i + 1 < len(_STAGE_ORDER) else None

    def is_later_than(self, other: "OwnershipStage") -> bool:
        return self.rank > other.rank

    def is_earlier_than(self, other: "OwnershipStage") -> bool:
        return self.rank < other.rank


_STAGE_ORDER: tuple[OwnershipStage, ...] = (
    OwnershipStage.PARENT_MANAGED,
    OwnershipStage.PARENT_GUIDED,
    OwnershipStage.CHILD_PARTICIPATED,
    OwnershipStage.CHILD_OWNED,
)


class InstanceState(StrEnum):
    """TECHNICAL_MODEL §4 "QuestInstance state machine"."""

    AVAILABLE = "available"
    PENDING = "pending"
    VERIFIED = "verified"
    NOT_YET = "not_yet"
    EXPIRED = "expired"


class VerificationBehaviour(StrEnum):
    """Range of ``verification_behaviour(ownership_stage)`` — TECHNICAL_MODEL §4.

    Computed, never persisted (INV-4).
    """

    PARENT_RECORDS = "PARENT_RECORDS"        # PARENT_MANAGED — no child self-mark path
    REQUIRES_APPROVAL = "REQUIRES_APPROVAL"  # PARENT_GUIDED — completion pends until approved
    IMMEDIATE = "IMMEDIATE"                  # CHILD_PARTICIPATED / CHILD_OWNED — verified on self-mark


class LedgerKind(StrEnum):
    """TECHNICAL_MODEL §2 (LedgerEntry), §6."""

    EARN = "earn"          # points >= 0, exactly one per verified completion (INV-11)
    REDEEM = "redeem"      # points <= 0, affects Spendable Balance only (INV-13)
    ADJUSTMENT = "adjustment"  # additive-only in MVP, parent-instructed only (TOQ-5)


class RedemptionState(StrEnum):
    """TECHNICAL_MODEL §6 "Redemption modes"."""

    GRANTED = "granted"
    PENDING = "pending"     # parent_confirmed awaiting the parent
    DECLINED = "declined"   # gentle, no penalty


class RedemptionMode(StrEnum):
    SELF_SERVICE = "self_service"
    PARENT_CONFIRMED = "parent_confirmed"


class Recurrence(StrEnum):
    """TECHNICAL_MODEL §2 (QuestSchedule): daily / weekday set / weekly."""

    DAILY = "daily"
    WEEKDAYS = "weekdays"   # a specific set of ISO weekdays (1=Mon … 7=Sun)
    WEEKLY = "weekly"       # once per ISO week, on an anchor weekday (impl choice — see IMPLEMENTATION_NOTES IL-2)


class Actor(StrEnum):
    """TECHNICAL_MODEL §5 actor matrix columns."""

    CHILD = "child"
    PARENT = "parent"
    SERVER = "server"
    SYSTEM = "system"


# Verification-behaviour derivation — TECHNICAL_MODEL §4. Pure function; INV-4.
_VERIFICATION_BY_STAGE: dict[OwnershipStage, VerificationBehaviour] = {
    OwnershipStage.PARENT_MANAGED: VerificationBehaviour.PARENT_RECORDS,
    OwnershipStage.PARENT_GUIDED: VerificationBehaviour.REQUIRES_APPROVAL,
    OwnershipStage.CHILD_PARTICIPATED: VerificationBehaviour.IMMEDIATE,
    OwnershipStage.CHILD_OWNED: VerificationBehaviour.IMMEDIATE,
}


def verification_behaviour(stage: OwnershipStage) -> VerificationBehaviour:
    """TECHNICAL_MODEL §4: ``verification_behaviour(ownership_stage)``.

    This is the *only* place verification behaviour is decided (INV-4,
    DECISION-007). No stored flag is ever consulted.
    """

    return _VERIFICATION_BY_STAGE[stage]

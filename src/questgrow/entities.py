"""Domain entities — TECHNICAL_MODEL.md §2 "Domain concepts".

This is not a persistence schema (§10 / TOQ-7). Each dataclass is the set of
things an implementation must represent. Mutation of authoritative state
happens only through ``QuestGrowService`` (§5, §7); the dataclasses carry no
write logic of their own.

INV-1 / INV-4: note what is deliberately **absent** —
  * no ``Child.independence_level`` / ``ownership_level`` field anywhere;
  * no ``Quest.verification_required`` / ``self_mark_preauthorized`` field;
  * no stored ``balance`` / ``lifetime_points`` / ``spendable_points`` /
    ``owned_routine_count`` / ``streak`` field.
The only ownership state is ``ChildQuest.ownership_stage`` (INV-2).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime

from .enums import (
    InstanceState,
    LedgerKind,
    OwnershipStage,
    Recurrence,
    RedemptionMode,
    RedemptionState,
)


@dataclass
class Account:
    account_id: str
    parent_gate_configured: bool = True
    points_enabled: bool = True  # §6: points may be disabled account-wide
    notifications_enabled: bool = False  # C4: parent notifications are opt-in


@dataclass
class Child:
    child_id: str
    account_id: str
    name: str
    avatar: str = ""
    age_band: str = ""          # e.g. "3-4" / "5-6" / "7-8" — coarse band (ARCHITECTURE: privacy)
    birthdate: date | None = None
    adaptation_overrides: dict[str, str] = field(default_factory=dict)
    # deliberately NO independence/ownership level here (INV-1)


@dataclass(frozen=True)
class QuestId:
    """Quest identity is ``questId`` + ``version`` (§2). Edits create a new
    version; historical instances keep the version they were created under.
    """

    quest_id: str
    version: int = 1


@dataclass
class Quest:
    id: QuestId
    account_id: str
    title: str
    icon: str
    points: int = 10                 # §6: value is independent of ownership_stage (INV-14)
    age_suitability: str = ""        # informational hint only
    active: bool = True
    archived: bool = False
    # NO verification_required / self_mark_preauthorized (INV-4)


@dataclass
class QuestSchedule:
    quest_id: str
    recurrence: Recurrence = Recurrence.DAILY
    weekdays: frozenset[int] = frozenset()   # ISO weekdays 1..7 (for WEEKDAYS / WEEKLY anchor)
    start: date | None = None
    end: date | None = None


@dataclass
class ChildQuest:
    """The ``(childId, questId)`` pairing (§2). Carries the *only* ownership
    state in the system (INV-2). ``ownership_stage`` and
    ``consecutive_ok_count`` are writable only in parent scope / by the server
    on parent instruction (INV-5, INV-6).
    """

    child_id: str
    quest_id: str
    ownership_stage: OwnershipStage
    consecutive_ok_count: int = 0
    assigned_at: datetime | None = None


@dataclass
class QuestInstance:
    """Identity ``(questId@version, childId, date)`` (§2, §4). Also the
    idempotency anchor for the one-``earn``-per-completion rule (INV-11 / TOQ-3).
    ``state`` is server-only (§4).
    """

    quest_id: str
    quest_version: int
    child_id: str
    on_date: date
    state: InstanceState = InstanceState.AVAILABLE
    stage_at_completion: OwnershipStage | None = None
    parent_note: str = ""            # optional gentle note on a not_yet

    @property
    def key(self) -> tuple[str, int, str, str]:
        return (self.quest_id, self.quest_version, self.child_id, self.on_date.isoformat())


@dataclass
class CompletionRequest:
    """Child (or parent-record) *intent*. The child never writes
    ``QuestInstance.state``; it creates this and the server resolves it
    (INV-10, INV-18).
    """

    id: str
    quest_instance_key: tuple[str, int, str, str]
    child_id: str
    created_at: datetime
    note: str = ""
    evidence_ref: str | None = None
    recorded_by_parent: bool = False  # true only for the PARENT_RECORDS path


@dataclass
class ParentReview:
    """Non-blocking post-hoc parent glance on a verified completion
    (§4, INV-15). Creating one never touches instance state or the ledger.
    """

    id: str
    quest_instance_key: tuple[str, int, str, str]
    child_id: str
    note: str
    created_at: datetime
    flagged_problem: bool = False


@dataclass(frozen=True)
class LedgerEntry:
    """Append-only, server-written only (§6, INV-12). Identity = ``id`` plus an
    ``idempotency_key``; for ``earn`` the key is the QuestInstance identity
    (INV-11 / TOQ-3).
    """

    id: str
    child_id: str
    kind: LedgerKind
    points: int              # signed: earn >= 0, redeem <= 0, adjustment > 0 in MVP
    source: str
    created_at: datetime
    idempotency_key: str


@dataclass
class Reward:
    reward_id: str
    account_id: str
    name: str
    icon: str
    cost: int
    redemption_mode: RedemptionMode
    active: bool = True


@dataclass
class RewardRedemption:
    id: str
    reward_id: str
    child_id: str
    state: RedemptionState
    requested_at: datetime
    resolved_at: datetime | None = None


@dataclass(frozen=True)
class AuditLogEntry:
    """Server-only. Records parent actions on meaningful state, including
    **every** ``ownership_stage`` transition (§2, INV-6).
    """

    id: str
    actor: str               # "parent:<account_id>" / "server:<reason>"
    action: str
    target: str
    before: str
    after: str
    created_at: datetime


@dataclass(frozen=True)
class AdvancementSuggestion:
    """A *derived* signal, not stored authoritative state (§7). Proposes
    exactly one stage (DECISION-008). At most one outstanding per ChildQuest
    (§3).
    """

    child_id: str
    quest_id: str
    from_stage: OwnershipStage
    to_stage: OwnershipStage
    dismissed: bool = False

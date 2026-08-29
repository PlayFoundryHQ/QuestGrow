"""QuestGrow MVP domain.

Implemented strictly against ``docs/architecture/TECHNICAL_MODEL.md`` (the
contract), which is itself bound to ``DECISION_LOG.md`` (DECISION-001…019) and
``OWNERSHIP_MODEL.md``. See ``docs/architecture/IMPLEMENTATION_NOTES.md`` for
the code → §/INV/AC map and the implementation-level notes (IL-1 resolved via
issue #18; IL-2 scheduling anchor; known defects tracked for the persistence
phase).
"""

from __future__ import annotations

from .enums import (
    Actor,
    InstanceState,
    LedgerKind,
    OwnershipStage,
    Recurrence,
    RedemptionMode,
    RedemptionState,
    VerificationBehaviour,
    verification_behaviour,
)
from .errors import AuthorizationError, ContractViolation, NotFound, QuestGrowError
from .events import CelebrationEvent, EventSink
from .entities import QuestSchedule
from .repository import InMemoryRepository
from .scope import ChildScope, ParentScope, ServerScope
from .service import DEFAULT_ADVANCEMENT_THRESHOLD, QuestGrowService

__all__ = [
    "QuestGrowService",
    "DEFAULT_ADVANCEMENT_THRESHOLD",
    "InMemoryRepository",
    "EventSink",
    "CelebrationEvent",
    "ChildScope",
    "ParentScope",
    "ServerScope",
    "QuestSchedule",
    "OwnershipStage",
    "InstanceState",
    "VerificationBehaviour",
    "verification_behaviour",
    "LedgerKind",
    "Recurrence",
    "RedemptionMode",
    "RedemptionState",
    "Actor",
    "QuestGrowError",
    "AuthorizationError",
    "ContractViolation",
    "NotFound",
]

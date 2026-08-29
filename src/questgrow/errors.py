"""Error types. ``AuthorizationError`` is the enforcement point for the
TECHNICAL_MODEL §5 actor matrix and INV-5 / INV-12 / INV-17 / INV-18.
"""

from __future__ import annotations


class QuestGrowError(Exception):
    """Base for all domain errors."""


class AuthorizationError(QuestGrowError):
    """A caller attempted an operation its scope does not permit
    (TECHNICAL_MODEL §5). Raised for: child-scope writes to authoritative
    state, cross-child intent, forged state transitions, autonomous
    ownership-stage changes.
    """


class ContractViolation(QuestGrowError):
    """An operation would violate an invariant (e.g. a non-additive
    adjustment, an illegal state transition, an out-of-vocabulary value).
    """


class NotFound(QuestGrowError):
    """A referenced entity does not exist."""

"""Error types. ``AuthorizationError`` is the enforcement point for the
TECHNICAL_MODEL §5 actor matrix and INV-5 / INV-12 / INV-17 / INV-18.
"""

from __future__ import annotations


class QuestGrowError(Exception):
    """Base for all domain errors. ``code`` is a stable, machine-readable slug
    for API clients (Phase F) — the human ``str(exc)`` message may change, the
    code does not."""

    code = "error"
    http_status = 400


class AuthorizationError(QuestGrowError):
    """A caller attempted an operation its scope does not permit
    (TECHNICAL_MODEL §5). Raised for: child-scope writes to authoritative
    state, cross-child intent, forged state transitions, autonomous
    ownership-stage changes.
    """

    code = "not_authorized"
    http_status = 403


class ContractViolation(QuestGrowError):
    """An operation would violate an invariant (e.g. a non-additive
    adjustment, an illegal state transition, an out-of-vocabulary value).
    """

    code = "contract_violation"
    http_status = 409


class NotFound(QuestGrowError):
    """A referenced entity does not exist."""

    code = "not_found"
    http_status = 404


class AuthenticationError(QuestGrowError):
    """No valid credential / token was presented (transport concern — Phase F)."""

    code = "not_authenticated"
    http_status = 401


class BadRequest(QuestGrowError):
    """A request parameter is malformed (transport concern — Phase F)."""

    code = "bad_request"
    http_status = 422

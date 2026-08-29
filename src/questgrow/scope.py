"""Actor scopes — TECHNICAL_MODEL §5 "Authority / actor matrix".

A caller presents a scope. The service (``QuestGrowService``) checks it before
every write. This is the code-level trust boundary; ARCHITECTURE's "trust
boundary is architectural" constraint and INV-5 / INV-17 / INV-18 are enforced
here rather than only in a UI.

The matrix assumes a single parent scope per account (TOQ-8, deferred). The
``account_id`` on ``ParentScope`` is the seam that keeps a future
second-caregiver role from being costly to add.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ChildScope:
    """A child-scope token. May create *intent* rows only, and only for its
    own ``child_id`` (INV-18). Cannot write any authoritative state.
    """

    child_id: str


@dataclass(frozen=True)
class ParentScope:
    """A parent-scope token (post parent-gate). The only scope that may change
    meaningful state (DECISION-016). Its capability set is identical at every
    ``ownership_stage`` (INV-17) — nothing in the code branches parent
    capability on a stage.
    """

    account_id: str


# The server itself. Used internally for the scheduled ``expired`` sweep and
# for ledger writes that follow a validated event. Never exposed to a caller.
@dataclass(frozen=True)
class ServerScope:
    reason: str = ""


type Scope = ChildScope | ParentScope | ServerScope

"""Projections / read-models — TECHNICAL_MODEL §7.

Never authoritative; always recomputed from the ledger / instances. Nothing
here is stored. Specifically **not** present: any ``balance`` column, any
ownership-progress aggregate (INV-9), any ``ownership_stage`` in a child-facing
payload (INV-8).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from .adaptation import ComplexityProfile
from .entities import LedgerEntry
from .enums import LedgerKind


def lifetime_achievement(entries: list[LedgerEntry]) -> int:
    """``Σ earn`` — monotonic non-decreasing (INV-13, DECISION-015).

    Redeem and adjustment entries are ignored here by construction.
    """

    return sum(e.points for e in entries if e.kind is LedgerKind.EARN)


def spendable_balance(entries: list[LedgerEntry]) -> int:
    """``Σ earn − Σ |redeem| ± adjustment`` (DECISION-015).

    ``redeem`` points are stored non-positive; ``adjustment`` is additive-only
    in MVP. Summing the signed ``points`` of all entries yields the balance.
    """

    return sum(e.points for e in entries)


@dataclass(frozen=True)
class TodayItem:
    """One row of the child-facing "today" view. INV-8: carries no
    ``ownership_stage`` / stage label / readiness verdict. It exposes only the
    two child-visible reward modes, as a single boolean.
    """

    quest_id: str
    title: str
    icon: str
    state: str                    # available | pending | verified  (child-visible subset)
    waits_for_grownup: bool       # True == Mode A (do → wait → celebrate); False == Mode B


@dataclass(frozen=True)
class TodayPayload:
    child_id: str
    on_date: str
    items: tuple[TodayItem, ...]
    lifetime_achievement: int
    spendable_balance: int
    complexity_profile: ComplexityProfile | None = None  # §13 rendering config — no stage/level (INV-8)
    # deliberately: no ownership %, no "N of M owned", no independence score (INV-9)


@dataclass(frozen=True)
class DailyProgress:
    """Parent-facing daily roll-up for one child. Plain counts of instance
    states for ``on_date`` — no ownership aggregate, no streak (INV-9/INV-16).
    """

    child_id: str
    on_date: str
    total: int
    verified: int
    pending: int
    available: int
    expired: int


@dataclass(frozen=True)
class WeeklyConsistency:
    """Progressive consistency, never a streak (DECISION-013/014). Describes
    only what happened; a quieter week is simply a smaller number and there is
    no "chain" to break.
    """

    child_id: str
    week_start: str
    active_days: int              # count of days in the week with >=1 verified completion

"""Celebration / notification event sink.

TECHNICAL_MODEL §4 "On ``verified``": emit a ``completion.verified`` event
that drives the child's celebration. ARCHITECTURE "Notification service":
opt-in, informational, never loss-framed, never targets the child.

This is the interface a real notification/celebration transport would
implement. Tests assert on the recorded events (AC-1, AC-2, AC-10).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime


@dataclass(frozen=True)
class CelebrationEvent:
    """A ``completion.verified`` event. It carries no ownership-stage label
    (INV-8) — only what the child needs to celebrate.
    """

    child_id: str
    quest_id: str
    on_date: str
    points_awarded: int
    at: datetime


class EventSink:
    def __init__(self) -> None:
        self._events: list[CelebrationEvent] = []

    def emit_celebration(self, e: CelebrationEvent) -> None:
        self._events.append(e)

    def celebrations(self) -> list[CelebrationEvent]:
        return list(self._events)

    def celebrations_for(self, child_id: str) -> list[CelebrationEvent]:
        return [e for e in self._events if e.child_id == child_id]

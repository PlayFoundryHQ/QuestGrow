"""Celebration / notification event sink.

TECHNICAL_MODEL §4 "On ``verified``": emit a ``completion.verified`` event
that drives the child's celebration. ARCHITECTURE "Notification service":
opt-in, informational, never loss-framed, never targets the child.

This is the interface a real notification/celebration transport would
implement. Tests assert on the recorded events (AC-1, AC-2, AC-10).

C4 adds two delivery lanes over the same sink, both **poll**-based (no push in
MVP — off the D1 critical path):

* the **child celebration** lane — every ``completion.verified`` lands here,
  regardless of any account setting; it is what plays the animation.
* the **parent notification** lane — a per-account feed, populated only when
  ``Account.notifications_enabled`` is set (opt-in). Text comes from
  ``notifications.render_*`` — informational only, never loss-framed, never
  addressed to the child.
"""

from __future__ import annotations

from dataclasses import dataclass
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


@dataclass(frozen=True)
class ParentNotification:
    """An informational, parent-facing message. Never targets the child; never
    loss-framed (ARCHITECTURE "Notification service")."""

    account_id: str
    child_id: str
    kind: str            # e.g. "completion.verified"
    text: str
    at: datetime


class EventSink:
    def __init__(self) -> None:
        self._events: list[CelebrationEvent] = []
        self._parent: list[ParentNotification] = []

    # -- child celebration lane (always on) ------------------------
    def emit_celebration(self, e: CelebrationEvent) -> None:
        self._events.append(e)

    def celebrations(self) -> list[CelebrationEvent]:
        return list(self._events)

    def celebrations_for(self, child_id: str) -> list[CelebrationEvent]:
        return [e for e in self._events if e.child_id == child_id]

    def celebrations_since(self, child_id: str, since: datetime | None) -> list[CelebrationEvent]:
        return [
            e for e in self._events
            if e.child_id == child_id and (since is None or e.at > since)
        ]

    # -- parent notification lane (opt-in) ------------------------
    def emit_parent_notification(self, n: ParentNotification) -> None:
        self._parent.append(n)

    def parent_notifications(self) -> list[ParentNotification]:
        return list(self._parent)

    def parent_notifications_since(
        self, account_id: str, since: datetime | None
    ) -> list[ParentNotification]:
        return [
            n for n in self._parent
            if n.account_id == account_id and (since is None or n.at > since)
        ]

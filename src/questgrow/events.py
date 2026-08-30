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
    """In-process sink — the default for tests and the pure-domain path.
    ``SqlEventSink`` (Phase F) is the restart-safe substrate for a server."""

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


class SqlEventSink:
    """Restart-safe ``EventSink`` backed by ``celebration_event`` /
    ``parent_notification`` (migration ``0002``). Identical semantics: the
    child celebration lane records every ``completion.verified``; the parent
    lane is written only when the caller (the service) decides the account has
    opted in. Rows are never deleted."""

    def __init__(self, db) -> None:
        self.db = db

    def _next_seq(self, table: str) -> int:
        row = self.db.fetchone(f"SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM {table}")
        return row["n"]

    # -- child celebration lane ---------------------------------
    def emit_celebration(self, e: CelebrationEvent) -> None:
        with self.db.transaction():
            self.db.execute(
                "INSERT INTO celebration_event (seq, child_id, quest_id, on_date, "
                "points_awarded, at) VALUES (?, ?, ?, ?, ?, ?)",
                (self._next_seq("celebration_event"), e.child_id, e.quest_id, e.on_date,
                 e.points_awarded, e.at.isoformat()),
            )

    @staticmethod
    def _cel(r) -> CelebrationEvent:
        return CelebrationEvent(child_id=r["child_id"], quest_id=r["quest_id"],
                                on_date=r["on_date"], points_awarded=r["points_awarded"],
                                at=datetime.fromisoformat(r["at"]))

    def celebrations(self) -> list[CelebrationEvent]:
        return [self._cel(r) for r in
                self.db.fetchall("SELECT * FROM celebration_event ORDER BY seq")]

    def celebrations_for(self, child_id: str) -> list[CelebrationEvent]:
        return [self._cel(r) for r in self.db.fetchall(
            "SELECT * FROM celebration_event WHERE child_id = ? ORDER BY seq", (child_id,))]

    def celebrations_since(self, child_id: str, since: datetime | None) -> list[CelebrationEvent]:
        if since is None:
            return self.celebrations_for(child_id)
        return [self._cel(r) for r in self.db.fetchall(
            "SELECT * FROM celebration_event WHERE child_id = ? AND at > ? ORDER BY seq",
            (child_id, since.isoformat()))]

    # -- parent notification lane -------------------------------
    def emit_parent_notification(self, n: ParentNotification) -> None:
        with self.db.transaction():
            self.db.execute(
                "INSERT INTO parent_notification (seq, account_id, child_id, kind, text, at) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (self._next_seq("parent_notification"), n.account_id, n.child_id, n.kind,
                 n.text, n.at.isoformat()),
            )

    @staticmethod
    def _note(r) -> ParentNotification:
        return ParentNotification(account_id=r["account_id"], child_id=r["child_id"],
                                  kind=r["kind"], text=r["text"],
                                  at=datetime.fromisoformat(r["at"]))

    def parent_notifications(self) -> list[ParentNotification]:
        return [self._note(r) for r in
                self.db.fetchall("SELECT * FROM parent_notification ORDER BY seq")]

    def parent_notifications_since(
        self, account_id: str, since: datetime | None
    ) -> list[ParentNotification]:
        if since is None:
            return [self._note(r) for r in self.db.fetchall(
                "SELECT * FROM parent_notification WHERE account_id = ? ORDER BY seq", (account_id,))]
        return [self._note(r) for r in self.db.fetchall(
            "SELECT * FROM parent_notification WHERE account_id = ? AND at > ? ORDER BY seq",
            (account_id, since.isoformat()))]

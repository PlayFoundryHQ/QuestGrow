"""Parent-notification templates (C4).

ARCHITECTURE "Notification service": opt-in, **informational** templates only.
No re-engagement, no loss framing, no "your child is waiting", no streak
language, never addressed to the child. ``tests/test_notifications.py`` scans
the rendered output of every template against a banned-phrase list.

Templates are plain functions returning a string. Keep them factual and
neutral: what happened, for whom.
"""

from __future__ import annotations

# Phrases a parent notification must never contain (scanned in tests).
BANNED_SUBSTRINGS = (
    "streak", "don't lose", "dont lose", "you'll lose", "youll lose",
    "waiting for you", "is waiting", "falling behind", "keep it up",
    "act now", "hurry", "last chance", "missed", "broke your",
)


def render_completion_verified(*, child_name: str, quest_title: str, count_today: int | None = None) -> str:
    """A completion was verified. Factual, no urgency."""
    if count_today and count_today > 1:
        return f"{child_name} finished {quest_title}. That's {count_today} quests done today."
    return f"{child_name} finished {quest_title}."

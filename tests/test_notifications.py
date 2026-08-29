"""C4 — notification / celebration transport.

- `completion.verified` reaches the child celebration poll;
- a Mode-A (REQUIRES_APPROVAL) completion produces the event only on parent
  approval; a Mode-B (IMMEDIATE) completion produces it immediately;
- with notifications opted out (the default), no parent notification is
  produced — but the child celebration is still available;
- template output carries no streak / loss / "is waiting" wording.
"""

from __future__ import annotations

from datetime import date

import pytest

from questgrow import (
    ChildScope,
    EventSink,
    InMemoryRepository,
    OwnershipStage,
    ParentScope,
    QuestGrowService,
    QuestSchedule,
    Recurrence,
)
from questgrow.notifications import BANNED_SUBSTRINGS, render_completion_verified

DAY = date(2026, 8, 3)


def _world(notifications_enabled: bool):
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)
    parent = ParentScope("acct-1")
    svc.create_account("acct-1", notifications_enabled=notifications_enabled)
    svc.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc.create_quest(parent, quest_id="teeth", title="Brush teeth", icon="🪥", points=10)
    svc.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc.assign_quest(parent, child_id="mia", quest_id="teeth")
    svc.materialise_day(DAY)
    return svc, parent, ChildScope("mia")


def test_mode_a_event_only_on_approval():
    svc, parent, child = _world(notifications_enabled=True)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)  # PARENT_GUIDED → pending
    assert svc.events.celebrations_for("mia") == []
    assert svc.events.parent_notifications() == []

    svc.approve(parent, child_id="mia", quest_id="teeth", day=DAY)
    assert len(svc.events.celebrations_for("mia")) == 1
    assert len(svc.events.parent_notifications_since("acct-1", None)) == 1


def test_mode_b_event_immediately():
    svc, parent, child = _world(notifications_enabled=True)
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)  # IMMEDIATE
    assert len(svc.events.celebrations_for("mia")) == 1
    assert len(svc.events.parent_notifications_since("acct-1", None)) == 1


def test_opt_out_suppresses_parent_notification_but_not_celebration():
    svc, parent, child = _world(notifications_enabled=False)  # the default
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert len(svc.events.celebrations_for("mia")) == 1          # child still celebrates
    assert svc.events.parent_notifications() == []              # parent feed empty


def test_toggle_notifications_at_runtime():
    svc, parent, child = _world(notifications_enabled=False)
    svc.set_account_notifications(parent, enabled=True)
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    assert len(svc.events.parent_notifications_since("acct-1", None)) == 1


def test_since_filter():
    svc, parent, child = _world(notifications_enabled=True)
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    ev = svc.events.celebrations_for("mia")[0]
    assert svc.events.celebrations_since("mia", ev.at) == []     # strictly after
    assert len(svc.events.celebrations_since("mia", None)) == 1


def test_template_has_no_loss_or_streak_wording():
    variants = [
        render_completion_verified(child_name="Mia", quest_title="Brush teeth"),
        render_completion_verified(child_name="Mia", quest_title="Brush teeth", count_today=3),
    ]
    for text in variants:
        low = text.lower()
        for banned in BANNED_SUBSTRINGS:
            assert banned not in low, (banned, text)
        assert "mia" in low  # parent-facing: names the child, doesn't address them


def test_template_never_addresses_the_child():
    text = render_completion_verified(child_name="Mia", quest_title="Brush teeth").lower()
    for second_person in ("you did", "you finished", "your turn", "well done"):
        assert second_person not in text

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
    RedemptionMode,
)

DAY = date(2026, 8, 3)  # a Monday


@pytest.fixture
def svc() -> QuestGrowService:
    return QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)


@pytest.fixture
def parent() -> ParentScope:
    return ParentScope(account_id="acct-1")


@pytest.fixture
def world(svc: QuestGrowService, parent: ParentScope):
    """A minimal set-up family: account, one child, a daily quest 'teeth'
    worth 10 points, assigned to the child (default stage PARENT_GUIDED).
    """
    svc.create_account("acct-1")
    svc.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc.create_quest(parent, quest_id="teeth", title="Brush teeth", icon="🪥", points=10)
    svc.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc.assign_quest(parent, child_id="mia", quest_id="teeth")
    return svc


@pytest.fixture
def child() -> ChildScope:
    return ChildScope(child_id="mia")


def force_stage(svc: QuestGrowService, parent: ParentScope, child_id: str, quest_id: str,
                stage: OwnershipStage) -> None:
    """Test helper — move a ChildQuest to any stage through the real API
    (advance may skip; regress may go to any earlier stage, incl. PARENT_MANAGED)."""
    svc.set_ownership_stage(parent, child_id=child_id, quest_id=quest_id, target=stage)


def add_quest_at(svc: QuestGrowService, parent: ParentScope, *, child_id: str, quest_id: str,
                 stage: OwnershipStage, points: int = 10,
                 recurrence: Recurrence = Recurrence.DAILY) -> None:
    svc.create_quest(parent, quest_id=quest_id, title=quest_id, icon="⭐", points=points)
    svc.set_schedule(parent, quest_id=quest_id, schedule=QuestSchedule(quest_id, recurrence))
    svc.assign_quest(parent, child_id=child_id, quest_id=quest_id)
    if stage is not OwnershipStage.PARENT_GUIDED:
        force_stage(svc, parent, child_id, quest_id, stage)

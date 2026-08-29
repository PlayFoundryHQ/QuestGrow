"""Quest & schedule service — TECHNICAL_MODEL §4 (QuestInstance identity),
ARCHITECTURE "Quest & schedule service".

Instance materialisation is **eager** per ``(quest@version, child, date)``
(TOQ-7). ``due_on`` decides whether a schedule produces an occurrence on a
given date; ``WEEKLY`` uses an anchor weekday — a documented implementation
choice (IMPLEMENTATION_NOTES IL-2), not a product decision.
"""

from __future__ import annotations

from datetime import date

from .entities import QuestSchedule
from .enums import Recurrence


def due_on(schedule: QuestSchedule, day: date) -> bool:
    """Is ``schedule`` scheduled to produce an occurrence on ``day``?

    Used both to materialise instances and to distinguish a
    "non-scheduled day" (no effect on ``consecutive_ok_count``, DECISION-009)
    from a scheduled occurrence that expired (also no effect, DECISION-018).
    """

    if schedule.start and day < schedule.start:
        return False
    if schedule.end and day > schedule.end:
        return False

    iso_weekday = day.isoweekday()  # 1 = Monday … 7 = Sunday

    match schedule.recurrence:
        case Recurrence.DAILY:
            return True
        case Recurrence.WEEKDAYS:
            return iso_weekday in schedule.weekdays
        case Recurrence.WEEKLY:
            # IL-2: "weekly" is realised as "once per ISO week on an anchor
            # weekday". Anchor = the single weekday in ``weekdays`` if given,
            # else the schedule's ``start`` weekday, else Monday.
            if schedule.weekdays:
                anchor = min(schedule.weekdays)
            elif schedule.start:
                anchor = schedule.start.isoweekday()
            else:
                anchor = 1
            return iso_weekday == anchor
    return False

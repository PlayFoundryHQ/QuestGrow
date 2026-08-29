"""In-memory repository — the persistence *seam*.

TECHNICAL_MODEL §10 / TOQ-7: the persistence engine is an open construction
question; instance materialisation is eager for MVP. This class is the
interface a real datastore would implement. It stores authoritative state
only; projections (§7) are never stored here.

``LedgerEntry`` has **no** update or delete method — append-only (INV-12).
"""

from __future__ import annotations

from datetime import date

from .entities import (
    Account,
    AdvancementSuggestion,
    AuditLogEntry,
    Child,
    ChildQuest,
    CompletionRequest,
    LedgerEntry,
    ParentReview,
    Quest,
    QuestId,
    QuestSchedule,
    Reward,
    RewardRedemption,
)


class InMemoryRepository:
    def __init__(self) -> None:
        self.accounts: dict[str, Account] = {}
        self.children: dict[str, Child] = {}
        self.quests: dict[tuple[str, int], Quest] = {}
        self.schedules: dict[str, QuestSchedule] = {}
        self.child_quests: dict[tuple[str, str], ChildQuest] = {}
        self.instances: dict[tuple[str, int, str, str], "InstanceRecord"] = {}
        self.completion_requests: dict[str, CompletionRequest] = {}
        self.parent_reviews: list[ParentReview] = []
        self._ledger: list[LedgerEntry] = []
        self._ledger_keys: set[str] = set()
        self.rewards: dict[str, Reward] = {}
        self.redemptions: dict[str, RewardRedemption] = {}
        self.audit_log: list[AuditLogEntry] = []
        self.suggestions: dict[tuple[str, str], AdvancementSuggestion] = {}

    # --- accounts / children / quests -----------------------------------
    def add_account(self, a: Account) -> None:
        self.accounts[a.account_id] = a

    def add_child(self, c: Child) -> None:
        self.children[c.child_id] = c

    def add_quest(self, q: Quest) -> None:
        self.quests[(q.id.quest_id, q.id.version)] = q

    def get_quest(self, qid: QuestId) -> Quest | None:
        return self.quests.get((qid.quest_id, qid.version))

    def latest_quest(self, quest_id: str) -> Quest | None:
        versions = [q for (q_id, _), q in self.quests.items() if q_id == quest_id]
        return max(versions, key=lambda q: q.id.version, default=None)

    def add_schedule(self, s: QuestSchedule) -> None:
        self.schedules[s.quest_id] = s

    # --- child_quest ---------------------------------------------------
    def put_child_quest(self, cq: ChildQuest) -> None:
        self.child_quests[(cq.child_id, cq.quest_id)] = cq

    def get_child_quest(self, child_id: str, quest_id: str) -> ChildQuest | None:
        return self.child_quests.get((child_id, quest_id))

    # --- ledger (append-only; INV-12) --------------------------------
    def append_ledger(self, e: LedgerEntry) -> bool:
        """Returns True if written, False if the idempotency key already exists
        (INV-11). There is intentionally no update/delete.
        """
        if e.idempotency_key in self._ledger_keys:
            return False
        self._ledger.append(e)
        self._ledger_keys.add(e.idempotency_key)
        return True

    def ledger_for(self, child_id: str) -> list[LedgerEntry]:
        return [e for e in self._ledger if e.child_id == child_id]

    def all_ledger(self) -> list[LedgerEntry]:
        return list(self._ledger)

    # --- audit -------------------------------------------------------
    def append_audit(self, e: AuditLogEntry) -> None:
        self.audit_log.append(e)


class InstanceRecord:
    """Wrapper kept so the repo owns instance identity/lifecycle."""

    __slots__ = ("instance",)

    def __init__(self, instance) -> None:  # QuestInstance
        self.instance = instance

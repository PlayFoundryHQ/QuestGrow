"""Repository — the persistence seam (TOQ-7).

``Repository`` is the interface every store implements. Two implementations
ship: ``InMemoryRepository`` (reference / tests) and
``sqlite_repository.SqliteRepository`` (dev / D1). The interface is
**method-based** so a SQL store is a drop-in; mutation of authoritative state
is committed via explicit ``save_*`` calls (the service mutates a dataclass,
then hands it back to the repo).

Invariants enforced here:
- ``LedgerEntry`` is append-only — ``append_ledger`` only, no update/delete
  (INV-12); a duplicate ``idempotency_key`` is a no-op returning ``False``
  (INV-11).
- No stored ``balance`` / ``streak`` / ``verification_required`` /
  ``independence_level`` — the schema has no such column (INV-1/4/9/16).
"""

from __future__ import annotations

import itertools
from datetime import date
from typing import Protocol

from .entities import (
    Account,
    AdvancementSuggestion,
    AuditLogEntry,
    Child,
    ChildQuest,
    LedgerEntry,
    ParentReview,
    Quest,
    QuestId,
    QuestInstance,
    QuestSchedule,
    Reward,
    RewardRedemption,
)


class Repository(Protocol):
    # accounts / children
    def add_account(self, a: Account) -> None: ...
    def get_account(self, account_id: str) -> Account | None: ...
    def save_account(self, a: Account) -> None: ...
    def add_child(self, c: Child) -> None: ...
    def get_child(self, child_id: str) -> Child | None: ...
    def save_child(self, c: Child) -> None: ...
    def children_of(self, account_id: str) -> list[Child]: ...

    # quests / schedules
    def add_quest(self, q: Quest) -> None: ...
    def get_quest(self, qid: QuestId) -> Quest | None: ...
    def latest_quest(self, quest_id: str) -> Quest | None: ...
    def all_quest_versions(self, quest_id: str) -> list[Quest]: ...
    def quests_of(self, account_id: str) -> list[Quest]: ...
    def add_schedule(self, s: QuestSchedule) -> None: ...
    def get_schedule(self, quest_id: str) -> QuestSchedule | None: ...

    # child_quest
    def put_child_quest(self, cq: ChildQuest) -> None: ...
    def get_child_quest(self, child_id: str, quest_id: str) -> ChildQuest | None: ...
    def save_child_quest(self, cq: ChildQuest) -> None: ...
    def delete_child_quest(self, child_id: str, quest_id: str) -> None: ...
    def child_quests_of(self, child_id: str) -> list[ChildQuest]: ...
    def all_child_quests(self) -> list[ChildQuest]: ...

    # instances
    def put_instance(self, inst: QuestInstance) -> None: ...
    def save_instance(self, inst: QuestInstance) -> None: ...
    def get_instance_any_version(self, quest_id: str, child_id: str, on_date: date) -> QuestInstance | None: ...
    def instances_of(self, child_id: str) -> list[QuestInstance]: ...
    def all_instances(self) -> list[QuestInstance]: ...

    # completion requests / parent reviews
    def add_completion_request(self, cr) -> None: ...
    def add_parent_review(self, pr: ParentReview) -> None: ...
    def parent_reviews_of(self, child_id: str) -> list[ParentReview]: ...

    # ledger (append-only)
    def append_ledger(self, e: LedgerEntry) -> bool: ...
    def ledger_for(self, child_id: str) -> list[LedgerEntry]: ...
    def all_ledger(self) -> list[LedgerEntry]: ...

    # rewards
    def add_reward(self, r: Reward) -> None: ...
    def get_reward(self, reward_id: str) -> Reward | None: ...
    def save_reward(self, r: Reward) -> None: ...
    def rewards_of(self, account_id: str) -> list[Reward]: ...
    def add_redemption(self, red: RewardRedemption) -> None: ...
    def get_redemption(self, redemption_id: str) -> RewardRedemption | None: ...
    def save_redemption(self, red: RewardRedemption) -> None: ...
    def redemptions_of(self, child_ids: list[str]) -> list[RewardRedemption]: ...

    # audit
    def append_audit(self, e: AuditLogEntry) -> None: ...
    def audit_entries(self) -> list[AuditLogEntry]: ...

    # advancement suggestions (derived state — small mutable set)
    def put_suggestion(self, s: AdvancementSuggestion) -> None: ...
    def get_suggestion(self, child_id: str, quest_id: str) -> AdvancementSuggestion | None: ...
    def delete_suggestion(self, child_id: str, quest_id: str) -> None: ...
    def suggestions_of(self, child_id: str) -> list[AdvancementSuggestion]: ...

    # restart-safe id allocation (Phase F) — the counter lives in the store,
    # not in a process-local ``itertools.count``.
    def next_id(self, name: str) -> str: ...


class InMemoryRepository:
    """Reference implementation. Objects are stored by identity; ``save_*`` is
    effectively a no-op (the caller already holds the live object) but is kept
    explicit so the service code is identical against a SQL store.
    """

    def __init__(self) -> None:
        self._accounts: dict[str, Account] = {}
        self._children: dict[str, Child] = {}
        self._quests: dict[tuple[str, int], Quest] = {}
        self._schedules: dict[str, QuestSchedule] = {}
        self._child_quests: dict[tuple[str, str], ChildQuest] = {}
        self._instances: dict[tuple[str, int, str, str], QuestInstance] = {}
        self._completion_requests: list = []
        self._parent_reviews: list[ParentReview] = []
        self._ledger: list[LedgerEntry] = []
        self._ledger_keys: set[str] = set()
        self._rewards: dict[str, Reward] = {}
        self._redemptions: dict[str, RewardRedemption] = {}
        self._audit: list[AuditLogEntry] = []
        self._suggestions: dict[tuple[str, str], AdvancementSuggestion] = {}
        self._ids = itertools.count(1)

    def next_id(self, name: str) -> str:
        return f"{name}-{next(self._ids)}"

    # accounts / children ------------------------------------------------
    def add_account(self, a: Account) -> None:
        self._accounts[a.account_id] = a

    def get_account(self, account_id: str) -> Account | None:
        return self._accounts.get(account_id)

    def save_account(self, a: Account) -> None:
        self._accounts[a.account_id] = a

    def add_child(self, c: Child) -> None:
        self._children[c.child_id] = c

    def get_child(self, child_id: str) -> Child | None:
        return self._children.get(child_id)

    def save_child(self, c: Child) -> None:
        self._children[c.child_id] = c

    def children_of(self, account_id: str) -> list[Child]:
        return [c for c in self._children.values() if c.account_id == account_id]

    # quests / schedules ----------------------------------------------
    def add_quest(self, q: Quest) -> None:
        self._quests[(q.id.quest_id, q.id.version)] = q

    def get_quest(self, qid: QuestId) -> Quest | None:
        return self._quests.get((qid.quest_id, qid.version))

    def latest_quest(self, quest_id: str) -> Quest | None:
        vs = [q for (qid, _), q in self._quests.items() if qid == quest_id]
        return max(vs, key=lambda q: q.id.version, default=None)

    def all_quest_versions(self, quest_id: str) -> list[Quest]:
        return sorted(
            (q for (qid, _), q in self._quests.items() if qid == quest_id),
            key=lambda q: q.id.version,
        )

    def quests_of(self, account_id: str) -> list[Quest]:
        seen: dict[str, Quest] = {}
        for q in self._quests.values():
            if q.account_id == account_id:
                cur = seen.get(q.id.quest_id)
                if cur is None or q.id.version > cur.id.version:
                    seen[q.id.quest_id] = q
        return list(seen.values())

    def add_schedule(self, s: QuestSchedule) -> None:
        self._schedules[s.quest_id] = s

    def get_schedule(self, quest_id: str) -> QuestSchedule | None:
        return self._schedules.get(quest_id)

    # child_quest ---------------------------------------------------
    def put_child_quest(self, cq: ChildQuest) -> None:
        self._child_quests[(cq.child_id, cq.quest_id)] = cq

    def get_child_quest(self, child_id: str, quest_id: str) -> ChildQuest | None:
        return self._child_quests.get((child_id, quest_id))

    def save_child_quest(self, cq: ChildQuest) -> None:
        self._child_quests[(cq.child_id, cq.quest_id)] = cq

    def delete_child_quest(self, child_id: str, quest_id: str) -> None:
        self._child_quests.pop((child_id, quest_id), None)

    def child_quests_of(self, child_id: str) -> list[ChildQuest]:
        return [cq for (c, _), cq in self._child_quests.items() if c == child_id]

    def all_child_quests(self) -> list[ChildQuest]:
        return list(self._child_quests.values())

    # instances ---------------------------------------------------
    def put_instance(self, inst: QuestInstance) -> None:
        self._instances[inst.key] = inst

    def save_instance(self, inst: QuestInstance) -> None:
        self._instances[inst.key] = inst

    def get_instance_any_version(
        self, quest_id: str, child_id: str, on_date: date
    ) -> QuestInstance | None:
        d = on_date.isoformat()
        cands = [
            i for (qid, _v, cid, dd), i in self._instances.items()
            if qid == quest_id and cid == child_id and dd == d
        ]
        # newest version wins if (pathologically) more than one exists
        return max(cands, key=lambda i: i.quest_version, default=None)

    def instances_of(self, child_id: str) -> list[QuestInstance]:
        return [i for i in self._instances.values() if i.child_id == child_id]

    def all_instances(self) -> list[QuestInstance]:
        return list(self._instances.values())

    # completion requests / parent reviews -------------------------
    def add_completion_request(self, cr) -> None:
        self._completion_requests.append(cr)

    def add_parent_review(self, pr: ParentReview) -> None:
        self._parent_reviews.append(pr)

    def parent_reviews_of(self, child_id: str) -> list[ParentReview]:
        return [p for p in self._parent_reviews if p.child_id == child_id]

    # ledger (append-only, INV-11/12) -----------------------------
    def append_ledger(self, e: LedgerEntry) -> bool:
        if e.idempotency_key in self._ledger_keys:
            return False
        self._ledger.append(e)
        self._ledger_keys.add(e.idempotency_key)
        return True

    def ledger_for(self, child_id: str) -> list[LedgerEntry]:
        return [e for e in self._ledger if e.child_id == child_id]

    def all_ledger(self) -> list[LedgerEntry]:
        return list(self._ledger)

    # rewards ----------------------------------------------------
    def add_reward(self, r: Reward) -> None:
        self._rewards[r.reward_id] = r

    def get_reward(self, reward_id: str) -> Reward | None:
        return self._rewards.get(reward_id)

    def save_reward(self, r: Reward) -> None:
        self._rewards[r.reward_id] = r

    def rewards_of(self, account_id: str) -> list[Reward]:
        return [r for r in self._rewards.values() if r.account_id == account_id]

    def add_redemption(self, red: RewardRedemption) -> None:
        self._redemptions[red.id] = red

    def get_redemption(self, redemption_id: str) -> RewardRedemption | None:
        return self._redemptions.get(redemption_id)

    def save_redemption(self, red: RewardRedemption) -> None:
        self._redemptions[red.id] = red

    def redemptions_of(self, child_ids: list[str]) -> list[RewardRedemption]:
        s = set(child_ids)
        return sorted((r for r in self._redemptions.values() if r.child_id in s),
                      key=lambda r: r.requested_at)

    # audit ----------------------------------------------------
    def append_audit(self, e: AuditLogEntry) -> None:
        self._audit.append(e)

    def audit_entries(self) -> list[AuditLogEntry]:
        return list(self._audit)

    # advancement suggestions --------------------------------
    def put_suggestion(self, s: AdvancementSuggestion) -> None:
        self._suggestions[(s.child_id, s.quest_id)] = s

    def get_suggestion(self, child_id: str, quest_id: str) -> AdvancementSuggestion | None:
        return self._suggestions.get((child_id, quest_id))

    def delete_suggestion(self, child_id: str, quest_id: str) -> None:
        self._suggestions.pop((child_id, quest_id), None)

    def suggestions_of(self, child_id: str) -> list[AdvancementSuggestion]:
        return [s for (c, _), s in self._suggestions.items() if c == child_id]

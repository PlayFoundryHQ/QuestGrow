"""SqliteRepository — the dev / D1 persistence backend (C1).

A straight implementation of the ``repository.Repository`` protocol on top of
``sqlite3``. The schema is deliberately plain SQL and Postgres-portable (no
SQLite-only types, no ORM): ``TEXT`` / ``INTEGER`` columns, explicit PKs,
one ``UNIQUE`` index for the ledger idempotency key.

Structural guarantees (match ``InMemoryRepository`` and the contract):
- ``ledger`` is append-only here too — only ``append_ledger`` inserts, and a
  duplicate ``idempotency_key`` hits the ``UNIQUE`` index and returns ``False``
  (INV-11/INV-12).
- No ``balance`` / ``streak`` / ``verification_required`` /
  ``independence_level`` column exists (INV-1/4/9/16) — see ``SCHEMA``.

Authoritative dataclasses are round-tripped: the service mutates an object and
calls ``save_*``; here that is an ``UPDATE`` (or upsert).
"""

from __future__ import annotations

import json
import sqlite3
from datetime import date, datetime

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
    QuestInstance,
    QuestSchedule,
    Reward,
    RewardRedemption,
)
from .enums import (
    InstanceState,
    LedgerKind,
    OwnershipStage,
    Recurrence,
    RedemptionMode,
    RedemptionState,
)

SCHEMA = """
CREATE TABLE IF NOT EXISTS account (
    account_id TEXT PRIMARY KEY,
    parent_gate_configured INTEGER NOT NULL DEFAULT 1,
    points_enabled INTEGER NOT NULL DEFAULT 1,
    notifications_enabled INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS child (
    child_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    name TEXT NOT NULL,
    avatar TEXT NOT NULL DEFAULT '',
    age_band TEXT NOT NULL DEFAULT '',
    birthdate TEXT,
    adaptation_overrides TEXT NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS quest (
    quest_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    account_id TEXT NOT NULL,
    title TEXT NOT NULL,
    icon TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 10,
    age_suitability TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    archived INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (quest_id, version)
);
CREATE TABLE IF NOT EXISTS quest_schedule (
    quest_id TEXT PRIMARY KEY,
    recurrence TEXT NOT NULL,
    weekdays TEXT NOT NULL DEFAULT '[]',
    start TEXT,
    end TEXT
);
CREATE TABLE IF NOT EXISTS child_quest (
    child_id TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    ownership_stage TEXT NOT NULL,
    consecutive_ok_count INTEGER NOT NULL DEFAULT 0,
    assigned_at TEXT,
    PRIMARY KEY (child_id, quest_id)
);
CREATE TABLE IF NOT EXISTS quest_instance (
    quest_id TEXT NOT NULL,
    quest_version INTEGER NOT NULL,
    child_id TEXT NOT NULL,
    on_date TEXT NOT NULL,
    state TEXT NOT NULL,
    stage_at_completion TEXT,
    parent_note TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (quest_id, quest_version, child_id, on_date)
);
CREATE TABLE IF NOT EXISTS completion_request (
    id TEXT PRIMARY KEY,
    quest_instance_key TEXT NOT NULL,
    child_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    evidence_ref TEXT,
    recorded_by_parent INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS parent_review (
    id TEXT PRIMARY KEY,
    quest_instance_key TEXT NOT NULL,
    child_id TEXT NOT NULL,
    note TEXT NOT NULL,
    created_at TEXT NOT NULL,
    flagged_problem INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS ledger (
    id TEXT PRIMARY KEY,
    child_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    points INTEGER NOT NULL,
    source TEXT NOT NULL,
    created_at TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    seq INTEGER
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ledger_idem ON ledger (idempotency_key);
CREATE TABLE IF NOT EXISTS reward (
    reward_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    cost INTEGER NOT NULL,
    redemption_mode TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS redemption (
    id TEXT PRIMARY KEY,
    reward_id TEXT NOT NULL,
    child_id TEXT NOT NULL,
    state TEXT NOT NULL,
    requested_at TEXT NOT NULL,
    resolved_at TEXT
);
CREATE TABLE IF NOT EXISTS audit_log (
    id TEXT PRIMARY KEY,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT NOT NULL,
    before TEXT NOT NULL,
    after TEXT NOT NULL,
    created_at TEXT NOT NULL,
    seq INTEGER
);
CREATE TABLE IF NOT EXISTS advancement_suggestion (
    child_id TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    from_stage TEXT NOT NULL,
    to_stage TEXT NOT NULL,
    dismissed INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (child_id, quest_id)
);
"""


def _dt(s: str | None) -> datetime | None:
    return datetime.fromisoformat(s) if s else None


def _d(s: str | None) -> date | None:
    return date.fromisoformat(s) if s else None


def _iso(v) -> str | None:
    return v.isoformat() if v is not None else None


class SqliteRepository:
    def __init__(self, path: str = ":memory:") -> None:
        self._db = sqlite3.connect(path)
        self._db.row_factory = sqlite3.Row
        self._db.executescript(SCHEMA)
        self._db.commit()
        self._seq = 0

    def _next_seq(self) -> int:
        self._seq += 1
        return self._seq

    # accounts / children ------------------------------------------------
    def add_account(self, a: Account) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO account VALUES (?,?,?,?)",
            (
                a.account_id, int(a.parent_gate_configured), int(a.points_enabled),
                int(a.notifications_enabled),
            ),
        )
        self._db.commit()

    save_account = add_account

    def get_account(self, account_id: str) -> Account | None:
        r = self._db.execute(
            "SELECT * FROM account WHERE account_id=?", (account_id,)
        ).fetchone()
        if r is None:
            return None
        return Account(
            r["account_id"], bool(r["parent_gate_configured"]), bool(r["points_enabled"]),
            bool(r["notifications_enabled"]),
        )

    def add_child(self, c: Child) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO child VALUES (?,?,?,?,?,?,?)",
            (
                c.child_id, c.account_id, c.name, c.avatar, c.age_band,
                _iso(c.birthdate), json.dumps(c.adaptation_overrides),
            ),
        )
        self._db.commit()

    save_child = add_child

    def get_child(self, child_id: str) -> Child | None:
        r = self._db.execute("SELECT * FROM child WHERE child_id=?", (child_id,)).fetchone()
        return self._child(r) if r else None

    @staticmethod
    def _child(r: sqlite3.Row) -> Child:
        return Child(
            child_id=r["child_id"], account_id=r["account_id"], name=r["name"],
            avatar=r["avatar"], age_band=r["age_band"], birthdate=_d(r["birthdate"]),
            adaptation_overrides=json.loads(r["adaptation_overrides"]),
        )

    def children_of(self, account_id: str) -> list[Child]:
        rows = self._db.execute(
            "SELECT * FROM child WHERE account_id=?", (account_id,)
        ).fetchall()
        return [self._child(r) for r in rows]

    # quests / schedules ----------------------------------------------
    def add_quest(self, q: Quest) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO quest VALUES (?,?,?,?,?,?,?,?,?)",
            (
                q.id.quest_id, q.id.version, q.account_id, q.title, q.icon, q.points,
                q.age_suitability, int(q.active), int(q.archived),
            ),
        )
        self._db.commit()

    @staticmethod
    def _quest(r: sqlite3.Row) -> Quest:
        return Quest(
            id=QuestId(r["quest_id"], r["version"]), account_id=r["account_id"],
            title=r["title"], icon=r["icon"], points=r["points"],
            age_suitability=r["age_suitability"], active=bool(r["active"]),
            archived=bool(r["archived"]),
        )

    def get_quest(self, qid: QuestId) -> Quest | None:
        r = self._db.execute(
            "SELECT * FROM quest WHERE quest_id=? AND version=?", (qid.quest_id, qid.version)
        ).fetchone()
        return self._quest(r) if r else None

    def latest_quest(self, quest_id: str) -> Quest | None:
        r = self._db.execute(
            "SELECT * FROM quest WHERE quest_id=? ORDER BY version DESC LIMIT 1", (quest_id,)
        ).fetchone()
        return self._quest(r) if r else None

    def all_quest_versions(self, quest_id: str) -> list[Quest]:
        rows = self._db.execute(
            "SELECT * FROM quest WHERE quest_id=? ORDER BY version", (quest_id,)
        ).fetchall()
        return [self._quest(r) for r in rows]

    def quests_of(self, account_id: str) -> list[Quest]:
        rows = self._db.execute(
            "SELECT * FROM quest q WHERE account_id=? AND version=("
            "  SELECT MAX(version) FROM quest q2 WHERE q2.quest_id=q.quest_id)",
            (account_id,),
        ).fetchall()
        return [self._quest(r) for r in rows]

    def add_schedule(self, s: QuestSchedule) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO quest_schedule VALUES (?,?,?,?,?)",
            (
                s.quest_id, s.recurrence.value, json.dumps(sorted(s.weekdays)),
                _iso(s.start), _iso(s.end),
            ),
        )
        self._db.commit()

    def get_schedule(self, quest_id: str) -> QuestSchedule | None:
        r = self._db.execute(
            "SELECT * FROM quest_schedule WHERE quest_id=?", (quest_id,)
        ).fetchone()
        if r is None:
            return None
        return QuestSchedule(
            quest_id=r["quest_id"], recurrence=Recurrence(r["recurrence"]),
            weekdays=frozenset(json.loads(r["weekdays"])),
            start=_d(r["start"]), end=_d(r["end"]),
        )

    # child_quest ---------------------------------------------------
    def put_child_quest(self, cq: ChildQuest) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO child_quest VALUES (?,?,?,?,?)",
            (
                cq.child_id, cq.quest_id, cq.ownership_stage.value,
                cq.consecutive_ok_count, _iso(cq.assigned_at),
            ),
        )
        self._db.commit()

    save_child_quest = put_child_quest

    @staticmethod
    def _child_quest_row(r: sqlite3.Row) -> ChildQuest:
        return ChildQuest(
            child_id=r["child_id"], quest_id=r["quest_id"],
            ownership_stage=OwnershipStage(r["ownership_stage"]),
            consecutive_ok_count=r["consecutive_ok_count"],
            assigned_at=_dt(r["assigned_at"]),
        )

    def get_child_quest(self, child_id: str, quest_id: str) -> ChildQuest | None:
        r = self._db.execute(
            "SELECT * FROM child_quest WHERE child_id=? AND quest_id=?", (child_id, quest_id)
        ).fetchone()
        return self._child_quest_row(r) if r else None

    def child_quests_of(self, child_id: str) -> list[ChildQuest]:
        rows = self._db.execute(
            "SELECT * FROM child_quest WHERE child_id=?", (child_id,)
        ).fetchall()
        return [self._child_quest_row(r) for r in rows]

    def all_child_quests(self) -> list[ChildQuest]:
        rows = self._db.execute("SELECT * FROM child_quest").fetchall()
        return [self._child_quest_row(r) for r in rows]

    # instances ---------------------------------------------------
    def put_instance(self, inst: QuestInstance) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO quest_instance VALUES (?,?,?,?,?,?,?)",
            (
                inst.quest_id, inst.quest_version, inst.child_id, inst.on_date.isoformat(),
                inst.state.value,
                inst.stage_at_completion.value if inst.stage_at_completion else None,
                inst.parent_note,
            ),
        )
        self._db.commit()

    save_instance = put_instance

    @staticmethod
    def _instance(r: sqlite3.Row) -> QuestInstance:
        return QuestInstance(
            quest_id=r["quest_id"], quest_version=r["quest_version"], child_id=r["child_id"],
            on_date=date.fromisoformat(r["on_date"]), state=InstanceState(r["state"]),
            stage_at_completion=(
                OwnershipStage(r["stage_at_completion"]) if r["stage_at_completion"] else None
            ),
            parent_note=r["parent_note"],
        )

    def get_instance_any_version(
        self, quest_id: str, child_id: str, on_date: date
    ) -> QuestInstance | None:
        r = self._db.execute(
            "SELECT * FROM quest_instance WHERE quest_id=? AND child_id=? AND on_date=? "
            "ORDER BY quest_version DESC LIMIT 1",
            (quest_id, child_id, on_date.isoformat()),
        ).fetchone()
        return self._instance(r) if r else None

    def instances_of(self, child_id: str) -> list[QuestInstance]:
        rows = self._db.execute(
            "SELECT * FROM quest_instance WHERE child_id=?", (child_id,)
        ).fetchall()
        return [self._instance(r) for r in rows]

    def all_instances(self) -> list[QuestInstance]:
        return [self._instance(r) for r in self._db.execute("SELECT * FROM quest_instance").fetchall()]

    # completion requests / parent reviews -------------------------
    def add_completion_request(self, cr: CompletionRequest) -> None:
        self._db.execute(
            "INSERT INTO completion_request VALUES (?,?,?,?,?,?,?)",
            (
                cr.id, json.dumps(list(cr.quest_instance_key)), cr.child_id,
                cr.created_at.isoformat(), cr.note, cr.evidence_ref, int(cr.recorded_by_parent),
            ),
        )
        self._db.commit()

    def add_parent_review(self, pr: ParentReview) -> None:
        self._db.execute(
            "INSERT INTO parent_review VALUES (?,?,?,?,?,?)",
            (
                pr.id, json.dumps(list(pr.quest_instance_key)), pr.child_id, pr.note,
                pr.created_at.isoformat(), int(pr.flagged_problem),
            ),
        )
        self._db.commit()

    def parent_reviews_of(self, child_id: str) -> list[ParentReview]:
        rows = self._db.execute(
            "SELECT * FROM parent_review WHERE child_id=?", (child_id,)
        ).fetchall()
        return [
            ParentReview(
                id=r["id"], quest_instance_key=tuple(json.loads(r["quest_instance_key"])),
                child_id=r["child_id"], note=r["note"], created_at=_dt(r["created_at"]),
                flagged_problem=bool(r["flagged_problem"]),
            )
            for r in rows
        ]

    # ledger (append-only) ---------------------------------------
    def append_ledger(self, e: LedgerEntry) -> bool:
        try:
            self._db.execute(
                "INSERT INTO ledger VALUES (?,?,?,?,?,?,?,?)",
                (
                    e.id, e.child_id, e.kind.value, e.points, e.source,
                    e.created_at.isoformat(), e.idempotency_key, self._next_seq(),
                ),
            )
        except sqlite3.IntegrityError:
            return False  # duplicate idempotency_key — INV-11
        self._db.commit()
        return True

    @staticmethod
    def _ledger(r: sqlite3.Row) -> LedgerEntry:
        return LedgerEntry(
            id=r["id"], child_id=r["child_id"], kind=LedgerKind(r["kind"]), points=r["points"],
            source=r["source"], created_at=_dt(r["created_at"]),
            idempotency_key=r["idempotency_key"],
        )

    def ledger_for(self, child_id: str) -> list[LedgerEntry]:
        rows = self._db.execute(
            "SELECT * FROM ledger WHERE child_id=? ORDER BY seq", (child_id,)
        ).fetchall()
        return [self._ledger(r) for r in rows]

    def all_ledger(self) -> list[LedgerEntry]:
        rows = self._db.execute("SELECT * FROM ledger ORDER BY seq").fetchall()
        return [self._ledger(r) for r in rows]

    # rewards ----------------------------------------------------
    def add_reward(self, r: Reward) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO reward VALUES (?,?,?,?,?,?,?)",
            (
                r.reward_id, r.account_id, r.name, r.icon, r.cost,
                r.redemption_mode.value, int(r.active),
            ),
        )
        self._db.commit()

    save_reward = add_reward

    @staticmethod
    def _reward(r: sqlite3.Row) -> Reward:
        return Reward(
            reward_id=r["reward_id"], account_id=r["account_id"], name=r["name"], icon=r["icon"],
            cost=r["cost"], redemption_mode=RedemptionMode(r["redemption_mode"]),
            active=bool(r["active"]),
        )

    def get_reward(self, reward_id: str) -> Reward | None:
        r = self._db.execute("SELECT * FROM reward WHERE reward_id=?", (reward_id,)).fetchone()
        return self._reward(r) if r else None

    def rewards_of(self, account_id: str) -> list[Reward]:
        rows = self._db.execute(
            "SELECT * FROM reward WHERE account_id=?", (account_id,)
        ).fetchall()
        return [self._reward(r) for r in rows]

    def add_redemption(self, red: RewardRedemption) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO redemption VALUES (?,?,?,?,?,?)",
            (
                red.id, red.reward_id, red.child_id, red.state.value,
                red.requested_at.isoformat(), _iso(red.resolved_at),
            ),
        )
        self._db.commit()

    save_redemption = add_redemption

    def get_redemption(self, redemption_id: str) -> RewardRedemption | None:
        r = self._db.execute(
            "SELECT * FROM redemption WHERE id=?", (redemption_id,)
        ).fetchone()
        if r is None:
            return None
        return RewardRedemption(
            id=r["id"], reward_id=r["reward_id"], child_id=r["child_id"],
            state=RedemptionState(r["state"]), requested_at=_dt(r["requested_at"]),
            resolved_at=_dt(r["resolved_at"]),
        )

    # audit ----------------------------------------------------
    def append_audit(self, e: AuditLogEntry) -> None:
        self._db.execute(
            "INSERT INTO audit_log VALUES (?,?,?,?,?,?,?,?)",
            (
                e.id, e.actor, e.action, e.target, e.before, e.after,
                e.created_at.isoformat(), self._next_seq(),
            ),
        )
        self._db.commit()

    def audit_entries(self) -> list[AuditLogEntry]:
        rows = self._db.execute("SELECT * FROM audit_log ORDER BY seq").fetchall()
        return [
            AuditLogEntry(
                id=r["id"], actor=r["actor"], action=r["action"], target=r["target"],
                before=r["before"], after=r["after"], created_at=_dt(r["created_at"]),
            )
            for r in rows
        ]

    # advancement suggestions --------------------------------
    def put_suggestion(self, s: AdvancementSuggestion) -> None:
        self._db.execute(
            "INSERT OR REPLACE INTO advancement_suggestion VALUES (?,?,?,?,?)",
            (
                s.child_id, s.quest_id, s.from_stage.value, s.to_stage.value, int(s.dismissed),
            ),
        )
        self._db.commit()

    @staticmethod
    def _suggestion(r: sqlite3.Row) -> AdvancementSuggestion:
        return AdvancementSuggestion(
            child_id=r["child_id"], quest_id=r["quest_id"],
            from_stage=OwnershipStage(r["from_stage"]), to_stage=OwnershipStage(r["to_stage"]),
            dismissed=bool(r["dismissed"]),
        )

    def get_suggestion(self, child_id: str, quest_id: str) -> AdvancementSuggestion | None:
        r = self._db.execute(
            "SELECT * FROM advancement_suggestion WHERE child_id=? AND quest_id=?",
            (child_id, quest_id),
        ).fetchone()
        return self._suggestion(r) if r else None

    def delete_suggestion(self, child_id: str, quest_id: str) -> None:
        self._db.execute(
            "DELETE FROM advancement_suggestion WHERE child_id=? AND quest_id=?",
            (child_id, quest_id),
        )
        self._db.commit()

    def suggestions_of(self, child_id: str) -> list[AdvancementSuggestion]:
        rows = self._db.execute(
            "SELECT * FROM advancement_suggestion WHERE child_id=?", (child_id,)
        ).fetchall()
        return [self._suggestion(r) for r in rows]

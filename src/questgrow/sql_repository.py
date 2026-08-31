"""SqlRepository — the portable SQL persistence backend (Phase F).

One implementation of the ``repository.Repository`` protocol that runs against
either SQLite or PostgreSQL through the ``db.Database`` seam. Replaces the
former inline-``SCHEMA`` / ``INSERT OR REPLACE`` / in-process-``seq``
``SqliteRepository`` with:

* schema owned by ``migrations/*.sql`` and applied by ``migrate.run`` on init;
* portable ``INSERT … ON CONFLICT … DO UPDATE/NOTHING`` upserts;
* ``seq`` / service ids derived in SQL (``MAX(seq)+1`` / an ``id_counter``
  row) so nothing monotonic lives in Python — restart-safe;
* the append-only ledger guarantee unchanged (INSERT-only; a duplicate
  ``idempotency_key`` is a no-op returning ``False`` — INV-11/12).

``SqliteRepository`` and ``PostgresRepository`` are thin subclasses that only
choose the ``Database``.
"""

from __future__ import annotations

import json
from datetime import date, datetime

from .db import Database, SqliteDatabase
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
from .migrate import _migration_files, run as run_migrations

# concatenation of all migration SQL — kept for schema-scan tests (INV-1/4/9/16).
SCHEMA = "\n".join(sql for _v, sql in _migration_files())


def _dt(s: str | None) -> datetime | None:
    return datetime.fromisoformat(s) if s else None


def _d(s: str | None) -> date | None:
    return date.fromisoformat(s) if s else None


def _iso(v) -> str | None:
    return v.isoformat() if v is not None else None


class SqlRepository:
    def __init__(self, db: Database) -> None:
        self.db = db
        run_migrations(db)

    # ---- portable helpers ------------------------------------------------
    def _upsert(self, table: str, cols: list[str], pk: list[str], values: tuple) -> None:
        ph = ", ".join("?" for _ in cols)
        updates = ", ".join(f"{c} = excluded.{c}" for c in cols if c not in pk)
        conflict = ", ".join(pk)
        sql = f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({ph}) "
        sql += f"ON CONFLICT ({conflict}) DO UPDATE SET {updates}" if updates else \
               f"ON CONFLICT ({conflict}) DO NOTHING"
        self.db.execute(sql, values)

    def _insert(self, table: str, cols: list[str], values: tuple) -> None:
        ph = ", ".join("?" for _ in cols)
        self.db.execute(f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({ph})", values)

    def next_id(self, name: str) -> str:
        """Restart-safe monotonic id (replaces the service's itertools.count)."""
        with self.db.transaction():
            self.db.execute(
                "INSERT INTO id_counter (name, value) VALUES (?, 1) "
                "ON CONFLICT (name) DO UPDATE SET value = id_counter.value + 1",
                (name,),
            )
            row = self.db.fetchone("SELECT value FROM id_counter WHERE name = ?", (name,))
        return f"{name}-{row['value']}"

    # ---- accounts / children ------------------------------------------
    def add_account(self, a: Account) -> None:
        self._upsert(
            "account",
            ["account_id", "parent_gate_configured", "points_enabled", "notifications_enabled"],
            ["account_id"],
            (a.account_id, int(a.parent_gate_configured), int(a.points_enabled),
             int(a.notifications_enabled)),
        )

    save_account = add_account

    def get_account(self, account_id: str) -> Account | None:
        r = self.db.fetchone("SELECT * FROM account WHERE account_id = ?", (account_id,))
        if r is None:
            return None
        return Account(r["account_id"], bool(r["parent_gate_configured"]),
                       bool(r["points_enabled"]), bool(r["notifications_enabled"]))

    def add_child(self, c: Child) -> None:
        self._upsert(
            "child",
            ["child_id", "account_id", "name", "avatar", "age_band", "birthdate",
             "adaptation_overrides"],
            ["child_id"],
            (c.child_id, c.account_id, c.name, c.avatar, c.age_band, _iso(c.birthdate),
             json.dumps(c.adaptation_overrides)),
        )

    save_child = add_child

    @staticmethod
    def _child(r) -> Child:
        return Child(
            child_id=r["child_id"], account_id=r["account_id"], name=r["name"],
            avatar=r["avatar"], age_band=r["age_band"], birthdate=_d(r["birthdate"]),
            adaptation_overrides=json.loads(r["adaptation_overrides"]),
        )

    def get_child(self, child_id: str) -> Child | None:
        r = self.db.fetchone("SELECT * FROM child WHERE child_id = ?", (child_id,))
        return self._child(r) if r else None

    def children_of(self, account_id: str) -> list[Child]:
        return [self._child(r) for r in
                self.db.fetchall("SELECT * FROM child WHERE account_id = ? ORDER BY child_id",
                                 (account_id,))]

    # ---- quests / schedules ------------------------------------------
    def add_quest(self, q: Quest) -> None:
        self._upsert(
            "quest",
            ["quest_id", "version", "account_id", "title", "icon", "points",
             "age_suitability", "active", "archived"],
            ["quest_id", "version"],
            (q.id.quest_id, q.id.version, q.account_id, q.title, q.icon, q.points,
             q.age_suitability, int(q.active), int(q.archived)),
        )

    @staticmethod
    def _quest(r) -> Quest:
        return Quest(
            id=QuestId(r["quest_id"], r["version"]), account_id=r["account_id"],
            title=r["title"], icon=r["icon"], points=r["points"],
            age_suitability=r["age_suitability"], active=bool(r["active"]),
            archived=bool(r["archived"]),
        )

    def get_quest(self, qid: QuestId) -> Quest | None:
        r = self.db.fetchone("SELECT * FROM quest WHERE quest_id = ? AND version = ?",
                             (qid.quest_id, qid.version))
        return self._quest(r) if r else None

    def latest_quest(self, quest_id: str) -> Quest | None:
        r = self.db.fetchone(
            "SELECT * FROM quest WHERE quest_id = ? ORDER BY version DESC LIMIT 1", (quest_id,))
        return self._quest(r) if r else None

    def all_quest_versions(self, quest_id: str) -> list[Quest]:
        return [self._quest(r) for r in
                self.db.fetchall("SELECT * FROM quest WHERE quest_id = ? ORDER BY version",
                                 (quest_id,))]

    def quests_of(self, account_id: str) -> list[Quest]:
        rows = self.db.fetchall(
            "SELECT * FROM quest q WHERE account_id = ? AND version = "
            "(SELECT MAX(version) FROM quest q2 WHERE q2.quest_id = q.quest_id) "
            "ORDER BY quest_id",
            (account_id,),
        )
        return [self._quest(r) for r in rows]

    def add_schedule(self, s: QuestSchedule) -> None:
        self._upsert(
            "quest_schedule",
            ["quest_id", "recurrence", "weekdays", "start_date", "end_date"],
            ["quest_id"],
            (s.quest_id, s.recurrence.value, json.dumps(sorted(s.weekdays)),
             _iso(s.start), _iso(s.end)),
        )

    def get_schedule(self, quest_id: str) -> QuestSchedule | None:
        r = self.db.fetchone("SELECT * FROM quest_schedule WHERE quest_id = ?", (quest_id,))
        if r is None:
            return None
        return QuestSchedule(
            quest_id=r["quest_id"], recurrence=Recurrence(r["recurrence"]),
            weekdays=frozenset(json.loads(r["weekdays"])),
            start=_d(r["start_date"]), end=_d(r["end_date"]),
        )

    # ---- child_quest --------------------------------------------------
    def put_child_quest(self, cq: ChildQuest) -> None:
        self._upsert(
            "child_quest",
            ["child_id", "quest_id", "ownership_stage", "consecutive_ok_count", "assigned_at"],
            ["child_id", "quest_id"],
            (cq.child_id, cq.quest_id, cq.ownership_stage.value, cq.consecutive_ok_count,
             _iso(cq.assigned_at)),
        )

    save_child_quest = put_child_quest

    def delete_child_quest(self, child_id: str, quest_id: str) -> None:
        self.db.execute(
            "DELETE FROM child_quest WHERE child_id = ? AND quest_id = ?",
            (child_id, quest_id))

    @staticmethod
    def _child_quest_row(r) -> ChildQuest:
        return ChildQuest(
            child_id=r["child_id"], quest_id=r["quest_id"],
            ownership_stage=OwnershipStage(r["ownership_stage"]),
            consecutive_ok_count=r["consecutive_ok_count"], assigned_at=_dt(r["assigned_at"]),
        )

    def get_child_quest(self, child_id: str, quest_id: str) -> ChildQuest | None:
        r = self.db.fetchone("SELECT * FROM child_quest WHERE child_id = ? AND quest_id = ?",
                             (child_id, quest_id))
        return self._child_quest_row(r) if r else None

    def child_quests_of(self, child_id: str) -> list[ChildQuest]:
        return [self._child_quest_row(r) for r in
                self.db.fetchall("SELECT * FROM child_quest WHERE child_id = ? ORDER BY quest_id",
                                 (child_id,))]

    def all_child_quests(self) -> list[ChildQuest]:
        return [self._child_quest_row(r) for r in
                self.db.fetchall("SELECT * FROM child_quest ORDER BY child_id, quest_id")]

    # ---- instances --------------------------------------------------
    def put_instance(self, inst: QuestInstance) -> None:
        self._upsert(
            "quest_instance",
            ["quest_id", "quest_version", "child_id", "on_date", "state",
             "stage_at_completion", "parent_note"],
            ["quest_id", "quest_version", "child_id", "on_date"],
            (inst.quest_id, inst.quest_version, inst.child_id, inst.on_date.isoformat(),
             inst.state.value,
             inst.stage_at_completion.value if inst.stage_at_completion else None,
             inst.parent_note),
        )

    save_instance = put_instance

    @staticmethod
    def _instance(r) -> QuestInstance:
        return QuestInstance(
            quest_id=r["quest_id"], quest_version=r["quest_version"], child_id=r["child_id"],
            on_date=date.fromisoformat(r["on_date"]), state=InstanceState(r["state"]),
            stage_at_completion=(OwnershipStage(r["stage_at_completion"])
                                 if r["stage_at_completion"] else None),
            parent_note=r["parent_note"],
        )

    def get_instance_any_version(self, quest_id: str, child_id: str,
                                 on_date: date) -> QuestInstance | None:
        r = self.db.fetchone(
            "SELECT * FROM quest_instance WHERE quest_id = ? AND child_id = ? AND on_date = ? "
            "ORDER BY quest_version DESC LIMIT 1",
            (quest_id, child_id, on_date.isoformat()),
        )
        return self._instance(r) if r else None

    def instances_of(self, child_id: str) -> list[QuestInstance]:
        return [self._instance(r) for r in
                self.db.fetchall("SELECT * FROM quest_instance WHERE child_id = ?", (child_id,))]

    def all_instances(self) -> list[QuestInstance]:
        return [self._instance(r) for r in self.db.fetchall("SELECT * FROM quest_instance")]

    # ---- completion requests / parent reviews ----------------------
    def add_completion_request(self, cr: CompletionRequest) -> None:
        self._insert(
            "completion_request",
            ["id", "quest_instance_key", "child_id", "created_at", "note", "evidence_ref",
             "recorded_by_parent"],
            (cr.id, json.dumps(list(cr.quest_instance_key)), cr.child_id,
             cr.created_at.isoformat(), cr.note, cr.evidence_ref, int(cr.recorded_by_parent)),
        )

    def add_parent_review(self, pr: ParentReview) -> None:
        self._insert(
            "parent_review",
            ["id", "quest_instance_key", "child_id", "note", "created_at", "flagged_problem"],
            (pr.id, json.dumps(list(pr.quest_instance_key)), pr.child_id, pr.note,
             pr.created_at.isoformat(), int(pr.flagged_problem)),
        )

    def parent_reviews_of(self, child_id: str) -> list[ParentReview]:
        return [
            ParentReview(
                id=r["id"], quest_instance_key=tuple(json.loads(r["quest_instance_key"])),
                child_id=r["child_id"], note=r["note"], created_at=_dt(r["created_at"]),
                flagged_problem=bool(r["flagged_problem"]),
            )
            for r in self.db.fetchall("SELECT * FROM parent_review WHERE child_id = ?", (child_id,))
        ]

    # ---- ledger (append-only) ------------------------------------
    def append_ledger(self, e: LedgerEntry) -> bool:
        with self.db.transaction():
            cur = self.db.execute(
                "INSERT INTO ledger (id, child_id, kind, points, source, created_at, "
                "idempotency_key, seq) VALUES (?, ?, ?, ?, ?, ?, ?, "
                "COALESCE((SELECT MAX(seq) FROM ledger), 0) + 1) "
                "ON CONFLICT (idempotency_key) DO NOTHING",
                (e.id, e.child_id, e.kind.value, e.points, e.source,
                 e.created_at.isoformat(), e.idempotency_key),
            )
            inserted = (cur.rowcount == 1)
        return inserted  # False on replay → no second entry (INV-11)

    @staticmethod
    def _ledger(r) -> LedgerEntry:
        return LedgerEntry(
            id=r["id"], child_id=r["child_id"], kind=LedgerKind(r["kind"]), points=r["points"],
            source=r["source"], created_at=_dt(r["created_at"]),
            idempotency_key=r["idempotency_key"],
        )

    def ledger_for(self, child_id: str) -> list[LedgerEntry]:
        return [self._ledger(r) for r in
                self.db.fetchall("SELECT * FROM ledger WHERE child_id = ? ORDER BY seq", (child_id,))]

    def all_ledger(self) -> list[LedgerEntry]:
        return [self._ledger(r) for r in self.db.fetchall("SELECT * FROM ledger ORDER BY seq")]

    # ---- rewards --------------------------------------------------
    def add_reward(self, r: Reward) -> None:
        self._upsert(
            "reward",
            ["reward_id", "account_id", "name", "icon", "cost", "redemption_mode", "active"],
            ["reward_id"],
            (r.reward_id, r.account_id, r.name, r.icon, r.cost, r.redemption_mode.value,
             int(r.active)),
        )

    save_reward = add_reward

    @staticmethod
    def _reward(r) -> Reward:
        return Reward(
            reward_id=r["reward_id"], account_id=r["account_id"], name=r["name"], icon=r["icon"],
            cost=r["cost"], redemption_mode=RedemptionMode(r["redemption_mode"]),
            active=bool(r["active"]),
        )

    def get_reward(self, reward_id: str) -> Reward | None:
        r = self.db.fetchone("SELECT * FROM reward WHERE reward_id = ?", (reward_id,))
        return self._reward(r) if r else None

    def rewards_of(self, account_id: str) -> list[Reward]:
        return [self._reward(r) for r in
                self.db.fetchall("SELECT * FROM reward WHERE account_id = ? ORDER BY reward_id",
                                 (account_id,))]

    def add_redemption(self, red: RewardRedemption) -> None:
        self._upsert(
            "redemption",
            ["id", "reward_id", "child_id", "state", "requested_at", "resolved_at"],
            ["id"],
            (red.id, red.reward_id, red.child_id, red.state.value,
             red.requested_at.isoformat(), _iso(red.resolved_at)),
        )

    save_redemption = add_redemption

    def get_redemption(self, redemption_id: str) -> RewardRedemption | None:
        r = self.db.fetchone("SELECT * FROM redemption WHERE id = ?", (redemption_id,))
        if r is None:
            return None
        return RewardRedemption(
            id=r["id"], reward_id=r["reward_id"], child_id=r["child_id"],
            state=RedemptionState(r["state"]), requested_at=_dt(r["requested_at"]),
            resolved_at=_dt(r["resolved_at"]),
        )

    def redemptions_of(self, child_ids: list[str]) -> list[RewardRedemption]:
        if not child_ids:
            return []
        q = ",".join("?" * len(child_ids))
        rows = self.db.fetchall(
            f"SELECT * FROM redemption WHERE child_id IN ({q}) ORDER BY requested_at",
            tuple(child_ids),
        )
        return [
            RewardRedemption(
                id=r["id"], reward_id=r["reward_id"], child_id=r["child_id"],
                state=RedemptionState(r["state"]), requested_at=_dt(r["requested_at"]),
                resolved_at=_dt(r["resolved_at"]),
            )
            for r in rows
        ]

    # ---- audit --------------------------------------------------
    def append_audit(self, e: AuditLogEntry) -> None:
        self.db.execute(
            "INSERT INTO audit_log (id, actor, action, target, before, after, created_at, seq) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT MAX(seq) FROM audit_log), 0) + 1)",
            (e.id, e.actor, e.action, e.target, e.before, e.after, e.created_at.isoformat()),
        )

    def audit_entries(self) -> list[AuditLogEntry]:
        return [
            AuditLogEntry(id=r["id"], actor=r["actor"], action=r["action"], target=r["target"],
                          before=r["before"], after=r["after"], created_at=_dt(r["created_at"]))
            for r in self.db.fetchall("SELECT * FROM audit_log ORDER BY seq")
        ]

    # ---- advancement suggestions --------------------------------
    def put_suggestion(self, s: AdvancementSuggestion) -> None:
        self._upsert(
            "advancement_suggestion",
            ["child_id", "quest_id", "from_stage", "to_stage", "dismissed"],
            ["child_id", "quest_id"],
            (s.child_id, s.quest_id, s.from_stage.value, s.to_stage.value, int(s.dismissed)),
        )

    @staticmethod
    def _suggestion(r) -> AdvancementSuggestion:
        return AdvancementSuggestion(
            child_id=r["child_id"], quest_id=r["quest_id"],
            from_stage=OwnershipStage(r["from_stage"]), to_stage=OwnershipStage(r["to_stage"]),
            dismissed=bool(r["dismissed"]),
        )

    def get_suggestion(self, child_id: str, quest_id: str) -> AdvancementSuggestion | None:
        r = self.db.fetchone(
            "SELECT * FROM advancement_suggestion WHERE child_id = ? AND quest_id = ?",
            (child_id, quest_id))
        return self._suggestion(r) if r else None

    def delete_suggestion(self, child_id: str, quest_id: str) -> None:
        self.db.execute(
            "DELETE FROM advancement_suggestion WHERE child_id = ? AND quest_id = ?",
            (child_id, quest_id))

    def suggestions_of(self, child_id: str) -> list[AdvancementSuggestion]:
        return [self._suggestion(r) for r in
                self.db.fetchall("SELECT * FROM advancement_suggestion WHERE child_id = ?",
                                 (child_id,))]

    def close(self) -> None:
        self.db.close()


class SqliteRepository(SqlRepository):
    """SQLite backend. ``path`` may be ``:memory:`` (default) or a file path."""

    def __init__(self, path: str = ":memory:") -> None:
        super().__init__(SqliteDatabase(path))


class PostgresRepository(SqlRepository):
    """PostgreSQL backend. ``dsn`` is a libpq connection string / URL."""

    def __init__(self, dsn: str) -> None:
        from .db import PostgresDatabase

        super().__init__(PostgresDatabase(dsn))

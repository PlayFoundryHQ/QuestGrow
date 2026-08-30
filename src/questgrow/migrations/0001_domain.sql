-- QuestGrow domain schema. Portable across SQLite (>= 3.24) and PostgreSQL.
-- Booleans are stored as INTEGER 0/1; timestamps and dates as TEXT ISO-8601;
-- JSON blobs as TEXT. No stored balance / streak / verification_required /
-- independence_level column (INV-1/4/9/16).

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
CREATE INDEX IF NOT EXISTS ix_child_account ON child (account_id);

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
CREATE INDEX IF NOT EXISTS ix_quest_account ON quest (account_id);

CREATE TABLE IF NOT EXISTS quest_schedule (
    quest_id TEXT PRIMARY KEY,
    recurrence TEXT NOT NULL,
    weekdays TEXT NOT NULL DEFAULT '[]',
    start_date TEXT,
    end_date TEXT
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
CREATE INDEX IF NOT EXISTS ix_instance_child ON quest_instance (child_id);
CREATE INDEX IF NOT EXISTS ix_instance_lookup ON quest_instance (quest_id, child_id, on_date);

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
CREATE INDEX IF NOT EXISTS ix_review_child ON parent_review (child_id);

-- append-only (INV-11 / INV-12): only INSERT ever touches this table.
CREATE TABLE IF NOT EXISTS ledger (
    id TEXT PRIMARY KEY,
    child_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    points INTEGER NOT NULL,
    source TEXT NOT NULL,
    created_at TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    seq BIGINT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ledger_idem ON ledger (idempotency_key);
CREATE INDEX IF NOT EXISTS ix_ledger_child ON ledger (child_id, seq);

CREATE TABLE IF NOT EXISTS reward (
    reward_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    cost INTEGER NOT NULL,
    redemption_mode TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_reward_account ON reward (account_id);

CREATE TABLE IF NOT EXISTS redemption (
    id TEXT PRIMARY KEY,
    reward_id TEXT NOT NULL,
    child_id TEXT NOT NULL,
    state TEXT NOT NULL,
    requested_at TEXT NOT NULL,
    resolved_at TEXT
);
CREATE INDEX IF NOT EXISTS ix_redemption_child ON redemption (child_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id TEXT PRIMARY KEY,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT NOT NULL,
    before TEXT NOT NULL,
    after TEXT NOT NULL,
    created_at TEXT NOT NULL,
    seq BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS advancement_suggestion (
    child_id TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    from_stage TEXT NOT NULL,
    to_stage TEXT NOT NULL,
    dismissed INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (child_id, quest_id)
);

-- service-issued id counter, restart-safe (replaces itertools.count in memory).
CREATE TABLE IF NOT EXISTS id_counter (
    name TEXT PRIMARY KEY,
    value BIGINT NOT NULL
);

-- Phase F: durable auth + event/notification state.
-- Moves what used to live in AuthService / EventSink in-process dicts into the
-- database so a server restart no longer loses credentials, sessions, or the
-- child celebration / parent notification feeds.

-- credentials (email + PBKDF2 password hash + PBKDF2 PIN hash).
CREATE TABLE IF NOT EXISTS auth_account (
    email TEXT PRIMARY KEY,
    account_id TEXT NOT NULL UNIQUE,
    pw_hash TEXT NOT NULL,
    pin_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

-- opaque tokens: kind ∈ {session, parent, child}. expires_at NULL = no expiry
-- (child tokens). A session token is deliberately not a scope (resolve → None).
CREATE TABLE IF NOT EXISTS auth_token (
    token TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    account_id TEXT NOT NULL,
    child_id TEXT,
    expires_at TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_token_account ON auth_token (account_id);

-- failed-attempt tracking for login / unlock rate-limiting + lockout.
CREATE TABLE IF NOT EXISTS auth_attempt (
    scope_key TEXT PRIMARY KEY,   -- e.g. "login:mum@x.com" / "unlock:<account_id>"
    fail_count INTEGER NOT NULL DEFAULT 0,
    first_fail_at TEXT,
    locked_until TEXT
);

-- child celebration lane — every completion.verified lands here (always on).
CREATE TABLE IF NOT EXISTS celebration_event (
    seq BIGINT PRIMARY KEY,
    child_id TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    on_date TEXT NOT NULL,
    points_awarded INTEGER NOT NULL,
    at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_celebration_child ON celebration_event (child_id, at);

-- parent notification lane — populated only when account.notifications_enabled.
CREATE TABLE IF NOT EXISTS parent_notification (
    seq BIGINT PRIMARY KEY,
    account_id TEXT NOT NULL,
    child_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    text TEXT NOT NULL,
    at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_notification_account ON parent_notification (account_id, at);

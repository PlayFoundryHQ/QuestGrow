"""Runtime configuration + the production app builder (Phase F).

Everything a deployment needs to tune comes from the environment, with
production-safe defaults (no open CORS, a real on-disk database, sane TTLs and
abuse limits). None of these values is a product decision — they are
operational knobs, like ``pending_grace_days``.

``build_app()`` is the production entrypoint: it opens the configured
database, runs migrations, wires a ``SqlRepository`` + ``SqlEventSink`` +
``AuthService``, and returns the FastAPI app. ``questgrow.asgi:app`` exposes it
for ``uvicorn questgrow.asgi:app``.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _env_int(name: str, default: int) -> int:
    v = os.environ.get(name)
    return int(v) if v not in (None, "") else default


def _env_list(name: str) -> list[str]:
    v = os.environ.get(name, "")
    return [p.strip() for p in v.split(",") if p.strip()]


@dataclass
class Settings:
    # persistence
    database_url: str = field(
        default_factory=lambda: os.environ.get("QUESTGROW_DATABASE_URL", "sqlite://data/questgrow.db")
    )
    # auth / session — the parent-token TTL IS the re-challenge cadence (unchanged default)
    session_ttl_s: int = field(default_factory=lambda: _env_int("QUESTGROW_SESSION_TTL_S", 600))
    parent_ttl_s: int = field(default_factory=lambda: _env_int("QUESTGROW_PARENT_TTL_S", 900))
    # abuse protection (tunable operational defaults, not a DECISION)
    auth_max_attempts: int = field(default_factory=lambda: _env_int("QUESTGROW_AUTH_MAX_ATTEMPTS", 5))
    auth_window_s: int = field(default_factory=lambda: _env_int("QUESTGROW_AUTH_WINDOW_S", 900))
    auth_lockout_s: int = field(default_factory=lambda: _env_int("QUESTGROW_AUTH_LOCKOUT_S", 900))
    # domain
    pending_grace_days: int = field(
        default_factory=lambda: _env_int("QUESTGROW_PENDING_GRACE_DAYS", 1))
    advancement_threshold: int = field(
        default_factory=lambda: _env_int("QUESTGROW_ADVANCEMENT_THRESHOLD", 8))
    # transport — CORS is OFF unless an explicit allow-list is configured
    cors_origins: list[str] = field(default_factory=lambda: _env_list("QUESTGROW_CORS_ORIGINS"))

    @classmethod
    def from_env(cls) -> "Settings":
        return cls()


def build_app(settings: Settings | None = None):
    """Wire the production stack. Returns a FastAPI app."""
    from .api import create_app
    from .auth import AuthService
    from .db import open_database
    from .events import SqlEventSink
    from .migrate import run as run_migrations
    from .service import QuestGrowService
    from .sql_repository import SqlRepository

    s = settings or Settings.from_env()

    if s.database_url.startswith("sqlite://"):
        _ensure_sqlite_dir(s.database_url)

    db = open_database(s.database_url)
    run_migrations(db)
    repo = SqlRepository(db)
    events = SqlEventSink(db)
    svc = QuestGrowService(
        repo=repo, events=events,
        advancement_threshold=s.advancement_threshold,
        pending_grace_days=s.pending_grace_days,
    )
    auth = AuthService(
        svc, session_ttl_s=s.session_ttl_s, parent_ttl_s=s.parent_ttl_s,
        max_attempts=s.auth_max_attempts, window_s=s.auth_window_s,
        lockout_s=s.auth_lockout_s,
    )
    return create_app(svc, auth=auth, cors_origins=s.cors_origins)


def _ensure_sqlite_dir(url: str) -> None:
    from .db import sqlite_path

    path = sqlite_path(url)
    if path != ":memory:":
        d = os.path.dirname(path)
        if d:
            os.makedirs(d, exist_ok=True)

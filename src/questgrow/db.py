"""Database layer (Phase F) — connection lifecycle + a two-dialect seam.

QuestGrow's SQL is written once, in a portable subset, and run against either
SQLite (dev / tests / single-family) or PostgreSQL (multi-family production).
The only differences this module hides:

* **placeholder** — SQLite ``?`` vs Postgres ``%s`` (SQL is authored with
  ``?``; ``Database.q`` rewrites it for Postgres).
* **upsert** — both engines support ``INSERT … ON CONFLICT (…) DO UPDATE`` /
  ``DO NOTHING`` (SQLite ≥ 3.24), so the repository uses that directly.
* **row access** — ``sqlite3.Row`` and psycopg's ``dict_row`` both support
  ``row["col"]``.

Design constraints:
* No ORM. Plain SQL, explicit columns.
* ``:memory:`` SQLite must keep working (a single shared connection).
* Access is serialised by the single-writer ``QuestGrowService`` — this layer
  does not add its own locking, but SQLite gets ``busy_timeout`` + WAL on file
  databases and Postgres uses a small connection pool when ``psycopg_pool`` is
  available.
* Restart-safe: no monotonic state lives in Python here; sequence numbers are
  derived in SQL (``COALESCE((SELECT MAX(seq) …), 0) + 1``).
"""

from __future__ import annotations

import sqlite3
import threading
from collections.abc import Iterable, Iterator, Sequence
from contextlib import contextmanager
from typing import Any


class Database:
    """Base class. ``dialect`` is ``"sqlite"`` or ``"postgres"``."""

    dialect = "sqlite"

    def q(self, sql: str) -> str:
        """Rewrite ``?`` placeholders for the dialect."""
        return sql

    def execute(self, sql: str, params: Sequence[Any] = ()) -> Any:  # pragma: no cover - overridden
        raise NotImplementedError

    def executemany(self, sql: str, rows: Iterable[Sequence[Any]]) -> None:  # pragma: no cover
        raise NotImplementedError

    def fetchone(self, sql: str, params: Sequence[Any] = ()) -> Any:
        cur = self.execute(sql, params)
        return cur.fetchone()

    def fetchall(self, sql: str, params: Sequence[Any] = ()) -> list[Any]:
        cur = self.execute(sql, params)
        return list(cur.fetchall())

    @contextmanager
    def transaction(self) -> Iterator[None]:  # pragma: no cover - overridden
        yield

    def close(self) -> None:  # pragma: no cover - overridden
        pass


# --------------------------------------------------------------------------- #
# SQLite                                                                       #
# --------------------------------------------------------------------------- #
class SqliteDatabase(Database):
    dialect = "sqlite"

    def __init__(self, path: str = ":memory:") -> None:
        self.path = path
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        # durability / concurrency pragmas (no-ops / harmless on :memory:)
        self._conn.execute("PRAGMA foreign_keys = ON")
        if path != ":memory:":
            self._conn.execute("PRAGMA journal_mode = WAL")
            self._conn.execute("PRAGMA synchronous = NORMAL")
            self._conn.execute("PRAGMA busy_timeout = 5000")
        self._conn.commit()

    def execute(self, sql: str, params: Sequence[Any] = ()) -> sqlite3.Cursor:
        with self._lock:
            cur = self._conn.execute(sql, tuple(params))
            self._conn.commit()
            return cur

    def fetchone(self, sql: str, params: Sequence[Any] = ()) -> Any:
        # Materialise the row while the lock is held — a bare cursor returned
        # from execute() is stepped by fetchone() *after* the lock is released,
        # which races a concurrent writer on this shared connection.
        with self._lock:
            cur = self._conn.execute(sql, tuple(params))
            row = cur.fetchone()
            self._conn.commit()
            return row

    def fetchall(self, sql: str, params: Sequence[Any] = ()) -> list[Any]:
        with self._lock:
            cur = self._conn.execute(sql, tuple(params))
            rows = list(cur.fetchall())
            self._conn.commit()
            return rows

    def executemany(self, sql: str, rows: Iterable[Sequence[Any]]) -> None:
        with self._lock:
            self._conn.executemany(sql, [tuple(r) for r in rows])
            self._conn.commit()

    def executescript(self, script: str) -> None:
        with self._lock:
            self._conn.executescript(script)
            self._conn.commit()

    @contextmanager
    def transaction(self) -> Iterator[None]:
        with self._lock:
            try:
                yield
                self._conn.commit()
            except Exception:
                self._conn.rollback()
                raise

    def close(self) -> None:
        self._conn.close()


# --------------------------------------------------------------------------- #
# PostgreSQL                                                                   #
# --------------------------------------------------------------------------- #
class PostgresDatabase(Database):
    dialect = "postgres"

    def __init__(self, dsn: str) -> None:
        import psycopg
        from psycopg.rows import dict_row

        self.dsn = dsn
        self._dict_row = dict_row
        try:  # small pool when available
            from psycopg_pool import ConnectionPool

            self._pool = ConnectionPool(dsn, min_size=1, max_size=8, kwargs={"row_factory": dict_row})
            self._pool.wait(timeout=10)
            self._single = None
        except Exception:
            self._pool = None
            self._single = psycopg.connect(dsn, row_factory=dict_row, autocommit=True)
        self._lock = threading.RLock()

    def q(self, sql: str) -> str:
        # authored with ? — Postgres wants %s. No ? appears inside string
        # literals in this codebase's SQL, so a plain replace is safe.
        return sql.replace("?", "%s")

    @contextmanager
    def _conn(self):
        if self._pool is not None:
            with self._pool.connection() as c:
                yield c
        else:
            yield self._single

    def execute(self, sql: str, params: Sequence[Any] = ()):
        sql = self.q(sql)
        with self._lock, self._conn() as c:
            cur = c.execute(sql, tuple(params))
            if self._pool is not None:
                c.commit()
            return _MaterialisedCursor(cur)

    def executemany(self, sql: str, rows: Iterable[Sequence[Any]]) -> None:
        sql = self.q(sql)
        with self._lock, self._conn() as c:
            c.cursor().executemany(sql, [tuple(r) for r in rows])
            if self._pool is not None:
                c.commit()

    @contextmanager
    def transaction(self) -> Iterator[None]:
        with self._lock, self._conn() as c:
            if self._pool is not None:
                with c.transaction():
                    yield
            else:
                yield

    def close(self) -> None:
        if self._pool is not None:
            self._pool.close()
        elif self._single is not None:
            self._single.close()


class _MaterialisedCursor:
    """psycopg cursors are exhausted once the pooled connection returns to the
    pool; materialise rows eagerly so callers can ``.fetchone()`` / ``.fetchall()``
    afterwards, mirroring sqlite3's detached-cursor behaviour."""

    def __init__(self, cur) -> None:
        self.rowcount = cur.rowcount
        try:
            self._rows = list(cur.fetchall())
        except Exception:
            self._rows = []
        self._i = 0

    def fetchone(self):
        if self._i >= len(self._rows):
            return None
        r = self._rows[self._i]
        self._i += 1
        return r

    def fetchall(self):
        rest = self._rows[self._i:]
        self._i = len(self._rows)
        return rest


# --------------------------------------------------------------------------- #
# factory                                                                      #
# --------------------------------------------------------------------------- #
def sqlite_path(url: str) -> str:
    """``sqlite://<path>`` → ``<path>`` (``/abs`` stays absolute, ``rel`` stays
    relative); ``sqlite://`` / ``sqlite://:memory:`` → ``:memory:``."""
    rest = url[len("sqlite://"):] if url.startswith("sqlite://") else url
    return ":memory:" if rest in ("", ":memory:", "/:memory:") else rest


def open_database(url: str) -> Database:
    """``sqlite://<path>`` / a bare path → SQLite; ``postgres://…`` /
    ``postgresql://…`` → Postgres."""
    if url.startswith(("postgres://", "postgresql://")):
        return PostgresDatabase(url)
    if url.startswith("sqlite://"):
        return SqliteDatabase(sqlite_path(url))
    return SqliteDatabase(url)

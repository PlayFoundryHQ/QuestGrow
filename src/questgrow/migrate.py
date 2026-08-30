"""Migration runner (Phase F).

``run(db)`` applies every ``migrations/NNNN_*.sql`` that has not yet been
recorded in ``schema_migrations``, in filename order, each inside a
transaction. Idempotent — safe to call on every process start.

No framework: statements are split on ``;`` at top level. The migration SQL is
authored to stay within that constraint (no PL/pgSQL blocks, no ``;`` inside
string literals).

CLI: ``python -m questgrow.migrate <database-url>``  (defaults to
``QUESTGROW_DATABASE_URL`` or ``sqlite://:memory:``).
"""

from __future__ import annotations

import sys
from importlib.resources import files

from .db import Database, open_database

_MIG_TABLE = """
CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL
)
"""


def _split(script: str) -> list[str]:
    out, buf = [], []
    for line in script.splitlines():
        s = line.strip()
        if not s or s.startswith("--"):
            continue
        buf.append(line)
        if s.endswith(";"):
            out.append("\n".join(buf).rstrip().rstrip(";"))
            buf = []
    if any(b.strip() for b in buf):
        out.append("\n".join(buf).rstrip().rstrip(";"))
    return [s for s in out if s.strip()]


def _migration_files() -> list[tuple[str, str]]:
    d = files(__package__).joinpath("migrations")
    names = sorted(p.name for p in d.iterdir() if p.name.endswith(".sql"))
    return [(n[:-4], d.joinpath(n).read_text(encoding="utf-8")) for n in names]


def applied_versions(db: Database) -> set[str]:
    db.execute(_MIG_TABLE)
    return {r["version"] for r in db.fetchall("SELECT version FROM schema_migrations")}


def run(db: Database, *, verbose: bool = False) -> list[str]:
    """Apply pending migrations. Returns the list of versions applied now."""
    from datetime import datetime, timezone

    done = applied_versions(db)
    applied: list[str] = []
    for version, script in _migration_files():
        if version in done:
            continue
        with db.transaction():
            for stmt in _split(script):
                db.execute(stmt)
            db.execute(
                "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)",
                (version, datetime.now(timezone.utc).isoformat()),
            )
        applied.append(version)
        if verbose:
            print(f"applied {version}")
    return applied


def main(argv: list[str] | None = None) -> int:
    import os

    argv = argv if argv is not None else sys.argv[1:]
    url = argv[0] if argv else os.environ.get("QUESTGROW_DATABASE_URL", "sqlite://:memory:")
    db = open_database(url)
    applied = run(db, verbose=True)
    print(f"up to date ({len(applied)} applied this run)" if applied else "already up to date")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())

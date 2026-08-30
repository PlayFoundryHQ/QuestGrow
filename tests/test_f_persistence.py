"""Phase F — portable persistence, migrations, restart-safety, Postgres parity.

`SqlRepository` runs against SQLite and (when `QUESTGROW_TEST_POSTGRES_URL` is
set) PostgreSQL. These tests assert:

* the migration runner is idempotent on both engines;
* a full domain flow yields identical observable results on both;
* restart-safety: a brand-new repository object on the *same* database
  continues the ledger `seq` and the id counter without collision or reset,
  and all authoritative state survives (INV-11/12 preserved);
* the append-only ledger idempotency key still de-dupes on Postgres.
"""

from __future__ import annotations

import os
import uuid
from datetime import date

import pytest

from questgrow import (
    AuthService,
    ChildScope,
    EventSink,
    LedgerKind,
    OwnershipStage,
    ParentScope,
    PostgresRepository,
    QuestGrowService,
    QuestSchedule,
    Recurrence,
    SqliteRepository,
)
from questgrow.db import open_database
from questgrow.migrate import run as run_migrations

DAY = date(2026, 8, 3)
PG_URL = os.environ.get("QUESTGROW_TEST_POSTGRES_URL")


# --------------------------------------------------------------------------- #
# repository factories                                                         #
# --------------------------------------------------------------------------- #
def _sqlite_file(tmp_path):
    return SqliteRepository(str(tmp_path / "qg.db"))


def _postgres(_tmp_path):
    if not PG_URL:
        pytest.skip("QUESTGROW_TEST_POSTGRES_URL not set")
    # isolate: a fresh schema per test via a unique search_path… simplest is a
    # throwaway database name suffix on the same server.
    schema = "t_" + uuid.uuid4().hex[:12]
    admin = open_database(PG_URL)
    admin.execute(f"CREATE SCHEMA {schema}")
    admin.close()
    url = PG_URL + ("&" if "?" in PG_URL else "?") + f"options=-csearch_path%3D{schema}"
    return PostgresRepository(url)


REPO_FACTORIES = {"sqlite-file": _sqlite_file}
if PG_URL:
    REPO_FACTORIES["postgres"] = _postgres


@pytest.fixture(params=list(REPO_FACTORIES))
def repo_factory(request):
    return REPO_FACTORIES[request.param]


# --------------------------------------------------------------------------- #
# tests                                                                        #
# --------------------------------------------------------------------------- #
def test_migrations_idempotent(repo_factory, tmp_path):
    repo = repo_factory(tmp_path)
    assert run_migrations(repo.db) == []          # already applied by __init__
    versions = {r["version"] for r in repo.db.fetchall("SELECT version FROM schema_migrations")}
    assert {"0001_domain", "0002_auth_and_events"} <= versions


def _seed_and_run(repo):
    svc = QuestGrowService(repo=repo, events=EventSink(), advancement_threshold=8)
    parent = ParentScope("acct-1")
    child = ChildScope("mia")
    svc.create_account("acct-1")
    svc.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc.create_quest(parent, quest_id="teeth", title="Brush", icon="🪥", points=10)
    svc.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc.assign_quest(parent, child_id="mia", quest_id="teeth")
    svc.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                            target=OwnershipStage.CHILD_OWNED)
    svc.materialise_day(DAY)
    svc.submit_completion(child, child_id="mia", quest_id="teeth", day=DAY)
    return svc


def test_full_flow_parity(repo_factory, tmp_path):
    svc = _seed_and_run(repo_factory(tmp_path))
    assert svc.lifetime_achievement(child_id="mia") == 10
    assert svc.spendable_balance(child_id="mia") == 10
    earns = [e for e in svc.repo.all_ledger() if e.kind is LedgerKind.EARN]
    assert len(earns) == 1 and earns[0].points == 10
    # idempotency on the concrete engine
    assert svc.repo.append_ledger(earns[0]) is False
    assert len([e for e in svc.repo.all_ledger() if e.kind is LedgerKind.EARN]) == 1
    # audit + suggestion state present
    assert any(a.action == "ownership_advance" for a in svc.repo.audit_entries())


def test_restart_safety(repo_factory, tmp_path):
    """A new repository object on the same DB must continue, not reset."""
    if repo_factory is _postgres:
        pytest.skip("postgres schema is per-object in this harness; sqlite covers restart")
    path = str(tmp_path / "restart.db")
    svc1 = _seed_and_run(SqliteRepository(path))
    ledger1 = svc1.repo.all_ledger()
    audit1 = svc1.repo.audit_entries()
    last_id_1 = svc1._id("led")   # bumps the counter
    svc1.repo.close()

    # "restart": brand-new process would build a fresh repo on the same file
    repo2 = SqliteRepository(path)
    svc2 = QuestGrowService(repo=repo2, events=EventSink(), advancement_threshold=8)
    parent, kid = ParentScope("acct-1"), ChildScope("mia")

    # state survived
    assert svc2.lifetime_achievement(child_id="mia") == 10
    assert [e.id for e in svc2.repo.all_ledger()] == [e.id for e in ledger1]
    assert [a.id for a in svc2.repo.audit_entries()] == [a.id for a in audit1]

    # new work continues monotonically — no id collision, no seq reset
    new_id = svc2._id("led")
    assert new_id != last_id_1
    assert int(new_id.rsplit("-", 1)[1]) > int(last_id_1.rsplit("-", 1)[1])

    svc2.materialise_day(date(2026, 8, 4))
    svc2.submit_completion(kid, child_id="mia", quest_id="teeth", day=date(2026, 8, 4))
    seqs = [
        r["seq"] for r in svc2.repo.db.fetchall("SELECT seq FROM ledger ORDER BY seq")
    ]
    assert seqs == sorted(seqs) and len(seqs) == len(set(seqs))   # strictly monotonic, unique
    assert svc2.lifetime_achievement(child_id="mia") == 20


@pytest.mark.skipif(not PG_URL, reason="QUESTGROW_TEST_POSTGRES_URL not set")
def test_postgres_restart_safety(tmp_path):
    schema = "r_" + uuid.uuid4().hex[:12]
    admin = open_database(PG_URL)
    admin.execute(f"CREATE SCHEMA {schema}")
    admin.close()
    url = PG_URL + ("&" if "?" in PG_URL else "?") + f"options=-csearch_path%3D{schema}"

    svc1 = _seed_and_run(PostgresRepository(url))
    ids1 = [e.id for e in svc1.repo.all_ledger()]
    bump = svc1._id("led")
    svc1.repo.close()

    repo2 = PostgresRepository(url)          # "restart" — new connection/pool
    svc2 = QuestGrowService(repo=repo2, events=EventSink())
    assert svc2.lifetime_achievement(child_id="mia") == 10
    assert [e.id for e in svc2.repo.all_ledger()] == ids1
    assert int(svc2._id("led").rsplit("-", 1)[1]) > int(bump.rsplit("-", 1)[1])
    repo2.close()

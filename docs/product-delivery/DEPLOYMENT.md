# QuestGrow — Deployment & Operations

Operational reference for running the QuestGrow backend. This describes *how to
run it*, not *what it does* — the product contract is
[`TECHNICAL_MODEL.md`](../architecture/TECHNICAL_MODEL.md). Every value here is
an operational knob; none is a product decision.

## Entrypoint

```
uvicorn questgrow.asgi:app --host 0.0.0.0 --port 8000
```

`questgrow.asgi:app` calls `questgrow.config.build_app()`, which reads
`Settings.from_env()`, opens the configured database, **runs pending
migrations**, and wires `SqlRepository` + `SqlEventSink` + `AuthService` +
the FastAPI app.

## Configuration (environment)

| Variable | Default | Notes |
|---|---|---|
| `QUESTGROW_DATABASE_URL` | `sqlite://data/questgrow.db` | `sqlite://<path>` (a relative dir is created) or `postgresql://user:pw@host:5432/db` |
| `QUESTGROW_SESSION_TTL_S` | `600` | pre-gate session token lifetime |
| `QUESTGROW_PARENT_TTL_S` | `900` | parent-scope token lifetime = **the PIN re-challenge cadence** |
| `QUESTGROW_AUTH_MAX_ATTEMPTS` | `5` | failed logins/unlocks before lockout |
| `QUESTGROW_AUTH_WINDOW_S` | `900` | window the failures are counted over |
| `QUESTGROW_AUTH_LOCKOUT_S` | `900` | lockout duration once tripped |
| `QUESTGROW_PENDING_GRACE_DAYS` | `1` | IL-1 pending-instance grace window |
| `QUESTGROW_ADVANCEMENT_THRESHOLD` | `8` | DECISION-009 default (tunable) |
| `QUESTGROW_CORS_ORIGINS` | *(empty)* | comma-separated allow-list; **CORS is off unless set** |

## Database

* **SQLite** — zero-ops; suitable for a single family / dev / D1. WAL +
  `busy_timeout` + `foreign_keys` are enabled automatically on a file DB.
* **PostgreSQL** — for multiple families. Install the driver:
  `pip install -e '.[postgres]'`. A `psycopg_pool` connection pool is used
  when available. Point `QUESTGROW_DATABASE_URL` at the server.

### Migrations

Applied automatically on every process start (`migrate.run` is idempotent).
To apply them out of band:

```
python -m questgrow.migrate "$QUESTGROW_DATABASE_URL"
```

A `schema_migrations` table records what has run. Migration files live in
`src/questgrow/migrations/NNNN_*.sql` — portable SQL, one statement per `;`,
no engine-specific blocks. Add a new file with the next number; never edit an
applied one.

## Restart safety

A restart is safe. All authoritative state, credentials, tokens, and the
celebration / notification feeds are in the database. Nothing monotonic lives
in process memory — ledger/audit `seq` and service-issued ids are derived in
SQL, so a new process continues rather than resetting or colliding.
(`tests/test_f_persistence.py`, `tests/test_f_hardening.py`.)

## API surface for clients

* Every route is served **unprefixed** (reference web clients) and under
  **`/v1`** (pin this from a native client).
* Errors return `{"detail": "<human>", "code": "<stable-slug>"}` —
  `not_authenticated` (401), `not_authorized` (403), `not_found` (404),
  `contract_violation` (409), `bad_request` (422).
* `GET /health` — liveness.
* OpenAPI at `/openapi.json`.

## Not covered here (future grants)

Hosting topology / container images, TLS termination, managed-Postgres
provisioning, backups, log shipping, the native Android client (Phase G), and
push / real-time delivery.

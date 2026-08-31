# QuestGrow — PROJECT STATE (canonical current state)

> **Purpose.** This document is the single authoritative, repository-backed
> description of what QuestGrow **actually is today**. It is reconstructed by
> inspecting the code, the deployment, and the tests — not from chat history or
> phase reports. A previous phase report is *history*; this file is *current
> truth*. When a phase report and this file disagree, this file wins (or this
> file is wrong and should be corrected).
>
> Established: **2026-08-30**. The **code** state this describes is the tree at
> `2f15632` (release `v0.6.3`); this file and the accompanying documentation
> corrections are added in the immediately following **docs-only** commit
> `80449ec` (backend + Android suites pass unchanged — no behaviour change).
>
> **2026-08-31 — UX/terminology audit pass.** A whole-product parent-facing
> semantic audit (the Routines screen was the worked example). One small,
> in-model Android change: the parent **Routines** screen now shows which child
> a routine is being added to (a child switcher on the screen itself) and uses a
> single explicit action label — **«افزودن به {نام}»** — in place of the two
> unlabelled-target buttons «ساختن» / «اختصاص»; six dead string resources
> removed. No domain, API, or data-model change; no settled decision reopened.
> Findings that need an owner call are in §10/§11. Android unit 24 + lint pass;
> instrumented **not run this pass** (no emulator available — the change is copy
> + one picker row, `AppFlowTest` does not exercise the Routines screen).
>
> **2026-08-31 — post-audit reconciliation.** One corrective backend fix:
> `assign_quest` is now **idempotent** — re-assigning an already-assigned
> `(child, quest)` returns the existing `ChildQuest` unchanged instead of
> silently resetting its ownership stage + progress count (was CRACK-6; the
> reset contradicted INV-2 / DECISION-017, so this aligns code with an
> established decision rather than making a new one). Test added
> (`test_invariants.py::test_assign_quest_is_idempotent_preserves_stage_and_progress`).
> Backend: **61/8 stdlib + 117/1 venv** (both +1). Not yet cut as a release —
> CODE EXISTS on `main`, ships with the next backend version bump.

---

## 1. Current release / state summary

| Component | Version | State |
|---|---|---|
| Repository `HEAD` | `80449ec` — docs-only, on top of `2f15632` (`release: v0.6.3`) | `== origin/main`, tree clean, 72 commits |
| Latest git tag / GitHub Release | **`v0.6.3`** (2026-08-30) | 11 releases: `v0.3.1 … v0.6.3` |
| Backend package (`pyproject.toml`) | `0.6.3` | `FastAPI(title="QuestGrow API", version="0.6.3")` |
| Helm chart (`deploy/questgrow/Chart.yaml`) | `0.6.3` / appVersion `0.6.3` | published to `ghcr.io/playfoundryhq/charts/questgrow` for every release |
| Container image | `ghcr.io/playfoundryhq/questgrow:0.6.3` | published; also `:sha`, `:latest` per release |
| Android app (`versionName` at release) | `0.6.3` | signed `app-release.apk` attached to the GitHub Release |
| **Live deployment** (`questgrow.opscale.ir`) | **image `0.6.1`** | ArgoCD `Synced / Healthy`, tracking chart `0.6.1` |
| Open issues (this repo) | **0** | |
| Open PRs (this repo) | **0** | |
| Open PRs (ops repo `OpScaleLab/nuc-lab-operation`) | **#75** (bump 0.6.2), **#76** (bump 0.6.3) | not merged; both are pure `targetRevision` bumps |

**Why the live backend is `0.6.1` while the code is `0.6.3`:** releases `v0.6.1`,
`v0.6.2`, `v0.6.3` are **client-only** (Android). The backend source, API, schema
and behaviour are byte-for-byte identical across `0.6.0 → 0.6.3` — only the
version string in `pyproject.toml` / `api.py` / `Chart.yaml` changes. The live
`0.6.1` backend is functionally equal to `0.6.3`. Merging ops PR #76 aligns the
version label; it is a no-op deploy.

**Distinguish (used throughout this document):**

- **SHIPPED** — released as an artifact (tag + GitHub Release + image/chart) and, where applicable, deployed.
- **DEPLOYED** — running on `questgrow.opscale.ir` right now.
- **VERIFIED** — checked to work by a test or a hands-on run this reconciliation could reproduce.
- **CODE EXISTS** — implemented and compiles/passes tests but not independently exercised here.
- **DOCUMENTED** — described in a repo document.
- **PLANNED / DEFERRED** — named as future work, not built.

---

## 2. Product capabilities (what the product does now)

Each row: **1** implemented in code · **2** covered by an automated test · **3** deployed ·
**4** verified live/hands-on this session · **5** documented.

### Android app

| Capability | 1 | 2 | 3 | 4 | 5 | Notes |
|---|---|---|---|---|---|---|
| First-run stepper (who → account → child → routines) | ✓ | ✓ (`AppFlowTest`) | ✓ | ✓ | ✓ | 4 steps, progress dots, Persian errors mapped |
| Child-device pairing (6-digit code) | ✓ | ✓ | ✓ | ✓ | ✓ | `POST /v1/auth/pairing-code` → `POST /v1/auth/pair` |
| **Returning-parent sign-in recovery** ("قبلاً حساب دارم") | ✓ | — | ✓ | ✓ | ✓ | v0.6.1; `AuthRepository.signInExisting` |
| Kid board (default surface, no login) | ✓ | ✓ | ✓ | ✓ | ✓ | greeting + weekday, 2-col quest grid, tonal footer |
| **Multi-child avatar switcher on the family phone** | ✓ | ✓ (`TokenStore` add/switch/remove) | ✓ | ✓ | ✓ | shows when ≥2 children activated; auto-synced from the account (v0.6.2) |
| Do-it → "I did it" → celebration / waiting | ✓ | ✓ (verified & pending paths) | ✓ | ✓ | ✓ | server decides outcome (Mode A pending / Mode B celebrate) |
| Child progress screen (week strip + stars + spendable) | ✓ | — | ✓ | ✓ | ✓ | progressive-consistency framing, **no streak** (INV-16) |
| **Kid rewards screen + redemption request** | ✓ | ✓ (`AppFlowTest`) | ✓ | ✓ | ✓ | v0.5.0; `GET /v1/me/rewards`, `POST /v1/me/rewards/{id}/redeem` |
| Parent PIN gate (4-digit) | ✓ | ✓ (wrong→right) | ✓ | ✓ | ✓ | replays stored login+unlock; PIN-only everyday |
| Parent home (child glance + inboxes + setup hub) | ✓ | ✓ (approvals inbox) | ✓ | ✓ | ✓ | v0.6 card-hub redesign |
| Approvals inbox (approve / not-yet / approve-all) | ✓ | ✓ | ✓ | ✓ | ✓ | |
| **Reward-redemption inbox** (grant / decline) | ✓ | ✓ (`AppFlowTest`) | ✓ | ✓ | ✓ | v0.5.0; `GET /v1/redemptions` |
| Routines: starter templates → create family routine → add to a child | ✓ | ✓ (`test_invariants` idempotent-assign) | ✓ | ✓ | ✓ | v0.6.3+: on-screen child switcher; single «افزودن به {نام}» action; `assign_quest` idempotent (§10 CRACK-6, fixed) |
| Rewards: create (cost, self-service / parent-confirmed) | ✓ | — | ✓ | ✓ | ✓ | |
| Ownership: set stage per (child, quest); suggestions accept/dismiss | ✓ | — | ✓ | ✓ | ✓ | 4 stages parent-facing only (INV-8) |
| Children section (list; add child) | ✓ | — | ✓ | ✓ | ✓ | v0.6.2 dropped the per-child "activate on device" toggle |
| Settings: notifications toggle, child pairing code, **صدای فارسی (TTS status)**, backend URL, sign-out, forget device | ✓ | — | ✓ | ✓ | ✓ | TTS status row added v0.6.3 |
| Backend-URL change + clean relaunch | ✓ | ✓ (`ApiContractTest` retarget) | ✓ | — (this session) | ✓ | `restartApp()` — `NEW_TASK|CLEAR_TASK` + `exit(0)` |
| Offline read cache (last board + progress, "stale" banner) | ✓ | ✓ (`OfflineCacheTest`) | ✓ | — | ✓ | **single-slot — see §10 CRACK-1** |
| Offline write queue ("I did it" offline → flush on reconnect) | ✓ | ✓ (`OfflineAndSyncTest`) | ✓ | — | ✓ | 409 on replay = "already resolved", dropped (INV-11). **No childId — see §10 CRACK-1** |
| Persian-only, RTL, Persian digits, Vazirmatn | ✓ | ✓ (INV-8 string scan) | ✓ | ✓ | ✓ | DECISION-020; forced `fa` regardless of device locale |
| Dark mode | ✓ | — | ✓ | ✓ (earlier physical-device, dark) | ✓ | `isSystemInDarkTheme()`; full light/dark M3 palette |
| Loading / Empty / Error+Retry states | ✓ | ✓ (`Loadable`) | ✓ | ✓ | ✓ | |
| Tap-to-hear TTS / auto-read | ✓ | — | ✓ | ✗ **audible NOT VERIFIED** | ✓ | see §6 |
| Accessibility: ≥64dp child targets, content-descriptions, text+glyph state | ✓ | — (semantics-layer checked earlier) | ✓ | partial (earlier) | ✓ | live TalkBack traversal NOT VERIFIED |
| Persistence across process death | ✓ | — | ✓ | ✓ (relaunch) | ✓ | DataStore + file queue/cache survive |
| Device backup / transfer of auth | **disabled by design** | — | ✓ | — | ✓ | `data_extraction_rules.xml` excludes file/db/sharedpref |
| Push / real-time notifications | **not implemented** | — | — | — | ✓ (noted absent) | poll only, matches backend |
| Multiple **accounts** on one device | **not implemented** | — | — | — | ✓ (this doc) | one account per device; many children per account |
| iOS / Play Store / `.aab` / analytics / monetization | **not implemented / out of scope** | — | — | — | ✓ | |

### Backend

| Capability | 1 | 2 | 3 | 4 | 5 | Notes |
|---|---|---|---|---|---|---|
| `/v1` + legacy (unprefixed) API — 39 logical endpoints, each at `/` and `/v1/` | ✓ | ✓ (`test_api.py`, OpenAPI scan) | ✓ | ✓ (`/openapi.json` live) | ✓ | |
| Auth: signup / login / unlock(PIN) → session→parent→child tokens | ✓ | ✓ (`test_auth.py` ×9) | ✓ | ✓ | ✓ | no refresh token (auth-policy) |
| 6-digit pairing code → child token (single-use, 15-min TTL) | ✓ | ✓ (`test_auth.py`) | ✓ | ✓ | ✓ | kind `pair`; not resolvable as a scope |
| Rate-limit on login/unlock (5 / 900s / 900s lockout) | ✓ | ✓ (`test_f_hardening.py`) | ✓ | — | ✓ | env-tunable |
| Parent/child scope enforcement at HTTP edge + service | ✓ | ✓ (`test_api.py` 403 tests, `test_invariants.py`) | ✓ | ✓ (`/v1` unauth → 401) | ✓ | INV-5/10/12/17/18 |
| `_parent_owns_child` account isolation | ✓ | ✓ (`test_invariants.py`) | ✓ | ✓ (cross-child → `not_authorized`) | ✓ | `child.account_id == parent.account_id` |
| Quests: versioned, schedule, assign, materialise | ✓ | ✓ | ✓ | ✓ | ✓ | |
| Completion → verification derived from `ownership_stage` (pure fn) | ✓ | ✓ (`test_acceptance.py` AC-1/2) | ✓ | ✓ | ✓ | INV-4/10 |
| Approvals (parent) / not-yet / batch | ✓ | ✓ | ✓ | ✓ | ✓ | |
| Ledger: append-only, idempotent, one `earn` per verified completion | ✓ | ✓ (`test_invariants.py` INV-11/12/13, `test_c1_persistence.py`) | ✓ | — | ✓ | `ON CONFLICT DO NOTHING` on `idempotency_key` |
| Lifetime Achievement (monotonic) ⟂ Spendable Balance | ✓ | ✓ (`test_acceptance.py` AC-6) | ✓ | — | ✓ | DECISION-015, INV-13 |
| Rewards + redemptions (self-service / parent-confirmed) | ✓ | ✓ (`test_rewards_inbox.py` ×3, `test_integration_mvp.py`) | ✓ | ✓ | ✓ | v0.5.0 added `GET /v1/me/rewards`, `GET /v1/redemptions` |
| Advancement suggestions (`consecutive_ok_count ≥ threshold`) | ✓ | ✓ (`test_d1_acceptance.py`) | ✓ | — | ✓ | never surfaced as a streak (INV-16) |
| Regression (any earlier stage; neutral) | ✓ | ✓ (`test_acceptance.py` AC-4, `test_invariants.py` INV-7) | ✓ | — | ✓ | DECISION-010 |
| `complexityProfile` — 8 dimensions, per-dimension parent overrides, no stage/level | ✓ | ✓ (`ComplexityProfileTest` android, `test_api.py`) | ✓ | ✓ (`/me/today` payload) | ✓ | INV-8 |
| Celebration feed (child, poll) + notification feed (parent, opt-in, poll) | ✓ | ✓ (`test_notifications.py` ×7) | ✓ | ✓ | ✓ | `SqlEventSink`; no push |
| Portable persistence SQLite / PostgreSQL + migrations on startup | ✓ | ✓ (`test_c1_persistence.py`, `test_f_persistence.py`) | ✓ (SQLite) | ✓ | ✓ | Postgres test SKIPPED (needs `QUESTGROW_TEST_POSTGRES_URL`) |
| Restart-safe ids / `seq` (SQL `MAX+1`, `id_counter` row) | ✓ | ✓ (`test_f_persistence.py`) | ✓ | ✓ (pod restarts leave data intact — verified after DB wipe) | ✓ | |
| Reference web clients (`/app/child`, `/app/parent`) | ✓ | ✓ (`test_webclient.py` ×8) | ✓ | ✓ (200) | ✓ | **English**, retired as a *product* surface (DECISION-020) but still shipped & served |
| Clock admin (`/clock/materialise`, `/clock/end-of-day`) | ✓ | ✓ | ✓ | ✓ | ✓ | parent-scope; used by the client + tests |
| CORS | **off** (`QUESTGROW_CORS_ORIGINS=""`) | ✓ | ✓ | — | ✓ | |
| Subscription / monetization / admin console | **not implemented** | — | — | — | ✓ (absent) | |

### Deployment / operations — see §5 and §10.

---

## 3. Actual architecture

### Repository layout

```
QuestGrow/
├── src/questgrow/            backend — Python 3.11+, FastAPI, dependency-free domain
│   ├── enums / entities / ownership / scheduling / projections / service   the domain (no deps)
│   ├── adaptation.py         §13 complexityProfile resolver
│   ├── repository.py         Protocol + InMemoryRepository
│   ├── db.py / sql_repository.py / sqlite_repository.py   portable SQLite/Postgres seam
│   ├── migrations/*.sql      0001_domain, 0002_auth_and_events (applied on startup)
│   ├── migrate.py            idempotent migration runner
│   ├── auth.py / auth_store.py   AuthService + SqlAuthStore/InMemoryAuthStore
│   ├── events.py             SqlEventSink / EventSink (celebration + notification lanes)
│   ├── notifications.py      parent notification templates + banned-phrase list
│   ├── api.py                FastAPI transport — TokenStore, scope resolution, `/` + `/v1`
│   ├── config.py             Settings.from_env() + build_app()
│   ├── asgi.py               `uvicorn questgrow.asgi:app` → build_app()
│   └── webclient/            child.html · parent.html  (served at /app/child, /app/parent)
├── tests/                    117 collected (see §7)
├── android/                  native client — Kotlin, Jetpack Compose, single :app module
├── deploy/questgrow/         Helm chart (Chart, values, values-nuc-lab, templates/)
├── docs/                     product / UX / architecture / governance (see docs/README.md)
├── scripts/                  test.sh · release.sh
├── Dockerfile                multi-stage; non-root uid 10001; migrations on startup
└── pyproject.toml            version 0.6.3
```

No CI/CD in the repo (`.github/workflows/` absent — deliberate, see §5 and the
`cicd-approach` project note).

### Backend architecture

- **Domain layer** (`enums`, `entities`, `ownership`, `scheduling`, `projections`,
  `service.QuestGrowService`) — pure Python, no framework imports. All rules
  (verification derivation, ownership transitions, ledger, suggestions) live
  here. `QuestGrowService` is the single authority.
- **Repository** — `repository.Repository` `Protocol` with `InMemoryRepository`
  (tests) and `sql_repository.SqlRepository` in two dialects
  (`SqliteRepository`, `PostgresRepository`). `db.py` hides the `?`↔`%s`
  placeholder difference; SQLite gets WAL + `busy_timeout` + `foreign_keys`,
  Postgres a `psycopg_pool`. Schema in `migrations/NNNN_*.sql`; a
  `schema_migrations` table tracks what ran. Nothing monotonic in Python —
  `seq` and ids come from SQL (`MAX+1`, `id_counter` row), so a restart continues.
- **Auth** — `auth.AuthService` over `auth_store` (`SqlAuthStore` /
  `InMemoryAuthStore`). Email + PBKDF2 password + PBKDF2 PIN. Token flow:
  `login → session (600s) → unlock(PIN) → parent (900s default / 43200s deployed) → child (no TTL)`.
  `pairing-code → pair → child`. Session tokens are deliberately **not a scope**
  (`resolve` → `None`). No refresh tokens. Rate-limited (5 / 900s / lockout 900s).
- **Transport** (`api.py`) — FastAPI. A bearer token resolves to
  `ChildScope(child_id)` or `ParentScope(account_id)`; the §5 actor matrix is
  enforced at the HTTP edge (403 before the service) **and again** in the
  service. Child response models structurally carry no stage/level field
  (INV-8, asserted against the generated OpenAPI in `test_api.py`). Every
  endpoint is mounted twice — unprefixed (legacy) and `/v1/`.
- **Events** (`events.py`) — two poll-based lanes on `SqlEventSink`: the child
  celebration lane fires on every `completion.verified`; the parent
  notification lane fires only when `Account.notifications_enabled`. No push.
- **Config** (`config.py`) — `Settings.from_env()` (all `QUESTGROW_*`),
  `build_app()` opens the DB, runs migrations, wires everything, returns the
  FastAPI app.

**Runtime dependencies:** `fastapi>=0.110`, `uvicorn>=0.29`. Optional:
`psycopg[binary]>=3.1` + `psycopg_pool>=3.2` (`.[postgres]`, installed in the
image), `pytest>=8` + `httpx>=0.27` (`.[test]`). No other third-party services.

### Android architecture

Single Gradle module `:app`, package `hq.playfoundry.questgrow`. Kotlin 2.0.21,
AGP 8.7.3, Compose BOM 2024.12.01. minSdk 26, target/compile 35, Java 17.
**Deliberately no Hilt/KAPT and no Room/KSP** — manual DI keeps the client
single-pass-buildable; the offline queue stays JVM-unit-testable.

```
core/        ApiResult (Ok / Failure(status,code,detail) / Offline) · Loadable
data/net/    QuestGrowApi (every /v1 route) · Dtos (1:1 with api.py) · ApiClient
             (Retrofit + OkHttp + kotlinx.serialization; auth interceptor attaches
             the current-scope bearer token)
data/local/  TokenStore (DataStore) · OfflineQueue (FileOfflineQueue) · ReadCache
data/        AuthRepository · ChildRepository (offline-first) · ParentRepository
data/model/  Models.kt — UI domain types; QuestVisualState never carries a stage (INV-8)
adapt/       ComplexityProfile — consumes the server's §13 values, no age logic
ui/          Theme · Scaffold (AppScaffold/SectionHeader/Avatar/StepDots/SelectRow/SelectPill/Space)
             Common (BigButton/SecondaryButton/GhostButton/Field/DigitPad) · Fa · Locale · Starters · Ext
ui/onboarding/  OnboardingFlow — the stepper + sign-in + pairing branches
ui/child/    ChildFlow (board · do-it+TTS · waiting · celebration · progress · rewards)
             ChildViewModel · ChildTts (Narrator)
ui/parent/   ParentGate (PIN pad) · ParentFlow (home hub + sections) · ParentViewModel
MainActivity  AppRoot — the Onboarding / Kid / Gate / Parent state machine
QuestGrowApp  AppContainer (manual DI graph) + connectivity observer + close()
```

- **State management** — `ViewModel` + `StateFlow`; `collectAsStateSafe()`.
- **DI** — `AppContainer` built in `QuestGrowApp.onCreate`; `rebuildContainer()`
  (URL retarget / test) calls `container.close()` first (unregister the
  `ConnectivityManager` callback, cancel `appScope`) — v0.6.3.
- **Networking** — Retrofit pinned to the `v1/`-prefixed surface; `apiCall {}`
  folds every call into `ApiResult`. An OkHttp interceptor attaches the bearer
  token for the *current* scope (`AppContainer.Scope` — NONE/PARENT/CHILD).
- **Persistence** — DataStore-Preferences for tokens/account (`TokenStore`),
  a file-backed JSON offline queue, a two-file read cache. No Room, no DB.

**What must be running externally for the Android app to function:**
the QuestGrow backend (`https://questgrow.opscale.ir/` by default, or any URL
set in Settings / `QG_BACKEND_URL`). Every authoritative operation — identity,
`complexityProfile`, verification, rewards, ledger, approvals, ownership — is a
server call.

**What is fully local / offline on the device:**
- the last successfully-fetched child *Today* board and *Progress* (read cache,
  shown "stale");
- a queued "I did it" tap, replayed on reconnect;
- the stored account email+password and child token(s), and which child is
  active;
- the forced `fa`/RTL locale, Persian digit rendering, reduced-motion honouring,
  and the PIN gate re-prompt (the PIN itself is re-verified server-side — the
  client only knows how to replay login+unlock).

Nothing product-authoritative is computed on the device.

### Deployment architecture — see §5.

### Divergence from original architectural intent

| Area | Original intent | Actual | Class |
|---|---|---|---|
| Client surface | Two reference web clients (`/app/child`, `/app/parent`), English | Native Android (Kotlin/Compose), Persian/RTL, is the product surface; web clients kept as a QA/contract tool only | **Intentional product evolution** (DECISION-020; Phases G–M) |
| Identity on device | "a child device holds one child token" (README, ReadCache comment) | family device holds **many** child tokens + an active pointer; switches between children | **Intentional product evolution** (DECISION-021 / Phase M / v0.6.2) — but the offline layer did not follow (see §10 CRACK-1) |
| API path versioning | flagged in E_READINESS as "minor additive before a public client ships" | done — every route at `/` **and** `/v1/`; client pins `/v1` | Intentional |
| Multi-family production | Postgres + pooling + migrations ("post-D1") | code supports it; **deployed on SQLite** (single family, proportionate) | Intentional (deferred), documented |
| Parent-token TTL | 900s default = "the re-challenge cadence" | deployed at **43200s (12h)** via `values-nuc-lab.yaml` | Intentional operational tuning (auth-policy note); **not** a decision change |

---

## 4. Identity / multi-account model

### Definitions (from the code)

| Concept | Definition | Where |
|---|---|---|
| **Account** | `account` row keyed `account_id` (`acct_<hex>`); one `auth_account` row (email + PBKDF2 password + PBKDF2 PIN). Holds children, quests, rewards, `notifications_enabled`. | `migrations/0001`, `0002`; `auth.py::signup` |
| **Child** | `child` row keyed `child_id`; `account_id` FK; `name`, `age_band`, optional coarse `birthdate`, `adaptation_overrides` JSON. **No independence/ownership-level field** (INV-1). | `migrations/0001`; `entities.Child` |
| **Parent** | **not a separate entity.** The "parent" is the account holder, reached by passing the PIN gate. `ParentScope(account_id)`. | `scope.py`; `auth.py::unlock` |
| **`ChildQuest`** | ownership lives here, keyed `(child_id, quest_id)` — `ownership_stage`, `consecutive_ok_count`. | `migrations/0001`; INV-2 |

### Backend identity behaviour

- **Many accounts per backend** — yes. Each is fully isolated:
  `_parent_owns_child` rejects any parent operation on a child whose
  `account_id` differs (`AuthorizationError "parent does not own this child"`).
  A `ChildScope` carries only `child_id`; every `/me/*` endpoint operates on
  `c.child_id` — a child token cannot name another child.
- **Tokens** — `auth_token(token, kind ∈ {session, parent, child, pair}, account_id, child_id, expires_at)`.
  `session` (600s), `parent` (900s default / 43200s deployed), `child` (NULL =
  no expiry), `pair` (15 min, single-use, consumed on redeem). `session` and
  `pair` are **not scopes** (`resolve` → `None` for `pair`; explicit for
  `session`).
- **No refresh token.** On parent-token expiry the client re-runs login+unlock
  (a PIN re-prompt). Child tokens do not expire.

### Android identity behaviour (`TokenStore` = `DataStoreTokenStore`)

- **One account per device.** `TokenStore` stores exactly one
  `account_email` + `account_password` (so the PIN gate is PIN-only — it
  replays login+unlock). There is **no** UI or storage for a second account.
- **Many children per device.** `child_tokens` (JSON `{child_id → token}`),
  `child_names` (`{child_id → name}`), `child_order` (CSV), `active_child_id`,
  plus a legacy single `child_token` for a paired kid-only device. This map is
  mirrored in memory (a `Kids` struct) so a blocking read never races a
  DataStore write that is still flushing.
- **Family device vs kid-only device** — a family device has a stored account;
  a kid-only device paired with a 6-digit code has a child token and **no**
  account. `AuthRepository.syncFamilyChildren()` no-ops on a kid-only device.
- **Switching** — the kid board shows an avatar row when ≥2 children are on the
  device; tapping switches `active_child_id` and reloads. On every parent-area
  refresh and after adding a child, `syncFamilyChildren()` mints a token for
  every account child that lacks one and drops tokens for children no longer on
  the account — so on the family phone every child appears automatically, with
  **no per-child "activate" step** (v0.6.2).
- **Sign-in recovery** — after "clear app data", the account is gone → the PIN
  gate can't work → onboarding. "قبلاً حساب دارم" → `signInExisting` (stores the
  account) → `syncFamilyChildren` → the board (v0.6.1 / v0.6.2).

### What is scoped to which identity

| Data | Scope | Notes |
|---|---|---|
| account email + password | device (one) | DataStore; excluded from backup/transfer |
| child token(s) | device, per child | DataStore map |
| `active_child_id` | device | which board shows |
| parent token | device (transient) | cleared on 401 / sign-out |
| **offline read cache** (`today.json`, `progress.json`) | **device — single slot, NOT per child** | ⚠ CRACK-1 (§10) |
| **offline write queue** | **device — single file, entries carry no `child_id`** | ⚠ CRACK-1 (§10) |
| server data (quests/ledger/approvals/…) | account (parent) / child (child) | enforced server-side |

### What survives what

| Event | Account creds | Child tokens | Parent token | Cache/queue | Server data |
|---|---|---|---|---|---|
| App restart / process death | ✓ | ✓ | ✓ (until TTL) | ✓ | ✓ |
| Backend-URL change + relaunch | ✓ | ✓ | ✓ | ✓ | n/a (new backend) |
| Sign out (Settings) | ✓ (kept) | ✓ | **cleared** | ✓ | ✓ |
| Forget this device (Settings) | **cleared** | **cleared** | **cleared** | **cleared** | ✓ |
| `pm clear` / OS "clear data" | **cleared** | **cleared** | **cleared** | **cleared** | ✓ (recover via sign-in) |
| Parent-token expiry | ✓ | ✓ | expired → re-unlock | ✓ | ✓ |
| Child removed on the account | ✓ | its token dropped on next `syncFamilyChildren` | ✓ | ⚠ its cache/queue not cleared | ✓ |
| OS backup / device transfer | **not restored** (excluded) | **not restored** | — | **not restored** | ✓ |

### Cross-identity leakage assessment

- **Backend:** no leakage. Cross-account and cross-child access is rejected
  (`_parent_owns_child`; `ChildScope` on own id only; INV-18). Verified this
  session: a parent request naming another account's child → `not_authorized`.
- **Android, online:** no leakage — every screen fetches fresh with the active
  identity's token.
- **Android, offline + multi-child:** **CRACK-1** — the single-slot cache and
  child-unaware queue mean switching children while offline shows the previous
  child's cached board (flagged "stale" but the payload is the other child's),
  and a completion queued for child A then flushed after switching the active
  child to B replays against B's token. Online (the normal case) is unaffected.
  See §10.

---

## 5. Backend & deployment

### Hosting

- **Cluster:** `nuc-lab` (k0s). Namespace `questgrow`.
- **Ingress:** Traefik (control-plane hostPort 80/443, SNI routing). The chart
  renders an `IngressRoute` (`ingress.kind=ingressroute`) for host
  `questgrow.opscale.ir`.
- **TLS:** ArvanCloud edge-terminates TLS for `*.opscale.ir` (Let's Encrypt
  wildcard, edge IPs 185.143.233.238 / .234.238). `ingress.tls.enabled=false`
  — **no in-cluster cert-manager Certificate**; the ArvanCloud→origin hop uses
  Traefik's default cert on `websecure`.
- **DNS:** `questgrow.opscale.ir` → ArvanCloud edge (owner-managed).

### Kubernetes resources (rendered by `deploy/questgrow/templates/`)

| Resource | Detail |
|---|---|
| `Deployment` | `replicas: 1`, `strategy: Recreate` (SQLite single-writer). Pod `securityContext`: `runAsNonRoot`, uid/fsGroup 10001, `seccompProfile: RuntimeDefault`. Container `securityContext`: `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities: drop ["ALL"]`. Mounts: `/data` (PVC), `/tmp` (emptyDir). Readiness + liveness `httpGet /health`. |
| `Service` | `ClusterIP`, port 80 → container 8000. |
| `PersistentVolumeClaim` (`questgrow-data`) | 1Gi, `ReadWriteOnce`, `local-path`. `helm.sh/resource-policy: keep`. |
| `IngressRoute` | host `questgrow.opscale.ir`, entrypoint `websecure`, service → `questgrow:80`. |

### Configuration model

All backend config is `QUESTGROW_*` environment, set by the chart from
`values*.yaml` (see `docs/product-delivery/DEPLOYMENT.md` for the full table).
Deployed non-default values (`values-nuc-lab.yaml`): image tag `0.6.3` (label
only; ArgoCD tracks `0.6.1`), `QUESTGROW_PARENT_TTL_S=43200`,
`QUESTGROW_CORS_ORIGINS=""`. `QUESTGROW_DATABASE_URL=sqlite:///data/questgrow.db`.

### Secrets

**None.** The GHCR packages (`questgrow`, `charts/questgrow`) are public, so
there is no `imagePullSecret` and the ArgoCD `Repository` secret
`ghcr-playfoundryhq-charts` carries no credential. SQLite needs no DB password.
The chart has **no Secret template**.

### Persistent vs ephemeral

- **Persistent:** `/data/questgrow.db` on the `questgrow-data` PVC (SQLite;
  WAL + shm files alongside).
- **Ephemeral:** everything else — a pod restart re-runs migrations against the
  existing DB and continues (ids/`seq` are DB-sourced). `/tmp` is emptyDir.

### Deploy & update mechanism

1. `./scripts/release.sh X.Y.Z [--with-apk]` on the build machine:
   run tests → bump versions (`pyproject.toml`, `api.py`,
   `deploy/questgrow/Chart.yaml`, `values-nuc-lab.yaml`) → build & health-check
   the image → one commit → git tag `vX.Y.Z` → push image (`X.Y.Z`, `sha`,
   `latest`) to GHCR → push the OCI Helm chart → `gh release create` (attach
   the signed APK with `--with-apk`).
2. A PR to `OpScaleLab/nuc-lab-operation` bumps
   `gitops/apps/questgrow.yaml` `targetRevision`.
3. Merge → **ArgoCD** syncs (`syncPolicy.automated`, `prune:false`,
   `selfHeal:false`).

No GitHub Actions. Local build → GHCR → tag → Release → ops PR → ArgoCD.

### Health / restart / rollback

- `/health` → `{"status":"ok","api":"<version>"}`. Readiness/liveness probe it.
- After restart: migrations re-run (idempotent), data intact.
- **Rollback:** revert the `targetRevision` in the ops repo (older chart+image
  are retained on GHCR). No DB down-migration path (append-only additive
  migrations; a rollback across a migration would need care — not exercised).

### Reproducibility

- **From Git:** the chart + `values-nuc-lab.yaml` fully describe the workload.
  The image is built from the repo `Dockerfile` at a tagged commit.
- **Manual, outside Git:**
  1. the ArgoCD `Application` (`gitops/apps/questgrow.yaml`) lives in the
     **ops repo**, not this one;
  2. the ArgoCD `Repository` secret `ghcr-playfoundryhq-charts` was created by
     hand (`kubectl -n argocd` — command in `deploy/README.md`);
  3. GHCR package visibility (public) was set in the GitHub UI by the owner;
  4. DNS + ArvanCloud edge config is owner-managed;
  5. the Android **signing keystore** (`/home/iceman/questgrow-release.jks`)
     and its credentials are on the build machine **only** — not in any repo.

---

## 6. TTS / accessibility

### Implementation (`android/.../ui/child/ChildTts.kt`, as of v0.6.3)

- **API:** `android.speech.tts.TextToSpeech`. Constructed with the user's
  **default engine** (`engine = null`); on failure, walks `tts.engines` and
  keeps the first whose `setLanguage(Locale("fa","IR"))` returns
  `LANG_AVAILABLE` / `LANG_COUNTRY_AVAILABLE` / `LANG_COUNTRY_VAR_AVAILABLE`.
- **Manifest** (v0.6.3): `<queries>` for `android.intent.action.TTS_SERVICE`
  **plus** an explicit `<package android:name="com.github.opscalehub.avacore" />`.
  Without this, Android 11+ (targetSdk 35) hides a separate-app engine and
  every `TextToSpeech` init fails — **this was the bug that kept "بشنو" silent**.
- **Threading:** the `TextToSpeech` binding is built off the main thread
  (`LaunchedEffect { withContext(Dispatchers.IO) { narrator.connect() } }`) so a
  slow/absent engine never stalls the board. `hasVoice` and `status` are
  Compose-observable (`mutableStateOf`).
- **Text spoken:** quest **titles only** (`narrator.say(title)` on the do-it
  screen; on the board, the visible quests joined by `، ` when
  `complexity_profile.audio_narration == "always"`). **No stage, level,
  readiness, or KPI text is ever passed to TTS** — the child-facing model
  carries none (INV-8), so this is structurally safe.
- **Fallback / failure:** if no engine accepts `fa`, `hasVoice = false`,
  `say()` is a no-op, and the "بشنو" button is **hidden** — the visible label
  is the fallback.
- **Offline:** TTS is a device service — no network. (AvaCore is an offline
  neural engine.)
- **User controls:** the "بشنو" button (tap-to-hear) on the do-it screen;
  parent **Settings → صدای فارسی** shows the engine status ("متصل (…)" /
  "این موتور صدا فارسی ندارد …") and an **«امتحان صدا»** test button.
- **Accessibility semantics:** quest cards and action buttons carry
  `contentDescription` (the quest name even when the label is visually hidden);
  state is shown as text + glyph ("✓ انجام شد" / "⏳ منتظر"), never colour
  alone; child touch targets ≥ 64 dp; reduced-motion honoured; forced large
  readable type; RTL throughout.

### AvaCore (the intended engine — sibling repo, not in this repo)

- Repo `OpScaleHub/AvaCore` (owner is migrating it to `PlayFoundryHQ`); package
  `com.github.opscalehub.avacore`. Sherpa-ONNX neural VITS Persian TTS. Its
  own manifest is correct — `AvaTtsService` with
  `BIND_TEXT_TO_SPEECH_SERVICE`, `CHECK_TTS_DATA` activity,
  `onIsLanguageAvailable("fa"|"fas") → LANG_COUNTRY_AVAILABLE`, voice
  `fa-ir-ava-premium` / `Locale("fa","IR")`, and its own `<queries>`.

### Verification

| Item | Status |
|---|---|
| Manifest `<queries>` merges into the final manifest | **VERIFIED** (merged-manifest inspection) |
| Settings → صدای فارسی renders + reports engine status | **VERIFIED on emulator** — correctly showed "no Persian voice" (only Google TTS, which returns `LANG_NOT_SUPPORTED` for `fa`; log: `setLanguage(fa-IR)=-2`) |
| `TextToSpeech` build no longer stalls the board | **VERIFIED** — fixed the `AppFlowTest` flakiness (§7) |
| Narration speaks no forbidden info (INV-8) | **VERIFIED by construction** — child model has no stage/level; only titles are spoken |
| **Audible Persian output with AvaCore on a real device** | **NOT VERIFIED** — AvaCore is not installed on the emulator; needs the owner to install v0.6.3 + AvaCore and check Settings → صدای فارسی shows "متصل (…)" and «امتحان صدا» plays |
| Live TalkBack swipe-traversal / focus order | **NOT VERIFIED** — earlier physical-device pass verified the semantics layer (`AccessibilityNodeInfo`), not a live screen-reader traversal |
| On-device font-scale 1.5×/2× | **VERIFIED on emulator** (v0.6.0/0.6.2 — quest cards size to content, no clipping); the test phone denies `settings put` so on-device font-scale toggle is BLOCKED |

### Known limitations

- No in-app engine picker — relies on the OS default TTS engine.
- If AvaCore uses a package name other than `com.github.opscalehub.avacore`
  (e.g. after a PlayFoundryHQ republish), the explicit `<package>` line needs
  updating (the generic `<intent>` query would still find it).

---

## 7. Test & verification matrix

### Backend (`./scripts/test.sh backend` — re-run this session at HEAD)

| Suite | Command | Result |
|---|---|---|
| stdlib (bare interpreter, domain) | `python3 -m pytest -q` | **61 passed, 8 skipped** (skips = suites needing `fastapi`/`httpx`) |
| full stack (venv) | `.venv/bin/python -m pytest -q` | **117 passed, 1 skipped** (skip = `test_f_persistence.py` Postgres, needs `QUESTGROW_TEST_POSTGRES_URL`) |

118 tests collected (`test_invariants.py` +1, 2026-08-31). By file: `test_invariants.py` 19 · `test_d1_acceptance.py`
17 · `test_acceptance.py` 17 · `test_c1_persistence.py` 15 · `test_f_hardening.py`
9 · `test_auth.py` 9 · `test_api.py` 9 · `test_webclient.py` 8 ·
`test_notifications.py` 7 · `test_f_persistence.py` 4 · `test_rewards_inbox.py` 3 ·
`test_integration_mvp.py` 1.

### Android

| Suite | Command | Result |
|---|---|---|
| unit (JVM) | `./gradlew :app:testDebugUnitTest` | **24 passed** (5 files: `ApiContractTest`, `ComplexityProfileTest`, `DtoSerializationTest`, `OfflineAndSyncTest`, `OfflineCacheTest`) |
| lint (release-gating) | `./gradlew :app:lintVitalRelease` | **pass** (`lint-results-debug`: 0 errors, 75 warnings — non-blocking: unused resources, etc.) |
| instrumented (needs emulator/device) | `./gradlew :app:connectedDebugAndroidTest` | **12 passed** — `AppFlowTest` ×9 + `TokenStoreTest` ×3. Run green **twice in a row** this session on the HEAD tree after the v0.6.3 container-leak fix (previously flaky: `kidBoard_rewards_redeem_asksGrownup` — root cause was leaked `ConnectivityManager` callbacks hammering MockWebServer; fixed by `AppContainer.close()`). |

### Verification level per major capability (this session)

| Capability | Unit | Instrumented | Emulator (hands-on) | Physical device | Live backend | Production deploy |
|---|---|---|---|---|---|---|
| First-run + sign-in recovery + pairing | — | VERIFIED | VERIFIED | NOT VERIFIED (this session) | VERIFIED (local + live) | n/a |
| Multi-child switcher (family device) | VERIFIED (`TokenStoreTest`) | VERIFIED | VERIFIED | NOT VERIFIED | VERIFIED (local) | n/a |
| Kid board / do-it / celebration | — | VERIFIED | VERIFIED | VERIFIED (earlier, dark mode) | VERIFIED | n/a |
| Kid rewards + parent redemption inbox | — | VERIFIED | VERIFIED | NOT VERIFIED | VERIFIED (local) | n/a |
| Parent gate + approvals | — | VERIFIED | VERIFIED | VERIFIED (earlier) | VERIFIED | n/a |
| Offline cache / queue | VERIFIED | VERIFIED | NOT VERIFIED | NOT VERIFIED (real airplane-mode) | n/a | n/a |
| Backend auth / scope / ledger / rewards | VERIFIED | (contract test) | n/a | n/a | VERIFIED (`/openapi.json`, `/v1` 401, cross-child 403) | VERIFIED (Synced/Healthy) |
| Backend persistence / restart-safety | VERIFIED | n/a | n/a | n/a | VERIFIED (pod restart after DB wipe, data schema rebuilt) | VERIFIED |
| TTS audible output | — | — | Settings row VERIFIED (reports "no voice") | **NOT VERIFIED** (needs AvaCore) | n/a | n/a |
| Deployment on `questgrow.opscale.ir` | — | — | — | — | VERIFIED (`/health` `api:0.6.1`, DB freshly wiped/empty) | **DEPLOYED** (image 0.6.1) |

**BLOCKED:** on-device font-scale toggle + live TalkBack (test phone denies
`WRITE_SECURE_SETTINGS` / `pm clear`). **FAILED:** nothing currently failing.

---

## 8. Security / privacy posture

| Area | Finding | Class |
|---|---|---|
| Password / PIN storage (backend) | PBKDF2 hashes in `auth_account` (`pw_hash`, `pin_hash`). | **VERIFIED** |
| Token storage (Android) | DataStore-Preferences; `allowBackup=false`; `data_extraction_rules.xml` excludes `file`/`database`/`sharedpref` from cloud-backup **and** device-transfer. | **VERIFIED** |
| Account isolation (backend) | `_parent_owns_child` + `ChildScope`-on-own-id + the §5 matrix at HTTP edge and service. Cross-account/cross-child rejected. | **VERIFIED** (`test_invariants.py`, live check) |
| TLS (Android release) | `network_security_config.xml`: `cleartextTrafficPermitted="false"` base; cleartext allowed only for `10.0.2.2`/`localhost`/`127.0.0.1` (dev backends). The **debug** build has a permissive override (`src/debug/res/xml/`). | **VERIFIED** — release APK is HTTPS-only for real hosts. Debug build is not distributed. |
| TLS (backend) | ArvanCloud edge terminates; Traefik→origin hop uses Traefik's default cert (not a managed cert). Within a single-node cluster on a trusted network this is **LOW RISK / ACCEPTED**; a public multi-tenant deployment would want end-to-end TLS. | **LOW RISK / ACCEPTED** (documented in `values-nuc-lab.yaml`) |
| Auth abuse protection | login+unlock rate-limited: 5 failures / 900s → 900s lockout (`test_f_hardening.py`). | **VERIFIED** |
| Parent-token TTL | 43200s (12h) deployed vs 900s default — a deliberate personal-project trade-off (a longer window a stolen unlocked device could act in). | **PRODUCT OWNER DECISION (already made)** — auth-policy note; documented here + in `DEPLOYMENT.md` |
| Refresh tokens | none — re-login on expiry. | **PRODUCT OWNER DECISION (already made)** — auth-policy |
| CORS | off (`QUESTGROW_CORS_ORIGINS=""`); web clients are same-origin. | **VERIFIED** |
| Secrets in the cluster | none (public packages, SQLite, no imagePullSecret). ArgoCD `Repository` secret has no credential. | **VERIFIED** |
| Child PII | first name + coarse age band + optional coarse `birthdate`. No photos, no last name, no contact info. Auth PII = the parent's email. | **VERIFIED / LOW RISK** |
| Logging | backend: uvicorn access log (paths, status — no bodies, no tokens). Android: `QG.Narrator` logs the engine name + `setLanguage` result (no PII). Passing the raw child token in a URL for debugging was a one-off the owner did while debugging — not a code path. | **LOW RISK** |
| Local caches / offline queue | plaintext JSON in app-private storage (`/data/data/<pkg>/files/`). App-private; excluded from backup/transfer. **Not per-identity scoped** (CRACK-1). | **TECHNICAL DEBT** (the scoping) / LOW RISK (the plaintext — app-private, backup-excluded) |
| Debug vs release | debug: `.debug` appId, dev backend default, `isMinifyEnabled=false`, permissive network config. release: R8 + resource shrink, live backend default, HTTPS-enforced, signed. Only the release APK is distributed. | **VERIFIED** |
| Signing keystore | `CN=QuestGrow,O=PlayFoundryHQ,C=IR`, on the build machine at `/home/iceman/questgrow-release.jks` with creds in `/home/iceman/questgrow-keystore.creds`. **Not in any repo.** `android/keystore.properties` (gitignored) points at it; absent → the build falls back to the debug key. | **PRODUCT OWNER DECISION REQUIRED** — this is a single point of loss; the owner must back it up (losing it means no upgrade path for installed APKs). |

No **POSSIBLE SECURITY ISSUE** class findings. The offline-queue mis-attribution
(CRACK-1) is a **correctness** defect, not a privacy breach (it can lose or
mis-file a completion between two of the same parent's children; it cannot
cross accounts).

---

## 9. Documentation reconciliation matrix

| Area | Product truth / intent (docs) | Current implementation | Status |
|---|---|---|---|
| Ownership per (child × quest), 4 stages, child never sees them | DECISION-001/002/003/004; INV-1/2/3/8 | `ChildQuest.ownership_stage`; child payloads stage-free (OpenAPI-asserted) | **MATCH** |
| Verification derived from stage by a pure function, never a stored flag | DECISION-007; INV-4 | `service` computes it; no `verification_required` column | **MATCH** |
| Two child-visible reward modes (calm wait / instant celebrate) | DECISION-005/006; INV-10/15 | Mode A pending on approval, Mode B celebrate immediately | **MATCH** |
| No streaks; progressive consistency; expired occurrence is neutral | DECISION-013/014/018; INV-16 | `consecutive_ok_count` used only by the suggestion evaluator; absent from all response schemas | **MATCH** |
| Lifetime Achievement monotonic ⟂ Spendable Balance | DECISION-015; INV-13 | `redeem`/`adjustment` never reduce `Σ earn` (property test) | **MATCH** |
| Ledger append-only, idempotent, server-written | DECISION-016; INV-11/12 | INSERT-only; `ON CONFLICT DO NOTHING` on `idempotency_key` | **MATCH** |
| Parent authority invariant across stages | DECISION-016; INV-17 | authz matrix test parameterised over all 4 stages | **MATCH** |
| Ownership stage + progress are durable, parent-controlled state | DECISION-017; INV-2 | `assign_quest` idempotent (2026-08-31) — re-assign no longer resets stage/count | **MATCH** (was a latent contradiction — CRACK-6 — now fixed) |
| `PARENT_MANAGED` domain-valid but not MVP-assignable | DECISION-019 | enum has it; MVP assignment UI omits it | **MATCH** (deferred, documented) |
| Persian-only, RTL client; web clients retired as a product surface | DECISION-020 | Android is Persian/RTL; web clients still served but English, QA-only | **MATCH** |
| Family device holds multiple children; kid spends points in-app | DECISION-021 | multi-child `TokenStore`; kid rewards screen; parent redemption inbox | **MATCH** (implementation went further — v0.6.2 auto-sync, no "activate" step) |
| Auth: static email/password + PIN, no OIDC, no refresh, no payment | auth-policy note; `E_READINESS` addendum; `ARCHITECTURE.md` | exactly as built | **MATCH** |
| **Android README "Shape (Phase L)" section** | describes the L-era parent home ("each child's day in a line + approvals inbox") and the L-era gate ("'بزرگترها ›' text button") | v0.6 redesigned the parent home into a card hub; the gate is a pill in the board header; multi-child + sign-in recovery + TTS status added | **OBSOLETE DOCUMENTATION** → fixed in this pass (§12) |
| **Android README offline section** | "A child device holds one child token, so this is single-slot" | family device is multi-child since v0.5.0; the offline layer is still single-slot | **IMPLEMENTATION DIFFERED FROM DESIGN** → CRACK-1; README note added |
| **`migrations/0002_auth_and_events.sql` comment** | "kind ∈ {session, parent, child}" | a fourth kind, `pair`, was added in Phase M | **OBSOLETE DOCUMENTATION** (code comment) → fixed in this pass |
| **`docs/README.md` document map** | "DECISION-001 … DECISION-019" (×2); "Phase G (native Android client)" | 21 decisions; L/M/v0.6.x all shipped | **STALE DOCUMENTATION** → fixed in this pass |
| `E_READINESS.md` addendum | "Remaining to close out the MVP … deployment … release tooling … signed APK … light security/ops … hands-on a11y" | deployment + release tooling + signed APK **done**; Phases L/M/v0.6 shipped on top; a11y/airplane-mode still hands-on-pending | **PARTIALLY IMPLEMENTED** (doc predates K/L/M) — superseded by this file; not rewritten (historical) |
| `docs/product-delivery/ROADMAP.md` | Layers 1–5 (real-time celebration, milestone keepsakes, deeper adaptation, meta-game, multi-caregiver, photo evidence, web dashboard, localization) | all **DEFERRED**; "multi-child polish" (a roadmap item) partly delivered via Phase M / v0.6.2 | **PARTIALLY IMPLEMENTED** (multi-child) / rest **DEFERRED** |
| `IMPLEMENTATION_NOTES.md` | code → contract map through Phase F | accurate for the backend; predates the Android client and the `/v1` API additions (list endpoints, rewards inbox, pairing) | **DOCUMENTED BUT NOT UPDATED** — accurate as far as it goes; not extended (backend contract unchanged) |
| `docs/product-delivery/DEPLOYMENT.md` | env table shows `QUESTGROW_PARENT_TTL_S` default 900 = re-challenge cadence | correct as a *default*; the live deploy overrides to 43200 | **MATCH** (default is right; override is in `values-nuc-lab.yaml` + noted here) |

No **DOCUMENTED BUT NOT IMPLEMENTED** findings against a decision or invariant.

---

## 10. Cracks / gaps

### CRACK-1 — the offline layer is not multi-child-aware — **POSSIBLE DEFECT**

`ReadCache` (`today.json` / `progress.json`) and `OfflineQueue`
(`PendingCompletion` has `questId`, `day`, `note`, `enqueuedAt` — **no
`childId`**) are single-slot, from the Phase G–J design when a device held one
child. Since **v0.5.0** the family device holds many children and switches
between them.

- **Offline kid-switch:** switching the active child while offline shows the
  *previous* child's cached board, flagged "stale" — but the payload
  (`child_id`, quest titles, points) is the other child's. Misleading.
- **Offline completion then switch:** a "I did it" queued for child A, then the
  active child switched to B, then reconnect → `flushQueue()` replays
  `POST /v1/me/quests/{questId}/complete` with **B's** token. If B has that
  quest scheduled today → the completion is recorded for **B** (wrong child).
  If not → 404 → the item is silently dropped (`flushQueue` drops 4xx that
  isn't auth-expired) → **A's completion is lost**.
- Online (the normal state of a family phone) is unaffected.
- Cannot cross accounts — both children belong to the same parent. Not a
  privacy issue; a data-correctness issue.

**Not fixed in this reconciliation phase** (scope is documentation). A fix
would key the cache and the queue by `child_id`, and clear a child's slot when
its token is dropped. → §11.

### CRACK-2 — live deployment version label lags the code — **LOW / cosmetic**

Live backend image is `0.6.1`; code/chart is `0.6.3`. The three releases in
between are client-only, so behaviour is identical — but `/health` and
`/openapi.json` report `0.6.1`, and ops PRs #75/#76 are open. An independent
reviewer checking "does the live version match HEAD" would see a mismatch that
is benign. Resolved by merging #76.

### CRACK-3 — the ArgoCD Application + one cluster Secret live outside Git — **DOCUMENTED, ACCEPTED**

`gitops/apps/questgrow.yaml` is in `OpScaleLab/nuc-lab-operation`, not this
repo; the `ghcr-playfoundryhq-charts` ArgoCD `Repository` secret was created by
hand. Both are recorded in `deploy/README.md` and §5 here. Standard for a
GitOps-with-separate-ops-repo setup; noted so a reviewer doesn't expect the
full deploy to be reproducible from this repo alone.

### CRACK-4 — the Android signing keystore is a single point of loss — **PRODUCT OWNER DECISION REQUIRED**

`/home/iceman/questgrow-release.jks` + creds exist only on the build machine.
Losing them means no signed upgrade for any installed APK (a fresh key = a new
app identity / reinstall). The owner must back this up. Recorded in
`android/RELEASE.md` and the project memory; restated here.

### CRACK-6 — `assign_quest` reset ownership progress on re-assign — **FIXED 2026-08-31**

`QuestGrowService.assign_quest` unconditionally wrote a fresh
`ChildQuest(ownership_stage=default_for_age, consecutive_ok_count=0)`, so a
second `assign` for an already-assigned `(child, quest)` silently discarded the
child's ownership stage and progress — contradicting INV-2 / DECISION-017
(durable, parent-controlled ownership state). The parent Routines screen offers
the "add" action for every starter regardless of per-child assignment state, so
a stray double-tap could trigger it.

**Fix:** `assign_quest` now returns the existing `ChildQuest` unchanged when one
exists (idempotent). Test:
`test_invariants.py::test_assign_quest_is_idempotent_preserves_stage_and_progress`.
If an explicit "reset this routine" affordance is ever wanted it can be added as
a separate, named action — that would be a product decision; the silent reset
was a bug.

### CRACK-5 — stale code/doc comments (fixed in this pass)

`android/README.md` "Shape (Phase L)" section, its offline description, and the
`migrations/0002` header comment described pre-v0.5/v0.6 behaviour; `docs/README.md`
said "DECISION-001…019" and "Phase G". Corrected in the same commit as this file.

### Not gaps (checked, clean)

- INV-8: re-scanned — no child-facing payload or Android child model carries a
  stage/level/readiness/KPI field; TTS speaks only titles. **CLEAN.**
- API contract vs client: the Android `Dtos.kt` and `QuestGrowApi.kt` are 1:1
  with `api.py`; every client call hits a `/v1` route that exists in the live
  OpenAPI. **CLEAN.**
- Ledger idempotency across app ↔ backend: the client has no idempotency key;
  the server keys on `(quest@version, child, date)` — one completion per quest
  per day, replay-safe. Matches. **CLEAN.**
- Backend account isolation: **CLEAN** (verified live).
- No undocumented backend endpoint: the live OpenAPI == the routes in `api.py`.
  **CLEAN.**

---

## 11. Open product-owner decisions

1. **CRACK-1 (offline multi-child scoping).** Fixing it is engineering, not a
   product decision — but *whether it's worth fixing now* is the owner's call
   (it only bites offline on a shared phone, a narrow window). If yes, it is a
   small, well-scoped change (key cache + queue by `child_id`).
2. **Merge ops PRs #75 / #76** to align the live version label with the code
   (no behaviour change).
3. **Back up the Android signing keystore** (CRACK-4). Not optional if the
   owner ever wants to ship an upgrade to an installed APK.
4. **Parent-token TTL of 12h** — already the owner's decision; flagged here so
   it is on the record as a deliberate trade-off, not a default left unset.
5. **AvaCore package name after the PlayFoundryHQ migration** — if it changes,
   the explicit `<package>` in the manifest needs a one-line update (tell the
   dev agent; the generic query still works either way).

Everything else the grant lists (identity model, reward/ownership semantics,
monetization, iOS, push, analytics, backend architecture, self-hosted mode) is
**not open** — it is settled by an existing decision or is deliberately out of
scope, and nothing in this reconciliation changes it.

---

## 12. Deferred / out of scope

**Deferred (named, not built):**

- Offline multi-child cache/queue scoping (CRACK-1).
- Hands-on accessibility verification: live TalkBack swipe-traversal, real
  airplane-mode cycle on a physical device, on-device font-scale toggle (test
  phone denies `WRITE_SECURE_SETTINGS`).
- Audible AvaCore verification on a real device.
- Postgres in production (code-ready; SQLite is deployed and proportionate).
- API path versioning is done; delta/ETag polling, per-occurrence completion
  timestamps, PIN-change flow — post-MVP (`E_READINESS` Class D).
- `PARENT_MANAGED` assignment UI (DECISION-019).
- ROADMAP Layers 1–5: real-time / co-present celebration, milestone keepsakes,
  deeper age adaptation, meta-game, multi-caregiver / verifier roles, photo
  evidence, web parent dashboard, additional languages.

**Out of scope (this reconciliation did not do, per the grant):**

- Any product redesign, new feature, or semantic change.
- Refactor for cleanliness (single `:app` module kept; manual DI kept).
- iOS, Play Store `.aab`, analytics, push, monetization, subscriptions.
- Self-hosted / backend-in-the-app mode (rejected — would break
  server-authoritative invariants).

---

## 13. Release / deployment status

| Thing | State |
|---|---|
| Latest release | `v0.6.3` — GitHub Release with signed `app-release.apk`, image `ghcr.io/playfoundryhq/questgrow:0.6.3`, chart `ghcr.io/playfoundryhq/charts/questgrow:0.6.3` — **SHIPPED** |
| All releases | `v0.3.1`–`v0.6.3` (11), all with image + chart; APK attached from `v0.3.4` on |
| Android APK | signed with the real upload key when `keystore.properties`/`QG_KEYSTORE_*` present, else debug key. `versionCode` = `maj*10000 + min*100 + pat` (monotonic per semver). Distribution: sideload from `github.com/PlayFoundryHQ/QuestGrow/releases/latest`. **No Play Store.** |
| Android ↔ backend compatibility | v0.4.0–v0.6.3 clients all speak the same `/v1` contract. v0.5.0+ needs `GET /v1/me/rewards` + `GET /v1/redemptions` (present since backend 0.5.0; live backend is 0.6.1 ⟹ present). A v0.5.0+ client against a ≤0.4.x backend would get 404s on the rewards screen (graceful "Not Found" state). |
| Backend deployment | **DEPLOYED**: image `0.6.1` on `questgrow.opscale.ir`, ArgoCD `Synced / Healthy`. |
| Backend ↔ chart | live tracks chart `0.6.1`; repo chart is `0.6.3`; ops PRs #75/#76 (bump to 0.6.2 / 0.6.3) **open, not merged**. |
| Migrations | `0001_domain`, `0002_auth_and_events` — applied on every startup; `schema_migrations` table tracks. No pending migration. |
| Database | **freshly wiped** 2026-08-30 at the owner's request — empty schema, zero accounts. |
| Rollout / upgrade risk | client-only releases (0.6.1–0.6.3): none. A future backend release with a migration: additive-only so far; a down-migration path is not implemented (rollback = revert `targetRevision`, keep the newer schema). |

**SHIPPED:** everything through `v0.6.3` (code, image, chart, APK, GitHub Release).
**DEPLOYED:** backend `0.6.1` (functionally == `0.6.3`).
**READY, not deployed:** chart/image `0.6.2`, `0.6.3` (published; ops PRs open).
**CODE EXISTS, not shipped:** nothing — HEAD is a release commit.
**PLANNED:** §12.

---

## 14. Product decisions (index)

Authoritative record: [`docs/governance/DECISION_LOG.md`](governance/DECISION_LOG.md).
**DECISION-001 … DECISION-021**, all status **Accepted**, none reversed or
superseded. Technical invariants **INV-1 … INV-18** in
[`docs/architecture/TECHNICAL_MODEL.md`](architecture/TECHNICAL_MODEL.md) §
"Invariants", each with implementation evidence and (where automatable) a test.
§5 (Decision & Invariant audit) of the reconciliation confirmed all 21
decisions and all 18 invariants hold against the current code — see §9.

Auth policy (static email/password + PIN, no OIDC, no refresh, no payment) is
recorded in `E_READINESS.md` (addendum), `docs/product-delivery/ARCHITECTURE.md`,
`android/README.md`, and the project memory — it resolved a long-open question
(`OQ` / the "parent-gate re-challenge cadence" flagged repeatedly in Phases
E–J) **without** a `DECISION_LOG` entry, by the owner, as an operational
simplification proportionate to a personal project.

---

## 15. Known limitations

- One account per Android device (many children per account).
- Offline layer is not multi-child-scoped (CRACK-1).
- No push / real-time; poll only.
- Backend on SQLite, single replica, `Recreate` rollout — single-family scale.
- No end-to-end TLS to the backend origin (ArvanCloud edge terminates).
- No CI; releases are a local operator action.
- Audible TTS and live screen-reader traversal not verified on a real device.
- Signing keystore lives only on the build machine.
- `docs/` product/UX/architecture documents describe the model as of Phase F
  and are **not** rewritten for each client iteration — this file (`PROJECT_STATE.md`)
  is the current-state layer on top of them.

---

## 16. Architecture summary (one screen)

```
                      ┌─────────────────────────────────────────────┐
   Android (Kotlin/   │  MainActivity → AppRoot state machine        │
   Compose, Persian/  │   Onboarding / Kid / Gate / Parent           │
   RTL, one :app)     │  ViewModel + StateFlow · Retrofit/OkHttp     │
                      │  DataStore(tokens) · file queue · read cache │
                      └───────────────┬─────────────────────────────┘
                                      │  HTTPS  /v1/*   (Bearer: parent|child token)
                                      ▼
        ArvanCloud edge (TLS term, *.opscale.ir)  ──►  nuc-lab Traefik (SNI)
                                      │
                                      ▼
                      ┌─────────────────────────────────────────────┐
   Backend (FastAPI,  │  api.py  — scope resolution, §5 actor matrix │
   Python, one pod,   │  auth.py — session→parent→child, no refresh  │
   Recreate, non-root)│  service.QuestGrowService — the authority    │
                      │  sql_repository → SQLite  /data/questgrow.db  │
                      │  migrations on startup · events (poll feeds)  │
                      └─────────────────────────────────────────────┘
                                      │
                              PVC (local-path, 1Gi, keep)

   GitOps:  release.sh → GHCR (image + OCI chart) + git tag + GH Release
            → PR to OpScaleLab/nuc-lab-operation (targetRevision) → ArgoCD sync
```

---

## 17. Reproduction / build instructions

### Backend

```bash
# tests
./scripts/test.sh backend                       # 60/8 stdlib + 116/1 venv

# run locally
PYTHONPATH=src QUESTGROW_DATABASE_URL=sqlite:///tmp/qg.db \
  .venv/bin/python -m uvicorn questgrow.asgi:app --port 8000
# → http://localhost:8000/health · /openapi.json · /app/child · /app/parent

# container
docker build -t questgrow:local .
docker run --rm -p 8000:8000 questgrow:local
```

### Android

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest                # 24
./gradlew :app:lintVitalRelease
./gradlew :app:connectedDebugAndroidTest        # 12 (needs an emulator/device)
./gradlew :app:assembleDebug                    # -> app/build/outputs/apk/debug/
# release APK (real key needs android/keystore.properties or QG_KEYSTORE_* env):
QG_VERSION_NAME=0.6.3 QG_VERSION_CODE=603 ./gradlew :app:assembleRelease
```

### Release (operator)

```bash
./scripts/release.sh 0.6.4 --with-apk           # tests → bump → image+chart → tag → GH Release
# then: PR to OpScaleLab/nuc-lab-operation bumping gitops/apps/questgrow.yaml targetRevision
```

### Deploy prerequisites (outside this repo — see §5)

ArgoCD `Application` in the ops repo; `ghcr-playfoundryhq-charts` `Repository`
secret in `argocd` namespace (no credential); public GHCR packages; DNS +
ArvanCloud edge.

---

## 18. Files changed by this reconciliation

The **reconciliation** commits (`80449ec`, `1cbea77`) were documentation-only:
`docs/PROJECT_STATE.md` (new, this file), plus corrections to
`android/README.md`, `docs/README.md`, and one code comment
(`src/questgrow/migrations/0002_auth_and_events.sql`).

The **2026-08-31 UX/terminology audit** additionally changed:
- `android/app/src/main/res/values/strings.xml` — routines block reworked:
  removed `routine_add`, `routine_title`, `routine_icon`, `routine_points`,
  `routine_recurrence`, `routine_assign_title`, `routine_create`,
  `routine_assign` (six were dead — a custom-routine form that was never built —
  and the two action labels are replaced); added `routine_pick_child`,
  `routine_starters_sub`, `routine_add_to_child`, `routine_add_plain`,
  `routines_family`, `routines_family_sub`; `routines_empty` reworded.
- `android/app/src/main/java/hq/playfoundry/questgrow/ui/parent/ParentFlow.kt` —
  `Routines()` gains an on-screen child switcher (shown when ≥2 children), a
  target-child hint, one `«افزودن به {نام}»` action for both the create+add and
  add-only paths, and the second section header renamed
  `nav_routines` → `routines_family` with a subtitle.

No domain / API / data-model change in that commit. Android unit 24 + `lintVitalRelease` pass.

The **2026-08-31 post-audit reconciliation** additionally changed:
- `src/questgrow/service.py` — `assign_quest` returns the existing `ChildQuest`
  when one exists (idempotent; CRACK-6 fix). ~4 lines, no signature/API change.
- `tests/test_invariants.py` — `test_assign_quest_is_idempotent_preserves_stage_and_progress`.
- `docs/PROJECT_STATE.md` — this note, §2, §9, §10, §11.

Backend suites: **61 passed / 8 skipped** stdlib, **117 passed / 1 skipped** venv
(both +1). Instrumented Android not run (no emulator).

---

## 19. How to independently verify this state

```bash
# 1. baseline
git fetch origin && git rev-parse HEAD && git status --porcelain
git rev-list --left-right --count origin/main...HEAD          # 0  0
gh pr list --state open ; gh issue list --state open          # empty
gh release list

# 2. backend truth
./scripts/test.sh backend                                     # 60/8 + 116/1
grep version pyproject.toml src/questgrow/api.py              # 0.6.3

# 3. live deployment truth
curl -s https://questgrow.opscale.ir/health                   # {"status":"ok","api":"0.6.1"}
curl -s https://questgrow.opscale.ir/openapi.json | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["paths"]))'   # 78 (39 logical × {/, /v1})
kubectl -n questgrow get deploy questgrow -o jsonpath='{.spec.template.spec.containers[0].image}'   # :0.6.1
kubectl -n argocd get application questgrow -o jsonpath='{.status.sync.status}/{.status.health.status}'   # Synced/Healthy
cat /tmp/ops/gitops/apps/questgrow.yaml | grep targetRevision  # 0.6.1 (ops repo); PRs #75/#76 open

# 4. android truth
cd android && ./gradlew :app:testDebugUnitTest :app:lintVitalRelease   # pass
./gradlew :app:connectedDebugAndroidTest                      # 12 (emulator)
grep -E "minSdk|targetSdk" app/build.gradle.kts               # 26 / 35

# 5. invariants (INV-8 spot check)
curl -s https://questgrow.opscale.ir/openapi.json | grep -i "ownership_stage\|readiness\|streak"   # nothing in a child schema
grep -rn "ownership_stage\|readiness\|streak" android/app/src/main/java/hq/playfoundry/questgrow/data/model/   # nothing

# 6. the two open cracks
#    CRACK-1: android/app/.../data/local/ReadCache.kt + OfflineQueue.kt  (single-slot, no childId)
#    CRACK-2: image 0.6.1 vs code 0.6.3  (client-only releases in between)
```

The repository at `HEAD` (`80449ec`, docs-only on top of the `v0.6.3` release
`2f15632`) **is** the authoritative source of truth for the current project
state. This file is its index.

---

## 20. Historical traceability

This file does not erase or rewrite prior phase reports. The history stands:

| Phase / milestone | Where it lives | Now |
|---|---|---|
| Foundation docs, DECISION-001…019, domain, C0–C6, D1 acceptance | `docs/` (`D1_ACCEPTANCE.md`, `TECHNICAL_MODEL.md`, `DECISION_LOG.md`, `IMPLEMENTATION_NOTES.md`) | current for the backend contract |
| Phase E (readiness) | `docs/product-delivery/E_READINESS.md` | historical; its "remaining to close out" list is largely done — superseded by this file |
| Phase F (production foundation) | `E_READINESS.md` addendum, `DEPLOYMENT.md`, `migrations/` | current |
| Phases G–J (native Android client) | `android/README.md`, `android/RELEASE.md` | current, but the README's "Shape (Phase L)" section is corrected in this pass |
| Phase K (deploy + release tooling + APK) | `deploy/`, `scripts/`, project memory | current; live at `questgrow.opscale.ir` |
| Phase L (Persian/RTL kid-first rewrite) — `6d476d8`…`1ed5d2f`, `v0.4.0` | commits, `DECISION-020`, `android/README.md` | current, then superseded by v0.6 design pass |
| `v0.4.1` (`25db88d`) — visible parent gate | commit | current |
| Phase M (`718dc62`, `v0.5.0`) — multi-child family device + in-app rewards | commit, `DECISION-021`, `test_rewards_inbox.py` | current — the origin of CRACK-1 |
| `v0.6.0` (`5fcfde0`+`65d699a`+`bbc85d5`) — full UI/UX design pass | commits, `android/README.md` design-system note | current |
| `v0.6.1` (`a2f36be`) — sign-in recovery | commit | current |
| `v0.6.2` (`f80e7ee`) — shared-phone auto-sync of all children | commit | current |
| `v0.6.3` (`23338ea`) — TTS `<queries>` + container-leak fix | commit | current |
| Reconciliation (`80449ec`, `1cbea77`) — `docs/PROJECT_STATE.md` | this file | the current-state index |
| 2026-08-31 UX/terminology audit — parent Routines screen clarity | this file §10/§11/§18, `strings.xml`, `ParentFlow.kt` | current |
| 2026-08-31 post-audit reconciliation — `assign_quest` idempotency (CRACK-6 fix) | this file §2/§9/§10/§18, `service.py`, `test_invariants.py` | current |

Current truth = this file + the code at `HEAD`. Historical truth = the phase
reports, unchanged.

# QuestGrow MVP — Implementation Notes

> The implementation in `src/questgrow/` is written **strictly against**
> [`TECHNICAL_MODEL.md`](./TECHNICAL_MODEL.md) (the contract), which is bound
> to [`DECISION_LOG.md`](../governance/DECISION_LOG.md) (DECISION-001…019) and
> [`OWNERSHIP_MODEL.md`](../experience/OWNERSHIP_MODEL.md). This file maps the
> code to the contract and records implementation-level choices that could not
> be read verbatim from a single document. It creates **no** product
> semantics and resolves **no** open question (OQ-A long-term, OQ-B…OQ-H).

## Stack

Python 3.11+, `pytest`. The domain library (`enums`/`entities`/`ownership`/
`scheduling`/`projections`/`service`) is dependency-free; C1 adds two seams on
top — `adaptation.py` (the §13 `complexityProfile` resolver) and
`sqlite_repository.py` (a `sqlite3`-backed `Repository`, Postgres-portable
schema). `repository.Repository` is a `Protocol`; `InMemoryRepository` and
`SqliteRepository` are drop-in and the full AC/INV suite runs against both.
C2 adds `api.py` — a FastAPI transport over `QuestGrowService` with bearer
token → scope resolution (`TokenStore`), the §5 actor matrix mirrored at the
HTTP boundary (403 before the service is reached), and child response models
that structurally carry no stage/level field (INV-8, asserted against the
generated OpenAPI). C3 adds `auth.py` — `AuthService` with email + PBKDF2
password, a PBKDF2 PIN parent-gate, and a session→gate→parent-scope token
flow (session tokens are deliberately **not** a scope); it plugs into
`create_app(auth=…)` as the token resolver and mounts `/auth/*`. C4 adds
`notifications.py` (informational parent templates + a banned-phrase list) and
a second lane on `EventSink`: the child celebration lane fires on every
`completion.verified` (Mode-A on approval, Mode-B immediately); the parent
notification lane fires only when `Account.notifications_enabled` is set
(opt-in). Both are **poll**-based — `GET /me/celebrations?since=` (child) and
`GET /children/{id}/notifications?since=` (parent); no push in MVP. C5/C6 add
two thin static single-file clients under `webclient/` — `child.html`
(Today / Do-it / Celebration / Progress; consumes `complexityProfile`; offline
queue in `localStorage`, drops an item on a 409 per INV-11) and `parent.html`
(PIN gate → dashboard / approvals + batch / quests + starter templates /
rewards / ownership + suggestions / progress). Served at `/app/child` and
`/app/parent`. Full end-to-end acceptance is D1's; `tests/test_webclient.py`
covers transport wiring + the copy guarantees a source scan can verify.
**Not yet** implemented: production mobile client, any long-term meta-game. `PARENT_MANAGED` **is** implemented and
tested (it "remains a valid part of the contract" — DECISION-019) but nothing
assigns it by default in MVP.

## Module → contract map

| Module | TECHNICAL_MODEL | Invariants | Acceptance criteria |
|---|---|---|---|
| `enums.py` (`OwnershipStage`, `InstanceState`, `VerificationBehaviour`, `LedgerKind`, `Recurrence`, `Actor`, `verification_behaviour()`) | §2, §3 states, §4 derivation | INV-3, INV-4 | — |
| `entities.py` (12 dataclasses) | §2 domain concepts | INV-1, INV-2, INV-4 (absent fields) | — |
| `scope.py` + `service.py` guards (`_require_parent` / `_require_child` / `_parent_owns_child`) | §5 actor matrix | INV-5, INV-12, INV-17, INV-18 | AC-5 |
| `ownership.py` (`plan_transition`, counter functions, `default_stage_for_age_band`, `should_suggest_advancement`) | §3 transitions, §4 counter, DECISION-017/018/019, TOQ-2/9 | INV-3, INV-6, INV-7, INV-16 | AC-3, AC-4, AC-13, AC-14, AC-15 |
| `scheduling.py` (`due_on`) | §4 QuestInstance identity; eager materialisation (TOQ-7) | INV-2 | — |
| `service.py` completion flow (`submit_completion`, `record_completion`, `approve`, `not_yet`, `_transition_to_verified`) | §4 QuestInstance state machine + "On verified" | INV-10, INV-11, INV-15 | AC-1, AC-2, AC-10, AC-11, AC-12 |
| `service.py` ledger (`_award_earn`, `apply_adjustment`) + `repository.append_ledger` | §6, §7; TOQ-3, TOQ-5 | INV-11, INV-12, INV-13, INV-14 | AC-2, AC-6, AC-7, AC-12 |
| `service.py` rewards (`redeem_reward`, `grant_redemption`, `decline_redemption`) | §6 redemption modes | INV-13, INV-18 | AC-6 |
| `projections.py` (`lifetime_achievement`, `spendable_balance`, `TodayPayload`, `WeeklyConsistency`, `DailyProgress`) | §7 | INV-8, INV-9, INV-13, INV-16 | AC-8, AC-9 |
| `adaptation.py` (`ComplexityProfile`, `resolve_complexity_profile`) | §13 | INV-8 (no stage/level field — structural) | — |
| `repository.py` (`Repository` protocol) + `sqlite_repository.py` (`SqliteRepository`, `SCHEMA`) | §10 / TOQ-7; append-only ledger | INV-1, INV-11, INV-12 | AC-2, AC-12 |
| `api.py` (`create_app`, `TokenStore`, wire models) | §5 actor matrix at the HTTP boundary; §13 payload | INV-5, INV-8, INV-17, INV-18 | AC-1, AC-2, AC-5, AC-8, AC-9, AC-11, AC-13 |
| `auth.py` (`AuthService`: signup / login / `unlock_parent` / `issue_child_token` / `resolve`) | §5 (parent gate); ARCHITECTURE "Auth & authorization" | INV-17, INV-18 | AC-5 |
| `events.py` (`EventSink`, `CelebrationEvent`, `ParentNotification`) + `notifications.py` (templates, `BANNED_SUBSTRINGS`) | §4 `completion.verified`; ARCHITECTURE notification service (opt-in, informational, never child-addressed) | INV-8 (no stage label in event) | AC-1, AC-2, AC-10 |
| `service.py` ownership stage service (`set_ownership_stage`, `advancement_suggestions`, `accept/dismiss`) | §3, §5; DECISION-008/017 | INV-5, INV-6 | AC-3, AC-4, AC-13, AC-15 |
| `service.py` `materialise_day` / `end_of_day` | §4 `→ expired`; TOQ-7 | INV-6 (sweep never touches stage), INV-16 | AC-14 |

## Where each ratified decision is enforced in code

| Decision | Code |
|---|---|
| DECISION-003 (four stages) | `OwnershipStage` enum; `INV-3` test |
| DECISION-004 / INV-8 (child never sees the model) | `projections.TodayItem` / `TodayPayload` carry no stage; `CelebrationEvent` carries no stage |
| DECISION-005 (two child reward modes) | `verification_behaviour` → `IMMEDIATE` vs `REQUIRES_APPROVAL`; `TodayItem.waits_for_grownup` is the single bit |
| DECISION-006 / INV-15 (spot-check non-blocking) | `create_parent_review` mutates nothing else, even when `flagged=True` |
| DECISION-007 / INV-4 (verification derived) | `verification_behaviour()` is the only decision point; no stored flag |
| DECISION-008 / INV-6 (app suggests, parent confirms) | `should_suggest_advancement` returns a *signal*; only `set_ownership_stage` (parent scope) mutates the stage |
| DECISION-009 (threshold 8, tunable) | `QuestGrowService(advancement_threshold=8)` — a constructor/config value, not a constant in the state machine |
| DECISION-010 / INV-7 (regression neutral, reversible) | `plan_transition` allows any-earlier; `set_ownership_stage` writes no failure flag / points delta / event |
| DECISION-011 / INV-9 (ownership never a KPI) | no aggregate field or projection function exists; `INV-9` test scans for it |
| DECISION-012 / INV-14 (reward value stage-independent) | `_award_earn` reads `quest.points` only; **no wind-down rule** (OQ-C left open) |
| DECISION-013/014 / INV-16 (no streaks) | `consecutive_ok_count` absent from every read model; `WeeklyConsistency` is a plain count |
| DECISION-015 (Lifetime Achievement vs Spendable Balance) | `projections.lifetime_achievement` = Σ earn; `spendable_balance` = Σ signed points; both recomputed, never stored |
| DECISION-016 / INV-17 (authority invariant across stages) | no code path branches parent or child capability on `ownership_stage` |
| DECISION-017 (advance may skip stages) | `plan_transition` computes `bypassed`; `set_ownership_stage` returns the plan so the UI can name them; the auto-suggestion is always exactly one stage |
| DECISION-018 (expired neutral for the counter) | `end_of_day` changes no counter; `counter_after_expired` is identity |
| DECISION-019 (MVP on-ramp) | `default_stage_for_age_band` returns `PARENT_GUIDED` for every band; no assignment path produces `PARENT_MANAGED` |

## Implementation-level ambiguities (IL) — not product decisions

### IL-1 — lifetime of a `pending` instance at end of day — **RESOLVED (#18)**

`PARENT_CHILD_MODEL` "Completion states" said *"an available **or pending**
quest not completed by end of day … → expired"* and `TECHNICAL_MODEL §4`'s
table listed `available / pending → expired`, while `VERIFICATION` implies a
`pending` completion survives past the day it was marked ("…next time the
child opens the app").

**Resolution (contract updated — `TECHNICAL_MODEL §4`, `PARENT_CHILD_MODEL`
"Completion states"):**

- `available` instances expire the night they were due.
- `pending` instances are **not** swept the same day. A `pending` instance
  expires on the first end-of-day sweep that is `>= pending_grace_days` days
  past its occurrence date — **default 1** (survives the occurrence day, then
  expires the following day if the parent has not acted). It expires
  **silently**: the quest rolls over per schedule, the child sees no negative
  signal, and the counter is unaffected (`expired` is neutral, DECISION-018).
- `pending_grace_days` is a tunable operational default (like the advancement
  threshold), not a domain invariant. `None` disables `pending` expiry.

**Implementation:** `QuestGrowService(pending_grace_days=1)` (was
`pending_ttl_days=None`); `end_of_day` sweeps both `available` (immediately)
and stale `pending` (past the grace window). Test:
`test_il1_pending_grace_window` in `tests/test_acceptance.py`.

This resolution introduces **no product decision** — it clarifies a
cross-document contradiction and does not touch DECISION-001…019 or OQ-A…OQ-H.

### IL-2 — "weekly" recurrence anchor

`QuestSchedule.recurrence` includes `weekly` (§2) with no stated anchor day.
Implemented as "once per ISO week on an anchor weekday", where the anchor is
the single weekday in `weekdays` if given, else the schedule `start` weekday,
else Monday. Purely a scheduling mechanic; no product weight.

## Known defects

### IL-5 — quest-version instance lookup — **RESOLVED (C1)**

`_get_instance` and `materialise_day` previously keyed `QuestInstance` lookups
on `latest_quest().version`, so after a same-day `edit_quest` a pre-edit
instance became unaddressable and `materialise_day` duplicated it at the new
version.

**Fix (C1):** all instance resolution goes through
`Repository.get_instance_any_version(quest_id, child_id, date)`, which returns
the newest-version instance for that `(quest, child, date)`. `materialise_day`
uses the same call as its same-day duplicate guard. Instances still keep the
version they were created under (`QUEST_MODEL` unchanged).

**Regression test:**
`test_il5_quest_edit_midday_keeps_instance_addressable_and_no_duplicate` in
`tests/test_acceptance.py` (now a plain passing test; the `xfail` marker is
gone). Contract-consistent — this was an implementation defect, not contract
drift.

## Test coverage

`tests/test_acceptance.py` — AC-1 … AC-15 + `test_il1_pending_grace_window`
(issue #18).
`tests/test_invariants.py` — INV-1 … INV-18, one test each (structural scans
for INV-1/4/8/9; behavioural for the rest).
`tests/test_c1_persistence.py` — `InMemory`/`Sqlite` parity flow, `edit_reward`,
`set_child_profile`, `daily_progress`, `seed_starter_quests`, `complexityProfile`
in `today()` + the INV-8 no-stage/level guard, and a schema no-drift scan.
`tests/test_api.py` — AC-1/2/5/8/9/11/13 replayed over HTTP, 403/401 on
forged/cross-scope requests, OpenAPI scanned for the INV-8 boundary
(needs `httpx`; `pytest.importorskip`).
`tests/test_auth.py` — session token is not a parent scope, wrong PIN does not
unlock, child tokens are per-child and non-escalatable, cross-account parent
cannot mint a foreign child token, secrets stored hashed, parent-token expiry.
`tests/test_webclient.py` — both reference clients served as HTML, no
streak/downgrade/failure/"% owned" framing in either, child client calls only
`/me/*`, offline queue + 409-drop wiring, `complexityProfile` consumed.
`tests/test_notifications.py` — Mode-A event on approval only / Mode-B
immediately, opt-out suppresses the parent feed but not the child celebration,
runtime toggle, `since=` filter, template banned-phrase + no-second-person
scan.
`tests/test_integration_mvp.py` — one full-stack scenario over
`AuthService` + FastAPI + `SqliteRepository` walking MVP feature areas 1–12.
`tests/test_d1_acceptance.py` — **the D1 acceptance run**: one test per
`MVP.md` acceptance scenario 1–10, the enforceable cross-cutting requirements,
and out-of-scope absence checks, all end-to-end on the D1 backend. See
[`D1_ACCEPTANCE.md`](../product-delivery/D1_ACCEPTANCE.md).

Run: `python3 -m pytest -q` (from the repo root). The domain + C1 suite needs
no third-party packages; the API / auth / webclient / integration / D1 suites
need `fastapi` + `httpx` (use a venv — `.venv/bin/python -m pytest -q`).

## Reference web-client notes (C5/C6)

`webclient/child.html` and `webclient/parent.html` are thin static single-file
clients over the C2–C4 API, served at `/app/child` and `/app/parent`. They are
the D1 reference clients, not the production track. Both were driven and
screenshotted in a real headless Chrome during Phase E — see
[`E_READINESS.md`](../product-delivery/E_READINESS.md).

`child.html` accessibility: `@media (prefers-reduced-motion)` disables the
celebration animation (verified: `.celebrate .burst` computed
`animation-name` → `none` under `reduce`); tap-to-hear via `speechSynthesis`
(Web Speech API — no dependency, silent where unavailable) with `aria-label`s,
plus `audio_narration: "always"` auto-reads the day's quests; instance state
shown as text + glyph (`✓ done` / `⏳ waiting`), never colour alone;
interactive controls ≥64×64pt (`.say` button; UX_PRINCIPLES). Body/label text
is `#001858` on `#fef6e4` (~13:1). The celebration animation runs ~1.6s
(UX_PRINCIPLES "bounded 1–3s").

`parent.html` covers the full UX_PRINCIPLES parent screen inventory:
Dashboard, Approvals, **Family** (child profile — name / age band / birthdate —
and per-dimension age-adaptation overrides), Quests (+ one-tap starter
templates), Rewards, Ownership (+ advancement suggestions), Progress,
**Settings** (notifications opt-in, parent-gate note, sign out).

API note: optional poll cursors (`?since=`) tolerate an empty string (a fresh
client has nothing stored) via the `_since` dependency — an empty value means
"no cursor", not a 422. `ChildIn` / `ChildProfileIn` / `ChildOut` carry
`birthdate` (MVP feature area 1/11 — "birthdate or explicit age band"); it is
stored but nothing derives behaviour from it (age band drives
`complexityProfile`).

## Known implementation gaps (deferred — out of MVP-subsystem scope)

- Hardened parent-gate challenge (rate-limiting, lockout, re-challenge policy);
  token + event persistence across restarts (`AuthService` and `EventSink` are
  in-memory).
- Real-time celebration delivery (poll only in MVP — ROADMAP Layer 1).
- Production mobile client (post-D1); a browser/visual QA pass on the two
  reference clients (see `D1_ACCEPTANCE.md` caveats).
- `PARENT_MANAGED` assignment UX (DECISION-019 — post-MVP).
- Optional evidence photos, long-term meta-game, multi-caregiver (all
  post-MVP / deferred in the contract).

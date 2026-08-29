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
generated OpenAPI). **Not yet** implemented (later C-phases): real auth
tokens / PIN parent-gate (C3 — `TokenStore` issuance is a placeholder),
notification transport (C4), the reference web clients (C5/C6), and any
long-term meta-game. `PARENT_MANAGED` **is** implemented and
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
| `events.py` (`EventSink`, `CelebrationEvent`) | §4 `completion.verified`; ARCHITECTURE notification service | INV-8 (no stage label in event) | AC-1, AC-2, AC-10 |
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

Run: `python3 -m pytest -q` (from the repo root). The domain + C1 suite needs
no third-party packages; later C-phases add `fastapi`/`httpx` (use a venv —
`.venv/bin/python -m pytest -q`).

## Known implementation gaps (deferred — out of MVP-subsystem scope)

- Real auth tokens + PIN parent-gate challenge (C3 — `api.TokenStore` issuance
  is a dev placeholder).
- Notification delivery transport (only the event sink interface exists) (C4).
- Reference web clients (C5/C6); production mobile client (post-readiness).
- `PARENT_MANAGED` assignment UX (DECISION-019 — post-MVP).
- Optional evidence photos, long-term meta-game, multi-caregiver (all
  post-MVP / deferred in the contract).

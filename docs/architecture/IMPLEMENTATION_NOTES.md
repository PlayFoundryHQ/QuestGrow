# QuestGrow MVP — Implementation Notes

> The implementation in `src/questgrow/` is written **strictly against**
> [`TECHNICAL_MODEL.md`](./TECHNICAL_MODEL.md) (the contract), which is bound
> to [`DECISION_LOG.md`](../governance/DECISION_LOG.md) (DECISION-001…019) and
> [`OWNERSHIP_MODEL.md`](../experience/OWNERSHIP_MODEL.md). This file maps the
> code to the contract and records implementation-level choices that could not
> be read verbatim from a single document. It creates **no** product
> semantics and resolves **no** open question (OQ-A long-term, OQ-B…OQ-H).

## Stack

Python 3.11+, standard library only, `pytest`. Chosen because `ARCHITECTURE.md`
leaves stack open ("decide against team skills"; none given) and a
dependency-free domain library is the most auditable substrate for the
acceptance tests. **Not** implemented: the HTTP/REST API, auth tokens /
parent-gate, the mobile client (thin/presentational), a real datastore
(`InMemoryRepository` is the seam — TOQ-7), and any long-term meta-game.
`PARENT_MANAGED` **is** implemented and tested (it "remains a valid part of
the contract" — DECISION-019) but nothing assigns it by default in MVP.

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
| `projections.py` (`lifetime_achievement`, `spendable_balance`, `TodayPayload`, `WeeklyConsistency`) | §7 | INV-8, INV-9, INV-13, INV-16 | AC-8, AC-9 |
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

### IL-1 — lifetime of a `pending` instance at end of day

`PARENT_CHILD_MODEL` "Completion states" says *"an available **or pending**
quest not completed by end of day … → expired"*, and `TECHNICAL_MODEL §4`'s
table lists `available / pending → expired`. But `VERIFICATION` says approval
*"triggers the child's celebration … otherwise **next time the child opens the
app**"* — implying a `pending` completion survives past the day it was marked.
These conflict on whether a child-marked-but-not-yet-approved completion
silently disappears at midnight.

**Chosen implementation:** `end_of_day` sweeps **`available`** instances to
`expired`; **`pending`** instances persist until the parent resolves them
(approve / `not_yet`). A `pending_ttl_days` constructor parameter (default
`None` = no expiry) is provided so a later contract clarification can tighten
this without a redesign.

**Rationale:** expiring a `pending` completion the child already did their part
on is a silent negative outcome, contradicting "no negative signal" and
`VERIFICATION`'s wording. This choice does not reopen a product decision.

**Recommended follow-up:** a contract clarification pass on `TECHNICAL_MODEL §4`
and `PARENT_CHILD_MODEL` "Completion states" to state the `pending` lifetime
explicitly.

### IL-2 — "weekly" recurrence anchor

`QuestSchedule.recurrence` includes `weekly` (§2) with no stated anchor day.
Implemented as "once per ISO week on an anchor weekday", where the anchor is
the single weekday in `weekdays` if given, else the schedule `start` weekday,
else Monday. Purely a scheduling mechanic; no product weight.

## Test coverage

`tests/test_acceptance.py` — AC-1 … AC-15, one test each.
`tests/test_invariants.py` — INV-1 … INV-18, one test each (structural scans
for INV-1/4/8/9; behavioural for the rest).

Run: `python3 -m pytest -q` (from the repo root).

## Known implementation gaps (deferred — out of MVP-subsystem scope)

- HTTP/API surface, auth tokens, parent-gate challenge.
- Real persistence (engine/schema — an open construction question).
- Notification delivery transport (only the event sink interface exists).
- Age-adaptation *rendering* values in `complexityProfile` (only the structural
  guarantee — no stage/level — is implemented).
- Mobile client.
- `PARENT_MANAGED` assignment UX (DECISION-019 — post-MVP).
- Optional evidence photos, long-term meta-game, multi-caregiver (all
  post-MVP / deferred in the contract).

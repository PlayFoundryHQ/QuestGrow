# QuestGrow Technical Model

> **Status:** Contract layer. This document defines **what an implementation
> must guarantee**. It does not describe how the system is built (that is
> [`ARCHITECTURE.md`](../product-delivery/ARCHITECTURE.md)) and it introduces
> no product semantics of its own.

## 1. Purpose and position in the hierarchy

```
PRODUCT FOUNDATION   (MANIFESTO, PRODUCT_VISION, CORE_PRINCIPLES)
        ↓
DECISION LOG         (DECISION-001 … DECISION-019; 001–016 frozen)
        ↓
OWNERSHIP MODEL      (the developmental arc and state model)
        ↓
TECHNICAL MODEL      (this document — the contract an implementation must obey)
        ↓
ARCHITECTURE         (how the system is organised and built)
        ↓
IMPLEMENTATION       (future — schema, API, code)
```

This document translates **ratified product semantics into precise technical
constraints**. It is bound by, and traceable to:

- [`DECISION_LOG.md`](../governance/DECISION_LOG.md) — DECISION-001 … DECISION-019
- [`OWNERSHIP_MODEL.md`](../experience/OWNERSHIP_MODEL.md)
- [`CORE_PRINCIPLES.md`](../product-foundation/CORE_PRINCIPLES.md)
- [`PARENT_CHILD_MODEL.md`](../trust-and-safety/PARENT_CHILD_MODEL.md),
  [`VERIFICATION.md`](../trust-and-safety/VERIFICATION.md),
  [`QUEST_MODEL.md`](../game-design/QUEST_MODEL.md),
  [`REWARD_MODEL.md`](../game-design/REWARD_MODEL.md),
  [`MVP.md`](../product-delivery/MVP.md)

Rules of this document:

- It **must not** resolve a product open question or introduce a product
  decision **on its own authority**. Where a Product Owner decision recorded
  in [`DECISION_LOG.md`](../governance/DECISION_LOG.md) settles a point, this
  document records the consequence and cites that decision (e.g.
  DECISION-017 … DECISION-019). OQ-A's long-term aspect and OQ-B … OQ-H
  remain open (§10).
- Where a technical point cannot be derived unambiguously from the sources
  above, it is recorded as a **Technical Open Question (TOQ)** in §10 — never
  given an invented answer.
- Where [`ARCHITECTURE.md`](../product-delivery/ARCHITECTURE.md) previously
  stated a contract-level rule, that rule now lives here and
  `ARCHITECTURE.md` references it (§11).

## 2. Domain concepts

Conceptual entities, their identity, and **who may write them**. This is not a
persistence schema (persistence is a construction concern — §10 / TOQ-7); it
is the set of things an implementation must represent and the authority over
each.

| Entity | Identity | Essential attributes | Writable by |
|---|---|---|---|
| **Account** | `accountId` | one parent identity; settings; parent-gate config | parent scope |
| **Child** | `childId` (owned by an Account) | name; avatar; birthdate or explicit age band; per-dimension age-adaptation overrides | parent scope |
| **Quest** | `questId` + `version` | title; icon/art ref; points value; age-suitability hint; active/archived | parent scope |
| **QuestSchedule** | `questId` | recurrence (daily / weekday set / weekly); optional time window; optional start/end | parent scope |
| **ChildQuest** | `(childId, questId)` pairing | `ownership_stage` ∈ {`PARENT_MANAGED`,`PARENT_GUIDED`,`CHILD_PARTICIPATED`,`CHILD_OWNED`}; `consecutive_ok_count` | **parent scope only** — never child scope, never the system autonomously |
| **QuestInstance** | `(questId@version, childId, date)` | `state` ∈ {`available`,`pending`,`verified`,`not_yet`,`expired`}; `stage_at_completion` | **server only**, via the transitions in §4 |
| **CompletionRequest** | its own id | `questInstanceId`; `childId`; createdAt; optional note / evidence ref | child scope (intent only) — for own `childId` only |
| **ParentReview** | its own id | `questInstanceId`; `childId`; parent note; timestamp | parent scope; **non-blocking** (§4) |
| **LedgerEntry** | its own id + idempotency key | `childId`; `kind` ∈ {`earn`,`redeem`,`adjustment`}; `points` (signed); `source`; timestamp | **server only**; append-only |
| **Reward** | `rewardId` | name; icon; cost (in points); `redemption_mode` ∈ {`self_service`,`parent_confirmed`}; active | parent scope |
| **RewardRedemption** | its own id | `rewardId`; `childId`; `state`; timestamps | child scope (intent) → server (resolution) → parent (grant, if `parent_confirmed`) |
| **AuditLogEntry** | its own id | actor; action; target; before/after; timestamp | server only (records parent actions on meaningful state, incl. every `ownership_stage` transition) |

There is **no** entity, field, or attribute representing a single
child-level independence or ownership level (INV-1). There is **no**
`verification_required` / `self_mark_preauthorized` field anywhere (INV-4).

## 3. Ownership state machine

`ownership_stage` is a property of a **ChildQuest** — the `(childId, questId)`
pairing. Every quest, for every child, has exactly one `ownership_stage`.

### States

```
PARENT_MANAGED  →  PARENT_GUIDED  →  CHILD_PARTICIPATED  →  CHILD_OWNED
```

Ordered from least to most child responsibility. The order defines "earlier"
and "later" for advancement and regression; it does **not** rank the states
as better or worse ([DECISION-010](../governance/DECISION_LOG.md),
[OWNERSHIP_MODEL §7](../experience/OWNERSHIP_MODEL.md)).

### Transitions

| Transition | Trigger | Actor | Guard | Effect | Non-effects |
|---|---|---|---|---|---|
| **Advance** (to any later stage) | parent confirms a one-stage advancement suggestion, **or** parent sets any later stage directly — one or more stages in a single action | parent scope | none required to set it directly. The app's *suggestion* proposes exactly **one** stage and is emitted only when `consecutive_ok_count ≥ threshold` (§4) | `ownership_stage` updated; `consecutive_ok_count` reset to 0; audit-log entry with the parent as initiator. When the parent advances by **more than one stage**, the confirmation UI names the bypassed stage(s) | no points change; no child-visible event; no reward-value change |
| **Regress** (to any earlier stage) | parent action | parent scope | none | `ownership_stage` updated; `consecutive_ok_count` reset to 0; audit-log entry | **no** failure flag, points delta, negative animation, or child-visible "downgrade" ([DECISION-010](../governance/DECISION_LOG.md)); reversible — a regressed quest may advance again later |
| **(no transition)** | advancement suggestion emitted; time passing; child activity | — | — | at most one outstanding suggestion per ChildQuest | **the system never changes `ownership_stage` on its own** ([DECISION-008](../governance/DECISION_LOG.md)) |

- The **default** `ownership_stage` at quest assignment is computed
  server-side from the child's age band
  ([QUEST_MODEL](../game-design/QUEST_MODEL.md),
  [OWNERSHIP_MODEL §10](../experience/OWNERSHIP_MODEL.md); §7, TOQ-9).
  **In MVP this default is `PARENT_GUIDED` for every quest**, and
  `PARENT_MANAGED` is not assignable or rendered through the MVP UI
  ([DECISION-019](../governance/DECISION_LOG.md)). `PARENT_MANAGED` and its
  `PARENT_RECORDS` behaviour (§4) remain a valid part of the contract for
  post-MVP use.
- **Forward movement may skip stages.** A parent may set `ownership_stage`
  to any later value in a single manual action; when more than one stage is
  bypassed, the confirmation names the bypassed stage(s)
  ([DECISION-017](../governance/DECISION_LOG.md)). The app's automatic
  *suggestion* still proposes exactly one stage at a time
  ([DECISION-008](../governance/DECISION_LOG.md)).
- **Regression** may move to any earlier stage
  ([OWNERSHIP_MODEL §7](../experience/OWNERSHIP_MODEL.md)).
- **Any transition** (advance, skip-advance, or regress) resets
  `consecutive_ok_count` to 0 — the counter always measures consecutive
  completions *at the current stage* toward the next one-stage suggestion
  (TOQ-2).

## 4. Completion and verification derivation

### Verification behaviour is a pure function of `ownership_stage`

```
verification_behaviour(ownership_stage) =
    PARENT_MANAGED      → PARENT_RECORDS      (no child self-mark path)
    PARENT_GUIDED       → REQUIRES_APPROVAL   (completion is pending until parent approves)
    CHILD_PARTICIPATED  → IMMEDIATE           (verified on self-mark; optional post-hoc parent review)
    CHILD_OWNED         → IMMEDIATE           (verified on self-mark; no routine review nudge)
```

This function is **evaluated, never persisted** as an independent field
([DECISION-007](../governance/DECISION_LOG.md), INV-4).

### QuestInstance state machine

```
                 available
                    │  child submits CompletionRequest        parent records
                    ▼        (stage ≠ PARENT_MANAGED)         (stage = PARENT_MANAGED)
        ┌───────────┴───────────┐                             │
   IMMEDIATE                REQUIRES_APPROVAL                  │
        │                        │                            │
        ▼                        ▼                            ▼
    verified                  pending                      verified
                                 │  parent: approve ──────────►
                                 │  parent: not_yet ─────► available (+ optional gentle note)
   available, not completed by end of day ───────────────► expired  (no penalty; rolls over per schedule)
   pending, unresolved past the grace window ────────────► expired  (silent; rolls over; no child signal)
```

| From | To | Trigger | Actor |
|---|---|---|---|
| `available` | `verified` | CompletionRequest with stage ∈ {`CHILD_PARTICIPATED`,`CHILD_OWNED`} | server (evaluating the request) |
| `available` | `pending` | CompletionRequest with stage = `PARENT_GUIDED` | server |
| `available` | `verified` | parent records (stage = `PARENT_MANAGED`) | parent scope → server |
| `pending` | `verified` | parent `approve` | parent scope → server |
| `pending` | `available` | parent `not_yet` (declines **this instance only**; no penalty) | parent scope → server |
| `available` | `expired` | end of day, not completed | server (scheduled) |
| `pending` | `expired` | not resolved by the parent within the **pending grace window** — default **1 day** past the occurrence date (a tunable operational default, **not** a domain invariant) | server (scheduled) |

**Pending lifetime (resolves IL-1).** A `pending` instance is **not** swept on
the same day it was marked — the child must be able to see its "waiting for
grown-up" state on their next session
([VERIFICATION](../trust-and-safety/VERIFICATION.md)). It persists until the
parent resolves it (`approve` / `not_yet`) or until the **pending grace
window** elapses (default: occurrence date + 1 day), after which it expires
**silently**: the quest rolls over to its next scheduled instance, the child
sees no negative signal (MANIFESTO — "missing a quest is a neutral
non-event"), and the counter is unaffected (`expired` is neutral —
[DECISION-018](../governance/DECISION_LOG.md)). The grace window is
configurable, like the advancement threshold.

The child never writes `QuestInstance.state` directly; the child only creates
a `CompletionRequest` (intent). The server sets the resulting state
(INV-10, INV-18).

### On `verified`

- Emit a `completion.verified` event → drives the child's celebration
  (immediately for `IMMEDIATE`; on parent approval for `REQUIRES_APPROVAL`;
  when the parent records for `PARENT_RECORDS`).
- Write **exactly one** `earn` `LedgerEntry` for this completion, idempotently
  (INV-11). **The idempotency anchor is the `QuestInstance` identity
  `(questId@version, childId, date)`** — an instance reaches `verified` at
  most once and yields exactly one `earn` through every path (self-mark,
  parent approval, parent record) (TOQ-3).
- `stage_at_completion` records the `ownership_stage` in force at the moment
  of the transition.

### ParentReview

A `ParentReview` on a `verified` completion (stages `CHILD_PARTICIPATED` /
`CHILD_OWNED`) is **non-blocking**: it never delays, withholds, reverses, or
probabilistically gates the celebration, the `verified` state, or the ledger
entry ([DECISION-006](../governance/DECISION_LOG.md), INV-15). A review that
surfaces a problem is handled out of band (a conversation, optionally a stage
regression per §3) — it does not claw back a reward already given.

### `consecutive_ok_count`

An internal per-ChildQuest counter of consecutive **completed** eligible
scheduled occurrences at the current `ownership_stage`. Effect of each
outcome on the counter:

| Outcome | Effect |
|---|---|
| occurrence `completed` (→ `verified`) | **+1** |
| parent `not_yet` (decline) | **reset to 0** ([DECISION-009](../governance/DECISION_LOG.md)) |
| occurrence `expired` (scheduled, not completed) | **no effect** ([DECISION-018](../governance/DECISION_LOG.md)) |
| non-scheduled day (occurrence never due) | **no effect** ([DECISION-009](../governance/DECISION_LOG.md)) |
| any `ownership_stage` transition (advance / skip / regress) | **reset to 0** (TOQ-2) |

When the counter reaches the configured threshold (**default 8, a tunable
product default — not a domain invariant**;
[DECISION-009](../governance/DECISION_LOG.md)), the system emits a **one-stage**
advancement **suggestion** to the parent and takes no other action. The
counter is **never** surfaced to child or parent and is **never** framed as a
streak ([DECISION-013](../governance/DECISION_LOG.md), INV-16). It drives a
suggestion only at stages that have a next stage (`PARENT_GUIDED`,
`CHILD_PARTICIPATED`); at `CHILD_OWNED` it is inert, and its behaviour at the
post-MVP `PARENT_MANAGED` stage is deferred with that stage's experience.

## 5. Authority / actor matrix

Columns: **Child** = child-scope token; **Parent** = parent-scope token
(post parent-gate); **Server** = the QuestGrow backend; **System** = any
automated / scheduled process. "✔ intent" = may create an intent row only,
resolved by the Server.

| Operation / state | Child | Parent | Server | System |
|---|---|---|---|---|
| Define / edit / archive a Quest | ✘ | ✔ | — | ✘ |
| Edit a QuestSchedule | ✘ | ✔ | — | ✘ |
| Set a quest's `points` value | ✘ | ✔ | — | ✘ |
| Define / edit a Reward | ✘ | ✔ | — | ✘ |
| Edit Child profile / age configuration | ✘ | ✔ | — | ✘ |
| **Set `ChildQuest.ownership_stage`** — advance one stage, **advance multiple stages**, or regress to any earlier stage | ✘ | ✔ (manual; confirmation names any bypassed stages) | writes on parent instruction; audit-logs the transition and resets `consecutive_ok_count` | **✘** — never autonomously ([DECISION-008](../governance/DECISION_LOG.md), [DECISION-017](../governance/DECISION_LOG.md)) |
| Emit an advancement **suggestion** | ✘ | ✘ | — | ✔ (suggestion only; no state change) |
| Create a `CompletionRequest` | ✔ intent (own `childId`) | ✔ (records, for `PARENT_MANAGED`) | resolves | ✘ |
| Transition `QuestInstance.state` | ✘ | approve / `not_yet` on `pending` | performs all transitions (§4) | `→ expired` only (scheduled) |
| Write a `LedgerEntry` (`earn`) | ✘ | ✘ | ✔ (one per `verified` completion) | ✘ |
| Write a `LedgerEntry` (`redeem`) | ✘ | ✘ | ✔ (on valid redemption) | ✘ |
| Write a `LedgerEntry` (`adjustment`) | ✘ | ✔ explicit instruction (additive-only in MVP) → Server writes | ✘ | **✘ in MVP** — no automated/system trigger (TOQ-5); a future non-parent trigger would be a separate decision |
| Create a `ParentReview` | ✘ | ✔ | — | ✘ |
| Redeem a Reward | ✔ intent | grants (if `parent_confirmed`) | resolves, checks Spendable Balance | ✘ |
| Read child-facing "today" / own progress | ✔ (own `childId`, no stage labels — INV-8) | ✔ | serves | — |
| Read parent dashboard / approvals / history | ✘ | ✔ | serves | — |
| Read any ownership-progress aggregate | **✘ — no such value exists** (INV-9) | **✘ — no such value exists** (INV-9) | — | — |

**Ownership transfer changes none of the Parent column.** The parent's
authority set is identical at every `ownership_stage`
([DECISION-016](../governance/DECISION_LOG.md), INV-17).

The matrix assumes a single parent scope per account. A second-caregiver /
limited-verifier role is **out of MVP scope**
([PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md)
"Multi-parent / caregiver (future)"); the account↔parent-identity
relationship should be modelled so it is not costly to extend later
(TOQ-8, deferred).

## 6. Reward semantics (contract level)

- **`LedgerEntry` is the authoritative reward record.** Append-only.
  Server-written only. No client, and no automated non-completion process,
  writes an `earn` entry.
- **`earn`** — `points ≥ 0`, produced by exactly one `verified` completion,
  idempotent (INV-11).
- **`redeem`** — `points ≤ 0`, produced by a valid RewardRedemption; affects
  **Spendable Balance only** (INV-13).
- **`adjustment`** — additive-only in MVP; written by the Server **only on
  explicit parent instruction**, with no automated or system trigger
  (INV-12; TOQ-5). A future non-parent adjustment trigger (e.g. data
  remediation) would require a separate decision.
- A quest's `points` value is **independent of `ownership_stage`** — the same
  quest yields the same points at `PARENT_GUIDED` and at `CHILD_OWNED`
  ([DECISION-012](../governance/DECISION_LOG.md), INV-14). Only the *timing*
  of the reward differs (§4).
- Points may be disabled account-wide; when disabled, no `earn` entries are
  written and the counters below are zero.
- **Redemption modes**
  - `self_service`: child intent → Server checks Spendable Balance ≥ cost →
    writes a `redeem` entry → notifies parent.
  - `parent_confirmed`: child intent → `RewardRedemption.state = pending` →
    parent grants → Server writes the `redeem` entry. Declining is gentle and
    carries no penalty.
- **No wind-down rule.** Nothing here reduces or removes rewards as a routine
  reaches `CHILD_OWNED`. Whether the celebration layer should eventually
  quieten for owned routines is **OQ-C — unresolved** and must not be
  implemented as a rule ([DECISION-012](../governance/DECISION_LOG.md)).

### The two counters (both projections — §7)

| Counter | Definition | Property |
|---|---|---|
| **Lifetime Achievement** | `Σ earn` | monotonic non-decreasing over a child's lifetime; never reduced by `redeem` or `adjustment` (INV-13). Drives long-term progression / meta unlocks. |
| **Spendable Balance** | `Σ earn − Σ |redeem| ± adjustment` | may rise and fall; what a child can spend on rewards. |

([DECISION-015](../governance/DECISION_LOG.md))

## 7. Authoritative state vs projections

**Authoritative state** (the system's source of truth; changes only through
the legal transitions in §3–§6):

Account · Child · Quest (`@version`) · QuestSchedule ·
`ChildQuest.ownership_stage` · `ChildQuest.consecutive_ok_count` ·
`QuestInstance.state` (+ `stage_at_completion`) · CompletionRequest ·
ParentReview · LedgerEntry · Reward · `RewardRedemption.state` · AuditLogEntry.

**Projections / read-models** (never authoritative; always recomputable from
authoritative state; if cached, must be invalidatable and reproducible):

- the child-facing "today" payload (resolved quest instances + adaptation
  data)
- `verification_behaviour(ownership_stage)` (§4)
- **Lifetime Achievement**, **Spendable Balance** (§6)
- daily / weekly progress views ("progressive consistency" — never a stored
  streak counter; [DECISION-013/014](../governance/DECISION_LOG.md))
- the advancement **suggestion** (a derived signal, not stored state)
- `complexityProfile` — the resolved age-adaptation **rendering** values
  (vocabulary, text amount, iconography, interaction complexity, task
  complexity, reading requirement, reward presentation). It **does not**
  carry `ownership_stage` or an "independence level" — neither ever reaches a
  child-facing surface (INV-8; TOQ-9). The child's default `ownership_stage`
  for a newly assigned quest is derived server-side from the age band and
  stored on the `ChildQuest`, not delivered to the client.

**Rule.** No projection may be persisted in a form that can drift from its
authoritative source. In particular there is **no** stored `balance`,
`lifetime_points`, `spendable_points`, `verification_required`,
`independence_level`, `owned_routine_count`, or `streak` column
(INV-1, INV-4, INV-9, INV-12, INV-16).

## 8. Invariants

Each invariant is machine-checkable. "Check shape" sketches how an
implementation's test suite / static analysis would assert it.

| # | Invariant | Enforces | Check shape |
|---|---|---|---|
| **INV-1** | No field, on any entity, represents a single child-level independence or ownership level. | DECISION-002 | schema/type scan: no `independence*` / `ownership_level` attribute on `Child` or elsewhere; only `ChildQuest.ownership_stage` exists |
| **INV-2** | Every `ownership_stage` value is addressed by a `(childId, questId)` pair. | DECISION-002 | `ownership_stage` is reachable only via a `ChildQuest` keyed by both ids |
| **INV-3** | `ownership_stage` ∈ {`PARENT_MANAGED`,`PARENT_GUIDED`,`CHILD_PARTICIPATED`,`CHILD_OWNED`}. | DECISION-003 | enum/type constraint; property test rejects any other value |
| **INV-4** | Verification behaviour is computed from `ownership_stage` by the §4 function and is never read from a stored flag. | DECISION-007 | no `verification_required` / `self_mark_preauthorized` field; all verification decisions call the pure function |
| **INV-5** | `ownership_stage` is writable only by a parent-scope actor. | DECISION-008, DECISION-016 | authz test: child-scope and unauthenticated writes to `ChildQuest.ownership_stage` are rejected |
| **INV-6** | The system never transitions `ownership_stage` autonomously; every transition (single-stage, multi-stage, or regression) has a parent actor recorded in `audit_log`. | DECISION-008, DECISION-017 | for every `ownership_stage` change there is an `AuditLogEntry` with a parent `actor`; no code path mutates it from a scheduler/job or from the suggestion evaluator |
| **INV-7** | Regression is a legal transition to any earlier stage, is reversible, and produces no negative artifact (no failure flag, no points delta, no child-visible event). | DECISION-010 | regress then re-advance succeeds; ledger unchanged across a regression; no child-facing event emitted |
| **INV-8** | No child-facing surface exposes `ownership_stage`, a stage label, a level, a readiness verdict, or an ownership-progress value. | DECISION-004, DECISION-011 | contract test: every child-scope API response schema is asserted to contain none of these fields |
| **INV-9** | No stored value and no API response represents an aggregate of ownership progress (count / percentage / ranking / streak of owned routines) as a family-facing value or an optimisation target. | DECISION-011 | schema scan + response-schema scan for `owned_count`, `owned_pct`, `independence_score`, etc. |
| **INV-10** | A `QuestInstance` reaches `verified` only via (a) parent approval of a `pending` instance (`PARENT_GUIDED`), (b) server evaluation of a `CompletionRequest` where stage ∈ {`CHILD_PARTICIPATED`,`CHILD_OWNED`}, or (c) a parent record where stage = `PARENT_MANAGED`. | DECISION-005, DECISION-007, anti-self-scoring | state-machine test: no other transition into `verified` exists; forged child write to `state` rejected |
| **INV-11** | Exactly one `earn` `LedgerEntry` exists per `verified` completion. | DECISION-015, ARCHITECTURE | replaying the same completion event twice yields one entry (idempotency key) |
| **INV-12** | `LedgerEntry` is append-only and server-written; no client and no automated non-completion process writes `earn`. | DECISION-016 | no update/delete path on `LedgerEntry`; write authz limited to server-internal completion/redemption handlers |
| **INV-13** | Lifetime Achievement (`Σ earn`) is monotonic non-decreasing; `redeem` and `adjustment` never decrease it. | DECISION-015 | property test over arbitrary earn/redeem/adjustment sequences |
| **INV-14** | A quest's points value is identical regardless of the `(child, quest)` `ownership_stage`. | DECISION-012 | earn amount for quest Q is a function of Q only, not of `ownership_stage` |
| **INV-15** | A `ParentReview` never changes, delays, or reverses a completion's `verified` state, its celebration, or its ledger entry. | DECISION-006 | creating a `ParentReview` (including a negative one) leaves `QuestInstance.state` and the ledger unchanged |
| **INV-16** | No streak / consecutive-day counter is exposed to child or parent; `consecutive_ok_count` is used only to emit advancement suggestions, is never surfaced or framed as a streak, and a missed (`expired`) scheduled occurrence has no effect on it (DECISION-018). | DECISION-013, DECISION-014, DECISION-018 | `consecutive_ok_count` absent from all API response schemas; only referenced by the suggestion evaluator; property test: an `expired` outcome leaves the counter unchanged |
| **INV-17** | The parent authority set (§5, Parent column) is invariant across all `ownership_stage` values; a stage transition transfers no authority to the child or the system. | DECISION-016 | authz matrix test parameterised over all four stages yields identical parent capabilities and identical child capabilities |
| **INV-18** | A child-scope actor can create rows only in intent tables (`CompletionRequest`, reward-redemption intent) and only for its own `childId`. | PARENT_CHILD_MODEL, ARCHITECTURE | authz test: child-scope writes to any non-intent table, or to another child's intent, are rejected |

## 9. Testable acceptance criteria

Given/When/Then form, implementation-agnostic. Several correspond directly to
PHASE 7A adversarial checks so that review can become executable.

| # | Given | When | Then | Ties to |
|---|---|---|---|---|
| **AC-1** | quest Q at `PARENT_GUIDED` for child C | C submits a `CompletionRequest` for Q's current instance | `QuestInstance.state = pending`; **no** `LedgerEntry`; no celebration event | §4, INV-10 |
| **AC-2** | quest Q at `CHILD_OWNED` for C | C submits a `CompletionRequest` | `state = verified`; **exactly one** `earn` `LedgerEntry`; celebration event emitted immediately | §4, INV-10, INV-11 |
| **AC-3** | `consecutive_ok_count ≥ threshold` for a ChildQuest | time passes with no parent action | `ownership_stage` unchanged; at most one advancement suggestion outstanding | §3, INV-6 (PHASE 7A #11) |
| **AC-4** | quest Q at `CHILD_OWNED` for C | parent regresses Q to `PARENT_GUIDED` | no `LedgerEntry` delta; no child-visible failure/downgrade event; `consecutive_ok_count` reset to 0; Q can subsequently advance again | §3, INV-7 (PHASE 7A #12) |
| **AC-5** | a forged child-scope request to write `ownership_stage`, a `LedgerEntry`, or `QuestInstance.state = verified` | it is submitted | the server rejects it | INV-5, INV-10, INV-12, INV-18 (PHASE 7A #6) |
| **AC-6** | any sequence of `earn`, `redeem`, `adjustment` entries for C | Lifetime Achievement is recomputed after each | it never decreases | INV-13 (PHASE 7A #4) |
| **AC-7** | quest Q with points `p` | Q is completed once at `PARENT_GUIDED` and once at `CHILD_OWNED` (for the same or comparable child) | both `earn` entries have value `p` | INV-14 (PHASE 7A #13) |
| **AC-8** | child C with quests across all four stages | any API is queried for C | no response contains a count, percentage, level, or ranking of owned routines | INV-9 (PHASE 7A #4) |
| **AC-9** | child C | the child-scope "today" / progress endpoints are queried | no response contains `ownership_stage` or a derived stage label / readiness verdict | INV-8 (PHASE 7A #13, #15) |
| **AC-10** | a `verified` completion for C at `CHILD_PARTICIPATED` | parent creates a `ParentReview`, including a negative one | the completion stays `verified`; celebration already delivered is not reversed; ledger unchanged | INV-15 (PHASE 7A #8) |
| **AC-11** | quest Q at `PARENT_MANAGED` for C (a contract-valid stage; not MVP-assignable — [DECISION-019](../governance/DECISION_LOG.md)) | C attempts to submit a `CompletionRequest` | no child self-mark path exists; only a parent record transitions the instance | §4, INV-18 |
| **AC-12** | a completion event for instance I | it is delivered to the ledger writer twice (retry / replay) | exactly one `earn` `LedgerEntry` results | INV-11 |
| **AC-13** | quest Q at `PARENT_GUIDED` for C | parent sets Q directly to `CHILD_OWNED` in one action | the transition succeeds; the confirmation names the bypassed `CHILD_PARTICIPATED` stage; `consecutive_ok_count` for (C, Q) is 0; an audit-log entry records the parent as initiator | §3, §5 ([DECISION-017](../governance/DECISION_LOG.md)) |
| **AC-14** | `consecutive_ok_count = 5` for a ChildQuest at `PARENT_GUIDED` | a scheduled occurrence reaches `expired` uncompleted | `consecutive_ok_count` is still 5 | §4 ([DECISION-018](../governance/DECISION_LOG.md)) |
| **AC-15** | `consecutive_ok_count = 7` for a ChildQuest | the parent advances, skips, or regresses the stage | `consecutive_ok_count` becomes 0 | §3, §4 (TOQ-2) |

## 10. Technical questions — dispositioned

All nine technical open questions raised by the PHASE 8 reconciliation have
been dispositioned. Product/governance decisions are cited by their durable id
([DECISION-017](../governance/DECISION_LOG.md) …
[DECISION-019](../governance/DECISION_LOG.md)); technical dispositions are
engineering choices made within the existing contract; "deferred" items are
out of MVP scope.

| TOQ | Question | Disposition | Authority |
|---|---|---|---|
| **TOQ-1** | May forward `ownership_stage` movement skip stages? | **Yes.** A parent may set any later stage in one manual action; the confirmation names bypassed stages; the app's automatic suggestion still proposes one stage. (§3, §5, AC-13) | [DECISION-017](../governance/DECISION_LOG.md) |
| **TOQ-2** | Does a stage transition reset `consecutive_ok_count`? | **Yes** — any transition (advance / skip / regress) resets it to 0. (§3, §4, AC-15) | technical |
| **TOQ-3** | Idempotency anchor for one-`earn`-per-completion? | The **`QuestInstance` identity `(questId@version, childId, date)`**. (§4, INV-11, AC-12) | technical |
| **TOQ-4** | Is `PARENT_MANAGED` assignable in MVP? | **No** — MVP starts every quest at `PARENT_GUIDED`; `PARENT_MANAGED` stays in the domain model and contract (§2, §4) but is not assignable or rendered in the MVP UI; a dedicated `PARENT_MANAGED` / ~3–4 experience is post-MVP. | [DECISION-019](../governance/DECISION_LOG.md) (MVP-scope aspect of **OQ-A**) |
| **TOQ-5** | Non-parent trigger for an `adjustment` ledger entry? | **No** in MVP — written only on explicit parent instruction (additive-only). A future non-parent trigger is a separate decision. (§5, §6) | technical |
| **TOQ-6** | Does an `expired` occurrence break `consecutive_ok_count`? | **No** — `expired` is neutral (no increment, no reset). Only `completed` increments; only `not_yet` resets. (§4, INV-16, AC-14) | [DECISION-018](../governance/DECISION_LOG.md) |
| **TOQ-7** | Eager vs lazy `QuestInstance` materialisation? | **Eager** per-day materialisation for MVP; a lazy strategy may be revisited only on scale evidence. A construction choice owned by [`ARCHITECTURE.md`](../product-delivery/ARCHITECTURE.md). | technical (ARCHITECTURE) |
| **TOQ-8** | Shape of a future multi-caregiver role? | **Out of MVP scope.** Model the account↔parent-identity relationship so it is not costly to extend later. (§5) | deferred (future product scope) |
| **TOQ-9** | Age band → default `ownership_stage`; does "independence level" reach the child? | Server derives the default stage from the age band at quest assignment and stores it on the `ChildQuest`. The client-facing `complexityProfile` carries only rendering dimensions — never `ownership_stage` or an "independence level" (INV-8). (§3, §7) | technical (INV-8 governs the child-facing half) |

### Relationship to product open questions

[DECISION-019](../governance/DECISION_LOG.md) settled the **MVP-scope aspect
of OQ-A** (MVP is an on-ramp; MVP quests begin at `PARENT_GUIDED`; a dedicated
`PARENT_MANAGED` / ~3–4 experience is post-MVP). OQ-A's longer-term
product-identity question — whether the ~3–4 experience becomes a complete
product in its own right — **remains open**. **OQ-B … OQ-H are untouched and
remain open.** This document resolves no product open question beyond the
MVP-scope aspect of OQ-A that DECISION-019 determined; foundation documents
([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md) "Open questions",
[PRODUCT_VISION §13](../product-foundation/PRODUCT_VISION.md)) still list OQ-A
as flatly unresolved and are to be reconciled to DECISION-019 in a later
documentation pass.

## 11. Relationship to `ARCHITECTURE.md`

`TECHNICAL_MODEL.md` = **what must be true**.
`ARCHITECTURE.md` = **how the system is organised and built**.

Moved from `ARCHITECTURE.md` into this document (and now referenced from
there):

- the domain entities and their write-authority (was "Data model (core
  entities, MVP)") → §2
- the completion / verification state machine and stage→behaviour mapping →
  §4
- the ownership-stage transition rules (parent-scope-only, never autonomous,
  suggestion threshold, audit-log-not-child-facing) → §3, §5
- the progress-ledger rules (append-only, server-only, one-per-completion,
  projections-not-counters, Lifetime Achievement vs Spendable Balance) → §6,
  §7
- the token-scope authority model and the single child-intent exception → §5
- the invariants → §8

`ARCHITECTURE.md` retains: the high-level shape, client construction,
service/module decomposition, the age-adaptation resolver and notification
service as modules, persistence *implementation* (storage engine, schema;
instance materialisation is eager for MVP — §10 / TOQ-7), deployment topology,
privacy/security posture, and the construction open questions about
framework / stack / hosting / real-time transport / parent-gate design.

## 12. Traceability — ratified decision → this document

| Decision | Carried by |
|---|---|
| DECISION-001 central arc | §1, §3 (the state machine exists as the product's spine) |
| DECISION-002 per (child × quest), no child-level score | §2 (`ChildQuest`), §3, INV-1, INV-2 |
| DECISION-003 four stages | §3 states, INV-3 |
| DECISION-004 graduating invisible to child | §5 (child read rows), INV-8, AC-9 |
| DECISION-005 two child-facing reward modes | §4 (`IMMEDIATE` vs `REQUIRES_APPROVAL`), INV-10, AC-1/AC-2 |
| DECISION-006 spot-check never gates reward | §4 (ParentReview), INV-15, AC-10 |
| DECISION-007 verification derived from stage | §4 pure function, INV-4, §7 (no stored flag) |
| DECISION-008 parent confirms advancement; app suggests | §3 transitions, §5 (System column ✘), INV-5, INV-6, AC-3 |
| DECISION-009 default trigger 8, tunable | §4 (`consecutive_ok_count`, threshold), TOQ-2/TOQ-6 |
| DECISION-010 regression allowed, never failure | §3 Regress row, INV-7, AC-4 |
| DECISION-011 ownership never a KPI | §5 (no aggregate read), §7 (no stored count), INV-8, INV-9, AC-8 |
| DECISION-012 child-owned quests keep normal reward value | §6, INV-14, AC-7; "no wind-down rule" note |
| DECISION-013 no traditional streaks | §7 (no `streak` column), INV-16 |
| DECISION-014 progressive consistency replaces streaks | §7 (progress views are projections), INV-16 |
| DECISION-015 Lifetime Achievement vs Spendable Balance | §6 counters, §7 projections, INV-11, INV-13, AC-6/AC-12 |
| DECISION-016 authority + agency coexist; long-term = gradual transfer | §5 actor matrix, INV-17; ownership transfer changes no Parent-column capability |
| **DECISION-017** forward stage-skipping allowed | §3 Advance row + bullets, §5 actor matrix, INV-6, AC-13; §10 TOQ-1 |
| **DECISION-018** `expired` is neutral for `consecutive_ok_count` | §4 counter table, INV-16, AC-14; §10 TOQ-6 |
| **DECISION-019** MVP is an on-ramp (MVP-scope aspect of OQ-A) | §3 default-stage bullet, §4 (`PARENT_RECORDS` stays valid), §9 AC-11, §10 TOQ-4; `PARENT_MANAGED` retained in §2 |

**Product open questions:** [DECISION-019](../governance/DECISION_LOG.md)
(a Product Owner decision, not this document) settled the **MVP-scope aspect
of OQ-A**. OQ-A's long-term product-identity aspect, and **OQ-B … OQ-H**,
remain open. This document introduces no product decision — it records the
three approved outcomes ([DECISION-017](../governance/DECISION_LOG.md),
[DECISION-018](../governance/DECISION_LOG.md),
[DECISION-019](../governance/DECISION_LOG.md)) and six technical dispositions.

## 13. Age-adaptation profile (`complexityProfile`)

*(Contract for issue #10 — the value contract; client consumption is a
separate implementation task.)*

The server resolves a child's **age band** + per-dimension **parent
overrides** into a `complexityProfile` — a projection (§7) delivered with the
child's "today" payload. It carries **rendering values only**. It **never**
carries `ownership_stage`, a stage label, a level, or an "independence value"
(INV-8; independence is the per-quest `ownership_stage`, which the band only
sets a *default* for — §3, TOQ-9).

### Fields and per-band resolved values

Bands are guidance, not hard gates ([UX_PRINCIPLES → age adaptation](../experience/UX_PRINCIPLES.md)).

| Field | Type | ~3–4 | ~5–6 | ~7–8 |
|---|---|---|---|---|
| `text_style` | enum `{icon_only, short_label, short_sentence}` | `icon_only` | `short_label` | `short_sentence` |
| `audio_narration` | enum `{always, on_tap, on_tap}` | `always` | `on_tap` | `on_tap` |
| `iconography` | enum `{large_simple, standard, standard}` | `large_simple` | `standard` | `standard` |
| `quests_shown_at_once` | int range | `1–3` | `3–5` | `5–7` |
| `interaction` | enum `{single_tap, tap_drag, tap_drag_order}` | `single_tap` | `tap_drag` | `tap_drag_order` |
| `task_complexity` | enum `{single_step, small_multi_step, multi_step_sequence}` | `single_step` | `small_multi_step` | `multi_step_sequence` |
| `reading_requirement` | enum `{none, minimal, light}` | `none` | `minimal` | `light` |
| `reward_presentation` | enum `{big_animation, animation_progress, progress_collectibles}` | `big_animation` | `animation_progress` | `progress_collectibles` |

### Rules

- **Resolution.** `complexityProfile[dim] = parent_override[dim] if present
  else band_default(band)[dim]`. Every field always has a resolved value.
- **Overrides** are per-dimension and per-child (`Child.adaptation_overrides`);
  a parent may pin any single dimension without affecting the others.
- **Band boundaries** are approximate; `age_band` may be derived from
  birthdate or set explicitly (ARCHITECTURE privacy: a coarse band is
  sufficient).
- The profile is **recomputed**, never stored (§7). Changing the band or an
  override changes every subsequent payload.
- **INV-8 boundary:** a contract test asserts no child-scope response — the
  `complexityProfile` included — contains `ownership_stage` or any stage /
  level / readiness field.
- `quests_shown_at_once` is a soft layout hint; it does not cap the number of
  scheduled quests, only how many the child surface presents at once.

### Not in this contract

The exact visual/interaction design of each variant, and the client rendering
that consumes the profile, are the client track (#10 client-consumption half /
child-client issue). Post-MVP: smoother band transitions as a child grows, and
a parent-facing overrides UI ([ROADMAP](../product-delivery/ROADMAP.md)
Layer 2).

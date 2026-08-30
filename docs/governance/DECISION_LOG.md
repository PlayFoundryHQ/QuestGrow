# Decision Log

## Purpose

This document records **durable product decisions** for QuestGrow — decisions
about product direction, experience, behaviour, trust/safety, or architecture
whose reversal would *materially change the product*.

**Why it exists.** As the foundation evolves through review cycles, the
*reasoning* behind a decision is easily lost even when the resulting document
text survives. This log preserves the decision, its rationale, and its
consequences in one place, so a future contributor can tell the difference
between "this is deliberate and load-bearing" and "this is incidental wording".

**What belongs here.** Only decisions that are:

- durable (expected to hold across many iterations), and
- material (reversing them would change what QuestGrow *is* or how it
  behaves), and
- cross-cutting (they constrain more than one document or component).

**What does NOT belong here.**

- Implementation history, refactors, file moves, renames — that is Git's job.
- Wording tweaks, typo fixes, link fixes, formatting.
- Open questions still under discussion (record them once decided).
- Routine scoping choices that any contributor could revisit freely.

**Sources of truth.**

- **Git** remains the source of truth for *implementation and document
  history* (what changed, when, by whom).
- **This document** is the source of truth for *durable product decisions*
  (what was decided, and why).
- [CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) remains the
  constitution; this log records decisions made *under* it, not amendments to
  it. Where a decision extended a principle, that is noted and the change
  lives in CORE_PRINCIPLES itself.
- [LEADERSHIP_PROTOCOL](./LEADERSHIP_PROTOCOL.md) governs *who may act on and
  change* the sources above. This log is Tier B in its source-of-truth
  hierarchy: operationally supreme for settled questions, but never a route to
  manufacture a new decision by reinterpreting an old one — that is a PO
  escalation.

## Decision format

For each decision:

### DECISION-XXX — Title

- **Status:** Accepted | Superseded by DECISION-YYY | Reversed
- **Date:** ISO date the decision was settled
- **Decision:** what was decided, in one or two sentences
- **Why:** the rationale
- **Consequences:** what this forces or forbids downstream
- **Related principles:** CORE_PRINCIPLES numbers
- **Affected documents:** docs that encode this decision
- **Related GitHub issues:** existing issues, if any

Keep entries concise but substantive. Do not restate the full model — link to
it.

---

### DECISION-001 — Ownership is the central developmental arc

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** QuestGrow's core purpose is to gradually transfer ownership of
  everyday routines from parent to child. Ownership is treated as a
  *developmental arc*, not a feature or a child-facing level system.
- **Why:** Without a named arc, a parent-facing app with verification and
  points drifts toward a digital compliance / behaviour-control tool. The arc
  is the primary defence against that, and it gives "ages 3–8" a structural
  meaning (independence axis) rather than a cosmetic one.
- **Consequences:** Most experience, game-design, trust, and delivery
  documents defer to [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).
  Every significant feature is judged partly on whether it supports natural,
  non-coerced ownership transfer.
- **Related principles:** #4, #13, #16, #20, #24
- **Affected documents:**
  [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md),
  [README](../README.md),
  [CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md)
- **Related GitHub issues:** #17

### DECISION-002 — Ownership is modeled per (child × quest)

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** Ownership state is scoped to the (child, quest) relationship.
  There is no single `child.independenceLevel` or child-level independence
  score.
- **Why:** A real child owns "brush teeth" while still being guided on "tidy
  room". A single score cannot represent that and would force false
  averaging or premature independence.
- **Consequences:** The data model carries `ownership_stage` on a
  `child_quest` pairing. Age sets only a *default* starting stage per quest;
  quests then progress independently.
- **Related principles:** #18, #20
- **Affected documents:**
  [OWNERSHIP_MODEL §10 + Data model implication](../experience/OWNERSHIP_MODEL.md),
  [ARCHITECTURE → data model](../product-delivery/ARCHITECTURE.md),
  [QUEST_MODEL](../game-design/QUEST_MODEL.md)
- **Related GitHub issues:** #17, #9, #8, #10

### DECISION-003 — Ownership has four system/parent-side stages

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** The stages are `PARENT_MANAGED` → `PARENT_GUIDED` →
  `CHILD_PARTICIPATED` → `CHILD_OWNED`. They are primarily a parent-/
  system-side construct.
- **Why:** Four stages capture the meaningful transitions in responsibility
  (parent does it → parent verifies → parent lightly reviews → parent trusts)
  without over-modelling.
- **Consequences:** `PARENT_MANAGED` must exist in the domain model even
  though MVP may not surface it and may start every quest at `PARENT_GUIDED`.
  Verification behaviour and reward timing are functions of the stage.
- **Related principles:** #13, #16
- **Affected documents:**
  [OWNERSHIP_MODEL §3](../experience/OWNERSHIP_MODEL.md),
  [QUEST_MODEL](../game-design/QUEST_MODEL.md),
  [ARCHITECTURE](../product-delivery/ARCHITECTURE.md),
  [MVP](../product-delivery/MVP.md)
- **Related GitHub issues:** #17

### DECISION-004 — The child never needs to understand the four-stage model

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** The child is never required to understand or see the four
  stages. No stage names, no stage indicator, no "level N of 4" on the child
  side.
- **Why:** Child-first simplicity. The model's richness is a parenting tool;
  exposing it to the child adds complexity and invites status/comparison
  framing.
- **Consequences:** The child UI exposes only the simplest interaction
  appropriate to the current reward mode (see DECISION-005). Stage transitions
  and history are parent-side only.
- **Related principles:** #1, #2, #16
- **Affected documents:**
  [OWNERSHIP_MODEL §8](../experience/OWNERSHIP_MODEL.md),
  [CHILD_JOURNEY](../experience/CHILD_JOURNEY.md),
  [UX_PRINCIPLES](../experience/UX_PRINCIPLES.md)
- **Related GitHub issues:** #17, #12, #4

### DECISION-005 — Child-facing reward behaviour has only two modes

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** From the child's experience there are exactly two modes:
  (A) *do → wait for parent → celebrate* (`PARENT_GUIDED`), and
  (B) *do → celebrate immediately* (`CHILD_PARTICIPATED` / `CHILD_OWNED`).
- **Why:** Four stages must not become four child UIs. A single "instant
  reward vs. wait for parent" bit keeps the child surface tiny.
- **Consequences:** Build one child UI driven by that bit. All four-stage
  logic lives on the parent/system side.
- **Related principles:** #1, #15, #16
- **Affected documents:**
  [OWNERSHIP_MODEL §4](../experience/OWNERSHIP_MODEL.md),
  [CHILD_JOURNEY](../experience/CHILD_JOURNEY.md),
  [GAMIFICATION](../game-design/GAMIFICATION.md)
- **Related GitHub issues:** #17, #12, #5

### DECISION-006 — Independent quests never randomly delay or withhold celebration

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** At `CHILD_PARTICIPATED` and `CHILD_OWNED` the celebration is
  immediate every time. Any parent spot-check happens strictly after
  completion and never delays, withholds, or probabilistically gates the
  celebration or the points.
- **Why:** Random reward gating is variable-ratio reinforcement — a compulsion
  mechanic explicitly banned. A spot-check that finds a problem is a parenting
  conversation (and optionally a regression), not a clawback.
- **Consequences:** Spot-check tooling is a post-hoc, non-blocking parent
  review (`parent_review` rows). No mechanism may make an independent quest's
  reward conditional on a later check.
- **Related principles:** #9, #10
- **Affected documents:**
  [OWNERSHIP_MODEL §5](../experience/OWNERSHIP_MODEL.md),
  [GAMIFICATION → banned mechanics](../game-design/GAMIFICATION.md),
  [VERIFICATION](../trust-and-safety/VERIFICATION.md)
- **Related GitHub issues:** #17, #5, #15

### DECISION-007 — Verification behaviour is derived from ownership_stage

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** Whether a completion needs parent approval is computed from
  the `(child, quest)` `ownership_stage`. The previously proposed
  `verification_required` and `self_mark_preauthorized` fields are removed.
- **Why:** Two independent representations of the same fact drift out of sync.
  A single source (`ownership_stage`) cannot contradict itself.
- **Consequences:** Only `PARENT_GUIDED` produces a `pending` state /
  approval. `ownership_stage` is writable only in parent scope; the server
  decides the completion outcome from it. No contradictory boolean flag exists
  anywhere (architecture invariant).
- **Related principles:** #14, #15, #16
- **Affected documents:**
  [OWNERSHIP_MODEL → Data model implication](../experience/OWNERSHIP_MODEL.md),
  [QUEST_MODEL](../game-design/QUEST_MODEL.md),
  [VERIFICATION](../trust-and-safety/VERIFICATION.md),
  [PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md),
  [ARCHITECTURE → guiding constraints, invariants](../product-delivery/ARCHITECTURE.md)
- **Related GitHub issues:** #17, #6, #9, #15, #8

### DECISION-008 — Parent confirms advancement; the app may suggest it

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** The app may *suggest* moving a quest to a more independent
  stage. The parent *confirms*. The app never advances a quest on its own.
- **Why:** Ownership transfer is a parenting decision, not a prize the app
  awards. Preserves parent authority while making progress visible and
  low-effort.
- **Consequences:** Advancement UI is an invitation ("Ready to let her own
  this quest? [Not yet] [Let her own it]"), never an announcement. Parents can
  advance earlier manually or dismiss the suggestion.
- **Related principles:** #13, #15
- **Affected documents:**
  [OWNERSHIP_MODEL §6](../experience/OWNERSHIP_MODEL.md),
  [PARENT_JOURNEY](../experience/PARENT_JOURNEY.md),
  [UX_PRINCIPLES → Verification UX](../experience/UX_PRINCIPLES.md)
- **Related GitHub issues:** #17, #13

### DECISION-009 — Default advancement trigger is 8 consecutive eligible occurrences (tunable)

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** The default suggestion trigger is 8 consecutive eligible
  scheduled occurrences completed without a "not yet". The number 8 is a
  tunable product default, not a hard-coded domain invariant.
- **Why:** A concrete default is needed to ship; the exact value is a UX-
  tuning matter and may later vary by age band.
- **Consequences:** The threshold lives in configuration. A skipped
  non-scheduled day is not a break; a "not yet" resets the count. This counter
  is internal and never shown as a streak (see DECISION-013).
- **Related principles:** #13, #20
- **Affected documents:**
  [OWNERSHIP_MODEL §6 + open questions](../experience/OWNERSHIP_MODEL.md),
  [ARCHITECTURE → ownership stage service, open questions](../product-delivery/ARCHITECTURE.md),
  [MVP → acceptance](../product-delivery/MVP.md)
- **Related GitHub issues:** #17, #8, #7

### DECISION-010 — Regression is allowed and never framed as failure

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** Moving a quest to an earlier ownership stage is allowed and
  expected. It is never framed — to parent or child — as a downgrade, a lost
  level, or a failure.
- **Why:** Real life is not linear (illness, a new sibling, a hard month).
  Punitive framing would recreate the shame dynamic QuestGrow exists to avoid.
- **Consequences:** CORE_PRINCIPLE #20 was extended to state this explicitly,
  and an anti-pattern was added. Regression is parent-initiated; the app may
  surface an observation ("this one has been bumpy lately") but must not
  prescribe or auto-apply it. Child-facing copy, if any, is "let's do this one
  together for a while". No points change.
- **Related principles:** #12, #20
- **Affected documents:**
  [OWNERSHIP_MODEL §7](../experience/OWNERSHIP_MODEL.md),
  [CORE_PRINCIPLES #20 + Anti-Patterns](../product-foundation/CORE_PRINCIPLES.md),
  [CHILD_JOURNEY](../experience/CHILD_JOURNEY.md),
  [PARENT_JOURNEY](../experience/PARENT_JOURNEY.md)
- **Related GitHub issues:** #17, #11

### DECISION-011 — Ownership is never a KPI

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** QuestGrow must never create dashboards, scores, percentages,
  targets, rankings, or nudges built on ownership progress (e.g. "37% of your
  child's quests are Child-owned"), and must never optimise parents toward
  faster ownership transfer.
- **Why:** The moment ownership becomes a target, the product is back to
  "parent optimising child behaviour" — the exact failure mode it exists to
  avoid. The goal is that transfer happens naturally *when readiness exists*,
  not sooner.
- **Consequences:** Stage transition history is recorded (audit_log) for the
  parent's own reference only, never surfaced as a metric. Any proposal that
  turns ownership into a percentage/target/comparison is rejected.
- **Related principles:** #7, #13, #22
- **Affected documents:**
  [OWNERSHIP_MODEL §9](../experience/OWNERSHIP_MODEL.md),
  [CORE_PRINCIPLES → Anti-Patterns](../product-foundation/CORE_PRINCIPLES.md),
  [MVP → cross-cutting requirements](../product-delivery/MVP.md)
- **Related GitHub issues:** #17, #11

### DECISION-012 — Child-owned quests retain normal reward value

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** A quest is worth the same points whether it is `PARENT_GUIDED`
  or `CHILD_OWNED`. Ownership never reduces reward value. Only the *timing* of
  the reward changes across the two modes.
- **Why:** Devaluing a routine because the child mastered it punishes success
  and removes the incentive to keep doing it.
- **Consequences:** Points value is independent of `ownership_stage`
  (architecture invariant). `CHILD_OWNED` quests are still tracked, still in
  parent history, still contribute to Lifetime Achievement and progress.
- **Related principles:** #8, #11
- **Affected documents:**
  [OWNERSHIP_MODEL §3](../experience/OWNERSHIP_MODEL.md),
  [REWARD_MODEL](../game-design/REWARD_MODEL.md),
  [GAMIFICATION](../game-design/GAMIFICATION.md),
  [ARCHITECTURE → invariants](../product-delivery/ARCHITECTURE.md)
- **Related GitHub issues:** #17, #14

### DECISION-013 — Traditional streak mechanics are prohibited

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** QuestGrow does not use streaks. There is no counter that
  resets or "breaks" when a scheduled occurrence is missed, and no such
  counter is shown to the child or the parent.
- **Why:** Streaks convert "today I did a good thing" into "if I don't, I lose
  my chain" — loss-framed compulsion, the opposite of the intended
  motivation.
- **Consequences:** "Streaks, if shown at all…" was removed from GAMIFICATION.
  An anti-pattern was added to CORE_PRINCIPLES. Design and UX principles state
  the position explicitly.
- **Related principles:** #9, #12
- **Affected documents:**
  [GAMIFICATION](../game-design/GAMIFICATION.md),
  [CORE_PRINCIPLES → Anti-Patterns](../product-foundation/CORE_PRINCIPLES.md),
  [DESIGN_PRINCIPLES #6, #11](../experience/DESIGN_PRINCIPLES.md),
  [UX_PRINCIPLES → anti-patterns](../experience/UX_PRINCIPLES.md),
  [CHILD_JOURNEY](../experience/CHILD_JOURNEY.md)
- **Related GitHub issues:** #5

### DECISION-014 — Progressive consistency replaces streak mechanics

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** The weekly/consistency signal is *progressive consistency and
  ownership-oriented history* — counts that only ever describe what did
  happen: "You showed up 4 days this week", "You've kept this routine going
  this week", "You've been doing this one yourself for 12 days".
- **Why:** Consistency still matters and should be visible, but only in a form
  that has nothing to lose, break, or keep alive.
- **Consequences:** These signals must never create loss framing, never
  create variable-ratio reinforcement, never become a KPI, and never punish a
  missed occurrence — a quieter week simply shows a smaller number.
- **Related principles:** #8, #11, #12
- **Affected documents:**
  [GAMIFICATION → Progressive consistency, not streaks](../game-design/GAMIFICATION.md),
  [MVP → weekly progress](../product-delivery/MVP.md)
- **Related GitHub issues:** #5, #7

### DECISION-015 — Lifetime Achievement and Spendable Balance are separate concepts

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** Two distinct counters. **Lifetime Achievement** (Σ earn) only
  ever increases and drives long-term progression. **Spendable Balance**
  (Σ earn − Σ redeem ± adjustment) is what the child can spend and decreases
  on redemption.
- **Why:** "Points only go up" and "you spend points on rewards" are both
  true but of different quantities. Conflating them makes redemption feel like
  going backwards.
- **Consequences:** Both are projections over the append-only ledger, never
  stored mutable numbers. Redeeming a reward never reduces Lifetime
  Achievement. Long-term unlocks key off Lifetime Achievement.
- **Related principles:** #11, #12
- **Affected documents:**
  [REWARD_MODEL → two counters](../game-design/REWARD_MODEL.md),
  [GAMIFICATION → points and rewards](../game-design/GAMIFICATION.md),
  [ARCHITECTURE → progress ledger, invariants](../product-delivery/ARCHITECTURE.md),
  [PARENT_CHILD_MODEL → reward redemption](../trust-and-safety/PARENT_CHILD_MODEL.md)
- **Related GitHub issues:** #14, #8

### DECISION-016 — Parent authority and child agency coexist; the long-term direction is gradual transfer of ownership

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** Both roles are real: parents define the environment and hold
  authority over protected state; children act and own their accomplishment.
  The product's long-term direction is the gradual, parent-controlled transfer
  of ownership of everyday routines to the child.
- **Why:** Framing QuestGrow purely as "help parents make children comply"
  produces a behaviour-control tool. Framing it as gradual ownership transfer
  produces a healthier, larger product and makes "ages 3–8" meaningful.
- **Consequences:** This is the frame DECISION-001 through DECISION-012
  operationalise. It is expected to shape the forthcoming PRODUCT_VISION
  rewrite (not yet started).
- **Related principles:** #4, #13, #16, #20, #24
- **Affected documents:**
  [OWNERSHIP_MODEL §1](../experience/OWNERSHIP_MODEL.md),
  [CORE_PRINCIPLES D + E](../product-foundation/CORE_PRINCIPLES.md),
  [PARENT_CHILD_MODEL → roles](../trust-and-safety/PARENT_CHILD_MODEL.md)
- **Related GitHub issues:** #17, #1, #2

### DECISION-017 — Ownership advancement may skip stages in a single manual action

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** A parent may set a quest's `ownership_stage` to any later value
  in a single manual action; advancing by more than one stage at once is
  permitted. When more than one stage is bypassed, the confirmation names the
  bypassed stage(s). The app's automatic advancement *suggestion* still
  proposes exactly one stage at a time (DECISION-008). Regression to any
  earlier stage remains permitted (DECISION-010).
- **Why:** DECISION-016 gives the parent authority over "the pace of
  handover," and pace includes "immediately." Forbidding a parent from
  recording trust they already hold constrains parental authority for no
  developmental gain, and it is asymmetric with the already-free entry stage
  at assignment and the already-free any-to-any regression. The
  `CHILD_PARTICIPATED` review is optional (DECISION-006), so skipping it
  removes an option, not a safeguard; a premature grant is fully recoverable
  through neutral regression (DECISION-010).
- **Consequences:** The ownership-stage service accepts any `(from, to)`
  transition within parent scope, audit-logged, with `consecutive_ok_count`
  reset on every transition. Multi-stage advancement is a manual parent action
  only — never produced by the suggestion mechanism, never autonomous. The
  confirmation UI must name bypassed stages. Encoded in
  [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md) §3, §5, INV-6, AC-13,
  §10 (TOQ-1).
- **Related principles:** #4, #13, #16, #20
- **Affected documents:**
  [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md);
  [OWNERSHIP_MODEL §6](../experience/OWNERSHIP_MODEL.md) — compatible today
  ("advance earlier manually"); a later pass may make the multi-stage case
  explicit.
- **Related GitHub issues:** #17

### DECISION-018 — Expired scheduled occurrences are neutral for `consecutive_ok_count`

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** A scheduled `QuestInstance` that reaches `expired` without
  completion neither increments nor resets the internal
  `consecutive_ok_count`. Only a `completed` occurrence increments it; only a
  parent `not_yet` resets it to zero (DECISION-009); a non-scheduled day has
  no effect; any `ownership_stage` transition resets it. The counter remains
  internal and is never surfaced or framed as a streak (DECISION-013).
- **Why:** DECISION-013 states there is "no counter that resets or 'breaks'
  when a scheduled occurrence is missed," and DECISION-014 calls a missed
  scheduled day "a neutral non-event." Treating `expired` as a reset or a
  decrement would make the internal counter *behave* as exactly the
  streak-on-miss mechanic those decisions prohibit. The residual
  signal-accuracy concern (the app may suggest advancing a
  somewhat-unreliable routine) is covered by the parent always deciding
  (DECISION-008) and by free neutral regression if the trust proves premature
  (DECISION-010).
- **Consequences:** The advancement suggestion can fire after a window that
  includes missed days, provided there are `threshold` completed occurrences
  with no `not_yet` between them. Encoded in
  [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md) §4, INV-16, AC-14,
  §10 (TOQ-6).
- **Related principles:** #9, #12, #13
- **Affected documents:**
  [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md);
  [OWNERSHIP_MODEL §6](../experience/OWNERSHIP_MODEL.md) — its "8 consecutive …
  *completed*" wording leans the other way and should be reconciled to this
  decision in a later pass.
- **Related GitHub issues:** #17

### DECISION-019 — MVP is an on-ramp; `PARENT_MANAGED` is domain-valid but not MVP-assignable

- **Status:** Accepted
- **Date:** 2026-08-29
- **Decision:** For the MVP, QuestGrow is an **on-ramp** rather than a
  dedicated ~3–4-year-old experience. Every MVP quest begins at
  `PARENT_GUIDED`. `PARENT_MANAGED` remains a valid part of the domain model
  and the technical contract (its `PARENT_RECORDS` completion behaviour is
  defined) but is **not assignable or rendered through the MVP UI**. A
  dedicated `PARENT_MANAGED` / ~3–4 experience is post-MVP.
- **Why:** Building the `PARENT_MANAGED` surface (a parent-records flow and a
  distinct child-side "parent runs this" mode) is meaningful additional MVP
  scope whose value depends on whether ~3–4 is a first-class target.
  Deferring it keeps the MVP focused on the core `PARENT_GUIDED →
  CHILD_PARTICIPATED → CHILD_OWNED` loop while preserving the full four-stage
  contract for later.
- **Consequences:** This resolves the **MVP-scope aspect of OQ-A**. OQ-A's
  longer-term product-identity question — whether the ~3–4 experience becomes
  a complete product in its own right — **remains open**. OQ-B … OQ-H are
  untouched and remain open. The age-band → default-stage derivation still
  exists in the contract but yields `PARENT_GUIDED` for every MVP quest.
  Encoded in [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md) §3, §4,
  §9 (AC-11), §10 (TOQ-4), §12.
- **Related principles:** #16, #18, #20
- **Affected documents:**
  [TECHNICAL_MODEL](../architecture/TECHNICAL_MODEL.md),
  [MVP](../product-delivery/MVP.md) (already lists a dedicated `PARENT_MANAGED`
  UI as out of scope);
  [OWNERSHIP_MODEL "Open questions"](../experience/OWNERSHIP_MODEL.md) and
  [PRODUCT_VISION §13](../product-foundation/PRODUCT_VISION.md) — both still
  list OQ-A as flatly unresolved and must be reconciled to this decision in a
  later pass.
- **Related GitHub issues:** #17, #7

---

## Reserved for future entries

New durable decisions are appended with the next sequential id. Superseding a
decision keeps the old entry and sets its status to
*Superseded by DECISION-YYY*; it is not deleted.

# QuestGrow Ownership Model

> **Status:** Foundational. This is the central developmental arc of
> QuestGrow. It explains *why* verification, configuration visibility, and
> independence differ from one quest to another, and it is the model every
> other experience, game-design, trust, and delivery document defers to.
> Governed by [CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md)
> #4, #13, #16, #20.

## 1. Purpose

Ownership in QuestGrow is a **developmental arc, not a feature and not a
child-facing level system**. QuestGrow does not merely ask a child to perform
a task for points — over time it changes the child's *relationship* to that
task:

> "Mum said do it" → "I know I should do it" → "I do it myself" → "This one is
> mine."

This document defines that arc, how a quest moves along it, and the rules that
keep the movement healthy. It is the reason two quests for the same child can
have different verification behavior at the same moment. When another document
describes verification, reward timing, or "granting independence," it is
describing a consequence of where a quest sits on this arc — this model is the
source of truth for that behavior.

This model is **primarily a parent-/system-side construct.** The child is
never required to understand it (§8).

## 2. The arc

```
   PARENT_MANAGED
        ↓
   PARENT_GUIDED
        ↓
   CHILD_PARTICIPATED
        ↓
   CHILD_OWNED
```

This is **not** a child-facing progress bar, a score, or a badge track. It is
a lens for describing the gradual transfer of responsibility for an everyday
routine from parent to child. Movement is expected to be slow, uneven across
quests, and reversible (§6).

Relationship to the [Core Loop](../product-foundation/MANIFESTO.md): the loop
is unchanged at every stage — *parent defines quest → child sees quest → child
performs real-world action → child marks completion → (verification when
required) → celebration → progress updates*. Ownership only changes **whether
the verification step is present** and **how much configuration the child can
see**.

## 3. The four stages

Each stage is defined on concrete axes. Note that the **child-visible reward
timing** column collapses to only two values — this is deliberate (§4).

| Axis | PARENT_MANAGED | PARENT_GUIDED | CHILD_PARTICIPATED | CHILD_OWNED |
|---|---|---|---|---|
| **Who opens the app** | Parent | Parent hands over, or child | Child | Child |
| **Who marks the quest done** | Parent (on the child's behalf) | Child (self-marks) | Child (self-marks) | Child (self-marks) |
| **Verification** | n/a — parent is recording a real-world event they witnessed | **Required before reward is finalized** | None blocking; parent **may** review after the fact | None; no routine review nudge |
| **Child-visible reward timing** | Celebration when parent records it | **Do → wait for parent → celebrate** | **Do → celebrate immediately** | **Do → celebrate immediately** |
| **Points earned** | Normal | Normal | Normal | Normal — ownership never reduces reward value |
| **Config the child sees** | None (child may not interact at all) | None | None | None |
| **Typical use** | Very young child; a brand-new routine done together | The default working stage; routine is being learned | Routine is reliable; parent stays lightly in the loop | Routine is the child's own; parent trusts it |

### Stage notes

- **PARENT_MANAGED** must exist in the domain model. It does **not** need to
  be exposed in MVP UI; MVP may start every quest at **PARENT_GUIDED**
  ([MVP](../product-delivery/MVP.md)). It is mainly relevant to ~3–4 year
  olds and to routines a family is just beginning together.
- **PARENT_GUIDED** is the default. It is the only stage where the child ever
  waits for a parent before the celebration. This is the "trust before
  points" path ([CORE_PRINCIPLES #14](../product-foundation/CORE_PRINCIPLES.md),
  [VERIFICATION](../trust-and-safety/VERIFICATION.md)).
- **CHILD_PARTICIPATED**: the child gets an **immediate celebration every
  time**. The parent may look at what was completed afterward — a post-hoc
  glance, never a gate. See §5 for why this must never become variable-ratio
  reinforcement.
- **CHILD_OWNED**: identical to CHILD_PARTICIPATED for the child. The only
  difference is parent-side: no routine "want to check these?" nudge. The
  quest is still tracked, still in parent history, still earns normal points.

## 4. The child-facing experience has only two reward modes

Although there are four stages, the child ever experiences only:

- **Mode A — Do → wait for parent → celebrate.** (PARENT_GUIDED, and the
  parent-records case of PARENT_MANAGED.)
- **Mode B — Do → celebrate immediately.** (CHILD_PARTICIPATED, CHILD_OWNED.)

**Build one child UI driven by a single "instant reward vs. wait for parent"
bit.** Do not build four child-side variants. The richness of the four-stage
model lives entirely on the parent/system side. This keeps
[CORE_PRINCIPLES #1, #15, #16](../product-foundation/CORE_PRINCIPLES.md)
intact.

## 5. Spot-checking must never gate the reward

For CHILD_PARTICIPATED and CHILD_OWNED:

- The celebration is **immediate, every time**.
- Any parent review or spot-check happens **strictly after completion** and
  **never** delays, withholds, or probabilistically gates the celebration or
  the points.
- Spot-checking must **never** become variable-ratio reinforcement (random
  "sometimes you get checked, sometimes you don't, and the reward depends on
  it"). That pattern is banned by
  [CORE_PRINCIPLES #10](../product-foundation/CORE_PRINCIPLES.md) and
  [GAMIFICATION → banned mechanics](../game-design/GAMIFICATION.md).

A spot-check that finds a problem is handled like any parenting moment
(a conversation, optionally moving the quest back a stage per §6) — it does
**not** claw back the celebration the child already received.

## 6. Advancement

- The app **may suggest** advancement. The **parent confirms**. The app never
  advances a quest on its own.
- **Default suggestion trigger: 8 eligible scheduled occurrences** completed
  by the child without a "not yet" in between. "Eligible" means the occurrence
  was actually scheduled. Only a parent **"not yet"** resets the count; a
  skipped non-scheduled day has no effect, and a missed scheduled occurrence
  (one that `expired` without completion) is **neutral** — it neither
  increments nor resets ([DECISION-018](../governance/DECISION_LOG.md),
  INV-16). The window may therefore include missed days.
- **The number 8 is a tunable product default, not a hard-coded domain
  invariant.** It should live in configuration, be adjustable, and may be
  tuned per age band later.
- The parent may **advance earlier** manually — to any later stage, in a
  single action; when this bypasses one or more stages the confirmation names
  the bypassed stage(s) ([DECISION-017](../governance/DECISION_LOG.md)). The
  automatic *suggestion* still proposes exactly one stage at a time. The
  parent may also **dismiss** the suggestion (permanently or "ask me later").
- Suggestion copy is an invitation, not an announcement:
  > 🌱 Mia has been doing this herself for two weeks.
  > Ready to let her own this quest?
  > **[ Not yet ]  [ Let her own it ]**
- The system never says "Congratulations! Your child is now independent!" —
  ownership is a parenting decision, not a prize the app awards.

## 7. Regression

- Regression (moving a quest to an earlier stage) is **allowed** and
  **expected** — real life is not linear.
- It is **never punitive** and **never framed** as a downgrade, a lost level,
  or a failure — for the parent or the child
  ([CORE_PRINCIPLES #20 and Anti-Patterns](../product-foundation/CORE_PRINCIPLES.md)).
- **Parent-initiated primarily.** The app may *surface an observation*
  ("this one has been bumpy lately") but must **not prescribe** regression or
  do it automatically.
- Child-facing copy, if anything is shown at all, is a gentle invitation:
  > 🌱 Let's do this one together for a while.
  There is no "level decreased", no "you lost your independence", no negative
  animation, no points change.
- A regressed quest can advance again later through the normal §6 path.

## 8. The child never has to understand this model

- The child is **never required** to understand or even know about the four
  stages.
- The child UI exposes only the **simplest interaction appropriate to the
  current reward/verification mode** (§4): either "do it and wait for a
  grown-up" or "do it and celebrate".
- No stage names, no stage indicator, no "you are level 3 of 4" on the child
  side, ever.

## 9. Ownership is never a KPI

- QuestGrow must **never** create dashboards, scores, or nudges such as
  *"37% of your child's quests are Child-owned"* or *"move 3 more quests to
  independent this month"*.
- The product must **never optimize parents toward faster ownership
  transfer.** The goal is not speed; the goal is that *when and if* readiness
  exists, the transfer is **natural and visible**.
- Treat any proposal that turns ownership into a target, a percentage, a
  streak, or a comparison as a regression to the "parent optimizing child
  behavior" failure mode QuestGrow exists to avoid.

## 10. Relationship to age

The ownership arc is what makes **"ages 3–8"** mean something structural
rather than cosmetic:

- Age band provides a **default starting stage** per quest (younger →
  PARENT_MANAGED / PARENT_GUIDED; older → PARENT_GUIDED / CHILD_PARTICIPATED).
- After that, **each quest progresses independently** based on the child's
  actual behavior with *that* quest — not on a single child-level number.
- A 3-year-old may live mostly in PARENT_MANAGED / PARENT_GUIDED for years; an
  8-year-old may own most routines. This is an **independence axis**, not a UI
  skin. See [UX_PRINCIPLES → age adaptation](./UX_PRINCIPLES.md).

There is **no single `child.independenceLevel`.** Ownership is scoped to the
**(child × quest)** relationship (§ Data model below).

### The arc does not require fast — or any — handover

QuestGrow's value does **not** depend on a routine reaching `CHILD_OWNED`, or
on it moving quickly. Before any handover has occurred — and for a younger
child, that may be most routines for a long time — the product still delivers
its day-one value: a calm, clear, shared picture of the day; less ambiguity
about what is expected; and less daily conflict around routines. Ownership
transfer is the long arc layered on top of that value, not a precondition for
it. A parent who chooses to keep a given routine at `PARENT_MANAGED` or
`PARENT_GUIDED` indefinitely is using the product correctly.

*(Whether the ~3–4 experience is best understood as a complete product in its
own right or primarily an on-ramp where ownership is latent is an unresolved
open question — see below. This paragraph does not resolve it.)*

## 11. Downstream: how this wires in

| Document | What it takes from this model |
|---|---|
| [CHILD_JOURNEY](./CHILD_JOURNEY.md) | The two reward modes (§4); "wait for grown-up" appears only in Mode A |
| [PARENT_JOURNEY](./PARENT_JOURNEY.md) | Advancement suggestions, confirming/dismissing, regression, spot-check review — all parent-side |
| [QUEST_MODEL](../game-design/QUEST_MODEL.md) | `verification_required` / `self_mark_preauthorized` are **removed**; verification behavior is **derived** from `ownership_stage` on the (child, quest) pairing |
| [VERIFICATION](../trust-and-safety/VERIFICATION.md) | Verification exists **iff** stage is PARENT_GUIDED (or PARENT_MANAGED recording); spot-check rules (§5) |
| [REWARD_MODEL](../game-design/REWARD_MODEL.md) | Reward value is stage-independent; reward *timing* follows the two modes |
| [GAMIFICATION](../game-design/GAMIFICATION.md) | §5 spot-check ban reinforces "no variable-ratio"; ownership is not a horizon and not a KPI |
| [MVP](../product-delivery/MVP.md) | MVP may start quests at PARENT_GUIDED; PARENT_MANAGED modelled but not necessarily surfaced; advancement suggestion + parent confirm is in scope |
| [ARCHITECTURE](../product-delivery/ARCHITECTURE.md) | `(child, quest)` carries `ownership_stage`; verification path is computed from it; no contradictory boolean flags |
| [CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) | #20 extended (regression is not failure); Anti-Patterns extended (stage regression as downgrade) |

## Data model implication

The **(child, quest)** relationship must carry `ownership_stage` ∈
`{ PARENT_MANAGED, PARENT_GUIDED, CHILD_PARTICIPATED, CHILD_OWNED }`.

- Default value is derived from the child's age band at quest assignment.
- The previously proposed `quest.verification_required` and
  `quest.self_mark_preauthorized` fields are **removed**. Whether a completion
  needs verification is **computed** from `ownership_stage`:
  - `PARENT_MANAGED` → parent records; no child self-mark path
  - `PARENT_GUIDED` → completion enters `pending`, requires parent approval
  - `CHILD_PARTICIPATED` / `CHILD_OWNED` → completion is `verified`
    immediately; optional post-hoc parent review row (no gate)
- **Do not** store ownership state as a set of independent booleans that can
  contradict `ownership_stage`. `ownership_stage` is the single source.
- Advancement history (stage transitions, who initiated, when) is recorded in
  `audit_log` for the parent's own reference — **not** exposed as a child
  metric or a "progress" surface (§9).

See [ARCHITECTURE → data model](../product-delivery/ARCHITECTURE.md).

## Open questions (non-blocking)

Model-level:

- Whether the 8-occurrence trigger should vary by age band from day one, or
  ship as one global default and be tuned later.
- Whether CHILD_PARTICIPATED should offer the parent an *opt-in* periodic
  "review these?" nudge, or rely entirely on parent-initiated review.
- Exact wording and placement of the (rare) child-facing regression message,
  pending UX writing and testing with real families.

Product-thesis-level (surfaced by the skeptical validation passes; recorded
here so they are not silently decided elsewhere):

- **OQ-A** — Is the ~3–4 experience a complete product in its own right, or
  primarily an on-ramp where ownership is latent?
  *MVP-scope aspect: decided — [DECISION-019](../governance/DECISION_LOG.md)
  makes the MVP an on-ramp (MVP quests start at `PARENT_GUIDED`;
  `PARENT_MANAGED` is domain-valid but not MVP-assignable; a dedicated
  `PARENT_MANAGED` / ~3–4 experience is post-MVP). The long-term
  product-identity aspect — whether ~3–4 becomes a complete product in its own
  right — **remains open.***
- **OQ-B** — At ~7–8, does the model evolve toward a broader
  responsibility-management model?
- **OQ-C** — For `CHILD_OWNED` routines, should celebrations/rewards remain
  fully active (as `DECISION-012` currently states) or gradually wind down?
  `DECISION-012` stands; the tension with "the app may become less needed for
  owned routines" is **unresolved** and no wind-down rule is implied.
- **OQ-D** — Does ownership of individual routines generalise into a broader
  disposition toward responsibility?
- **OQ-E** — Should the product ever provide developmental framing/advice to
  parents?
- **OQ-F** — Is the mature state continued child engagement with QuestGrow, or
  the app fading from the child's routine entirely?
- **OQ-G** — Should the day-one framing lead with "less daily conflict /
  clarity" or with the longer-term ownership thesis?
- **OQ-H** — Should the vision explicitly claim decreasing parental
  involvement, or only increasing child ownership?

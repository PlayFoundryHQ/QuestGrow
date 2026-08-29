# QuestGrow Verification

How parent verification works, and why it is designed as part of the game
loop rather than administration. Governed by
[Core Principles D (13–16)](../product-foundation/CORE_PRINCIPLES.md),
especially **#14 (trust before points)** and **#15 (verification is part of
the game loop)**. The trust boundary it enforces is defined in
[PARENT_CHILD_MODEL](./PARENT_CHILD_MODEL.md).

**Whether a completion needs verification is derived from the quest's
ownership stage for that child** — verification is present only at
`PARENT_GUIDED` (and as parent-recording at `PARENT_MANAGED`). See
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).

## Why verification exists

A child must not be able to tap a button and award themselves points,
progress, rewards, or goal completion (anti-self-scoring). Verification is the
mechanism that keeps QuestGrow honest — but it must feel **natural, fast, and
positive** (Core Principle #15), never like a parent doing data entry.

## The completion → verification flow

```
Child marks quest done
        │
        ▼
 ownership_stage?
   │                         │
   CHILD_PARTICIPATED /       │ PARENT_GUIDED
   CHILD_OWNED                ▼
   ▼                  pending  ──► appears in parent Approvals queue
 verified                     │
   │              parent approves│         parent: "not yet"
   │                             ▼                 ▼
   │                          verified          available (optional gentle note)
   ▼                             │
 celebration + 1 ledger entry ◄──┘
```

(`PARENT_MANAGED`: the parent records the completion themselves; it goes
straight to `verified`. The child has no self-mark path at that stage.)

- **pending** — child sees a calm "waiting for grown-up" state (friendly
  sleeping icon). No points, no celebration yet. Not an error, not a failure.
- **approve** — one tap. Transitions to `verified`, which triggers the
  child's celebration (live if co-present, otherwise next time the child
  opens the app) and writes exactly one append-only `earn` ledger entry
  (idempotent). **The parent is handing the child a win.**
- **not yet** — declines *this instance only*. Quest returns to `available`.
  No penalty, no negative signal to the child, no points lost (points can't
  be lost). Optional parent note, worded as encouragement
  ("let's do it together"). Core Principle #12.

## Making verification feel like play, not admin (Core Principle #15)

- **A queue, not a form.** Approvals is a simple list: quest, child, when
  marked. One tap per decision.
- **Batchable.** "Approve all" for low-stakes quests so a parent clears the
  routine ones in one gesture.
- **Approval *is* the celebration trigger.** The parent's tap is what makes
  the child's celebration happen — the parent experiences themselves as the
  giver of the reward.
- **Fast.** The daily approvals pass should take seconds
  ([PARENT_JOURNEY](../experience/PARENT_JOURNEY.md),
  [DESIGN_PRINCIPLES → parent effort is a budget](../experience/DESIGN_PRINCIPLES.md)).
- **Quiet.** Optional, opt-in notification ("Mia marked 2 quests"),
  informational only — never "your child is waiting!" guilt framing.

## Which quests require verification

Not a direct toggle. Verification is present exactly when the quest's
`ownership_stage` for that child is `PARENT_GUIDED`
([OWNERSHIP_MODEL → the four stages](../experience/OWNERSHIP_MODEL.md)).
The parent influences it by moving the quest along the ownership arc, not by
flipping a "verification" flag.

- New routines and younger children start at `PARENT_MANAGED` /
  `PARENT_GUIDED` → verification (or parent recording) is present.
- Reliable routines and older children reach `CHILD_PARTICIPATED` /
  `CHILD_OWNED` → no blocking verification.

## Granting independence over time (Core Principle #20)

Granting independence **is** advancing the quest's ownership stage
(`PARENT_GUIDED` → `CHILD_PARTICIPATED` → `CHILD_OWNED`). The app may suggest
it after 8 consecutive eligible scheduled completions without a "not yet"
(a tunable default); the parent confirms. This is the deliberate,
parent-controlled act of trust that is the second (and only other) valid path
past anti-self-scoring.

At `CHILD_PARTICIPATED` and `CHILD_OWNED` the child's celebration is
**immediate every time**. Any parent spot-check happens **after** completion
and must **never** delay, withhold, or probabilistically gate the celebration
or points — that would be variable-ratio reinforcement, banned by
[CORE_PRINCIPLES #10](../product-foundation/CORE_PRINCIPLES.md). A spot-check
that finds a problem is a parenting conversation and, if needed, a stage
regression (never punitive) — it does not claw back a reward already given.

## Optional evidence (post-MVP)

A photo the child or parent attaches can *support* a verification decision. It
is:

- **never required**,
- **purpose-limited** — used for the pending decision, not retained as a
  monitoring history,
- **deletable**,
- **never** framed or used as surveillance (Core Principle #22).

Not in MVP. See [ROADMAP](../product-delivery/ROADMAP.md).

## Enforcement (not just UI)

- Child-mode clients hold a restricted `child:<childId>` token scope.
- The completion/verification service accepts a `completion_request` only for
  the child's own current quest instance, and only writes it to the
  child-writable intent table.
- The `verified` transition and the resulting ledger entry are produced
  **server-side**, only from either (a) parent approval (stage
  `PARENT_GUIDED`) or (b) the quest's `ownership_stage` for that child being
  `CHILD_PARTICIPATED` / `CHILD_OWNED` (the parent-authorized self-mark path).
- `ownership_stage` is stored on the `(child, quest)` pairing and is only
  writable in parent scope. Even a tampered client cannot self-verify, change
  its own stage, or write points.

See [ARCHITECTURE → completion / verification service](../product-delivery/ARCHITECTURE.md)
and [PARENT_CHILD_MODEL → enforced in the data model and services](./PARENT_CHILD_MODEL.md).

## Open questions

- Real-time delivery of `completion.verified` to a co-present child (push vs
  poll vs socket) — MVP can poll on app foreground.
- Parent-gate challenge design strong enough that a bright 7-year-old cannot
  trivially pass it, but low-friction for adults.

# QuestGrow Verification

How parent verification works, and why it is designed as part of the game
loop rather than administration. Governed by
[Core Principles D (13–16)](../product-foundation/CORE_PRINCIPLES.md),
especially **#14 (trust before points)** and **#15 (verification is part of
the game loop)**. The trust boundary it enforces is defined in
[PARENT_CHILD_MODEL](./PARENT_CHILD_MODEL.md).

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
 verification_required?
   │                 │
   no / pre-authorized│ yes
   ▼                  ▼
 verified          pending  ──► appears in parent Approvals queue
   │                             │
   │              parent approves│         parent: "not yet"
   │                             ▼                 ▼
   │                          verified          available (optional gentle note)
   ▼                             │
 celebration + 1 ledger entry ◄──┘
```

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

Per-quest parent setting (`verification_required`). Guidance and age
influence are in [QUEST_MODEL → verification-requirement guidance](../game-design/QUEST_MODEL.md).
Summary:

- Require verification when the outcome matters or self-report is unreliable,
  for younger children, and for new routines.
- Allow self-marking for low-stakes quests, older children, and
  well-established routines.

## Granting independence over time (Core Principle #20)

As a routine becomes reliable, the parent can switch a quest from
"requires verification" to self-mark (`self_mark_preauthorized = true`). This
is a deliberate, parent-controlled act of trust — the parent is
*pre-authorizing* that class of completion, which is the second (and only
other) valid path past anti-self-scoring. Periodic spot-checks are encouraged
for older children.

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
  **server-side**, only from either (a) parent approval or (b) a quest flagged
  `self_mark_preauthorized`.
- Even a tampered client cannot self-verify or write points.

See [ARCHITECTURE → completion / verification service](../product-delivery/ARCHITECTURE.md)
and [PARENT_CHILD_MODEL → enforced in the data model and services](./PARENT_CHILD_MODEL.md).

## Open questions

- Real-time delivery of `completion.verified` to a co-present child (push vs
  poll vs socket) — MVP can poll on app foreground.
- Parent-gate challenge design strong enough that a bright 7-year-old cannot
  trivially pass it, but low-friction for adults.

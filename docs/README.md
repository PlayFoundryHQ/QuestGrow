# QuestGrow Product Foundation

This folder is the **source of truth** for QuestGrow's product, UX, game
design, trust model, and architecture decisions.

**Read first:** [`product-foundation/MANIFESTO.md`](./product-foundation/MANIFESTO.md),
then [`product-foundation/CORE_PRINCIPLES.md`](./product-foundation/CORE_PRINCIPLES.md).

## Document map

```
docs/
├── product-foundation/     WHY QuestGrow exists and the rules it obeys
│   ├── MANIFESTO.md            Core philosophy, the core loop, hard boundaries
│   ├── CORE_PRINCIPLES.md      24 numbered principles — acceptance criteria for every decision
│   └── PRODUCT_VISION.md       Problem, vision, target users, success metrics
│
├── experience/             HOW it feels to use
│   ├── DESIGN_PRINCIPLES.md    Visual / interaction / system design rules
│   ├── UX_PRINCIPLES.md        Child & parent UX rules, screen inventories, age table
│   ├── OWNERSHIP_MODEL.md      Central developmental arc: PARENT_MANAGED → CHILD_OWNED
│   ├── CHILD_JOURNEY.md        The child's day-to-day journey and emotional arc
│   └── PARENT_JOURNEY.md       The parent's onboarding, daily, and weekly journeys
│
├── game-design/            HOW motivation and quests work
│   ├── GAMIFICATION.md         Four reward horizons, banned mechanics, tuning guardrails
│   ├── QUEST_MODEL.md          What a quest is, parent configuration, instances, age adaptation
│   └── REWARD_MODEL.md         Points, lifetime vs spendable, rewards, redemption modes
│
├── trust-and-safety/       HOW integrity and parent authority are guaranteed
│   ├── PARENT_CHILD_MODEL.md   Roles, trust boundary, completion state machine, anti-self-scoring
│   └── VERIFICATION.md         Verification as part of the game loop; enforcement; evidence
│
└── product-delivery/       WHAT gets built, and when
    ├── MVP.md                  The MVP capabilities, cross-cutting requirements, acceptance
    ├── ROADMAP.md              Post-MVP layers and how items enter the roadmap
    └── ARCHITECTURE.md         Services, data model, invariants, privacy posture
```

## The three layers, in one sentence each

1. **Product foundation** defines *why* QuestGrow exists and the non-negotiable
   rules every decision is judged against.
2. **Experience / game design / trust & safety** define *how* the product
   works and feels within those rules. The
   [Ownership Model](./experience/OWNERSHIP_MODEL.md) — the arc from
   parent-managed to child-owned routines — is the spine most of these
   documents hang from.
3. **Product delivery** defines *what* the first version builds and what comes
   next.

## Governing rule

Every significant QuestGrow product decision must be evaluated against
[CORE_PRINCIPLES](./product-foundation/CORE_PRINCIPLES.md). If a feature
violates a core principle, it requires an explicit product decision and
documented justification, citing the principle numbers. When any document
conflicts with CORE_PRINCIPLES or the MANIFESTO, those win — or they change,
deliberately and in writing, first.

## Related

- GitHub issues track each foundation area — see the `foundation` label.

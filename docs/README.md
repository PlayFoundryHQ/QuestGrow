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
├── product-delivery/       WHAT gets built, and when
│   ├── MVP.md                  The MVP capabilities, cross-cutting requirements, acceptance
│   ├── D1_ACCEPTANCE.md        The D1 end-to-end MVP acceptance run + readiness verdict
│   ├── E_READINESS.md          Phase E: browser/UX validation, product-readiness + Android-readiness assessment
│   ├── DEPLOYMENT.md           Phase F: how to run the backend — env config, migrations, restart-safety
│   ├── ROADMAP.md              Post-MVP layers and how items enter the roadmap
│   └── ARCHITECTURE.md         How the system is organised and built (construction)
│
├── architecture/           THE CONTRACT an implementation must obey
│   ├── TECHNICAL_MODEL.md      Domain concepts, state machines, authority matrix, INV-1…18, AC-1…15, TOQ dispositions
│   └── IMPLEMENTATION_NOTES.md Code → contract map; implementation-level notes (IL-*)
│
└── governance/             FROZEN DECISIONS + THE OPERATING CONSTITUTION
    ├── DECISION_LOG.md         DECISION-001 … DECISION-019 + reserved
    └── LEADERSHIP_PROTOCOL.md  Autonomous Engineering Leadership & Supervised Execution — authority boundary, source-of-truth tiers, phase loop, escalation/stop conditions
```

**MVP status.** Layer 0 is complete through **D1** (end-to-end MVP acceptance)
— see [`product-delivery/D1_ACCEPTANCE.md`](./product-delivery/D1_ACCEPTANCE.md).
Post-D1 work requires a new Product Owner grant per
[`governance/LEADERSHIP_PROTOCOL.md`](./governance/LEADERSHIP_PROTOCOL.md) §22.

**Implementation.** `src/questgrow/` is the MVP domain, written strictly
against `architecture/TECHNICAL_MODEL.md`; `tests/` holds the AC-1…15 and
INV-1…18 suites. See `architecture/IMPLEMENTATION_NOTES.md`.

## The layers, in one sentence each

1. **Product foundation** defines *why* QuestGrow exists and the non-negotiable
   rules every decision is judged against.
2. **Experience / game design / trust & safety** define *how* the product
   works and feels within those rules. The
   [Ownership Model](./experience/OWNERSHIP_MODEL.md) — the arc from
   parent-managed to child-owned routines — is the spine most of these
   documents hang from.
3. **Product delivery** defines *what* the first version builds and when.
4. **Architecture** (`architecture/`) is the contract an implementation must
   obey — domain, state machines, invariants, acceptance criteria — sitting
   between the ownership model and `product-delivery/ARCHITECTURE.md`.
5. **Governance** (`governance/`) holds the frozen record of durable decisions
   (`DECISION_LOG.md`, DECISION-001 … DECISION-019) and the
   [Leadership Protocol](./governance/LEADERSHIP_PROTOCOL.md) — the ratified
   constitution under which an autonomous leadership agent may execute the
   MVP-readiness arc (through D1) without per-action approval, while product
   authority stays with the Product Owner.

## Governing rule

Every significant QuestGrow product decision must be evaluated against
[CORE_PRINCIPLES](./product-foundation/CORE_PRINCIPLES.md). If a feature
violates a core principle, it requires an explicit product decision and
documented justification, citing the principle numbers. When any document
conflicts with CORE_PRINCIPLES or the MANIFESTO, those win — or they change,
deliberately and in writing, first.

## Related

- GitHub issues track each foundation area — see the `foundation` label.

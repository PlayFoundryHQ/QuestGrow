# QuestGrow Roadmap

Sequencing of what gets built, after the [MVP](./MVP.md). This is direction,
not commitment; every item must still pass the
[Core Principles decision rule](../product-foundation/CORE_PRINCIPLES.md)
before design begins.

## Layer 0 — MVP (see [MVP.md](./MVP.md))

Smallest system that demonstrates the QuestGrow philosophy: parent sets up a
child and quests, child does them in the real world and marks them,
verification gates meaningful state, daily + weekly progress, points-only
ledger, age adaptation in the data model. Mobile, single family, one language.

**Exit criteria:** the end-to-end acceptance checklist in
[MVP.md](./MVP.md#mvp-acceptance-the-loop-works-end-to-end) passes with a real
reference family.

## Layer 1 — Strengthen the core loop

- Real-time celebration delivery for a co-present child (replace foreground
  polling).
- Richer celebration / feedback variety (still consistent in warmth — Core
  Principle #10).
- Better weekly view and a gentle end-of-week acknowledgement.
- One basic milestone badge → a small set of milestone keepsakes.
- Parent onboarding polish; quest template refinement.
- Offline robustness hardening for the child flow.

## Layer 2 — Personalization & growth

- Deeper age adaptation: per-dimension parent overrides UI, smoother band
  transitions as the child grows (Core Principles #18, #20).
- Multi-step / sequenced quests for older bands
  ([QUEST_MODEL → age adaptation](../game-design/QUEST_MODEL.md)).
- Multiple children per account: full parent-side UX (data model already
  supports it).
- Custom quest art upload.

## Layer 3 — Optional long-term meta-game

Layered depth *for the child* that adds **no** child-side complexity or daily
obligation, all parent-toggleable
([GAMIFICATION → long-term](../game-design/GAMIFICATION.md),
[REWARD_MODEL](../game-design/REWARD_MODEL.md)):

- Collectible characters / companions
- Evolving world or garden driven by the lifetime counter
- Storybook pages unlocked by weeks of participation
- Seasonal / themed art

Guardrail: no scarcity-, streak-loss-, or comparison-driven mechanics ever
(Core Principles #9, #12; [GAMIFICATION → banned mechanics](../game-design/GAMIFICATION.md)).

## Layer 4 — Household & caregivers

- Multi-parent / second-caregiver accounts; limited "verifier" role
  ([PARENT_CHILD_MODEL → multi-parent](../trust-and-safety/PARENT_CHILD_MODEL.md)).
- Optional evidence photos for verification — purpose-limited, deletable,
  non-surveillance ([VERIFICATION → optional evidence](../trust-and-safety/VERIFICATION.md)).
- Web parent dashboard.

## Layer 5 — Reach

- Localization (structure must not preclude it from MVP).
- Additional platforms as warranted.
- Accessibility beyond baseline.

## Explicitly not on the roadmap

Per [MANIFESTO → what QuestGrow must never become](../product-foundation/MANIFESTO.md)
and [Core Principles F](../product-foundation/CORE_PRINCIPLES.md):

- Child-to-child social features or leaderboards
- Location tracking / geofencing / sensor auto-verification
- Screen-time enforcement / device locking
- Punitive or shame-based mechanics
- Ad-supported or engagement-maximizing monetization
- Any feature whose primary purpose is increasing app usage

## How items enter the roadmap

1. Proposed with a one-line statement of the real-world development it serves
   (Core Principle #24).
2. Run through the [decision rule](../product-foundation/CORE_PRINCIPLES.md#decision-rule).
3. If it conflicts with a core principle, the conflict and rationale are
   documented and consciously accepted — or the item is rejected with the
   principle numbers cited.
4. Tracked as a GitHub issue referencing the relevant foundation docs.

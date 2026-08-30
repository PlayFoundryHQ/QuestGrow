# QuestGrow Roadmap

Sequencing of what gets built, after the [MVP](./MVP.md). This is direction,
not commitment; every item must still pass the
[Core Principles decision rule](../product-foundation/CORE_PRINCIPLES.md)
before design begins.

## Layer 0 — MVP (see [MVP.md](./MVP.md))

Smallest system that demonstrates the QuestGrow philosophy: parent sets up a
child and quests, child does them in the real world and marks them,
verification (derived from the per-quest `ownership_stage`) gates meaningful
state, daily + weekly progress, an append-only ledger with the
**Lifetime Achievement / Spendable Balance** split, age adaptation in the data
model. Mobile, single family, one language.

**On-ramp scope ([DECISION-019](../governance/DECISION_LOG.md)).** The MVP is
an on-ramp: every MVP quest starts at `PARENT_GUIDED`; `PARENT_MANAGED`
remains a valid contract stage but is not assignable or rendered in the MVP
UI; a dedicated `PARENT_MANAGED` / ~3–4 experience is post-MVP.

**Progress.** Layer 0 is **complete through D1**. The domain layer
(`src/questgrow/`, AC-1…15 / INV-1…18) plus C1–C6 — persistence
(`SqliteRepository`), HTTP API (`api.py`), auth + PIN parent gate (`auth.py`),
notification transport (`events.py` / `notifications.py`), the
`complexityProfile` resolver + reference-client consumption, and the child and
parent reference web clients (`webclient/`) — all shipped under the
`MVP Implementation` milestone (issues #20–#26, #10, closed).

**Exit criteria — MET (2026-08-30).** The end-to-end acceptance checklist in
[MVP.md](./MVP.md#mvp-acceptance-the-loop-works-end-to-end) passes through the
full stack on a reference family — see
[`D1_ACCEPTANCE.md`](./D1_ACCEPTANCE.md) (`tests/test_d1_acceptance.py`, 17
checks). Residual: a human visual/UX confirmation pass on the two reference
clients (browser + offline toggle + reduced-motion) — a QA activity, not
construction. Post-D1 work requires a new Product Owner grant
([LEADERSHIP_PROTOCOL §22](../governance/LEADERSHIP_PROTOCOL.md)).

**Post-D1 (Product-Owner grants, shipped).** Phase E — readiness &
browser/UX validation ([`E_READINESS.md`](./E_READINESS.md)). Phase F —
production foundation hardening: portable SQLite/PostgreSQL persistence +
migrations, restart-safe ids, durable `SqlAuthStore` / `SqlEventSink`,
login/unlock rate-limiting, env config + `build_app`, and an additive `/v1`
API surface with structured error codes and list/detail endpoints
([`DEPLOYMENT.md`](./DEPLOYMENT.md)). Phase G — the **native Android client**
([`../../android/README.md`](../../android/README.md)), consuming the `/v1`
contract; no product-model change.

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
- Evolving world or garden driven by Lifetime Achievement
- Storybook pages unlocked by weeks of participation
- Seasonal / themed art

Guardrail: no scarcity-, streak-, or comparison-driven mechanics ever
(QuestGrow does not use streaks — Core Principles #9, #12;
[GAMIFICATION → progressive consistency](../game-design/GAMIFICATION.md)).

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

The governance flow that actually emerged during the foundation and
technical-model phases:

1. A capability or question is proposed with a one-line statement of the
   real-world development it serves (Core Principle #24).
2. It is run through the
   [decision rule](../product-foundation/CORE_PRINCIPLES.md#decision-rule).
3. If it settles a **durable product/behavioural/architectural** point, it is
   recorded in [`DECISION_LOG.md`](../governance/DECISION_LOG.md) as the next
   `DECISION-0xx` (via the Decision Panel review used for
   [DECISION-017…019](../governance/DECISION_LOG.md)); a conflict with a core
   principle is documented and consciously accepted, or the item is rejected
   citing principle numbers.
4. If it is a **technical realization** question, it is dispositioned as a
   TOQ in [`TECHNICAL_MODEL §10`](../architecture/TECHNICAL_MODEL.md), or an
   implementation-level ambiguity (IL-\*) in
   [`IMPLEMENTATION_NOTES.md`](../architecture/IMPLEMENTATION_NOTES.md).
5. The remaining work is tracked as a GitHub issue under the appropriate
   milestone, referencing the decision / TOQ / foundation doc it derives from.
   Issues are audited against repository state before a phase begins, not
   treated as permanent backlog.

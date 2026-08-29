# QuestGrow Architecture

Foundational architecture for the MVP. This is a product-foundation document,
not a final technical spec — it constrains engineering choices so they serve
the [Manifesto](../product-foundation/MANIFESTO.md),
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) (especially
#13–#16), and the [Parent–Child Model](../trust-and-safety/PARENT_CHILD_MODEL.md).

## Guiding constraints

1. **The trust boundary is architectural.** Anti-self-scoring and parent
   authority are enforced by the server and the data model, never only by the
   client UI.
2. **Child side is thin.** The child surface is a small, mostly-presentational
   client. Logic, scheduling, and validation live server-side.
3. **State that matters is append-only and server-written.** Points, progress,
   goal completion, and reward grants are ledger entries created by the
   server in response to validated events.
4. **Offline-tolerant capture, authoritative resolution.** The child can
   capture a completion offline; the server is the sole authority on whether
   it becomes verified and produces a ledger entry.
5. **Age adaptation is data, resolved once.** A profile's age band + overrides
   resolve to a complexity level that flows to the client as config.
6. **Verification is derived, not configured.** Whether a completion needs
   parent approval is computed from the `(child, quest)` `ownership_stage`
   ([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md)) — there is no
   independent `verification_required` flag to fall out of sync.

## High-level shape

```
┌───────────────────────────────────────────────┐
│  QuestGrow app (parent's device)              │
│                                               │
│   Child mode            Parent mode           │
│   - Today / do-it       - Dashboard           │
│   - Celebration         - Approvals           │
│   - Progress (view)     - Children / Quests    │
│                         - Rewards / Progress   │
│   restricted scope      full scope (post-gate) │
└───────────────┬───────────────────────────────┘
                │ HTTPS (scoped tokens)
┌───────────────┴───────────────────────────────┐
│  QuestGrow API                                 │
│   - AuthN/Z + capability scoping               │
│   - Quest & schedule service                   │
│   - Completion / verification service          │
│   - Progress ledger (append-only)              │
│   - Rewards service                            │
│   - Age-adaptation resolver                    │
│   - Ownership stage service                    │
│   - Notification service (opt-in)              │
└───────────────┬───────────────────────────────┘
                │
┌───────────────┴───────────────────────────────┐
│  Datastore (relational) + object store (art)   │
└───────────────────────────────────────────────┘
```

Single app binary, two modes. Mode switch is gated client-side by the parent
gate **and** reflected in the token scope issued to the client.

## Client

- Cross-platform mobile (framework choice deferred; iOS + Android required).
- **Child mode**: presentational. Fetches "today" (resolved quest instances +
  adaptation config), renders age variants, captures completion intents,
  plays celebrations on `verified` events. Holds a **child-scoped token**:
  read own today/progress; write only `completion_request` / reward-redemption
  intent for own profile.
- **Parent mode**: full CRUD behind the parent gate. Holds a **parent-scoped
  token** obtained by passing the gate; short-lived, re-challenged per policy.
- Local cache for offline: queued completion intents sync on reconnect;
  celebration/progress reconcile from server truth.

## API services

### Auth & authorization
- One account = one parent identity (email/OAuth). Children are sub-resources.
- Token scopes: `child:<childId>` (restricted) and `parent:<accountId>`
  (full). Every state-changing endpoint checks for `parent` scope; the one
  exception is a child `completion_request`, whose outcome is decided by the
  server from the `(child, quest)` `ownership_stage` (which only parent scope
  can write).
- The parent gate is a client-enforced challenge that unlocks retrieval of a
  parent-scoped token; policy controls re-challenge frequency.

### Quest & schedule service
- Stores quest definitions (parent-authored, arbitrary) and their schedules.
- Resolves schedules into **quest instances** per child per day
  ("what's due today").
- Edits are versioned/forward-applying; historical instances keep the
  definition they were created under.

### Completion / verification service
- Accepts `completion_request` from child scope for the child's own current
  quest instance.
- Reads the `ownership_stage` of the `(child, quest)` pairing:
  - `CHILD_PARTICIPATED` / `CHILD_OWNED` → transition instance to `verified`
    immediately and emit `completion.verified`.
  - `PARENT_GUIDED` → `pending`; surfaces in parent Approvals.
  - `PARENT_MANAGED` → no child self-mark path; the parent records the
    completion (→ `verified`).
- Parent action on `pending`: `approve` → `verified` + event; `not_yet` →
  `available` (+ optional note), no penalty.
- `verified` transition is idempotent and produces **exactly one** ledger
  entry per completion.
- For `CHILD_PARTICIPATED` an optional non-blocking `parent_review` row may be
  created for the parent's later glance — it never gates the reward.

### Ownership stage service
- Stores `ownership_stage` per `(child, quest)`; writable only in parent
  scope.
- Tracks a per-pairing counter of consecutive eligible scheduled completions
  without a `not_yet`; at the configured threshold (**default 8, tunable**)
  emits an advancement *suggestion* for the parent. Never advances on its own.
- Records every stage transition (old, new, initiator, timestamp) to
  `audit_log`. This history is **not** exposed as a child-facing metric or a
  "progress" surface ([OWNERSHIP_MODEL §9](../experience/OWNERSHIP_MODEL.md)).

### Progress ledger (append-only)
- Immutable entries: `{ childId, source(completionId/adjustment), points,
  timestamp }`.
- Only the server writes entries, only in response to a `completion.verified`
  event (or an explicit parent adjustment, which is additive-only in MVP).
- Balances / daily / weekly views are **projections** over the ledger, not
  stored mutable counters.
- Separate **Lifetime Achievement** projection (never decremented by
  redemptions) vs **Spendable Balance** projection.

### Rewards service
- Parent-defined rewards: cost, redemption mode (`self_service` /
  `parent_confirmed`).
- `self_service`: child intent → server checks spendable balance → writes a
  redemption ledger entry (negative on spendable, no effect on lifetime) →
  notifies parent.
- `parent_confirmed`: child intent → `pending_reward` → parent grants.

### Age-adaptation resolver
- Input: child birthdate/explicit band + per-dimension overrides.
- Output: a `complexityProfile` (band + resolved values for vocabulary, text
  amount, iconography, interaction complexity, task complexity, reading
  requirement, reward presentation, independence level).
- Delivered with the "today" payload; client selects component variants from
  it. No age logic branches scattered in the client.

### Notification service
- Opt-in only. Informational templates ("Mia marked 2 quests"). No
  re-engagement or loss-framed messages. Never targets the child.

## Data model (core entities, MVP)

- **account** — parent identity, settings, parent-gate config.
- **child** — `accountId`, name, avatar, birthdate/band, adaptation overrides.
- **quest** — `accountId`, title, icon/art ref, points, age suitability,
  active/archived, version. *(No `verification_required` /
  `self_mark_preauthorized` — removed; see `child_quest`.)*
- **quest_schedule** — `questId`, recurrence (days, weekly/daily), time window.
- **child_quest** — the `(childId, questId)` pairing: `ownership_stage`
  (`PARENT_MANAGED` / `PARENT_GUIDED` / `CHILD_PARTICIPATED` / `CHILD_OWNED`,
  default derived from age band), `consecutive_ok_count`, timestamps.
  Writable only in parent scope. Verification behavior is **computed** from
  `ownership_stage` — never stored as a separate boolean.
- **quest_instance** — `questId@version`, `childId`, date, state
  (`available` / `pending` / `verified` / `not_yet` / `expired`),
  `stage_at_completion`, timestamps.
- **completion_request** — `questInstanceId`, `childId`, createdAt, optional
  note/evidence ref. (Child-writable intent table.)
- **parent_review** — optional, non-blocking; `questInstanceId`, `childId`,
  parent note, timestamp. For `CHILD_PARTICIPATED` after-the-fact glances.
- **ledger_entry** — append-only; `childId`, source, points (+/−), kind
  (`earn` / `redeem` / `adjustment`), timestamp. Server-writable only.
- **reward** — `accountId`, name, icon, cost, redemption mode, active.
- **reward_redemption** — `rewardId`, `childId`, state, timestamps.
- **audit_log** — parent actions on meaningful state.

### Invariants

- No client writes to `ledger_entry`, `quest`, `quest_schedule`,
  `child_quest` (incl. `ownership_stage`), `quest_instance.state` (except
  child → `pending`/`verified` via the completion flow, as the server
  decides), `reward`, or `child`/`account` outside parent scope.
- `ownership_stage` is the **single source** for verification behavior; no
  contradictory boolean flag exists anywhere.
- One `ledger_entry` of kind `earn` per `verified` completion (idempotency
  key = completionId).
- **Lifetime Achievement** `= Σ earn` (monotonic non-decreasing);
  **Spendable Balance** `= Σ earn − Σ redeem ± adjustment`; both derived,
  never stored mutable. Points value of a quest does not depend on
  `ownership_stage`.
- A child can read/write intent only for their own `childId`.

## Privacy & safety

- Data minimization: collect only what a routine game needs. Birthdate may be
  stored as a coarse band if the parent prefers.
- No location, no contacts, no analytics SDKs that profile the child.
- Optional evidence photos (post-MVP) are purpose-limited and deletable; not
  retained as a monitoring history.
- COPPA / GDPR-K posture: parent is the account holder and consent authority;
  child has no independent account, no PII beyond first name + avatar +
  age band required.
- All traffic TLS; tokens short-lived; child-scoped tokens cannot be
  escalated without passing the parent gate.

## Deferred / future

Multi-parent & verifier roles; web parent dashboard; long-term meta-game
services (characters/worlds/stories); reward marketplace; localization;
custom art pipeline; parent-side analytics beyond daily/weekly projections.

## Open questions

- Client framework (RN / Flutter / native) — decide against team skills +
  animation quality needs.
- Backend stack and hosting — decide against team skills; keep the service
  boundaries above regardless.
- Real-time delivery of `completion.verified` to a co-present child (push vs
  poll vs socket) — MVP can poll on app foreground.
- Exact parent-gate challenge design for the 3–8 context (adult friction that
  a bright 7-year-old cannot trivially pass).
- Whether the 8-occurrence advancement threshold varies by age band from day
  one or ships as one global tunable default
  ([OWNERSHIP_MODEL open questions](../experience/OWNERSHIP_MODEL.md)).

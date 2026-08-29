# QuestGrow Architecture

**How the QuestGrow system is organised and built.** What the system must
*guarantee* — the domain entities and their write-authority, the ownership
state machine, the authority/actor matrix, verification derivation, reward
semantics, and the invariants — is defined in
[`TECHNICAL_MODEL.md`](../architecture/TECHNICAL_MODEL.md). Where this
document previously stated contract-level rules, they now live there and are
referenced from here.

This document serves the [Manifesto](../product-foundation/MANIFESTO.md),
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) (especially
#13–#16), the [Parent–Child Model](../trust-and-safety/PARENT_CHILD_MODEL.md),
and the [Technical Model](../architecture/TECHNICAL_MODEL.md).

## Guiding constraints

1. **The trust boundary is architectural.** Anti-self-scoring and parent
   authority are enforced by the server and the persistence layer, never only
   by the client UI. The authority rules are the
   [actor matrix](../architecture/TECHNICAL_MODEL.md) (`TECHNICAL_MODEL §5`).
2. **Child side is thin.** The child surface is a small, mostly-presentational
   client. Scheduling, validation, and every state transition live
   server-side.
3. **State that matters is append-only and server-written.** See
   `TECHNICAL_MODEL §6–§7` (progress ledger, authoritative state vs
   projections).
4. **Offline-tolerant capture, authoritative resolution.** The child can
   capture a completion offline; the server is the sole authority on whether
   it becomes `verified` and produces a ledger entry (`TECHNICAL_MODEL §4`).
5. **Age adaptation is data, resolved once.** A profile's age band + overrides
   resolve to a `complexityProfile` that flows to the client as config.
6. **Verification is derived, not configured.** Whether a completion needs
   parent approval is a pure function of the `(child, quest)` `ownership_stage`
   (`TECHNICAL_MODEL §4`, INV-4) — there is no stored verification flag to
   fall out of sync.

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

Each service below is a **module boundary**. The rules it enforces are the
contract in [`TECHNICAL_MODEL.md`](../architecture/TECHNICAL_MODEL.md); this
section only records how responsibility is decomposed.

### Auth & authorization
- One account = one parent identity (email/OAuth). Children are sub-resources.
- Token scopes: `child:<childId>` (restricted) and `parent:<accountId>`
  (full). The authority rules — including the single child-intent exception —
  are `TECHNICAL_MODEL §5` (actor matrix). No parent authority moves with
  `ownership_stage` (INV-17).
- The parent gate is a client-enforced challenge that unlocks retrieval of a
  parent-scoped token; policy controls re-challenge frequency.

### Quest & schedule service
- Stores quest definitions (parent-authored, arbitrary) and their schedules.
- Resolves schedules into **quest instances** per child per day
  ("what's due today"). MVP materialises instances **eagerly** per
  `(quest@version, child, date)`; a lazy strategy may be revisited only on
  scale evidence (`TECHNICAL_MODEL §10` / TOQ-7).
- Edits are versioned/forward-applying; historical instances keep the
  definition they were created under.

### Completion / verification service
- Owns the `QuestInstance` state machine and the `verification_behaviour`
  derivation — see `TECHNICAL_MODEL §4` (and INV-10, INV-11, INV-15).
- Accepts a `CompletionRequest` from child scope for the child's own current
  quest instance (intent only); the server decides the resulting state from
  the `(child, quest)` `ownership_stage`.
- A non-blocking `parent_review` row may be created for a `verified`
  completion at `CHILD_PARTICIPATED` / `CHILD_OWNED`; it never gates the
  reward (INV-15).

### Ownership stage service
- Owns `ChildQuest.ownership_stage` and `consecutive_ok_count`. The transition
  rules (parent-scope-only, never autonomous, adjacency question,
  suggestion threshold) are `TECHNICAL_MODEL §3` (and INV-5, INV-6, INV-16).
- Emits an advancement **suggestion** at the configured threshold
  (**default 8, tunable**); it never changes state itself.
- Records every stage transition to `audit_log` with the parent as initiator;
  this history is **not** a child-facing or ownership-progress surface
  (INV-8, INV-9).

### Progress ledger (append-only)
- Owns `LedgerEntry`. Rules — append-only, server-written only, one `earn`
  per `verified` completion, projections not stored counters, Lifetime
  Achievement vs Spendable Balance — are `TECHNICAL_MODEL §6–§7`
  (and INV-11, INV-12, INV-13, INV-14).

### Rewards service
- Owns `Reward` and `RewardRedemption`. Redemption modes (`self_service` /
  `parent_confirmed`) and the "redeem affects Spendable Balance only" rule
  are `TECHNICAL_MODEL §6`. No wind-down rule for `CHILD_OWNED` routines
  (OQ-C unresolved).

### Age-adaptation resolver
- Input: child birthdate/explicit band + per-dimension overrides.
- Output: a `complexityProfile` — the field list, per-band resolved values,
  and override rules are the contract in
  [`TECHNICAL_MODEL §13`](../architecture/TECHNICAL_MODEL.md). Delivered with
  the "today" payload; the client selects component variants from it, with no
  age logic scattered in the client.
- Age band also sets the **default** `ownership_stage` for a newly assigned
  quest — a server-side derivation stored on the `ChildQuest`, never part of
  the client-facing `complexityProfile` (INV-8; `TECHNICAL_MODEL §10` /
  TOQ-9). In MVP the default is always `PARENT_GUIDED` (`TECHNICAL_MODEL §3`).

### Notification service
- Opt-in only. Informational templates ("Mia marked 2 quests"). No
  re-engagement or loss-framed messages. Never targets the child.

## Data model

The authoritative domain entities, their identity, and **who may write each**
are defined in
[`TECHNICAL_MODEL.md §2`](../architecture/TECHNICAL_MODEL.md) (Domain
concepts), with the state machines in §3–§4 and the split between
authoritative state and projections in §7.

The **persistence implementation** — storage engine, table layout, indexes,
partitioning — is an open construction question (see below). Instance
materialisation is eager for MVP and idempotency is anchored on the
`QuestInstance` identity (`TECHNICAL_MODEL §10`, TOQ-7 / TOQ-3). It must
realise §2–§8 of the technical model without introducing any stored value
that can drift from an authoritative source (no `balance`,
`verification_required`, `independence_level`, `owned_routine_count`, or
`streak` column).

## Invariants

The machine-checkable invariants an implementation must uphold — INV-1 …
INV-18 — are defined in
[`TECHNICAL_MODEL.md §8`](../architecture/TECHNICAL_MODEL.md), with testable
acceptance criteria in §9 and traceability to DECISION-001 … DECISION-016 in
§12.

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

Multi-parent & verifier roles; production (non-reference) web/mobile clients;
long-term meta-game
services (characters/worlds/stories); reward marketplace; localization;
custom art pipeline; parent-side analytics beyond daily/weekly projections.

## Stack (C0 — technical construction decision, not a product decision)

Decided for the MVP implementation stack. Time-boxed; revisable without
touching the contract (`TECHNICAL_MODEL`).

| Layer | Choice | Rationale |
|---|---|---|
| Language | **Python 3.11+** | the `a0b538c` domain is already stdlib Python; keep it |
| Web framework | **FastAPI** (+ `uvicorn`) | typed request/response models → the §5 scope model and INV-8 boundary are enforceable as schemas; auto OpenAPI |
| Persistence | **SQLite** for dev / reference / D1; **Postgres-portable schema** (plain SQL, no ORM lock-in) | zero-ops for the acceptance run; the `Repository` protocol keeps the engine swappable (TOQ-7) |
| Auth | email + password (hashed, `argon2`/`pbkdf2`), server-issued **scoped tokens**; **parent gate = PIN** | smallest thing that realises the §5 actor matrix; challenge design can harden later |
| Real-time | **poll** `completion.verified` on app foreground (MVP) | `ARCHITECTURE` already allows this; socket/push is Layer 1 |
| Client (MVP-readiness) | **thin reference web clients** — one child, one parent — sufficient to run every `MVP.md` acceptance item for D1 | a production **mobile** (iOS + Android) client is a distinct track *on the same API*, post-readiness; not built in the MVP-implementation phase |

The 8-occurrence advancement threshold ships as **one global tunable default**
(`OWNERSHIP_MODEL` open question — the deferred option); per-band tuning stays
post-MVP.

## Open questions (remaining, post-MVP)

- Production mobile client framework (RN / Flutter / native).
- Hosting / deployment topology.
- Socket/push real-time delivery (Layer 1).
- Hardened parent-gate challenge design for the 3–8 context.
- Per-age-band tuning of the advancement threshold
  ([OWNERSHIP_MODEL open questions](../experience/OWNERSHIP_MODEL.md)).

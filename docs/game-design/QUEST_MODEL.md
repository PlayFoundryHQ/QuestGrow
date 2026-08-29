# QuestGrow Quest Model

Defines what a "quest" is, how parents configure it, and how it turns into
something the child does today. Governed by
[Core Principles E (17–20)](../product-foundation/CORE_PRINCIPLES.md) and
[#21 (not a checklist)](../product-foundation/CORE_PRINCIPLES.md).

Whether a completion needs verification is **not** a quest field — it is
**derived from the quest's ownership stage** for that child. See
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).

## Principle: the catalog is not fixed

There is **no built-in list** of "correct" quests. Parents define goals that
fit their family. Templates exist only as one-tap starting points and are
fully editable and removable (Core Principles #17, #19).

Starter templates (illustrative, not prescriptive): brushing teeth, getting
dressed, making the bed, tidying, reading, drawing, bathing, outdoor activity,
helping with household tasks, preparing for school, eating meals.

## Principle: quests, not checkboxes

A quest is framed as a meaningful thing to do, carried visually (Core
Principles #2, #21). The real-world action is the point; the app interaction
is a thin wrapper around it (Core Principle #5). See
[CHILD_JOURNEY](../experience/CHILD_JOURNEY.md).

## Quest definition (parent-authored)

| Field | Notes | Default |
|---|---|---|
| `title` | Short, parent's words | — (required) |
| `icon` / `art ref` | From starter asset set; custom art post-MVP | — (required) |
| `points` | Parent-configurable; can be disabled account-wide | template default |
| `age_suitability` | Informational age-band hint | all bands |
| `active` / `archived` | Soft lifecycle | active |
| `version` | Edits are forward-applying; historical instances keep their version | 1 |

Minimum to create a working quest: `title` + `icon`. Everything else has a
default ([PARENT_JOURNEY](../experience/PARENT_JOURNEY.md)).

There is **no** `verification_required` or `self_mark_preauthorized` field.
Verification behavior is computed from the `ownership_stage` of the
**(child, quest)** pairing
([OWNERSHIP_MODEL → data model implication](../experience/OWNERSHIP_MODEL.md)):

| `ownership_stage` | Completion behavior |
|---|---|
| `PARENT_MANAGED` | Parent records the completion; no child self-mark path |
| `PARENT_GUIDED` | Child self-marks → `pending` → parent approval finalizes reward |
| `CHILD_PARTICIPATED` | Child self-marks → `verified` immediately; optional post-hoc parent review (no gate) |
| `CHILD_OWNED` | Child self-marks → `verified` immediately; no routine review nudge |

## Schedule (`quest_schedule`)

| Field | Notes |
|---|---|
| Recurrence | Daily, or specific weekdays; weekly quests supported |
| Time window | Optional (e.g. "morning", "before bed") — informational in MVP |
| Start / end | Optional bounds |

The **quest & schedule service** resolves schedules into **quest instances**
per child per day — "what's due today"
([ARCHITECTURE](../product-delivery/ARCHITECTURE.md)).

## Quest instance (`quest_instance`)

One row per (quest version, child, date). Holds the state machine:

```
available ──child marks done──► (ownership_stage?)
   ▲                               │ CHILD_PARTICIPATED / CHILD_OWNED → verified
   │                               │ PARENT_GUIDED → pending ──parent approves──► verified
   │                               │                        └─parent "not yet"──► available
   └───────────────────────────────┘ PARENT_MANAGED → parent records → verified
                              (end of day, incomplete) → expired / rollover (no penalty)
```

States: `available`, `pending`, `verified`, `not_yet`, `expired`. Full
semantics and the anti-self-scoring rule live in
[PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md); verification
UX in [VERIFICATION](../trust-and-safety/VERIFICATION.md).

Only a `verified` transition produces a points/progress ledger entry, exactly
once ([REWARD_MODEL](./REWARD_MODEL.md),
[GAMIFICATION](./GAMIFICATION.md)).

## Ownership stage (replaces per-quest verification config)

Each quest sits at an `ownership_stage` **per child**. The parent does not
toggle "verification" directly — they move a quest along the ownership arc,
and verification behavior follows
([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md)).

- **Default stage** is derived from the child's age band when the quest is
  assigned (younger → `PARENT_MANAGED` / `PARENT_GUIDED`; older →
  `PARENT_GUIDED` / `CHILD_PARTICIPATED`). MVP may start every quest at
  `PARENT_GUIDED`.
- **Advancement** is app-suggested / parent-confirmed (default trigger: 8
  consecutive eligible scheduled occurrences without a "not yet"; the 8 is a
  tunable product default).
- **Regression** is allowed, parent-initiated, and never framed as failure
  ([CORE_PRINCIPLES #20](../product-foundation/CORE_PRINCIPLES.md)).
- Quests progress **independently** — a child can own one routine while still
  being guided on another. There is no child-level independence score.

## Age adaptation of quests (Core Principle #18)

The same quest renders differently by the child's resolved complexity level:

- **Task complexity** — a single-step quest for ~3–4 may be a small multi-step
  sequence for ~7–8 (e.g. "get dressed" → "get dressed: socks, shirt,
  trousers, shoes").
- **Reading requirement** — icon-only vs short label vs short sentence.
- **Independence level** — expressed through the quest's `ownership_stage`
  (§ Ownership stage), which age sets a default for.

Adaptation is delivered as resolved data with the daily payload; the client
picks component variants (no scattered age branching). See
[UX_PRINCIPLES → age adaptation](../experience/UX_PRINCIPLES.md) and
[ARCHITECTURE → age-adaptation resolver](../product-delivery/ARCHITECTURE.md).

## Editing quests (Core Principle #20)

- Edits create a new `version`; existing instances keep the version they were
  created under, so history stays coherent.
- Archiving hides a quest going forward without destroying history
  (soft-delete preferred).
- Parents can change goals freely as the child grows — this is expected, not
  an edge case.

## Out of scope for MVP

Custom art upload; quest dependencies / prerequisites; shared quest libraries
between families; location- or sensor-based auto-verification (would risk
Core Principle #22 — surveillance). See
[ROADMAP](../product-delivery/ROADMAP.md).

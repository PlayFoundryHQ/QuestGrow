# QuestGrow UX Principles

This document translates the [Design Principles](./DESIGN_PRINCIPLES.md) —
themselves derived from
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) — into concrete UX
rules for each side of the product. The end-to-end journeys are in
[CHILD_JOURNEY](./CHILD_JOURNEY.md) and [PARENT_JOURNEY](./PARENT_JOURNEY.md).

## Child experience

### Mental model

The child sees a small number of "things to do today," each shown as a big
friendly picture. They do the real thing. They tap the picture to say "I did
it." Sometimes a grown-up needs to say yes. Then something happy happens and
their progress grows.

### Screen inventory (child side, MVP)

1. **Today** — today's quests as large cards/tiles. Primary screen.
2. **Quest detail / do-it** — one quest, big art, a single "I did it" action.
3. **Celebration** — full-screen animated reward after a valid completion.
4. **Progress** — today's fill + a simple weekly view (stars/blocks, not
   charts).

No menus, no settings, no search, no lists longer than the day's quests, no
back-stack deeper than 2.

### Interaction rules

- Every primary action is a single large tap or a simple drag.
- Touch targets ≥ 64×64 pt with generous spacing.
- Max 1 primary action per screen; secondary actions are visually minor or
  absent.
- Interactions complete in seconds. If the child is on-screen longer than
  ~30s in one sitting during normal use, the design has failed.
- All text has an audio equivalent (auto-play optional, tap-to-hear always).
- Animations are joyful but bounded (1–3s), and respect reduced-motion.

### States, child-visible

- **Available** — quest is ready to do.
- **Pending** — "waiting for grown-up" — shown as a calm, friendly waiting
  state (e.g. a sleeping icon), never an error.
- **Done** — celebrated, then shown as a happy completed state for the rest
  of the day.
- There is no "failed," "late," or "missed" state on the child side.

### What the child can never do

Change points, rewards, goals, schedules, ownership stages, age settings, or
another child's data; approve their own pending quests; see verification
controls or stage names; navigate into the parent app without the parent
gate.

## Parent experience

### Mental model

The parent runs the game: sets up each child, defines quests and when they
recur, moves each quest along the ownership arc (which decides whether it
needs verification — [OWNERSHIP_MODEL](./OWNERSHIP_MODEL.md)), sets rewards,
and does a quick daily pass to approve pending completions and glance at
progress.

### Screen inventory (parent side, MVP)

1. **Home / dashboard** — per child: today's progress, pending approvals count.
2. **Approvals** — queue of pending completions; approve / not-yet, batchable.
3. **Children** — create/edit child profile (name, avatar, birthdate/age band,
   adaptation overrides).
4. **Quests** — create/edit quests: title, icon, art, schedule (days/times),
   points/reward, age band suitability, active toggle, and per-child
   ownership stage (with advancement suggestions). No standalone
   "verification" flag — it follows the ownership stage.
5. **Rewards** — define rewards and how points map to them.
6. **Progress** — daily and weekly history per child.
7. **Settings** — account, notifications (off by default), parent gate.

### Interaction rules

- Rich forms, lists, and multi-step flows are acceptable here.
- Sensible defaults everywhere: a parent can create a working quest with a
  title and an icon; everything else has a default.
- Destructive actions (delete child, delete quest with history) confirm and
  prefer soft-delete / archive.
- The parent gate (PIN / biometric / math challenge) protects entry to the
  parent app and any escalation from the child app.

### Verification UX

- Only `PARENT_GUIDED` quests generate approvals; `CHILD_PARTICIPATED` /
  `CHILD_OWNED` completions never wait on the parent.
- A pending completion appears in **Approvals** and (optionally) as a quiet
  notification.
- Approving is one tap and **immediately triggers the child's celebration**
  the next time the child opens the app (or live, if co-present).
- "Not yet" is gentle: it returns the quest to available with an optional
  parent note ("let's do it again together"), never a penalty.
- Approvals are batchable ("approve all") for low-stakes quests.
- An **ownership advancement suggestion** ("ready to let her own this quest?")
  may appear here; it is an invitation with [Not yet] / [Let her own it],
  never an obligation ([OWNERSHIP_MODEL §6](./OWNERSHIP_MODEL.md)).

## Age adaptation (both sides, child-facing impact)

Age band is derived from the child's birthdate or set explicitly, with
per-dimension parent overrides. Bands are guidance, not hard gates.

| Dimension | ~3–4 | ~5–6 | ~7–8 |
|---|---|---|---|
| Text | Icon-only, audio | Short labels + audio | Short sentences |
| Quests shown at once | 1–3 | 3–5 | 5–7 |
| Interaction | Single tap | Tap, simple drag | Tap, drag, ordering |
| Task complexity | Single-step quests | Small multi-step | Multi-step, sequences |
| Reading required | None | Minimal | Light |
| Reward presentation | Big immediate animation | Animation + progress | Progress, collectibles, stories |
| Default ownership stage | `PARENT_MANAGED` / `PARENT_GUIDED` | `PARENT_GUIDED` | `PARENT_GUIDED` / `CHILD_PARTICIPATED` |

Components are built as variants keyed on the band / a derived complexity
level — never a single fixed layout. See
[GAMIFICATION](../game-design/GAMIFICATION.md) and [ARCHITECTURE](../product-delivery/ARCHITECTURE.md).

## Anti-patterns (never ship)

Infinite scroll or endless content; browsable content galleries in the child
flow; streaks of any kind (no breakable consecutive-day counter — use
progressive consistency instead) or countdown pressure; push notifications on
by default; leaderboards or child-to-child comparison; "keep playing" nudges;
ads; dark-pattern confirmshaming; any child-side path to changing meaningful
state.

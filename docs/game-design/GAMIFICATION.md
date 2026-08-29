# QuestGrow Gamification Model

Gamification in QuestGrow exists to make real-world routine feel rewarding and
to build intrinsic habit over time. It must never become an end in itself or a
driver of screen time or compulsion.

Governed by [CORE_PRINCIPLES C (9–12)](../product-foundation/CORE_PRINCIPLES.md).
Points and rewards mechanics are detailed in
[REWARD_MODEL](./REWARD_MODEL.md); quests in [QUEST_MODEL](./QUEST_MODEL.md).
Reward *timing* follows the two child reward modes in
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md); the ownership arc is
**not** a reward horizon and **never** a KPI.

## Design goals

- Make doing the routine feel good, immediately.
- Give a visible sense of progress today and across the week — as
  progressive consistency, never a breakable streak.
- Reward *consistency* and *effort*, not raw output or speed.
- Fade in importance as intrinsic motivation grows — the game is scaffolding.

## Explicit non-goals / banned mechanics

- **No infinite scroll or endless content.**
- **No streaks.** QuestGrow does **not** use traditional streak mechanics.
  There is no counter that resets or "breaks" when a scheduled occurrence is
  missed, and no such counter is shown to the child or the parent. Replaced by
  *progressive consistency* (see below).
- **No variable-ratio / loot-box reward schedules.** Rewards are predictable.
  Spot-checking an independent quest must never make the reward
  probabilistic — see [OWNERSHIP_MODEL §5](../experience/OWNERSHIP_MODEL.md).
- **No countdown timers or artificial urgency.**
- **No punishment mechanics** — no point loss, no downgrade, no "you fell
  behind," no red states.
- **No leaderboards or child-to-child comparison.**
- **No "keep playing" or re-engagement nudges toward the child.**
- **No screen-time-maximizing loops.** Session length is something we try to
  keep *short*.

## The four reward horizons

### 1. Immediate (per quest)
On a **valid** completion the child gets a short, full-screen celebration:
animation, sound, sparkle, a character reaction. 1–3 seconds. Same warmth
every time — not randomized in intensity to create anticipation.

"Valid" depends on the quest's ownership stage
([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md)): at `PARENT_GUIDED` the
celebration follows parent approval ("do → wait for parent → celebrate"); at
`CHILD_PARTICIPATED` / `CHILD_OWNED` it is immediate on self-mark ("do →
celebrate immediately"). The child experiences only these two modes.

### 2. Daily
A single, obvious "today" progress indicator fills as quests complete
(e.g. a jar filling, a path lighting up). Completing all of today's quests
triggers a slightly bigger "day complete" moment. Not completing them all is
a non-event — the indicator simply shows where the child got to, and resets
for the next day with no negativity.

### 3. Weekly
A calm weekly view (seven stars / blocks / stepping-stones, not a chart)
shows which days had activity. The emotional message is "look how many good
days you had," never "you missed Tuesday." An end-of-week acknowledgement
celebrates participation and consistency at whatever level was achieved.

The weekly signal is expressed as **progressive consistency**, never a streak:
counts and history that only ever describe what *did* happen, e.g.
- "You showed up 4 days this week."
- "You've kept this routine going this week."
- "You've been doing this one yourself for 12 days."

A quieter week simply shows a smaller number. There is no chain to break, no
"best streak," no decrement, no warning.

### 4. Long-term (optional, parent-toggleable)
Layered depth that never adds child-side complexity or daily obligation:
- Collectible characters / companions
- An evolving world or garden that grows with cumulative activity
- Badges for milestones (framed as keepsakes, not status)
- Storybook pages unlocked by weeks of participation
- Seasonal / themed art

All optional. All off-able. None required to get the core value. None
introduce scarcity-driven or comparison-driven pressure.

## Points and rewards

- **Points** are a parent-configurable currency attached to quests. Default
  values are provided; parents can change or disable points entirely.
- **Lifetime Achievement only ever increases**, via valid completions. The
  **Spendable Balance** goes down when a reward is redeemed. These are two
  separate counters and must never be conflated
  ([REWARD_MODEL → two counters](./REWARD_MODEL.md)).
- **Rewards** are parent-defined (e.g. "choose the bedtime story," "trip to
  the park," "sticker"). Parents define the point cost and whether redemption
  is automatic or parent-confirmed.
- Redemption does not "spend down" visible lifetime progress used for
  long-term unlocks — track a separate lifetime counter so cashing in a
  reward never feels like going backwards.

## Progressive consistency, not streaks

QuestGrow's position on streaks is explicit: **it does not use them.** No
consecutive-day counter that resets or breaks on a missed scheduled
occurrence, and no such counter is displayed anywhere.

Instead the system uses **progressive consistency and ownership-oriented
history** — signals that only ever describe what happened:

- *This week:* "You showed up 4 days this week." / "You've kept this routine
  going this week."
- *Per quest / ownership:* "You've been doing this one yourself for 12 days."
- *Cumulative:* days active, quests attempted, weeks participated.

These signals must:

- **never create loss framing** — nothing to "lose", "break", or "keep alive";
- **never create variable-ratio reinforcement** — they are plain descriptive
  counts, not rewards on a schedule;
- **never become a KPI** — not a target, percentage, ranking, or comparison,
  for the child or the parent (cf. [OWNERSHIP_MODEL §9](../experience/OWNERSHIP_MODEL.md));
- **never punish a missed occurrence** — a missed scheduled day is a neutral
  non-event; the number is simply smaller.

Where the system highlights anything, it highlights showing up. It does not
rank children, does not reward finishing fastest, and does not penalize an
off day.

> Note: the ownership-advancement trigger (8 consecutive eligible completions,
> [OWNERSHIP_MODEL §6](../experience/OWNERSHIP_MODEL.md)) is an internal,
> parent-suggestion signal — it is **not shown to the child**, is **not**
> framed as a streak, and a "not yet" or a bumpy patch just delays a
> *suggestion*; nothing is lost.

## Integrity

No celebration, point, or unlock occurs from a completion that has not passed
its required verification — i.e. a `PARENT_GUIDED` completion still `pending`.
Pending completions show a calm waiting state and nothing more. At
`CHILD_PARTICIPATED` / `CHILD_OWNED` there is no verification to pass and the
reward is immediate. See
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md) and
[PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md).

## Tuning guardrails for future work

- If session length trends up, treat it as a regression.
- If parents report the child is upset about "losing" or "missing," a mechanic
  has drifted into punishment — fix it.
- Any proposed mechanic must be checked against the banned list above before
  design begins.

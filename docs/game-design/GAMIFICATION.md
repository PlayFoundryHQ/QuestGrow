# QuestGrow Gamification Model

Gamification in QuestGrow exists to make real-world routine feel rewarding and
to build intrinsic habit over time. It must never become an end in itself or a
driver of screen time or compulsion.

Governed by [CORE_PRINCIPLES C (9–12)](../product-foundation/CORE_PRINCIPLES.md).
Points and rewards mechanics are detailed in
[REWARD_MODEL](./REWARD_MODEL.md); quests in [QUEST_MODEL](./QUEST_MODEL.md).

## Design goals

- Make doing the routine feel good, immediately.
- Give a visible sense of progress today and across the week.
- Reward *consistency* and *effort*, not raw output or speed.
- Fade in importance as intrinsic motivation grows — the game is scaffolding.

## Explicit non-goals / banned mechanics

- **No infinite scroll or endless content.**
- **No streak anxiety.** Streaks, if shown at all, never produce loss
  warnings, never reset with a negative animation, and are framed as "days in
  a row so far," not "don't break it."
- **No variable-ratio / loot-box reward schedules.** Rewards are predictable.
- **No countdown timers or artificial urgency.**
- **No punishment mechanics** — no point loss, no downgrade, no "you fell
  behind," no red states.
- **No leaderboards or child-to-child comparison.**
- **No "keep playing" or re-engagement nudges toward the child.**
- **No screen-time-maximizing loops.** Session length is something we try to
  keep *short*.

## The four reward horizons

### 1. Immediate (per quest)
On a **valid** completion (self-marked if no verification required; parent-
approved if required), the child gets a short, full-screen celebration:
animation, sound, sparkle, a character reaction. 1–3 seconds. Same warmth
every time — not randomized in intensity to create anticipation.

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
- Points **only increase**, and only via a valid completion. There is no
  mechanism to deduct points.
- **Rewards** are parent-defined (e.g. "choose the bedtime story," "trip to
  the park," "sticker"). Parents define the point cost and whether redemption
  is automatic or parent-confirmed.
- Redemption does not "spend down" visible lifetime progress used for
  long-term unlocks — track a separate lifetime counter so cashing in a
  reward never feels like going backwards.

## Consistency over performance

Where the system highlights anything, it highlights showing up: days active,
quests attempted, weeks participated. It deliberately does not rank children,
does not reward finishing fastest, and does not penalize an off day.

## Integrity

No celebration, point, or unlock occurs from a completion that has not passed
its required verification. Pending completions show a calm waiting state and
nothing more. See [PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md).

## Tuning guardrails for future work

- If session length trends up, treat it as a regression.
- If parents report the child is upset about "losing" or "missing," a mechanic
  has drifted into punishment — fix it.
- Any proposed mechanic must be checked against the banned list above before
  design begins.

# QuestGrow Design Principles

These principles govern visual, interaction, and system design. They apply to
both the child and parent experiences unless stated otherwise. They are the
design-level expression of
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) — when the two
appear to differ, CORE_PRINCIPLES wins.

## 1. Two products, one system

The child app and the parent app are designed to different standards:

| | Child side | Parent side |
|---|---|---|
| Text | Minimal, optional, read aloud | Full, precise |
| Density | One primary thing per screen | Rich, list- and form-heavy allowed |
| Interactions | Tap, drag; 1–2 steps | Multi-step flows acceptable |
| Feedback | Loud, animated, celebratory | Quiet, informative |
| Failure states | None visible | Explicit and actionable |

Never let parent-side complexity leak onto the child screen.

## 2. Visual before verbal

The child experience is carried by illustration, iconography, color, motion,
and sound. Text is a supplement that can always be turned off or spoken. Every
quest, state, and reward must be legible to a non-reader.

## 3. Defend simplicity actively

Simplicity is not the MVP that we outgrow — it is a permanent constraint on
the child side. New child-facing features must remove or replace something, or
justify their cost against the "a 5-year-old with no help" test.

## 4. The real-world action is the main event

Design interactions so the shortest path is: glance → go do it → come back →
mark. Anything that keeps the child tapping, browsing, or watching on-screen
is a bug. No infinite lists, no browsable galleries in the child flow, no
"just one more."

## 5. Positive-only feedback loop

- Completed quests celebrate.
- Pending quests wait calmly.
- Incomplete quests at day's end simply roll over or reset with no negative
  signal — no red, no frown, no "missed."
- Progress bars fill; they never drain as punishment.

## 6. Calm gamification

Rewards are frequent, small, and warm. Avoid variable-ratio reinforcement,
countdown timers, and any mechanic whose emotional hook is fear of loss.
**QuestGrow does not use streaks** — no breakable consecutive-day counter,
shown to no one. Consistency is celebrated as *progressive consistency*
("you showed up 4 days this week"); a missed occurrence is a neutral
non-event ([GAMIFICATION → progressive consistency](../game-design/GAMIFICATION.md)).

## 7. Integrity by construction

State that matters — points, progress, rewards earned, goals completed —
changes only through parent-controlled paths. The child UI can *request*
changes; it cannot *commit* them. This is enforced in the data model and
services, not just hidden in the UI. See
[PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md).

## 8. Age-adaptive by design

Every child-facing surface has an age dimension. A component should accept an
age band (or derived complexity level) and adjust vocabulary, text amount,
icon style, number of choices, and animation intensity. Design components as
a set of variants, not a single fixed layout. See
[UX_PRINCIPLES](./UX_PRINCIPLES.md).

## 9. Accessibility is baseline

Large touch targets (minimum 64×64 pt on the child side), high contrast,
color never the sole signal, full audio narration option, reduced-motion
support, and no reliance on fine motor precision.

## 10. Parent effort is a budget

The parent's daily interaction should cost seconds, not minutes. Verification,
in particular, must be fast, batchable, and feel like part of the game
(approving a quest triggers the child's celebration) rather than data entry.

## 11. Quiet by default

No notifications unless the parent opts in. When present, notifications are
informational ("Mia marked 2 quests"), never manipulative or loss-framed
(and there is no streak to invoke — see principle 6). Nothing about QuestGrow
should train compulsive checking — in the child or the parent.

## 12. Content neutrality

The quest catalog is authored by parents. The system ships structure,
iconography, and defaults — never a prescriptive list of what a "good child"
does. Cultural, religious, and family-specific routines are all first-class.

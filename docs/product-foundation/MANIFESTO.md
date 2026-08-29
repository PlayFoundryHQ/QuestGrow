# QuestGrow Manifesto

QuestGrow is a parent-guided, child-facing system that helps young children
(approximately ages 3–8) gradually take on everyday routines — building the
competence to do them, then real responsibility for them, then habits they
come to experience as their own.

**QuestGrow is not a checklist app.** A checklist tracks tasks and never
changes. QuestGrow is built around a routine's relationship to the child
*changing over time* — from something a parent runs to something the child
does themselves. The difference matters in every decision we make.

## What we believe

1. **Child-first simplicity.** The child's screen is large icons, large touch
   targets, minimal text, immediate visual feedback, and short interactions.
   If a five-year-old cannot use it without reading, it is too complex.

2. **Real-world behavior over screen interaction.** The point of a quest is the
   real thing the child does away from the device. The real-world action must
   be the longest part of every interaction. The app should push the child off
   the screen, not hold them on it.

3. **Gamification is scaffolding, not the product.** Celebrate effort and
   consistency to help a young child engage with the real-world routine — then
   let that motivation matter less as the child's own takes over. Never use
   infinite scroll, compulsive loops, streaks, loot-box mechanics, or
   manufactured urgency. Motivation should feel warm, not compulsive.

4. **Parent authority and child agency, together.** Parents define what
   matters, the standard, and the pace. Children act — and, routine by
   routine, as the parent judges them ready, take real ownership of everyday
   routines. Both roles are real; neither is diminished, and ownership
   transfer is never a transfer of the parent's authority.

5. **The parent controls the path, not every tap.** Points, progress, rewards,
   and goal completion change only through a path the parent controls: for a
   guided routine the parent confirms it; for a routine the parent has handed
   over, the trust was granted when they advanced it. Either way, a child
   cannot quietly award themselves anything the parent has not sanctioned.

6. **Positive reinforcement over punishment.** No lost points, no shame, no
   red X's, no "you failed today." Missing a quest is a neutral non-event.

7. **Flexible, parent-configurable quests.** There is no fixed catalog. Parents
   create the goals that fit their family and their child.

8. **Daily and weekly progression, calmly shown.** The child feels progress
   today and a sense of participation across the week — shown as a plain
   picture of what they did, never as a streak or a chain that can break.

9. **Strong graphical / visual UX.** The child experience is carried by
   illustration, animation, color, and sound — not copy.

10. **Complexity belongs on the parent side.** Scheduling, verification,
    rewards, age configuration, and history live in the parent app. The child
    side stays almost empty by comparison.

## The core loop

```
Parent defines quest
  → Child sees quest
    → Child performs real-world action
      → Child requests / marks completion
        → Parent confirms it, when the routine still needs confirming
          → Child receives celebration
            → Progress updates
```

Whether that confirmation step is present depends on where the routine sits on
the ownership arc — see [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).

## What QuestGrow must never become

- A conventional todo / checklist app
- A surveillance product
- A punishment system
- A discipline or compliance-enforcement system
- A behavioural scoreboard, or any score or ranking of a child
- A tool that pressures a parent about a child's pace
- A social network for children
- An infinite-scroll entertainment app
- Anything that increases a child's screen dependency

## How to use this document

The `docs/` folder is the source of truth for QuestGrow's product, UX, and
architecture decisions. When a design or engineering choice conflicts with
this manifesto, the manifesto wins or the manifesto changes — deliberately,
in writing — first.

The manifesto states what QuestGrow believes about children, parents,
routines, autonomy, trust, and technology.
[PRODUCT_VISION](./PRODUCT_VISION.md) explains what QuestGrow exists to
accomplish; [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md) defines the
developmental arc the rest of the product is built around; and
[CORE_PRINCIPLES](./CORE_PRINCIPLES.md) turns the philosophy into 24 numbered
principles used as acceptance criteria for every significant product decision.
The full document map is in [`docs/README.md`](../README.md).

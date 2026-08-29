# QuestGrow Parent Journey

How the parent experiences QuestGrow. Governed by
[Core Principles D (13–16)](../product-foundation/CORE_PRINCIPLES.md).
The parent side is allowed to be substantially richer than the child side
(Core Principle #16), but the daily cost must stay in **seconds, not minutes**
([DESIGN_PRINCIPLES → parent effort is a budget](./DESIGN_PRINCIPLES.md)).

## One-line model

The parent runs the game: sets up each child, defines the quests and when they
recur, decides which need verification, sets rewards, then does a quick daily
pass to approve pending completions and glance at progress.

## Onboarding journey (first run)

1. **Create account** — email / OAuth. Parent is the sole account holder and
   consent authority ([ARCHITECTURE → privacy & safety](../product-delivery/ARCHITECTURE.md)).
2. **Set the parent gate** — PIN, biometric, or adult-friction challenge.
   On by default; protects parent mode and any escalation from child mode.
3. **Create a child profile** — name, avatar, birthdate or explicit age band.
   Age band derives a complexity level; per-dimension overrides available.
4. **Add quests** — start from one-tap templates (teeth, dressing, bed,
   tidying, reading, outdoor, meals, help at home, school prep, bathing) or
   create arbitrary family-specific goals. Minimum to create a working quest:
   a title and an icon; everything else has a default
   ([QUEST_MODEL](../game-design/QUEST_MODEL.md)).
5. **(Optional) Define rewards** — name, icon, point cost, redemption mode
   ([REWARD_MODEL](../game-design/REWARD_MODEL.md)).
6. **Hand the device to the child in child mode.**

## Daily journey

1. **Open parent mode** (pass the gate) → **Dashboard**: per child, today's
   progress and a pending-approvals count.
2. **Approvals** — the queue of pending completions. Each: one tap to
   **approve** or **not yet**.
   - Approve → child's completion becomes verified; the celebration fires for
     the child; one append-only ledger entry is written.
   - Not yet → quest returns to available with an optional gentle note
     ("let's do it together"); no penalty, no negative signal to the child.
   - **Batch "approve all"** for low-stakes quests.
   Approving should feel like handing the child a win, not filing paperwork
   (Core Principle #15; [VERIFICATION](../trust-and-safety/VERIFICATION.md)).
3. **Glance at progress** — daily fill and the weekly view per child.
4. **Done** — typically under a minute.

## Occasional / weekly journey

- Adjust quests as the child grows or routines change (Core Principle #20):
  edit, archive, add. Changes apply going forward; history is preserved.
- Review the weekly view; note what's working.
- Tune verification requirements — move a well-established routine from
  "requires verification" to "self-mark" to grant independence
  ([VERIFICATION → granting independence](../trust-and-safety/VERIFICATION.md)).
- Adjust rewards and point mappings.
- Adjust age band / adaptation overrides.

## Parent-side screen inventory (MVP)

1. **Home / dashboard** — per child: today's progress, pending approvals count
2. **Approvals** — pending completion queue; approve / not-yet; batchable
3. **Children** — create / edit child profile, age band, adaptation overrides
4. **Quests** — create / edit / archive; schedule; verification flag; points;
   age suitability; active toggle
5. **Rewards** — define rewards and point mapping
6. **Progress** — daily and weekly history per child
7. **Settings** — account, notifications (off by default), parent gate

Full interaction rules in [UX_PRINCIPLES → parent experience](./UX_PRINCIPLES.md).

## Notifications

Opt-in only. Informational wording ("Mia marked 2 quests"), never
loss-framed or re-engagement bait. Nothing about QuestGrow should train
compulsive checking — in the child *or* the parent
([DESIGN_PRINCIPLES → quiet by default](./DESIGN_PRINCIPLES.md)).

## What the parent controls

Everything that changes meaningful state: quests, schedules, verification
requirements, points, rewards, child profiles, age configuration, approvals,
and additive point adjustments. See
[PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md).

## Related

- Child side of the same loop: [CHILD_JOURNEY](./CHILD_JOURNEY.md)
- Verification design in depth: [VERIFICATION](../trust-and-safety/VERIFICATION.md)
- Quest configuration: [QUEST_MODEL](../game-design/QUEST_MODEL.md)

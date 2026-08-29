# QuestGrow Child Journey

How the child experiences QuestGrow, day to day. Governed by
[Core Principles A (1–4)](../product-foundation/CORE_PRINCIPLES.md) and
[B (5–8)](../product-foundation/CORE_PRINCIPLES.md), and detailed further in
[UX_PRINCIPLES](./UX_PRINCIPLES.md).

## One-line model

The child sees a few friendly pictures of things to do today, goes and does
the real thing, taps "I did it," a grown-up says yes when needed, something
happy happens, and their progress grows.

## Daily journey

1. **Open** — parent hands the device (or the child opens it in child mode).
   Lands directly on **Today**. No login, no menu.
2. **Glance** — today's quests as large cards, count tuned to age band
   ([UX_PRINCIPLES → age adaptation](./UX_PRINCIPLES.md)). Icon-first,
   optional short label, tap-to-hear audio.
3. **Choose a quest** — one tap opens the quest's do-it screen: big art, the
   real-world action shown visually, one large "I did it" control.
4. **Leave the screen** — the child does the real thing. This is the longest
   part of the interaction by design (Core Principle #6). The app is not
   needed while it happens.
5. **Come back and mark** — one large tap on "I did it."
   - No verification required → instant celebration (see step 6).
   - Verification required → calm **"waiting for grown-up"** state (a friendly
     sleeping icon, never an error). No points yet, no celebration yet. See
     [VERIFICATION](../trust-and-safety/VERIFICATION.md).
6. **Celebration** — on a valid completion, a short (1–3s) full-screen
   celebration: animation, sound, character reaction. Same warmth every time.
7. **See progress** — today's indicator fills a little more. Optionally the
   child peeks at the weekly view (calm seven-unit view).
8. **Done for now** — nothing pulls the child back. No "one more," no
   notification, no unlockable that requires returning today.

## Emotional arc we want

- Opening: *"what are my quests today?"* (curiosity, ownership)
- Doing: *"I'm doing my thing"* (agency, focus in the real world)
- Marking: *"I did it!"* (pride)
- Waiting for a grown-up: *calm, expectant* — never anxious or punished
- Celebration: *joy, warmth*
- Closing: *satisfied, finished* — not craving more screen time

## States the child ever sees

| State | Presentation |
|---|---|
| Available | Bright, inviting quest card |
| Pending ("waiting for grown-up") | Calm waiting art, no negativity |
| Done | Happy completed card for the rest of the day |
| Day progress | Filling indicator (jar, path, etc.) |
| Week progress | Seven stars/blocks/stepping-stones = active days |

The child **never** sees: failed, late, missed, "you lost points," red error
states, streak-loss warnings, another child's data, verification controls,
settings, or any list longer than the day's quests. (Core Principles #12,
#23; [GAMIFICATION → banned mechanics](../game-design/GAMIFICATION.md).)

## What the child can do

- View today's quests and quest details
- Mark a quest as done (→ verified or pending)
- Hear any text read aloud
- View their own progress
- Redeem a reward **only** where the parent enabled self-service
  ([REWARD_MODEL](../game-design/REWARD_MODEL.md))

## What the child can never do

Change points, progress, rewards, goals, schedules, age settings, or another
child's data; approve their own pending quests; reach parent mode without the
parent gate. Enforced server-side, not just hidden in the UI
([PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md),
[ARCHITECTURE](../product-delivery/ARCHITECTURE.md)).

## Age-band differences (summary)

| | ~3–4 | ~5–6 | ~7–8 |
|---|---|---|---|
| Quests on screen | 1–3 | 3–5 | 5–7 |
| Text | Icon-only + audio | Short labels + audio | Short sentences |
| Independence | Parent co-present | Child solo, parent verifies | Child solo, lighter verification |
| Reward presentation | Big immediate animation | Animation + progress | Progress, collectibles, stories |

Full table in [UX_PRINCIPLES](./UX_PRINCIPLES.md).

## Related

- Parent side of the same loop: [PARENT_JOURNEY](./PARENT_JOURNEY.md)
- The loop's trust rules: [PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md)
- What celebrations and progress mean: [GAMIFICATION](../game-design/GAMIFICATION.md)

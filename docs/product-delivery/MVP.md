# QuestGrow MVP

The MVP proves the core loop: a parent can set up quests, a child can do them
in the real world and mark them, verification gates meaningful state, and both
daily and weekly progress feel real — all while keeping the child side
extremely simple.

The MVP is not a list of principles — it is the smallest system that
demonstrates the QuestGrow philosophy while staying consistent with
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md). What comes after
is sequenced in [ROADMAP](./ROADMAP.md).

## In scope

### 1. Parent creates a child profile
Name, avatar, birthdate or explicit age band. Age band derives a complexity
level; parent can override individual adaptation dimensions. Multiple children
per account supported.

### 2. Parent configures quests
Create / edit / archive quests. Per quest:
- Title + icon/art (from a starter asset set; custom later)
- Schedule: which days, optional time-of-day window, daily/weekly recurrence
- **Verification required?** (yes / no)
- Points value (default provided; points can be disabled account-wide)
- Age-band suitability (informational)
- Active toggle

The quest catalog is **fully parent-defined** — no fixed list. Starter
templates (teeth, dressing, bed, tidying, reading, outdoor, meals, help at
home, school prep, bathing) are offered as one-tap starting points, all
editable.

### 3. Child sees daily visual quests
"Today" screen: today's active quests as large cards, count and layout tuned
to age band. Icon-first, optional short label, tap-to-hear audio. Nothing
else on screen.

### 4. Child requests / marks completion
Child opens a quest, does the real thing, returns, taps one large "I did it"
control.
- If verification not required → completion is **verified** immediately.
- If verification required → completion goes **pending**; child sees a calm
  "waiting for grown-up" state.

### 5. Selected quests require parent verification
Pending completions appear in the parent **Approvals** queue (and optional
quiet notification). Parent taps approve or "not yet":
- Approve → completion **verified**, celebration fires for the child, one
  append-only ledger entry.
- "Not yet" → quest returns to available, optional gentle note, no penalty.
- Batch "approve all" for low-stakes quests.

### 6. Points / progress update only after valid completion
Points and progress are a server-written, append-only ledger. An entry is
created only on a **verified** completion, idempotently (one per completion).
No client writes balances. Points only ever increase.

### 7. Child receives immediate visual celebration
On a verified completion, full-screen celebration: animation + sound +
character reaction, 1–3s, consistent warmth (not randomized for anticipation).
Respects reduced-motion.

### 8. Daily progress
One clear "today" indicator that fills as quests are verified. A modest
"day complete" moment when all of today's quests are done. Incomplete day =
non-event; resets cleanly.

### 9. Weekly progress
Calm seven-unit weekly view (stars/blocks/stepping-stones) showing active
days. Framed as "good days you had," never "days you missed." Light
end-of-week acknowledgement.

### 10. Parent can modify quests and rewards
Edit/archive quests any time (changes apply going forward; history preserved).
Define rewards: name, icon, point cost, redemption mode (self-service or
parent-confirmed). Edit reward mapping.

### 11. Data model supports age adaptation
Child profile carries age band + per-dimension overrides → a derived
complexity level available to every child-facing component. Components render
as age variants. Adaptation dimensions: vocabulary, text amount, iconography,
interaction complexity, task complexity, reading requirement, reward
presentation, independence level.

## Cross-cutting MVP requirements

- **Parent gate** (PIN / biometric / adult-friction challenge) protecting
  parent mode and any escalation from child mode. On by default.
- **Trust boundary enforced server-side**: child-mode clients have restricted
  scope; state-changing writes require parent-authorized context; child can
  only write to intent tables for their own profile.
- **Positive-only**: no failure/missed/late states visible to the child
  anywhere.
- **Quiet by default**: notifications opt-in; informational wording only.
- **Offline-tolerant** child flow: marking a completion works offline and
  syncs (verification/celebration resolve on reconnect).
- Accessibility baseline: ≥64pt targets, contrast, color-not-sole-signal,
  audio narration, reduced-motion.

## Explicitly out of scope for MVP

- Multi-parent / caregiver / verifier roles (data model allows; UI later)
- Custom art upload; full long-term meta-game (characters, worlds, stories,
  badges beyond a basic milestone)
- Photo evidence attachments
- Reward marketplace / templates library
- Web parent dashboard (mobile only)
- Cross-family anything, social anything
- Analytics dashboards for parents beyond daily/weekly progress
- Localization beyond the initial language (structure should not preclude it)

## MVP acceptance (the loop works end to end)

1. Parent sets up a child and 3–5 quests, some requiring verification.
2. Child (in child mode) sees today's quests, does one in the real world,
   marks it.
3. A verification-required quest goes pending; a self-mark quest verifies
   immediately and celebrates.
4. Parent approves the pending one from the queue; child then sees the
   celebration and progress increments.
5. "Not yet" on another returns it to available with no penalty.
6. Daily indicator reflects only verified completions; weekly view updates.
7. Child mode offers no path to change points, quests, rewards, or settings;
   server rejects such writes even if the client is tampered with.
8. Switching the child's age band visibly changes text amount, quests-per-
   screen, and reward presentation.

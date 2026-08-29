# QuestGrow MVP

The MVP proves the core loop: a parent can set up quests, a child can do them
in the real world and mark them, verification gates meaningful state, and both
daily and weekly progress feel real — all while keeping the child side
extremely simple.

The MVP is not a list of principles — it is the smallest system that
demonstrates the QuestGrow philosophy while staying consistent with
[CORE_PRINCIPLES](../product-foundation/CORE_PRINCIPLES.md) and the
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md). What comes after
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
- Points value (default provided; points can be disabled account-wide)
- Age-band suitability (informational)
- Active toggle

There is **no** standalone "verification required" flag. Each quest has a
per-child **ownership stage** ([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md));
verification behavior is derived from it. MVP may start every quest at
`PARENT_GUIDED`.

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
control. Two reward modes ([OWNERSHIP_MODEL §4](../experience/OWNERSHIP_MODEL.md)):
- **Mode B** (`CHILD_PARTICIPATED` / `CHILD_OWNED`) → **verified** immediately,
  celebration now.
- **Mode A** (`PARENT_GUIDED`) → **pending**; child sees a calm "waiting for
  grown-up" state.

### 5. `PARENT_GUIDED` quests require parent verification
Pending completions appear in the parent **Approvals** queue (and optional
quiet notification). Parent taps approve or "not yet":
- Approve → completion **verified**, celebration fires for the child, one
  append-only ledger entry.
- "Not yet" → quest returns to available, optional gentle note, no penalty.
- Batch "approve all".

Advancement suggestion ("ready to let her own this quest?") + parent confirm
is in MVP scope. Regression (moving a quest back to more support) is
parent-initiated and never framed as failure.

### 6. Points / progress update only after valid completion
Points and progress are a server-written, append-only ledger. An entry is
created only on a **verified** completion, idempotently (one per completion).
No client writes balances. **Lifetime Achievement** only ever increases;
**Spendable Balance** decreases on reward redemption
([REWARD_MODEL](../game-design/REWARD_MODEL.md)).

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
days. Expressed as **progressive consistency** ("you showed up 4 days this
week"), **never a streak** — no breakable consecutive-day counter, no loss
framing, a quieter week just shows a smaller number
([GAMIFICATION → progressive consistency](../game-design/GAMIFICATION.md)).
Light end-of-week acknowledgement.

### 10. Parent can modify quests and rewards
Edit/archive quests any time (changes apply going forward; history preserved).
Define rewards: name, icon, point cost, redemption mode (self-service or
parent-confirmed). Edit reward mapping.

### 11. Data model supports age adaptation
Child profile carries age band + per-dimension overrides → a derived
complexity level available to every child-facing component. Components render
as age variants. Adaptation dimensions: vocabulary, text amount, iconography,
interaction complexity, task complexity, reading requirement, reward
presentation, independence level (expressed as the default ownership stage
per quest — [OWNERSHIP_MODEL §10](../experience/OWNERSHIP_MODEL.md)).

### 12. Data model supports ownership stage
The **(child, quest)** relationship carries `ownership_stage` ∈
`{ PARENT_MANAGED, PARENT_GUIDED, CHILD_PARTICIPATED, CHILD_OWNED }`, default
derived from age band. Verification behavior is computed from it; there are
no `verification_required` / `self_mark_preauthorized` fields. Stage
transitions are recorded in `audit_log`, never surfaced as a child metric.

## Cross-cutting MVP requirements

- **Parent gate** (PIN / biometric / adult-friction challenge) protecting
  parent mode and any escalation from child mode. On by default.
- **Trust boundary enforced server-side**: child-mode clients have restricted
  scope; state-changing writes require parent-authorized context; child can
  only write to intent tables for their own profile.
- **Positive-only**: no failure/missed/late states visible to the child
  anywhere; ownership regression is never shown as a downgrade or loss.
- **Ownership never a KPI**: no dashboards, percentages, or nudges that push
  parents toward faster ownership transfer
  ([OWNERSHIP_MODEL §9](../experience/OWNERSHIP_MODEL.md)).
- **Quiet by default**: notifications opt-in; informational wording only.
- **Offline-tolerant** child flow: marking a completion works offline and
  syncs (verification/celebration resolve on reconnect).
- Accessibility baseline: ≥64pt targets, contrast, color-not-sole-signal,
  audio narration, reduced-motion.

## Explicitly out of scope for MVP

- Multi-parent / caregiver / verifier roles (data model allows; UI later)
- Dedicated `PARENT_MANAGED` UI (stage exists in the model; MVP may start all
  quests at `PARENT_GUIDED`)
- Age-band-specific tuning of the 8-occurrence advancement trigger (ships as
  one global default)
- Parent-side ownership review/spot-check tooling beyond a simple after-the-
  fact list
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
3. A `PARENT_GUIDED` quest goes pending; a `CHILD_PARTICIPATED` / `CHILD_OWNED`
   quest verifies immediately and celebrates.
4. Parent approves the pending one from the queue; child then sees the
   celebration and progress increments.
5. "Not yet" on another returns it to available with no penalty.
6. Daily indicator reflects only verified completions; weekly view updates.
7. Child mode offers no path to change points, quests, rewards, ownership
   stage, or settings; server rejects such writes even if the client is
   tampered with.
8. Switching the child's age band visibly changes text amount, quests-per-
   screen, and reward presentation.
9. After 8 consecutive eligible completions of a `PARENT_GUIDED` quest, the
   parent is offered an advancement suggestion; accepting it makes that
   quest's completions verify immediately, and its points value is unchanged.
10. Moving a quest back to `PARENT_GUIDED` produces no negative signal to the
    child and no points change.

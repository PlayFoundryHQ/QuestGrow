# QuestGrow Reward Model

Defines points, rewards, and redemption. Governed by
[Core Principles C (9–12)](../product-foundation/CORE_PRINCIPLES.md) and
[#14 (trust before points)](../product-foundation/CORE_PRINCIPLES.md).
Sits under [GAMIFICATION](./GAMIFICATION.md), which defines the four reward
horizons and the banned mechanics list. Reward *timing* follows the two child
reward modes in [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md); reward
*value* does not depend on ownership stage.

## Points

- **Parent-configurable currency** attached to quests. Default values are
  provided; parents can change them or disable points entirely account-wide.
- **Lifetime Achievement only ever increases** from a valid completion — there
  is no deduction, downgrade, or decay mechanic on it (Core Principle #12).
  The **spendable balance** decreases when a reward is redeemed (see *Two
  counters*). The two must never be conflated: "points only go up" is true of
  lifetime, not of the spendable wallet.
- Points are recorded as **append-only ledger entries**, written **only by the
  server** in response to a `completion.verified` event, exactly once per
  completion (idempotency key = completion id). No client writes balances.
  See [ARCHITECTURE → progress ledger](../product-delivery/ARCHITECTURE.md).
- **Trust before points (Core Principle #14):** a completion that would award
  points either requires parent approval (`ownership_stage = PARENT_GUIDED`),
  or the quest is at a stage the parent advanced it to
  (`CHILD_PARTICIPATED` / `CHILD_OWNED`), which is itself the parent's
  pre-authorization. No third path. See
  [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md),
  [PARENT_CHILD_MODEL → anti-self-scoring](../trust-and-safety/PARENT_CHILD_MODEL.md)
  and [VERIFICATION](../trust-and-safety/VERIFICATION.md).
- **Reward value is ownership-independent.** A quest is worth the same points
  whether it is `PARENT_GUIDED` or `CHILD_OWNED` — mastering a routine never
  reduces its reward. Only the *timing* of the reward changes: "do → wait for
  parent → celebrate" at `PARENT_GUIDED`, "do → celebrate immediately" at
  `CHILD_PARTICIPATED` / `CHILD_OWNED`.

## Two counters, never conflated

| Counter | Definition | Used for |
|---|---|---|
| **Lifetime Achievement** | Σ of all `earn` entries | Long-term progression, milestone unlocks, weekly/overall sense of achievement. Only ever increases. |
| **Spendable Balance** | Σ `earn` − Σ `redeem` ± `adjustment` | What the child can spend on rewards. Decreases on redemption. |

Example: *Lifetime Achievement: 420 · Spendable Balance: 120.*

Redeeming a reward reduces **Spendable Balance** but never **Lifetime
Achievement** — so cashing in a reward never feels like going backwards
(Core Principle #11). Both are **projections over the ledger**, never stored
mutable numbers.

## Rewards

Parent-defined. Each reward:

| Field | Notes |
|---|---|
| `name` | Parent's words (e.g. "choose the bedtime story", "trip to the park", "sticker") |
| `icon` | Visual, for the child |
| `cost` | In points (spendable) |
| `redemption_mode` | `self_service` or `parent_confirmed` |
| `active` | Soft lifecycle |

Rewards are family-specific (Core Principle #19). No built-in "store", no
premium currency, no purchasable boosts.

## Redemption modes

### `self_service`
Child taps to redeem → server checks spendable balance → writes a `redeem`
ledger entry (negative spendable, no effect on lifetime) → parent is notified.
Appropriate for low-stakes rewards and older children.

### `parent_confirmed`
Child requests → state `pending_reward` → parent grants (or declines gently).
On grant, the `redeem` entry is written. Appropriate for higher-value or
real-world rewards.

Declining a reward request is gentle and carries no penalty (Core Principle
#12).

## Celebration vs manipulation (Core Principle #10)

- Celebrations are **consistent in warmth** — not randomized in intensity to
  build anticipation (no variable-ratio reinforcement).
- No loot boxes, mystery rewards, or gacha mechanics.
- No countdown timers, "limited-time" rewards, or urgency framing.
- Rewards are predictable: the child and parent always know what a quest is
  worth and what a reward costs.

## Long-term / meta rewards (optional, parent-toggleable)

Collectible characters, an evolving world/garden, milestone badges (framed as
keepsakes, not status), storybook pages unlocked by weeks of participation,
seasonal themes. Driven by the **lifetime** counter and by weeks-participated,
never by scarcity or comparison. All optional, all off-able, none required for
core value. Detail in [GAMIFICATION → long-term](./GAMIFICATION.md) and
sequencing in [ROADMAP](../product-delivery/ROADMAP.md).

## Data model touch-points

- `reward`, `reward_redemption` entities
- `ledger_entry` kinds: `earn`, `redeem`, `adjustment` (adjustment is
  additive-only in MVP)
- Projections: `lifetime_points(child)`, `spendable_points(child)`

See [ARCHITECTURE → data model](../product-delivery/ARCHITECTURE.md).

## MVP scope

In: parent-defined rewards, both redemption modes, points on/off, lifetime vs
spendable split, one basic milestone badge.
Out: full meta-game (characters/worlds/stories), reward templates library,
any cross-family or social reward mechanic.

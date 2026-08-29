# QuestGrow Parent–Child Model

This document defines the roles, the trust boundary, and the verification loop
that keep QuestGrow honest. It is the backbone the rest of the product hangs
from.

Governed by [CORE_PRINCIPLES D (13–16)](../product-foundation/CORE_PRINCIPLES.md).
The verification *experience* (queue, batch approve, "not yet", evidence) is
detailed in [VERIFICATION](./VERIFICATION.md); the parent and child sides of
the loop in [PARENT_JOURNEY](../experience/PARENT_JOURNEY.md) and
[CHILD_JOURNEY](../experience/CHILD_JOURNEY.md).

**Whether a given completion requires verification is derived from the
quest's ownership stage for that child**, not configured per quest — see
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).

## Roles

### Parent (account holder)
- Owns the account and the device the app lives on.
- Creates and manages child profiles.
- Defines quests, schedules, points, and rewards.
- Moves each quest along the ownership arc (which determines verification);
  verifies pending completions.
- Configures age adaptation.
- Views progress and history.
- The only role that can change meaningful state.

### Child (guided participant)
- Views today's quests.
- Performs real-world actions.
- Requests or marks completion.
- Redeems rewards where the parent has allowed self-redemption.
- Receives celebration and sees progress.
- Cannot change points, rewards, goals, schedules, ownership stage, age
  settings, or any other child's data.

A parent account may have multiple children. A child profile is data owned by
the parent, not an independent account.

## The trust boundary

QuestGrow runs primarily as one app on the parent's device with two **modes**:

- **Child mode** — the default, safe surface. Large, simple, restricted.
- **Parent mode** — reached only by passing the **parent gate**
  (PIN, biometric, or an adult-friction challenge such as a small
  multiplication problem). Configurable; on by default.

Everything that can change meaningful state lives behind the parent gate.
Child mode can *emit intent* (a completion request, a reward-redemption
request) but can never *commit* a meaningful state change.

### Enforced in the data model and services, not just the UI

- Child-mode clients get a restricted capability / token scope.
- Services reject state-changing writes (points, progress ledger, goal
  completion, reward grants, quest/schedule edits, profile edits) that do not
  carry a parent-authorized context.
- The child can create rows only in intent tables (e.g. `completion_request`)
  and only for their own profile.
- Points and progress are an **append-only ledger**; entries are written only
  by the server in response to a validated completion. No client writes
  balances directly.

## Completion states

```
                 ┌────────────┐
                 │ available  │
                 └─────┬──────┘
       child marks done │
      ┌─────────────────┴──────────────────────────┐
      │ ownership_stage (of this child × quest)?   │
      │   CHILD_PARTICIPATED / CHILD_OWNED → verified (immediate)
      │   PARENT_GUIDED                    → pending
      │   PARENT_MANAGED  → parent records → verified
      └─────────────────┬──────────────────────────┘
                        │
              pending ──┼── parent approves ──► verified ──► celebration + ledger entry
                        │
                        └── parent: "not yet" ──► available (optional gentle note)
```

- **available** — quest is doable today.
- **pending** — child marked done on a `PARENT_GUIDED` quest; awaiting parent
  verification. Child sees a calm "waiting for grown-up" state. No points, no
  celebration yet. (Only `PARENT_GUIDED` produces this state.)
- **verified** — completion is valid. Triggers celebration and a single
  append-only points/progress ledger entry (idempotent — one entry per
  completion).
- **not-yet** — parent declined *this instance*. Quest returns to available.
  This is not a failure state, carries no penalty, and is not shown to the
  child as negative. Optional parent note ("let's try together").
- **expired / rollover** — an available or pending quest not completed by end
  of day rolls over per its schedule with no negative signal.

## Which quests require verification?

Derived from the quest's `ownership_stage` for that child, not a per-quest
toggle ([OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md)):

- `PARENT_MANAGED` → parent records the completion (no child self-mark path).
- `PARENT_GUIDED` → child self-marks; completion is `pending` until the parent
  approves. **This is the only stage that requires verification.**
- `CHILD_PARTICIPATED` / `CHILD_OWNED` → child self-marks; `verified`
  immediately; any parent review is post-hoc and non-blocking.

Age band sets the *default* starting stage; the parent moves quests along the
arc over time.

## Verification as play, not admin

- Approvals are fast: a queue, one tap to approve, batch "approve all" for
  low-stakes items.
- Approving **is** the trigger for the child's celebration — the parent is
  handing the child a win, not filing paperwork.
- Optional lightweight evidence (a photo the child or parent attaches) can
  support verification but is never required and is never used for
  surveillance or retained beyond its purpose.
- "Not yet" is framed and worded as encouragement.

## Anti-self-scoring — the hard rule

A child must not be able to tap a button and award themselves points,
progress, rewards, or goal completion. Any completion that would change
meaningful state either:

1. requires parent approval — the quest is at `PARENT_GUIDED`
   (**pending → verified**), or
2. the quest is at `CHILD_PARTICIPATED` / `CHILD_OWNED`, a stage the parent
   deliberately advanced it to — the *parent has pre-authorized* that class of
   completion.

There is no third path. `ownership_stage` is writable only in parent scope,
and the server enforces this regardless of client behavior.

## Reward redemption

- Parent defines each reward, its point cost, and whether redemption is
  **self-service** (child can redeem, points deducted, parent notified) or
  **parent-confirmed** (child requests, parent grants).
- **Lifetime Achievement** (used for long-term unlocks) is tracked separately
  from **Spendable Balance** so redemption never feels like regression
  ([REWARD_MODEL → two counters](../game-design/REWARD_MODEL.md)).

## Multi-parent / caregiver (future)

The model allows additional adults (second parent, grandparent, sitter) with
the parent role or a limited "verifier" role. Not in MVP, but the data model
should not preclude it.

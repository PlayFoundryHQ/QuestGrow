# QuestGrow Product Vision

> This document explains **what QuestGrow exists to accomplish**. It describes
> the model defined in [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md) and
> governed by [CORE_PRINCIPLES](./CORE_PRINCIPLES.md); it does not redefine it.
> The durable decisions behind it are recorded in
> [DECISION_LOG](../governance/DECISION_LOG.md). The thesis wording below is a
> recommended formulation, not a new decision.

## 1. The problem

Young children thrive on predictable routines — but establishing them is hard,
and the daily work of it usually lands on the parent as *nagging*. Brush your
teeth. Get dressed. Put your shoes away. Get ready for school. The same
reminders, every day, often met with resistance. It is tiring for the parent,
demotivating for the child, and it quietly frames everyday responsibility as
something that happens *to* the child under supervision — not something that
can become their own.

What a parent wants underneath the daily friction is not a better way to
remind. It is a child who, over time, needs fewer reminders — who comes to
treat brushing their teeth or packing their bag as simply part of how their
day works.

## 2. Why existing solutions fall short

- **Chore charts and reward apps** treat the relationship to a task as fixed:
  a task, a checkbox, a sticker, forever. They let a child tap "done" and
  score themselves, which rewards working the system rather than doing the
  thing — and the parent's role never changes.
- **Habit trackers** are built for a literate, self-motivated adult. A young
  child is not that user, and a parent operating one on the child's behalf is
  an awkward fit.
- **Children's games** compete for screen time. QuestGrow's goal is the
  opposite: to send the child *off* the screen to do a real thing.

None of these models the one thing that matters most here: **the child's
relationship to a routine is meant to change.**

## 3. Product thesis

> **QuestGrow helps a young child gradually make each everyday routine their
> own — moving it, one routine at a time, from something the parent runs to
> something the child does themselves — while the parent keeps authority over
> what matters.**

Three things follow from that sentence:

- **The child is the centre of the outcome.** The point is not compliance
  today; it is a child who, routine by routine, first becomes *able* to do the
  thing (competence) and then does it *because it is theirs* (autonomy).
- **It happens one routine at a time.** A child can own "brush teeth" while
  still being guided through "tidy up". There is no single "independence
  level".
- **The parent stays the authority.** Handing a routine over is the parent
  choosing to stop checking that routine — not the parent giving up any say
  over what the routine is, what doing it well means, or how fast to let go.

## 4. The ownership arc

Under the surface, every routine sits somewhere on a four-stage arc, per
child:

```
PARENT_MANAGED  →  PARENT_GUIDED  →  CHILD_PARTICIPATED  →  CHILD_OWNED
```

- **PARENT_MANAGED** — parent and child do the routine together; the parent
  records it.
- **PARENT_GUIDED** — the child does it and marks it done; the parent confirms
  it before it counts.
- **CHILD_PARTICIPATED** — the child does it and marks it done; it counts
  immediately; the parent may glance at it afterward.
- **CHILD_OWNED** — the routine is simply the child's; the parent trusts it.

This arc is a **product model, not a child-facing level system.** The child
never sees a stage name, a "Level 1 / Level 2", a "you graduated", an
independence or ownership score, a "you're behind", or a verdict from the app
that they are ready. Movement along it is slow, uneven between routines, and
reversible.

The app may *suggest* that a routine looks ready to advance, based on the
child doing it consistently. **The parent decides.** The app is never the
authority on whether a child is ready.

Full detail: [OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md).

## 5. Parent authority

The parent permanently holds authority over:

- which routines matter;
- what "done well" means — the standard;
- when routines happen — scheduling;
- rewards and what they cost;
- the child's profile and age configuration;
- safety;
- the pace of handover — whether a routine should advance, hold, or move back;
- override and spot-checking, at any time.

None of this transfers. **Ownership transfer is not a transfer of parental
authority.**

**Verification follows the arc.** At `PARENT_GUIDED`, a completion waits for
the parent to confirm it. At `CHILD_PARTICIPATED` and `CHILD_OWNED`, it is not
gated by a routine approval step — because the parent already extended that
trust by advancing the routine. Whether a confirmation step is present is
*derived from* where a routine sits on the arc. It is not a permanent,
universal mechanism, and the older idea that "the parent verifies everything"
does not describe this product. See
[VERIFICATION](../trust-and-safety/VERIFICATION.md).

## 6. Child agency

As a routine moves along the arc, the child progressively gains:

- **initiation** — deciding to do it without being told;
- **execution** — doing the real thing, unsupervised;
- **self-report** — marking it done;
- **trusted completion** — being believed;
- eventually, **doing the routine without depending on the app to prompt or
  reward it.**

The child does not *earn* ownership like a prize, and the app does not award
it. The parent grants it, quietly, when they judge the child is ready.

**Regression is normal.** A rough week, an illness, a new sibling — a routine
can move back to more support. This is never framed as failure, a downgrade,
or a lost level, for the parent or the child. A regressed routine can move
forward again later. See [CORE_PRINCIPLES](./CORE_PRINCIPLES.md) principle #20.

## 7. Day-one value

**A family does not need to successfully transfer ownership of anything for
QuestGrow to be worth using.** A routine can stay at `PARENT_MANAGED` or
`PARENT_GUIDED` indefinitely and the family is using the product correctly.

Before any handover has happened — and for a young child that may be most
routines for a long time — QuestGrow still provides:

- a calm, shared, visual picture of the day;
- predictable expectations;
- less ambiguity about what is expected of the child;
- less daily conflict around routines.

Ownership is the longer arc layered on top of that value, not a precondition
for it. "Less conflict" is a real benefit — it is **not** a number the
product optimises.

## 8. Real-world first

The routine — the real thing the child does away from the screen — is the
point. The app interaction is a thin wrapper around it: see today's routines,
go and do one, come back and mark it. The real-world action should be the
longest part of any interaction, and the product should return the child to
real life quickly. QuestGrow does not try to maximise time in the app; if app
time for a routine the child has taken on tapers off, that is expected — not a
failure and not a sign of churn.

## 9. Gamification as scaffolding

QuestGrow has a motivation layer — immediate celebration, calm progress,
optional collectible depth — because a three- to eight-year-old often needs an
immediate, developmentally appropriate reason to engage with the real-world
loop.

That layer is **scaffolding**. It is subordinate to real-world action,
competence, trust, and autonomy. It is designed to matter less as the child's
own motivation grows. It is not the product, it is not the mechanism of
ownership transfer, and it is not an engagement loop built to pull the child
back.

- **No streaks.** No chain to break, no loss framing, no pressure from a
  missed day. Consistency, where it is shown at all, is a plain description of
  what happened ("you did this four days this week"), never a counter that
  resets. See [GAMIFICATION](../game-design/GAMIFICATION.md).
- A routine the child owns still earns its normal reward if the child chooses
  to log it. The product does not reduce rewards as a routine becomes owned,
  and it does not require continued logging for a routine to "count" as the
  child's. (Whether the celebration layer should eventually quieten for owned
  routines is an open question — see §13.)

## 10. What QuestGrow refuses to become

QuestGrow is not, and must never become:

- a chore chart or a checklist;
- a discipline or enforcement system;
- a behavioural scoreboard, or any score or ranking of a child;
- an "independence score" or an ownership percentage shown to a parent;
- a way to pressure a parent — "your child is behind", "advance more
  routines";
- a surveillance product;
- a social network for children;
- an infinite-scroll or engagement-maximising app;
- anything that increases a child's dependence on a screen.

See also
[MANIFESTO → what QuestGrow must never become](./MANIFESTO.md).

## 11. Age 3–8

The same thesis holds across the range; the lived experience differs by age.

- **~3–4:** shared routines and visual clarity; parent and child doing things
  together; ownership is mostly latent.
- **~5–6:** active participation and growing competence; routines begin to
  move into trusted, self-marked territory.
- **~7–8:** more self-initiation, less scaffolding; some routines may begin
  closer to `CHILD_PARTICIPATED` or `CHILD_OWNED`.

These are observations about how the product tends to feel, not developmental
guarantees. QuestGrow makes **no** claim that a child "should" own a given
routine by a given age, and it contains no developmental benchmarks. (Whether
the ~3–4 experience is a complete product or an on-ramp, and whether the ~7–8
experience should grow toward broader responsibility management, are open
questions — see §13.)

## 12. What success means

Success is described qualitatively. The direction we want:

> A child increasingly experiences the routines that are appropriate for them
> as something they can do themselves — and eventually as simply theirs —
> while the parent stays the authority over what matters and the daily
> friction eases.

Success is **not** defined as: a count or percentage of routines owned; how
fast routines advance; declining parental involvement; app usage or retention;
completion rates; or any streak. Some of those may one day be internal
research signals; none is the product's promise or a family-facing measure,
and none may become an optimisation target
([CORE_PRINCIPLES](./CORE_PRINCIPLES.md) anti-patterns,
[OWNERSHIP_MODEL](../experience/OWNERSHIP_MODEL.md) §9).

## 13. Boundaries and open questions

QuestGrow is **pro-child-agency and pro-parent-authority at the same time.**
It does not ask parents to step back from parenting; it gives a child room to
take real responsibility for everyday routines without the parent giving up
judgment. The goal is not parental withdrawal — it is child agency without
surrendering parental judgment.

The following product questions are **open and deliberately unresolved** (one
in part — OQ-A). This document makes no claim that depends on their answers:

- **OQ-A** — Is the ~3–4 experience a complete product, or primarily an
  on-ramp where ownership is latent?
  *MVP-scope aspect decided by [DECISION-019](../governance/DECISION_LOG.md):
  the MVP is an on-ramp. The long-term product-identity aspect remains open.*
- **OQ-B** — Does the ~7–8 experience evolve toward broader
  responsibility-management?
- **OQ-C** — Should celebrations / rewards wind down for `CHILD_OWNED`
  routines? (`DECISION-012` — normal reward value — stands.)
- **OQ-D** — Does owning individual routines generalise into a broader
  disposition toward responsibility?
- **OQ-E** — Should QuestGrow ever offer developmental framing or advice to
  parents?
- **OQ-F** — Is the mature state continued engagement with the app, or the app
  fading from the child's routine?
- **OQ-G** — Should day-one positioning lead with "less conflict / clarity" or
  with the ownership thesis?
- **OQ-H** — Should the product explicitly promise decreasing parental
  involvement, or only increasing child ownership?

These are tracked in
[OWNERSHIP_MODEL → Open questions](../experience/OWNERSHIP_MODEL.md).

## Long-term direction

Once the core loop is proven, optional depth can be layered on *for the
child* without adding child-side complexity: collectible characters, evolving
worlds, storybooks unlocked by weeks of participation, seasonal themes. All
optional, all parent-toggleable, none required to get value. This is flavour
layered on the arc — never the arc itself, and never a progression the child
must keep up with.

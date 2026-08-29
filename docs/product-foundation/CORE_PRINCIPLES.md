# QuestGrow Core Principles

> **Status:** Constitutional. This is the "قانون اساسی" (constitution) of
> QuestGrow. It sits alongside the [Manifesto](./MANIFESTO.md) at the top of
> the [product foundation](../README.md) and outranks every other document.

## Purpose

These principles define the non-negotiable product philosophy of QuestGrow.

Every significant product, UX, gamification, and technical decision should be
evaluated against these principles.

If a proposed feature conflicts with a core principle, the conflict must be
explicitly documented and consciously accepted before implementation.

> **Acceptance-criteria rule.** Every significant QuestGrow product decision
> must be evaluated against these principles. If a feature violates a core
> principle, it requires an explicit product decision and documented
> justification. Rejections should cite the principle numbers, e.g.
> *"Rejected — violates Core Principles #6, #9 and #24."*

## A. Child Experience

### 1. Child-first simplicity
The child is the primary interaction constraint.

### 2. Visual before textual
For young children, visual communication should dominate text wherever
possible.

### 3. Immediate understanding
The child should quickly understand what is expected without navigating
complex interfaces.

### 4. Child agency within boundaries
Children should feel ownership of their progress without having authority over
protected state.

## B. Real World & Behavior

### 5. Real-world first
The purpose of QuestGrow is behavior outside the application.

### 6. Short screen interaction
The app should encourage brief interactions and return the child to real life.

### 7. Behavior over engagement
Success is measured by positive real-world behavior, not time spent in the
app.

### 8. Progress over perfection
Consistency and improvement matter more than flawless completion.

## C. Gamification

### 9. Gamification without addiction
Game mechanics must motivate without creating compulsive engagement.

### 10. Celebrate, don't manipulate
Rewards and animations should celebrate achievement rather than exploit
attention.

### 11. Meaningful progression
Progress should communicate genuine accomplishment.

### 12. No shame or punishment mechanics
The system should avoid humiliation, fear, negative social comparison, or
punitive mechanics.

## D. Parent & Trust

### 13. Parent authority, child agency
Parents define the environment; children participate actively within it.

### 14. Trust before points
Important rewards must not be self-awarded without appropriate verification.

### 15. Verification is part of the game loop
Parent approval should feel natural, fast, and positive.

### 16. Parent controls complexity
Complex configuration belongs on the parent side. The child's interface must
remain simple.

## E. Personalization

### 17. Flexible quests
Parents can define goals appropriate for their family.

### 18. Age-aware experience
The experience should adapt approximately across ages 3–8.

### 19. Family-specific goals
There is no universal definition of the perfect routine.

### 20. Adaptation over rigid rules
The product should support changing goals as children grow.

## F. Product Philosophy

### 21. QuestGrow is not a checklist
Tasks should be presented as meaningful quests and experiences, not
administrative checkboxes.

### 22. QuestGrow is not surveillance
The product should support parenting, not monitor or control children
excessively.

### 23. QuestGrow must not increase screen dependency
The product should never optimize for unnecessary screen time.

### 24. Every feature must serve real-world development
A feature should have a clear connection to the child's development, routine,
independence, or healthy behavior.

## Decision Rule

Before introducing a significant feature, ask:

1. Does it help the child do something meaningful in the real world?
2. Does it preserve child-first simplicity?
3. Does it avoid unnecessary screen engagement?
4. Does it preserve appropriate parent authority?
5. Does it reinforce rather than manipulate?
6. Can the feature be explained simply to a young child?

If the answer is no to a core principle, document the conflict and rationale
before implementation.

## Anti-Patterns

The following should be treated as warning signs:

- Infinite scrolling
- Engagement loops
- Excessive notifications
- Punishment mechanics
- Shame-based scoring
- Competitive leaderboards
- Unverified self-awarded points
- Complex child-facing configuration
- Excessive text
- Features whose primary purpose is increasing app usage

## Relationship to MVP

These principles are not MVP features.

They are constraints that define how MVP features must be designed.

The MVP should implement the smallest possible system that demonstrates the
QuestGrow philosophy while remaining consistent with these principles.

See [`../product-delivery/MVP.md`](../product-delivery/MVP.md) for what the
first version builds, and [`../product-delivery/ROADMAP.md`](../product-delivery/ROADMAP.md)
for what comes after.

## Where each principle is applied

| Principles | Primary documents |
|---|---|
| A. Child Experience (1–4) | [UX_PRINCIPLES](../experience/UX_PRINCIPLES.md), [CHILD_JOURNEY](../experience/CHILD_JOURNEY.md), [DESIGN_PRINCIPLES](../experience/DESIGN_PRINCIPLES.md) |
| B. Real World & Behavior (5–8) | [MANIFESTO](./MANIFESTO.md), [PRODUCT_VISION](./PRODUCT_VISION.md), [GAMIFICATION](../game-design/GAMIFICATION.md) |
| C. Gamification (9–12) | [GAMIFICATION](../game-design/GAMIFICATION.md), [REWARD_MODEL](../game-design/REWARD_MODEL.md) |
| D. Parent & Trust (13–16) | [PARENT_CHILD_MODEL](../trust-and-safety/PARENT_CHILD_MODEL.md), [VERIFICATION](../trust-and-safety/VERIFICATION.md), [PARENT_JOURNEY](../experience/PARENT_JOURNEY.md) |
| E. Personalization (17–20) | [QUEST_MODEL](../game-design/QUEST_MODEL.md), [UX_PRINCIPLES → age adaptation](../experience/UX_PRINCIPLES.md) |
| F. Product Philosophy (21–24) | [MANIFESTO](./MANIFESTO.md), [ARCHITECTURE](../product-delivery/ARCHITECTURE.md) |

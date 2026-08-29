"""QuestGrowService — the server.

Every write to authoritative state passes through here behind a scope check
(TECHNICAL_MODEL §5). This is the code-level trust boundary: INV-5, INV-6,
INV-10, INV-12, INV-15, INV-17, INV-18 are enforced in this module rather
than in a UI.

Subsystems represented (ARCHITECTURE "API services"):
  * auth / actor scoping                    -> _require_parent / _require_child
  * quest & schedule service                -> create_quest / set_schedule / materialise_day
  * ownership stage service                 -> set_ownership_stage / suggestions
  * completion / verification service       -> submit_completion / approve / not_yet / record_completion
  * progress ledger                         -> _award_earn / apply_adjustment (append-only)
  * rewards service                         -> redeem_reward / grant_redemption
  * age-adaptation resolver (default stage) -> assign_quest
  * notification service                    -> EventSink
"""

from __future__ import annotations

import itertools
from datetime import date, datetime, timezone

from .entities import (
    Account,
    AdvancementSuggestion,
    AuditLogEntry,
    Child,
    ChildQuest,
    CompletionRequest,
    LedgerEntry,
    ParentReview,
    Quest,
    QuestId,
    QuestInstance,
    QuestSchedule,
    Reward,
    RewardRedemption,
)
from .enums import (
    Actor,
    InstanceState,
    LedgerKind,
    OwnershipStage,
    RedemptionMode,
    RedemptionState,
    VerificationBehaviour,
    verification_behaviour,
)
from .errors import AuthorizationError, ContractViolation, NotFound
from .events import CelebrationEvent, EventSink
from .ownership import (
    counter_after_completed,
    counter_after_not_yet,
    counter_after_transition,
    default_stage_for_age_band,
    plan_transition,
    should_suggest_advancement,
)
from .projections import (
    TodayItem,
    TodayPayload,
    WeeklyConsistency,
    lifetime_achievement,
    spendable_balance,
)
from .repository import InMemoryRepository, InstanceRecord
from .scheduling import due_on
from .scope import ChildScope, ParentScope, ServerScope

DEFAULT_ADVANCEMENT_THRESHOLD = 8  # DECISION-009: tunable product default, lives in config.


def _now() -> datetime:
    return datetime.now(timezone.utc)


class QuestGrowService:
    def __init__(
        self,
        repo: InMemoryRepository | None = None,
        events: EventSink | None = None,
        advancement_threshold: int = DEFAULT_ADVANCEMENT_THRESHOLD,
        pending_grace_days: int | None = 1,  # IL-1 resolved: pending survives to date+N, then expires silently (None == never)
    ) -> None:
        self.repo = repo or InMemoryRepository()
        self.events = events or EventSink()
        self.advancement_threshold = advancement_threshold
        self.pending_grace_days = pending_grace_days
        self._ids = itertools.count(1)

    def _id(self, prefix: str) -> str:
        return f"{prefix}-{next(self._ids)}"

    # ------------------------------------------------------------------ #
    # scope guards (TECHNICAL_MODEL §5)                                  #
    # ------------------------------------------------------------------ #
    def _require_parent(self, scope) -> ParentScope:
        if not isinstance(scope, ParentScope):
            raise AuthorizationError("parent scope required for this operation")
        return scope

    def _require_child(self, scope, child_id: str) -> ChildScope:
        if not isinstance(scope, ChildScope):
            raise AuthorizationError("child scope required for this operation")
        if scope.child_id != child_id:  # INV-18: own childId only
            raise AuthorizationError("child scope may act only for its own childId")
        return scope

    def _parent_owns_child(self, parent: ParentScope, child_id: str) -> Child:
        child = self.repo.children.get(child_id)
        if child is None:
            raise NotFound(f"child {child_id}")
        if child.account_id != parent.account_id:
            raise AuthorizationError("parent does not own this child")
        return child

    def _audit(self, actor: str, action: str, target: str, before: str, after: str) -> None:
        self.repo.append_audit(
            AuditLogEntry(self._id("audit"), actor, action, target, before, after, _now())
        )

    # ------------------------------------------------------------------ #
    # parent setup — quests, schedules, rewards, children                #
    # ------------------------------------------------------------------ #
    def create_account(self, account_id: str, points_enabled: bool = True) -> Account:
        a = Account(account_id=account_id, points_enabled=points_enabled)
        self.repo.add_account(a)
        return a

    def add_child(self, parent, *, child_id: str, name: str, age_band: str = "5-6") -> Child:
        p = self._require_parent(parent)
        if child_id in self.repo.children:
            raise ContractViolation("child_id already exists")
        c = Child(child_id=child_id, account_id=p.account_id, name=name, age_band=age_band)
        self.repo.add_child(c)
        return c

    def create_quest(
        self, parent, *, quest_id: str, title: str, icon: str, points: int = 10
    ) -> Quest:
        p = self._require_parent(parent)
        if points < 0:
            raise ContractViolation("quest points must be >= 0")
        q = Quest(
            id=QuestId(quest_id, 1),
            account_id=p.account_id,
            title=title,
            icon=icon,
            points=points,
        )
        self.repo.add_quest(q)
        return q

    def edit_quest(self, parent, *, quest_id: str, **changes) -> Quest:
        """Edits are versioned/forward-applying (§2). Existing instances keep
        the version they were created under.
        """
        self._require_parent(parent)
        current = self.repo.latest_quest(quest_id)
        if current is None:
            raise NotFound(f"quest {quest_id}")
        new = Quest(
            id=QuestId(quest_id, current.id.version + 1),
            account_id=current.account_id,
            title=changes.get("title", current.title),
            icon=changes.get("icon", current.icon),
            points=changes.get("points", current.points),
            age_suitability=changes.get("age_suitability", current.age_suitability),
            active=changes.get("active", current.active),
            archived=changes.get("archived", current.archived),
        )
        if new.points < 0:
            raise ContractViolation("quest points must be >= 0")
        self.repo.add_quest(new)
        return new

    def set_schedule(
        self, parent, *, quest_id: str, schedule: QuestSchedule
    ) -> QuestSchedule:
        self._require_parent(parent)
        self.repo.add_schedule(schedule)
        return schedule

    def create_reward(
        self, parent, *, reward_id: str, name: str, icon: str, cost: int, mode: RedemptionMode
    ) -> Reward:
        p = self._require_parent(parent)
        if cost < 0:
            raise ContractViolation("reward cost must be >= 0")
        r = Reward(reward_id, p.account_id, name, icon, cost, mode)
        self.repo.rewards[reward_id] = r
        return r

    # ------------------------------------------------------------------ #
    # quest assignment — age-adaptation resolver produces default stage  #
    # ------------------------------------------------------------------ #
    def assign_quest(self, parent, *, child_id: str, quest_id: str) -> ChildQuest:
        p = self._require_parent(parent)
        child = self._parent_owns_child(p, child_id)
        if self.repo.latest_quest(quest_id) is None:
            raise NotFound(f"quest {quest_id}")
        stage = default_stage_for_age_band(child.age_band)  # DECISION-019: always PARENT_GUIDED in MVP
        cq = ChildQuest(
            child_id=child_id,
            quest_id=quest_id,
            ownership_stage=stage,
            consecutive_ok_count=0,
            assigned_at=_now(),
        )
        self.repo.put_child_quest(cq)
        self._audit(
            f"parent:{p.account_id}", "assign_quest", f"child_quest:{child_id}:{quest_id}",
            "-", stage.value,
        )
        return cq

    # ------------------------------------------------------------------ #
    # ownership stage service (TECHNICAL_MODEL §3, INV-5/6, DECISION-017) #
    # ------------------------------------------------------------------ #
    def set_ownership_stage(
        self, parent, *, child_id: str, quest_id: str, target: OwnershipStage
    ):
        """Advance (one or more stages — DECISION-017), regress (any earlier
        stage — OWNERSHIP_MODEL §7), or no-op. Parent scope only (INV-5). The
        system never calls this (INV-6). Any transition resets
        ``consecutive_ok_count`` (TOQ-2) and clears an outstanding suggestion.

        Returns the ``TransitionPlan`` so the UI can name bypassed stages.
        """
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        cq = self.repo.get_child_quest(child_id, quest_id)
        if cq is None:
            raise NotFound(f"child_quest {child_id}:{quest_id}")

        plan = plan_transition(cq.ownership_stage, target)
        if plan.direction == "noop":
            return plan

        before = cq.ownership_stage.value
        cq.ownership_stage = target
        cq.consecutive_ok_count = counter_after_transition(cq.consecutive_ok_count)
        self.repo.suggestions.pop((child_id, quest_id), None)
        self._audit(
            f"parent:{p.account_id}",
            f"ownership_{plan.direction}",
            f"child_quest:{child_id}:{quest_id}",
            before,
            target.value,
        )
        return plan

    def advancement_suggestions(self, parent, *, child_id: str) -> list[AdvancementSuggestion]:
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        return [
            s for (c, _), s in self.repo.suggestions.items()
            if c == child_id and not s.dismissed
        ]

    def accept_advancement_suggestion(self, parent, *, child_id: str, quest_id: str):
        s = self.repo.suggestions.get((child_id, quest_id))
        if s is None or s.dismissed:
            raise NotFound("no outstanding advancement suggestion")
        return self.set_ownership_stage(
            parent, child_id=child_id, quest_id=quest_id, target=s.to_stage
        )

    def dismiss_advancement_suggestion(
        self, parent, *, child_id: str, quest_id: str, permanent: bool = False
    ) -> None:
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        key = (child_id, quest_id)
        s = self.repo.suggestions.get(key)
        if s is None:
            return
        if permanent:
            self.repo.suggestions[key] = AdvancementSuggestion(
                s.child_id, s.quest_id, s.from_stage, s.to_stage, dismissed=True
            )
        else:
            self.repo.suggestions.pop(key, None)  # "ask me later" — may re-emit

    # ------------------------------------------------------------------ #
    # scheduling / clock                                                 #
    # ------------------------------------------------------------------ #
    def materialise_day(self, day: date) -> list[QuestInstance]:
        """Eager per-day instance materialisation (TOQ-7). One
        ``QuestInstance`` per assigned quest that is scheduled on ``day``.
        """
        created: list[QuestInstance] = []
        for (child_id, quest_id), cq in self.repo.child_quests.items():
            latest = self.repo.latest_quest(quest_id)
            if latest is None or latest.archived or not latest.active:
                continue
            schedule = self.repo.schedules.get(quest_id)
            if schedule is None or not due_on(schedule, day):
                continue
            key = (quest_id, latest.id.version, child_id, day.isoformat())
            if key in self.repo.instances:
                continue
            inst = QuestInstance(
                quest_id=quest_id,
                quest_version=latest.id.version,
                child_id=child_id,
                on_date=day,
            )
            self.repo.instances[key] = InstanceRecord(inst)
            created.append(inst)
        return created

    def end_of_day(self, day: date) -> list[QuestInstance]:
        """Scheduled sweep (server/system actor). Transitions instances to
        ``expired`` (§4). Never touches ``ownership_stage`` (INV-6).

        IL-1 (resolved — TECHNICAL_MODEL §4, PARENT_CHILD_MODEL):
        * ``available`` instances expire the night they were due.
        * ``pending`` instances are **not** swept the same day (the child must
          be able to see "waiting for grown-up" on their next session, per
          VERIFICATION). A ``pending`` instance expires on the first
          end-of-day sweep that is >= ``pending_grace_days`` days past its
          occurrence date (default 1: survives the occurrence day, then
          expires the following day if still unresolved). It expires
          **silently**: rolls over per schedule, no child signal, no counter
          effect (``expired`` is neutral, DECISION-018).
        ``pending_grace_days = None`` disables pending expiry entirely.
        """
        expired: list[QuestInstance] = []
        for rec in self.repo.instances.values():
            inst = rec.instance
            if inst.on_date > day:
                continue
            if inst.state is InstanceState.AVAILABLE:
                inst.state = InstanceState.EXPIRED
                expired.append(inst)
            elif (
                inst.state is InstanceState.PENDING
                and self.pending_grace_days is not None
                and (day - inst.on_date).days >= self.pending_grace_days
            ):
                inst.state = InstanceState.EXPIRED
                expired.append(inst)
            # DECISION-018: expiry is neutral for consecutive_ok_count — no counter change here.
        return expired

    # ------------------------------------------------------------------ #
    # completion / verification service (TECHNICAL_MODEL §4)             #
    # ------------------------------------------------------------------ #
    def _get_instance(self, quest_id: str, child_id: str, day: date) -> QuestInstance:
        latest = self.repo.latest_quest(quest_id)
        if latest is None:
            raise NotFound(f"quest {quest_id}")
        key = (quest_id, latest.id.version, child_id, day.isoformat())
        rec = self.repo.instances.get(key)
        if rec is None:
            raise NotFound(f"no quest instance for {child_id}:{quest_id}:{day.isoformat()}")
        return rec.instance

    def _child_quest(self, child_id: str, quest_id: str) -> ChildQuest:
        cq = self.repo.get_child_quest(child_id, quest_id)
        if cq is None:
            raise NotFound(f"child_quest {child_id}:{quest_id}")
        return cq

    def submit_completion(
        self, child_scope, *, child_id: str, quest_id: str, day: date, note: str = ""
    ) -> QuestInstance:
        """Child *intent* (INV-18). The server decides the resulting state from
        the ownership stage (INV-10). The child never writes state directly.

        * IMMEDIATE  -> verified (+ celebration + one earn ledger entry)
        * REQUIRES_APPROVAL -> pending
        * PARENT_RECORDS -> rejected: no child self-mark path (AC-11)
        """
        self._require_child(child_scope, child_id)
        cq = self._child_quest(child_id, quest_id)
        inst = self._get_instance(quest_id, child_id, day)
        behaviour = verification_behaviour(cq.ownership_stage)

        if behaviour is VerificationBehaviour.PARENT_RECORDS:
            raise ContractViolation(
                "no child self-mark path at PARENT_MANAGED — a parent records this completion"
            )
        if inst.state not in (InstanceState.AVAILABLE,):
            raise ContractViolation(f"instance is {inst.state}, cannot submit")

        self.repo.completion_requests[self._id("cr")] = CompletionRequest(
            id=self._id("cr"),
            quest_instance_key=inst.key,
            child_id=child_id,
            created_at=_now(),
            note=note,
        )

        if behaviour is VerificationBehaviour.IMMEDIATE:
            self._transition_to_verified(inst, cq)
        else:  # REQUIRES_APPROVAL
            inst.state = InstanceState.PENDING
        return inst

    def record_completion(
        self, parent, *, child_id: str, quest_id: str, day: date
    ) -> QuestInstance:
        """PARENT_RECORDS path (§4): the parent records a completion they
        witnessed. Only valid at ``PARENT_MANAGED``.
        """
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        cq = self._child_quest(child_id, quest_id)
        if verification_behaviour(cq.ownership_stage) is not VerificationBehaviour.PARENT_RECORDS:
            raise ContractViolation("record_completion is only for PARENT_MANAGED quests")
        inst = self._get_instance(quest_id, child_id, day)
        if inst.state is InstanceState.VERIFIED:
            return inst
        if inst.state is not InstanceState.AVAILABLE:
            raise ContractViolation(f"instance is {inst.state}, cannot record")
        self._transition_to_verified(inst, cq)
        return inst

    def approve(self, parent, *, child_id: str, quest_id: str, day: date) -> QuestInstance:
        """Parent approves a ``pending`` completion (§4). pending -> verified."""
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        cq = self._child_quest(child_id, quest_id)
        inst = self._get_instance(quest_id, child_id, day)
        if inst.state is InstanceState.VERIFIED:
            return inst  # idempotent (INV-11)
        if inst.state is not InstanceState.PENDING:
            raise ContractViolation(f"instance is {inst.state}, nothing to approve")
        self._transition_to_verified(inst, cq)
        return inst

    def not_yet(
        self, parent, *, child_id: str, quest_id: str, day: date, note: str = ""
    ) -> QuestInstance:
        """Parent declines *this instance only* (§4). pending -> available.
        No penalty, no points delta, no negative child signal. Resets
        ``consecutive_ok_count`` to 0 (DECISION-009).
        """
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        cq = self._child_quest(child_id, quest_id)
        inst = self._get_instance(quest_id, child_id, day)
        if inst.state is not InstanceState.PENDING:
            raise ContractViolation(f"instance is {inst.state}, nothing to decline")
        inst.state = InstanceState.AVAILABLE
        inst.parent_note = note
        cq.consecutive_ok_count = counter_after_not_yet(cq.consecutive_ok_count)
        self.repo.suggestions.pop((child_id, quest_id), None)
        return inst

    def _transition_to_verified(self, inst: QuestInstance, cq: ChildQuest) -> None:
        """The single place an instance becomes ``verified`` (INV-10). Emits
        the celebration and writes exactly one ``earn`` (INV-11), increments
        ``consecutive_ok_count`` (+1 — §4), and (re)computes the advancement
        suggestion.
        """
        if inst.state is InstanceState.VERIFIED:
            return  # idempotent
        inst.state = InstanceState.VERIFIED
        inst.stage_at_completion = cq.ownership_stage

        points = self._award_earn(inst, cq)
        self.events.emit_celebration(
            CelebrationEvent(
                child_id=inst.child_id,
                quest_id=inst.quest_id,
                on_date=inst.on_date.isoformat(),
                points_awarded=points,
                at=_now(),
            )
        )
        cq.consecutive_ok_count = counter_after_completed(cq.consecutive_ok_count)
        self._maybe_suggest_advancement(cq)

    def _award_earn(self, inst: QuestInstance, cq: ChildQuest) -> int:
        """One ``earn`` per verified completion, idempotent on the QuestInstance
        identity (INV-11 / TOQ-3). Points come from the Quest only, never the
        stage (INV-14). Skipped entirely when points are disabled account-wide.
        """
        child = self.repo.children[inst.child_id]
        account = self.repo.accounts[child.account_id]
        quest = self.repo.get_quest(QuestId(inst.quest_id, inst.quest_version))
        assert quest is not None
        if not account.points_enabled or quest.points == 0:
            return 0
        key = f"earn:{inst.quest_id}@{inst.quest_version}:{inst.child_id}:{inst.on_date.isoformat()}"
        entry = LedgerEntry(
            id=self._id("led"),
            child_id=inst.child_id,
            kind=LedgerKind.EARN,
            points=quest.points,   # INV-14: function of the quest only
            source=f"completion:{inst.quest_id}@{inst.quest_version}:{inst.on_date.isoformat()}",
            created_at=_now(),
            idempotency_key=key,
        )
        self.repo.append_ledger(entry)  # False on replay → no second entry (INV-11)
        return quest.points

    def _maybe_suggest_advancement(self, cq: ChildQuest) -> None:
        nxt = should_suggest_advancement(
            cq.ownership_stage, cq.consecutive_ok_count, self.advancement_threshold
        )
        if nxt is None:
            return
        key = (cq.child_id, cq.quest_id)
        existing = self.repo.suggestions.get(key)
        if existing is not None and existing.dismissed:
            return  # permanently dismissed
        # at most one outstanding suggestion per ChildQuest (§3)
        self.repo.suggestions[key] = AdvancementSuggestion(
            child_id=cq.child_id,
            quest_id=cq.quest_id,
            from_stage=cq.ownership_stage,
            to_stage=nxt,
        )

    # ------------------------------------------------------------------ #
    # parent review — non-blocking (§4, INV-15)                          #
    # ------------------------------------------------------------------ #
    def create_parent_review(
        self, parent, *, child_id: str, quest_id: str, day: date, note: str, flagged: bool = False
    ) -> ParentReview:
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        inst = self._get_instance(quest_id, child_id, day)
        review = ParentReview(
            id=self._id("pr"),
            quest_instance_key=inst.key,
            child_id=child_id,
            note=note,
            created_at=_now(),
            flagged_problem=flagged,
        )
        self.repo.parent_reviews.append(review)
        # INV-15: deliberately nothing else happens — no state change, no ledger
        # touch, no celebration reversal, even when flagged=True.
        return review

    # ------------------------------------------------------------------ #
    # progress ledger — adjustments (§6)                                 #
    # ------------------------------------------------------------------ #
    def apply_adjustment(self, parent, *, child_id: str, amount: int, reason: str = "") -> LedgerEntry:
        """Parent-instructed, additive-only in MVP (TOQ-5, INV-12). No
        automated/system trigger exists.
        """
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        if amount <= 0:
            raise ContractViolation("adjustments are additive-only in MVP (amount must be > 0)")
        entry = LedgerEntry(
            id=self._id("led"),
            child_id=child_id,
            kind=LedgerKind.ADJUSTMENT,
            points=amount,
            source=f"parent_adjustment:{p.account_id}:{reason}",
            created_at=_now(),
            idempotency_key=self._id("adj"),
        )
        self.repo.append_ledger(entry)
        self._audit(f"parent:{p.account_id}", "adjustment", f"ledger:{child_id}", "-", str(amount))
        return entry

    # ------------------------------------------------------------------ #
    # rewards service (§6)                                               #
    # ------------------------------------------------------------------ #
    def redeem_reward(self, child_scope, *, child_id: str, reward_id: str) -> RewardRedemption:
        self._require_child(child_scope, child_id)
        reward = self.repo.rewards.get(reward_id)
        if reward is None:
            raise NotFound(f"reward {reward_id}")
        balance = spendable_balance(self.repo.ledger_for(child_id))
        red = RewardRedemption(
            id=self._id("rr"),
            reward_id=reward_id,
            child_id=child_id,
            state=RedemptionState.PENDING,
            requested_at=_now(),
        )
        if reward.redemption_mode is RedemptionMode.SELF_SERVICE:
            if balance < reward.cost:
                raise ContractViolation("insufficient Spendable Balance")
            self._write_redeem(child_id, reward)
            red.state = RedemptionState.GRANTED
            red.resolved_at = _now()
        self.repo.redemptions[red.id] = red
        return red

    def grant_redemption(self, parent, *, redemption_id: str) -> RewardRedemption:
        p = self._require_parent(parent)
        red = self.repo.redemptions.get(redemption_id)
        if red is None:
            raise NotFound(f"redemption {redemption_id}")
        self._parent_owns_child(p, red.child_id)
        if red.state is not RedemptionState.PENDING:
            raise ContractViolation(f"redemption is {red.state}")
        reward = self.repo.rewards[red.reward_id]
        balance = spendable_balance(self.repo.ledger_for(red.child_id))
        if balance < reward.cost:
            raise ContractViolation("insufficient Spendable Balance")
        self._write_redeem(red.child_id, reward)
        red.state = RedemptionState.GRANTED
        red.resolved_at = _now()
        return red

    def decline_redemption(self, parent, *, redemption_id: str) -> RewardRedemption:
        p = self._require_parent(parent)
        red = self.repo.redemptions.get(redemption_id)
        if red is None:
            raise NotFound(f"redemption {redemption_id}")
        self._parent_owns_child(p, red.child_id)
        red.state = RedemptionState.DECLINED  # gentle, no penalty (§6)
        red.resolved_at = _now()
        return red

    def _write_redeem(self, child_id: str, reward: Reward) -> None:
        entry = LedgerEntry(
            id=self._id("led"),
            child_id=child_id,
            kind=LedgerKind.REDEEM,
            points=-abs(reward.cost),  # non-positive; affects Spendable Balance only (INV-13)
            source=f"redeem:{reward.reward_id}",
            created_at=_now(),
            idempotency_key=self._id("rdm"),
        )
        self.repo.append_ledger(entry)

    # ------------------------------------------------------------------ #
    # read models / projections (§7) — never authoritative               #
    # ------------------------------------------------------------------ #
    def today(self, child_scope, *, child_id: str, day: date) -> TodayPayload:
        self._require_child(child_scope, child_id)
        return self._today_payload(child_id, day)

    def _today_payload(self, child_id: str, day: date) -> TodayPayload:
        items: list[TodayItem] = []
        for (c_id, quest_id), cq in self.repo.child_quests.items():
            if c_id != child_id:
                continue
            latest = self.repo.latest_quest(quest_id)
            if latest is None:
                continue
            key = (quest_id, latest.id.version, child_id, day.isoformat())
            rec = self.repo.instances.get(key)
            if rec is None or rec.instance.state is InstanceState.EXPIRED:
                continue
            st = rec.instance.state
            child_visible = "available" if st is InstanceState.NOT_YET else st.value
            behaviour = verification_behaviour(cq.ownership_stage)
            items.append(
                TodayItem(
                    quest_id=quest_id,
                    title=latest.title,
                    icon=latest.icon,
                    state=child_visible,
                    waits_for_grownup=(behaviour is VerificationBehaviour.REQUIRES_APPROVAL),
                    # INV-8: no ownership_stage / stage label / verdict here
                )
            )
        ledger = self.repo.ledger_for(child_id)
        return TodayPayload(
            child_id=child_id,
            on_date=day.isoformat(),
            items=tuple(items),
            lifetime_achievement=lifetime_achievement(ledger),
            spendable_balance=spendable_balance(ledger),
        )

    def lifetime_achievement(self, *, child_id: str) -> int:
        return lifetime_achievement(self.repo.ledger_for(child_id))

    def spendable_balance(self, *, child_id: str) -> int:
        return spendable_balance(self.repo.ledger_for(child_id))

    def weekly_consistency(self, *, child_id: str, week_start: date) -> WeeklyConsistency:
        active = set()
        for rec in self.repo.instances.values():
            inst = rec.instance
            if inst.child_id != child_id or inst.state is not InstanceState.VERIFIED:
                continue
            delta = (inst.on_date - week_start).days
            if 0 <= delta < 7:
                active.add(inst.on_date)
        return WeeklyConsistency(child_id, week_start.isoformat(), len(active))

    def approvals_queue(self, parent, *, child_id: str) -> list[QuestInstance]:
        p = self._require_parent(parent)
        self._parent_owns_child(p, child_id)
        return [
            rec.instance
            for rec in self.repo.instances.values()
            if rec.instance.child_id == child_id and rec.instance.state is InstanceState.PENDING
        ]

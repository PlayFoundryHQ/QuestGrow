"""HTTP API (C2) — FastAPI transport over ``QuestGrowService``.

The API adds **no** domain rules. Every endpoint resolves a bearer token to a
``ChildScope`` / ``ParentScope`` and calls the service, which is the authority
(TECHNICAL_MODEL §5). The transport layer mirrors the actor matrix so a forged
or cross-scope request is refused with 403 *before* the service is touched,
and again by the service guard.

INV-8 boundary: the child-facing response models below
(``TodayOut`` / ``TodayItemOut`` / ``ProgressOut`` / ``CompletionOut``) carry
no ``ownership_stage`` / stage label / readiness field — asserted by
``tests/test_api.py`` against the generated OpenAPI schema.

Auth here is deliberately thin (opaque random tokens, in-memory store). C3
replaces the store with password login + PIN parent-gate behind the same
``TokenStore.resolve`` seam.
"""

from __future__ import annotations

import secrets
from datetime import date, datetime

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

from .adaptation import ComplexityProfile
from .entities import QuestSchedule
from .enums import OwnershipStage, Recurrence, RedemptionMode
from .errors import AuthorizationError, ContractViolation, NotFound
from .scope import ChildScope, ParentScope, Scope
from .service import QuestGrowService


class TokenStore:
    """token (opaque) -> Scope. C3 swaps the issuance path for real auth."""

    def __init__(self) -> None:
        self._scopes: dict[str, Scope] = {}

    def issue_parent(self, account_id: str) -> str:
        tok = "p_" + secrets.token_urlsafe(24)
        self._scopes[tok] = ParentScope(account_id)
        return tok

    def issue_child(self, child_id: str) -> str:
        tok = "c_" + secrets.token_urlsafe(24)
        self._scopes[tok] = ChildScope(child_id)
        return tok

    def resolve(self, token: str) -> Scope | None:
        return self._scopes.get(token)


# ----------------------------------------------------------------------- #
# wire models                                                             #
# ----------------------------------------------------------------------- #
class ChildIn(BaseModel):
    child_id: str
    name: str
    age_band: str = "5-6"
    avatar: str = ""


class ChildProfileIn(BaseModel):
    name: str | None = None
    avatar: str | None = None
    age_band: str | None = None
    adaptation_overrides: dict[str, str] | None = None


class ChildOut(BaseModel):
    child_id: str
    name: str
    age_band: str
    avatar: str


class QuestIn(BaseModel):
    quest_id: str
    title: str
    icon: str
    points: int = 10


class QuestEditIn(BaseModel):
    title: str | None = None
    icon: str | None = None
    points: int | None = None
    active: bool | None = None
    archived: bool | None = None


class QuestOut(BaseModel):
    quest_id: str
    version: int
    title: str
    icon: str
    points: int
    active: bool
    archived: bool


class ScheduleIn(BaseModel):
    recurrence: Recurrence = Recurrence.DAILY
    weekdays: list[int] = []


class RewardIn(BaseModel):
    reward_id: str
    name: str
    icon: str
    cost: int
    mode: RedemptionMode


class RewardEditIn(BaseModel):
    name: str | None = None
    icon: str | None = None
    cost: int | None = None
    mode: RedemptionMode | None = None
    active: bool | None = None


class RewardOut(BaseModel):
    reward_id: str
    name: str
    icon: str
    cost: int
    mode: RedemptionMode
    active: bool


class AssignIn(BaseModel):
    quest_id: str


class OwnershipIn(BaseModel):
    target: OwnershipStage


class OwnershipPlanOut(BaseModel):
    direction: str
    bypassed: list[OwnershipStage]


class SuggestionOut(BaseModel):
    quest_id: str
    from_stage: OwnershipStage
    to_stage: OwnershipStage


class DayIn(BaseModel):
    day: date


class NotYetIn(BaseModel):
    day: date
    note: str = ""


class ReviewIn(BaseModel):
    quest_id: str
    day: date
    note: str
    flagged: bool = False


class AdjustmentIn(BaseModel):
    amount: int
    reason: str = ""


class ApprovalOut(BaseModel):
    quest_id: str
    on_date: date
    state: str  # always "pending" here — no stage (INV-8 not relevant, parent surface, but kept minimal)


class DashboardOut(BaseModel):
    child_id: str
    on_date: date
    total: int
    verified: int
    pending: int
    available: int
    expired: int
    week_active_days: int


class RedemptionOut(BaseModel):
    id: str
    reward_id: str
    state: str


# --- child-facing (INV-8: NO stage / level / readiness field) ---------- #
class TodayItemOut(BaseModel):
    quest_id: str
    title: str
    icon: str
    state: str
    waits_for_grownup: bool


class TodayOut(BaseModel):
    child_id: str
    on_date: date
    items: list[TodayItemOut]
    lifetime_achievement: int
    spendable_balance: int
    complexity_profile: ComplexityProfile


class CompletionOut(BaseModel):
    quest_id: str
    state: str  # child-visible subset only


class CelebrationOut(BaseModel):
    quest_id: str
    on_date: str
    points_awarded: int
    at: datetime


class NotificationOut(BaseModel):
    child_id: str
    kind: str
    text: str
    at: datetime


class NotificationsPrefIn(BaseModel):
    enabled: bool


class ProgressOut(BaseModel):
    child_id: str
    lifetime_achievement: int
    spendable_balance: int
    week_active_days: int


# ----------------------------------------------------------------------- #
# app factory                                                             #
# ----------------------------------------------------------------------- #
class SignupIn(BaseModel):
    email: str
    password: str
    pin: str


class LoginIn(BaseModel):
    email: str
    password: str


class UnlockIn(BaseModel):
    session_token: str
    pin: str


class ChildTokenIn(BaseModel):
    child_id: str


def create_app(
    service: QuestGrowService | None = None,
    tokens: TokenStore | None = None,
    auth=None,
) -> FastAPI:
    """``auth`` (an ``auth.AuthService``) is optional. When given, it is the
    token resolver and the ``/auth/*`` routes are mounted; otherwise a bare
    ``TokenStore`` is used (dev / C2 tests)."""
    svc = service or QuestGrowService()
    store = auth or tokens or TokenStore()
    app = FastAPI(title="QuestGrow API", version="0.2.0")
    app.state.service = svc
    app.state.tokens = store
    app.state.auth = auth

    def _scope(authorization: str = Header(default="")) -> Scope:
        if not authorization.startswith("Bearer "):
            raise HTTPException(401, "missing bearer token")
        sc = store.resolve(authorization[7:])
        if sc is None:
            raise HTTPException(401, "invalid token")
        return sc

    def _parent(scope: Scope = Depends(_scope)) -> ParentScope:
        if not isinstance(scope, ParentScope):
            raise HTTPException(403, "parent scope required")
        return scope

    def _child(scope: Scope = Depends(_scope)) -> ChildScope:
        if not isinstance(scope, ChildScope):
            raise HTTPException(403, "child scope required")
        return scope

    @app.exception_handler(AuthorizationError)
    def _auth_err(_req, exc):  # noqa: ANN001
        from fastapi.responses import JSONResponse

        return JSONResponse(status_code=403, content={"detail": str(exc)})

    @app.exception_handler(NotFound)
    def _nf_err(_req, exc):  # noqa: ANN001
        from fastapi.responses import JSONResponse

        return JSONResponse(status_code=404, content={"detail": str(exc)})

    @app.exception_handler(ContractViolation)
    def _cv_err(_req, exc):  # noqa: ANN001
        from fastapi.responses import JSONResponse

        return JSONResponse(status_code=409, content={"detail": str(exc)})

    if auth is not None:
        @app.post("/auth/signup")
        def signup(body: SignupIn):
            return {"account_id": auth.signup(email=body.email, password=body.password,
                                              pin=body.pin)}

        @app.post("/auth/login")
        def login(body: LoginIn):
            return {"session_token": auth.login(email=body.email, password=body.password)}

        @app.post("/auth/unlock")
        def unlock(body: UnlockIn):
            return {"parent_token": auth.unlock_parent(session_token=body.session_token,
                                                       pin=body.pin)}

        @app.post("/auth/child-token")
        def child_token(body: ChildTokenIn, authorization: str = Header(default=""),
                        p: ParentScope = Depends(_parent)):
            # _parent already validated the parent-gate token; pass it through
            tok = auth.issue_child_token(parent_token=authorization[7:], child_id=body.child_id)
            return {"child_token": tok}

    def _quest_out(q) -> QuestOut:
        return QuestOut(
            quest_id=q.id.quest_id, version=q.id.version, title=q.title, icon=q.icon,
            points=q.points, active=q.active, archived=q.archived,
        )

    def _reward_out(r) -> RewardOut:
        return RewardOut(
            reward_id=r.reward_id, name=r.name, icon=r.icon, cost=r.cost,
            mode=r.redemption_mode, active=r.active,
        )

    # -- parent: children --------------------------------------------
    @app.post("/children", response_model=ChildOut)
    def add_child(body: ChildIn, p: ParentScope = Depends(_parent)):
        c = svc.add_child(p, child_id=body.child_id, name=body.name,
                          age_band=body.age_band, avatar=body.avatar)
        return ChildOut(child_id=c.child_id, name=c.name, age_band=c.age_band, avatar=c.avatar)

    @app.patch("/children/{child_id}", response_model=ChildOut)
    def set_child_profile(child_id: str, body: ChildProfileIn, p: ParentScope = Depends(_parent)):
        c = svc.set_child_profile(
            p, child_id=child_id, name=body.name, avatar=body.avatar,
            age_band=body.age_band, adaptation_overrides=body.adaptation_overrides,
        )
        return ChildOut(child_id=c.child_id, name=c.name, age_band=c.age_band, avatar=c.avatar)

    # -- parent: quests / schedules --------------------------------
    @app.post("/quests", response_model=QuestOut)
    def create_quest(body: QuestIn, p: ParentScope = Depends(_parent)):
        return _quest_out(svc.create_quest(p, quest_id=body.quest_id, title=body.title,
                                           icon=body.icon, points=body.points))

    @app.patch("/quests/{quest_id}", response_model=QuestOut)
    def edit_quest(quest_id: str, body: QuestEditIn, p: ParentScope = Depends(_parent)):
        changes = body.model_dump(exclude_none=True)
        return _quest_out(svc.edit_quest(p, quest_id=quest_id, **changes))

    @app.post("/quests/seed-starters", response_model=list[QuestOut])
    def seed_starters(p: ParentScope = Depends(_parent)):
        return [_quest_out(q) for q in svc.seed_starter_quests(p)]

    @app.put("/quests/{quest_id}/schedule")
    def set_schedule(quest_id: str, body: ScheduleIn, p: ParentScope = Depends(_parent)):
        svc.set_schedule(p, quest_id=quest_id,
                         schedule=QuestSchedule(quest_id, body.recurrence,
                                                frozenset(body.weekdays)))
        return {"ok": True}

    # -- parent: rewards ------------------------------------------
    @app.post("/rewards", response_model=RewardOut)
    def create_reward(body: RewardIn, p: ParentScope = Depends(_parent)):
        return _reward_out(svc.create_reward(p, reward_id=body.reward_id, name=body.name,
                                             icon=body.icon, cost=body.cost, mode=body.mode))

    @app.patch("/rewards/{reward_id}", response_model=RewardOut)
    def edit_reward(reward_id: str, body: RewardEditIn, p: ParentScope = Depends(_parent)):
        changes = body.model_dump(exclude_none=True)
        return _reward_out(svc.edit_reward(p, reward_id=reward_id, **changes))

    # -- parent: assignment / ownership -------------------------
    @app.post("/children/{child_id}/quests")
    def assign_quest(child_id: str, body: AssignIn, p: ParentScope = Depends(_parent)):
        cq = svc.assign_quest(p, child_id=child_id, quest_id=body.quest_id)
        return {"child_id": cq.child_id, "quest_id": cq.quest_id}

    @app.put("/children/{child_id}/quests/{quest_id}/ownership", response_model=OwnershipPlanOut)
    def set_ownership(child_id: str, quest_id: str, body: OwnershipIn,
                      p: ParentScope = Depends(_parent)):
        plan = svc.set_ownership_stage(p, child_id=child_id, quest_id=quest_id, target=body.target)
        return OwnershipPlanOut(direction=plan.direction, bypassed=list(plan.bypassed))

    @app.get("/children/{child_id}/suggestions", response_model=list[SuggestionOut])
    def suggestions(child_id: str, p: ParentScope = Depends(_parent)):
        return [
            SuggestionOut(quest_id=s.quest_id, from_stage=s.from_stage, to_stage=s.to_stage)
            for s in svc.advancement_suggestions(p, child_id=child_id)
        ]

    @app.post("/children/{child_id}/quests/{quest_id}/suggestion/accept",
              response_model=OwnershipPlanOut)
    def accept_suggestion(child_id: str, quest_id: str, p: ParentScope = Depends(_parent)):
        plan = svc.accept_advancement_suggestion(p, child_id=child_id, quest_id=quest_id)
        return OwnershipPlanOut(direction=plan.direction, bypassed=list(plan.bypassed))

    @app.post("/children/{child_id}/quests/{quest_id}/suggestion/dismiss")
    def dismiss_suggestion(child_id: str, quest_id: str, permanent: bool = False,
                           p: ParentScope = Depends(_parent)):
        svc.dismiss_advancement_suggestion(p, child_id=child_id, quest_id=quest_id,
                                           permanent=permanent)
        return {"ok": True}

    # -- parent: verification / review / ledger ----------------
    @app.get("/children/{child_id}/approvals", response_model=list[ApprovalOut])
    def approvals(child_id: str, p: ParentScope = Depends(_parent)):
        return [
            ApprovalOut(quest_id=i.quest_id, on_date=i.on_date, state=i.state.value)
            for i in svc.approvals_queue(p, child_id=child_id)
        ]

    @app.post("/children/{child_id}/quests/{quest_id}/approve")
    def approve(child_id: str, quest_id: str, body: DayIn, p: ParentScope = Depends(_parent)):
        i = svc.approve(p, child_id=child_id, quest_id=quest_id, day=body.day)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @app.post("/children/{child_id}/quests/{quest_id}/not-yet")
    def not_yet(child_id: str, quest_id: str, body: NotYetIn, p: ParentScope = Depends(_parent)):
        i = svc.not_yet(p, child_id=child_id, quest_id=quest_id, day=body.day, note=body.note)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @app.post("/children/{child_id}/quests/{quest_id}/record")
    def record(child_id: str, quest_id: str, body: DayIn, p: ParentScope = Depends(_parent)):
        i = svc.record_completion(p, child_id=child_id, quest_id=quest_id, day=body.day)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @app.post("/children/{child_id}/reviews")
    def create_review(child_id: str, body: ReviewIn, p: ParentScope = Depends(_parent)):
        r = svc.create_parent_review(p, child_id=child_id, quest_id=body.quest_id,
                                     day=body.day, note=body.note, flagged=body.flagged)
        return {"id": r.id}

    @app.post("/children/{child_id}/adjustments")
    def adjustment(child_id: str, body: AdjustmentIn, p: ParentScope = Depends(_parent)):
        e = svc.apply_adjustment(p, child_id=child_id, amount=body.amount, reason=body.reason)
        return {"id": e.id, "points": e.points}

    @app.get("/children/{child_id}/dashboard", response_model=DashboardOut)
    def dashboard(child_id: str, day: date, week_start: date | None = None,
                  p: ParentScope = Depends(_parent)):
        dp = svc.daily_progress(p, child_id=child_id, day=day)  # also checks parent owns child
        wc = svc.weekly_consistency(child_id=child_id, week_start=week_start or day)
        return DashboardOut(
            child_id=child_id, on_date=day, total=dp.total, verified=dp.verified,
            pending=dp.pending, available=dp.available, expired=dp.expired,
            week_active_days=wc.active_days,
        )

    @app.post("/redemptions/{redemption_id}/grant", response_model=RedemptionOut)
    def grant(redemption_id: str, p: ParentScope = Depends(_parent)):
        r = svc.grant_redemption(p, redemption_id=redemption_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    @app.post("/redemptions/{redemption_id}/decline", response_model=RedemptionOut)
    def decline(redemption_id: str, p: ParentScope = Depends(_parent)):
        r = svc.decline_redemption(p, redemption_id=redemption_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    # -- parent: clock (admin; domain calls take no scope) -----
    @app.post("/clock/materialise")
    def materialise(body: DayIn, p: ParentScope = Depends(_parent)):
        return {"created": len(svc.materialise_day(body.day))}

    @app.post("/clock/end-of-day")
    def end_of_day(body: DayIn, p: ParentScope = Depends(_parent)):
        return {"expired": len(svc.end_of_day(body.day))}

    # -- child: own surface only --------------------------------
    @app.get("/me/today", response_model=TodayOut)
    def me_today(day: date, c: ChildScope = Depends(_child)):
        payload = svc.today(c, child_id=c.child_id, day=day)
        return TodayOut(
            child_id=payload.child_id, on_date=day,
            items=[
                TodayItemOut(quest_id=i.quest_id, title=i.title, icon=i.icon,
                             state=i.state, waits_for_grownup=i.waits_for_grownup)
                for i in payload.items
            ],
            lifetime_achievement=payload.lifetime_achievement,
            spendable_balance=payload.spendable_balance,
            complexity_profile=payload.complexity_profile,
        )

    @app.post("/me/quests/{quest_id}/complete", response_model=CompletionOut)
    def me_complete(quest_id: str, body: NotYetIn, c: ChildScope = Depends(_child)):
        i = svc.submit_completion(c, child_id=c.child_id, quest_id=quest_id,
                                  day=body.day, note=body.note)
        visible = "available" if i.state.value == "not_yet" else i.state.value
        return CompletionOut(quest_id=quest_id, state=visible)

    @app.post("/me/rewards/{reward_id}/redeem", response_model=RedemptionOut)
    def me_redeem(reward_id: str, c: ChildScope = Depends(_child)):
        r = svc.redeem_reward(c, child_id=c.child_id, reward_id=reward_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    @app.get("/me/celebrations", response_model=list[CelebrationOut])
    def me_celebrations(since: datetime | None = None, c: ChildScope = Depends(_child)):
        return [
            CelebrationOut(quest_id=e.quest_id, on_date=e.on_date,
                           points_awarded=e.points_awarded, at=e.at)
            for e in svc.events.celebrations_since(c.child_id, since)
        ]

    @app.put("/account/notifications")
    def set_notifications(body: NotificationsPrefIn, p: ParentScope = Depends(_parent)):
        a = svc.set_account_notifications(p, enabled=body.enabled)
        return {"notifications_enabled": a.notifications_enabled}

    @app.get("/children/{child_id}/notifications", response_model=list[NotificationOut])
    def child_notifications(child_id: str, since: datetime | None = None,
                            p: ParentScope = Depends(_parent)):
        svc._parent_owns_child(p, child_id)
        return [
            NotificationOut(child_id=n.child_id, kind=n.kind, text=n.text, at=n.at)
            for n in svc.events.parent_notifications_since(p.account_id, since)
            if n.child_id == child_id
        ]

    @app.get("/me/progress", response_model=ProgressOut)
    def me_progress(week_start: date, c: ChildScope = Depends(_child)):
        wc = svc.weekly_consistency(child_id=c.child_id, week_start=week_start)
        return ProgressOut(
            child_id=c.child_id,
            lifetime_achievement=svc.lifetime_achievement(child_id=c.child_id),
            spendable_balance=svc.spendable_balance(child_id=c.child_id),
            week_active_days=wc.active_days,
        )

    return app

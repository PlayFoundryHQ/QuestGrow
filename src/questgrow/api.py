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
from pathlib import Path

from fastapi import APIRouter, Depends, FastAPI, Header
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

_WEBCLIENT_DIR = Path(__file__).parent / "webclient"

from .adaptation import ComplexityProfile
from .entities import QuestSchedule
from .enums import OwnershipStage, Recurrence, RedemptionMode
from .errors import (
    AuthenticationError,
    AuthorizationError,
    BadRequest,
    ContractViolation,
    NotFound,
    QuestGrowError,
)
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
    birthdate: date | None = None


class ChildProfileIn(BaseModel):
    name: str | None = None
    avatar: str | None = None
    age_band: str | None = None
    birthdate: date | None = None
    adaptation_overrides: dict[str, str] | None = None


class ChildOut(BaseModel):
    child_id: str
    name: str
    age_band: str
    avatar: str
    birthdate: date | None = None


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


class PairingCodeIn(BaseModel):
    child_id: str


class PairIn(BaseModel):
    code: str


def create_app(
    service: QuestGrowService | None = None,
    tokens: TokenStore | None = None,
    auth=None,
    cors_origins: list[str] | None = None,
) -> FastAPI:
    """``auth`` (an ``auth.AuthService``) is optional. When given, it is the
    token resolver and the ``/auth/*`` routes are mounted; otherwise a bare
    ``TokenStore`` is used (dev / C2 tests).

    Every API route is served both unprefixed (legacy / reference clients) and
    under ``/v1`` (the stable base a native client pins). CORS is off unless
    ``cors_origins`` is a non-empty allow-list."""
    svc = service or QuestGrowService()
    store = auth or tokens or TokenStore()
    app = FastAPI(title="QuestGrow API", version="0.4.1")
    app.state.service = svc
    app.state.tokens = store
    app.state.auth = auth
    router = APIRouter()

    if cors_origins:
        from fastapi.middleware.cors import CORSMiddleware

        app.add_middleware(
            CORSMiddleware, allow_origins=list(cors_origins), allow_credentials=True,
            allow_methods=["*"], allow_headers=["*"],
        )

    def _scope(authorization: str = Header(default="")) -> Scope:
        if not authorization.startswith("Bearer "):
            raise AuthenticationError("missing bearer token")
        sc = store.resolve(authorization[7:])
        if sc is None:
            raise AuthenticationError("invalid or expired token")
        return sc

    def _parent(scope: Scope = Depends(_scope)) -> ParentScope:
        if not isinstance(scope, ParentScope):
            raise AuthorizationError("parent scope required")
        return scope

    def _child(scope: Scope = Depends(_scope)) -> ChildScope:
        if not isinstance(scope, ChildScope):
            raise AuthorizationError("child scope required")
        return scope

    def _since(since: str | None = None) -> datetime | None:
        """Optional ISO-8601 poll cursor. An empty string means 'no cursor'
        (a fresh client has nothing stored) — not an error."""
        if not since:
            return None
        try:
            return datetime.fromisoformat(since)
        except ValueError:
            raise BadRequest("since must be an ISO-8601 timestamp")

    def _domain_err(_req, exc: QuestGrowError):
        # structured error: stable `code` for clients + human `detail` (Phase F)
        return JSONResponse(status_code=exc.http_status,
                            content={"detail": str(exc), "code": exc.code})

    for _exc in (AuthenticationError, AuthorizationError, NotFound, ContractViolation,
                 BadRequest, QuestGrowError):
        app.add_exception_handler(_exc, _domain_err)

    # -- reference web clients (C5/C6) — static single-file apps ----
    @app.get("/app/child", include_in_schema=False)
    def _child_app():
        return FileResponse(_WEBCLIENT_DIR / "child.html")

    @app.get("/app/parent", include_in_schema=False)
    def _parent_app():
        return FileResponse(_WEBCLIENT_DIR / "parent.html")

    @app.get("/", include_in_schema=False)
    def _index():
        return FileResponse(_WEBCLIENT_DIR / "parent.html")

    if auth is not None:
        @router.post("/auth/signup")
        def signup(body: SignupIn):
            return {"account_id": auth.signup(email=body.email, password=body.password,
                                              pin=body.pin)}

        @router.post("/auth/login")
        def login(body: LoginIn):
            return {"session_token": auth.login(email=body.email, password=body.password)}

        @router.post("/auth/unlock")
        def unlock(body: UnlockIn):
            return {"parent_token": auth.unlock_parent(session_token=body.session_token,
                                                       pin=body.pin)}

        @router.post("/auth/child-token")
        def child_token(body: ChildTokenIn, authorization: str = Header(default=""),
                        p: ParentScope = Depends(_parent)):
            # _parent already validated the parent-gate token; pass it through
            tok = auth.issue_child_token(parent_token=authorization[7:], child_id=body.child_id)
            return {"child_token": tok}

        @router.post("/auth/pairing-code")
        def pairing_code(body: PairingCodeIn, authorization: str = Header(default=""),
                         p: ParentScope = Depends(_parent)):
            code = auth.create_pairing_code(parent_token=authorization[7:], child_id=body.child_id)
            return {"code": code}

        @router.post("/auth/pair")
        def pair(body: PairIn):
            return {"child_token": auth.redeem_pairing_code(code=body.code)}

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
    def _child_out(c) -> ChildOut:
        return ChildOut(child_id=c.child_id, name=c.name, age_band=c.age_band,
                        avatar=c.avatar, birthdate=c.birthdate)

    @router.post("/children", response_model=ChildOut)
    def add_child(body: ChildIn, p: ParentScope = Depends(_parent)):
        return _child_out(svc.add_child(p, child_id=body.child_id, name=body.name,
                                        age_band=body.age_band, avatar=body.avatar,
                                        birthdate=body.birthdate))

    @router.patch("/children/{child_id}", response_model=ChildOut)
    def set_child_profile(child_id: str, body: ChildProfileIn, p: ParentScope = Depends(_parent)):
        return _child_out(svc.set_child_profile(
            p, child_id=child_id, name=body.name, avatar=body.avatar,
            age_band=body.age_band, birthdate=body.birthdate,
            adaptation_overrides=body.adaptation_overrides,
        ))

    @router.get("/children", response_model=list[ChildOut])
    def list_children(p: ParentScope = Depends(_parent)):
        return [_child_out(c) for c in svc.list_children(p)]

    @router.get("/children/{child_id}", response_model=ChildOut)
    def get_child(child_id: str, p: ParentScope = Depends(_parent)):
        return _child_out(svc.get_child_for(p, child_id=child_id))

    # -- parent: quests / schedules --------------------------------
    @router.post("/quests", response_model=QuestOut)
    def create_quest(body: QuestIn, p: ParentScope = Depends(_parent)):
        return _quest_out(svc.create_quest(p, quest_id=body.quest_id, title=body.title,
                                           icon=body.icon, points=body.points))

    @router.patch("/quests/{quest_id}", response_model=QuestOut)
    def edit_quest(quest_id: str, body: QuestEditIn, p: ParentScope = Depends(_parent)):
        changes = body.model_dump(exclude_none=True)
        return _quest_out(svc.edit_quest(p, quest_id=quest_id, **changes))

    @router.post("/quests/seed-starters", response_model=list[QuestOut])
    def seed_starters(p: ParentScope = Depends(_parent)):
        return [_quest_out(q) for q in svc.seed_starter_quests(p)]

    @router.get("/quests", response_model=list[QuestOut])
    def list_quests(p: ParentScope = Depends(_parent)):
        return [_quest_out(q) for q in svc.list_quests(p)]

    @router.put("/quests/{quest_id}/schedule")
    def set_schedule(quest_id: str, body: ScheduleIn, p: ParentScope = Depends(_parent)):
        svc.set_schedule(p, quest_id=quest_id,
                         schedule=QuestSchedule(quest_id, body.recurrence,
                                                frozenset(body.weekdays)))
        return {"ok": True}

    # -- parent: rewards ------------------------------------------
    @router.post("/rewards", response_model=RewardOut)
    def create_reward(body: RewardIn, p: ParentScope = Depends(_parent)):
        return _reward_out(svc.create_reward(p, reward_id=body.reward_id, name=body.name,
                                             icon=body.icon, cost=body.cost, mode=body.mode))

    @router.patch("/rewards/{reward_id}", response_model=RewardOut)
    def edit_reward(reward_id: str, body: RewardEditIn, p: ParentScope = Depends(_parent)):
        changes = body.model_dump(exclude_none=True)
        return _reward_out(svc.edit_reward(p, reward_id=reward_id, **changes))

    @router.get("/rewards", response_model=list[RewardOut])
    def list_rewards(p: ParentScope = Depends(_parent)):
        return [_reward_out(r) for r in svc.list_rewards(p)]

    # -- parent: assignment / ownership -------------------------
    @router.post("/children/{child_id}/quests")
    def assign_quest(child_id: str, body: AssignIn, p: ParentScope = Depends(_parent)):
        cq = svc.assign_quest(p, child_id=child_id, quest_id=body.quest_id)
        return {"child_id": cq.child_id, "quest_id": cq.quest_id}

    @router.put("/children/{child_id}/quests/{quest_id}/ownership", response_model=OwnershipPlanOut)
    def set_ownership(child_id: str, quest_id: str, body: OwnershipIn,
                      p: ParentScope = Depends(_parent)):
        plan = svc.set_ownership_stage(p, child_id=child_id, quest_id=quest_id, target=body.target)
        return OwnershipPlanOut(direction=plan.direction, bypassed=list(plan.bypassed))

    @router.get("/children/{child_id}/suggestions", response_model=list[SuggestionOut])
    def suggestions(child_id: str, p: ParentScope = Depends(_parent)):
        return [
            SuggestionOut(quest_id=s.quest_id, from_stage=s.from_stage, to_stage=s.to_stage)
            for s in svc.advancement_suggestions(p, child_id=child_id)
        ]

    @router.post("/children/{child_id}/quests/{quest_id}/suggestion/accept",
              response_model=OwnershipPlanOut)
    def accept_suggestion(child_id: str, quest_id: str, p: ParentScope = Depends(_parent)):
        plan = svc.accept_advancement_suggestion(p, child_id=child_id, quest_id=quest_id)
        return OwnershipPlanOut(direction=plan.direction, bypassed=list(plan.bypassed))

    @router.post("/children/{child_id}/quests/{quest_id}/suggestion/dismiss")
    def dismiss_suggestion(child_id: str, quest_id: str, permanent: bool = False,
                           p: ParentScope = Depends(_parent)):
        svc.dismiss_advancement_suggestion(p, child_id=child_id, quest_id=quest_id,
                                           permanent=permanent)
        return {"ok": True}

    # -- parent: verification / review / ledger ----------------
    @router.get("/children/{child_id}/approvals", response_model=list[ApprovalOut])
    def approvals(child_id: str, p: ParentScope = Depends(_parent)):
        return [
            ApprovalOut(quest_id=i.quest_id, on_date=i.on_date, state=i.state.value)
            for i in svc.approvals_queue(p, child_id=child_id)
        ]

    @router.post("/children/{child_id}/quests/{quest_id}/approve")
    def approve(child_id: str, quest_id: str, body: DayIn, p: ParentScope = Depends(_parent)):
        i = svc.approve(p, child_id=child_id, quest_id=quest_id, day=body.day)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @router.post("/children/{child_id}/quests/{quest_id}/not-yet")
    def not_yet(child_id: str, quest_id: str, body: NotYetIn, p: ParentScope = Depends(_parent)):
        i = svc.not_yet(p, child_id=child_id, quest_id=quest_id, day=body.day, note=body.note)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @router.post("/children/{child_id}/quests/{quest_id}/record")
    def record(child_id: str, quest_id: str, body: DayIn, p: ParentScope = Depends(_parent)):
        i = svc.record_completion(p, child_id=child_id, quest_id=quest_id, day=body.day)
        return {"quest_id": i.quest_id, "state": i.state.value}

    @router.post("/children/{child_id}/reviews")
    def create_review(child_id: str, body: ReviewIn, p: ParentScope = Depends(_parent)):
        r = svc.create_parent_review(p, child_id=child_id, quest_id=body.quest_id,
                                     day=body.day, note=body.note, flagged=body.flagged)
        return {"id": r.id}

    @router.post("/children/{child_id}/adjustments")
    def adjustment(child_id: str, body: AdjustmentIn, p: ParentScope = Depends(_parent)):
        e = svc.apply_adjustment(p, child_id=child_id, amount=body.amount, reason=body.reason)
        return {"id": e.id, "points": e.points}

    @router.get("/children/{child_id}/dashboard", response_model=DashboardOut)
    def dashboard(child_id: str, day: date, week_start: date | None = None,
                  p: ParentScope = Depends(_parent)):
        dp = svc.daily_progress(p, child_id=child_id, day=day)  # also checks parent owns child
        wc = svc.weekly_consistency(child_id=child_id, week_start=week_start or day)
        return DashboardOut(
            child_id=child_id, on_date=day, total=dp.total, verified=dp.verified,
            pending=dp.pending, available=dp.available, expired=dp.expired,
            week_active_days=wc.active_days,
        )

    @router.post("/redemptions/{redemption_id}/grant", response_model=RedemptionOut)
    def grant(redemption_id: str, p: ParentScope = Depends(_parent)):
        r = svc.grant_redemption(p, redemption_id=redemption_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    @router.post("/redemptions/{redemption_id}/decline", response_model=RedemptionOut)
    def decline(redemption_id: str, p: ParentScope = Depends(_parent)):
        r = svc.decline_redemption(p, redemption_id=redemption_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    # -- parent: clock (admin; domain calls take no scope) -----
    @router.post("/clock/materialise")
    def materialise(body: DayIn, p: ParentScope = Depends(_parent)):
        return {"created": len(svc.materialise_day(body.day))}

    @router.post("/clock/end-of-day")
    def end_of_day(body: DayIn, p: ParentScope = Depends(_parent)):
        return {"expired": len(svc.end_of_day(body.day))}

    # -- child: own surface only --------------------------------
    @router.get("/me/today", response_model=TodayOut)
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

    @router.post("/me/quests/{quest_id}/complete", response_model=CompletionOut)
    def me_complete(quest_id: str, body: NotYetIn, c: ChildScope = Depends(_child)):
        i = svc.submit_completion(c, child_id=c.child_id, quest_id=quest_id,
                                  day=body.day, note=body.note)
        visible = "available" if i.state.value == "not_yet" else i.state.value
        return CompletionOut(quest_id=quest_id, state=visible)

    @router.post("/me/rewards/{reward_id}/redeem", response_model=RedemptionOut)
    def me_redeem(reward_id: str, c: ChildScope = Depends(_child)):
        r = svc.redeem_reward(c, child_id=c.child_id, reward_id=reward_id)
        return RedemptionOut(id=r.id, reward_id=r.reward_id, state=r.state.value)

    @router.get("/me/celebrations", response_model=list[CelebrationOut])
    def me_celebrations(since: datetime | None = Depends(_since), c: ChildScope = Depends(_child)):
        return [
            CelebrationOut(quest_id=e.quest_id, on_date=e.on_date,
                           points_awarded=e.points_awarded, at=e.at)
            for e in svc.events.celebrations_since(c.child_id, since)
        ]

    @router.put("/account/notifications")
    def set_notifications(body: NotificationsPrefIn, p: ParentScope = Depends(_parent)):
        a = svc.set_account_notifications(p, enabled=body.enabled)
        return {"notifications_enabled": a.notifications_enabled}

    @router.get("/children/{child_id}/notifications", response_model=list[NotificationOut])
    def child_notifications(child_id: str, since: datetime | None = Depends(_since),
                            p: ParentScope = Depends(_parent)):
        svc._parent_owns_child(p, child_id)
        return [
            NotificationOut(child_id=n.child_id, kind=n.kind, text=n.text, at=n.at)
            for n in svc.events.parent_notifications_since(p.account_id, since)
            if n.child_id == child_id
        ]

    @router.get("/me/progress", response_model=ProgressOut)
    def me_progress(week_start: date, c: ChildScope = Depends(_child)):
        wc = svc.weekly_consistency(child_id=c.child_id, week_start=week_start)
        return ProgressOut(
            child_id=c.child_id,
            lifetime_achievement=svc.lifetime_achievement(child_id=c.child_id),
            spendable_balance=svc.spendable_balance(child_id=c.child_id),
            week_active_days=wc.active_days,
        )

    @router.get("/health", include_in_schema=False)
    def health():
        return {"status": "ok", "api": app.version}

    # every API route, served unprefixed (legacy / reference clients) and
    # under /v1 (the stable base a native client pins).
    app.include_router(router)
    app.include_router(router, prefix="/v1")
    return app

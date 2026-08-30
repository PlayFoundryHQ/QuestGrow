"""Phase F — durable auth + events, abuse protection, config, Android-facing API.

* auth credentials + tokens survive a process "restart" (a new AuthService on
  the same database), with identical semantics;
* login / unlock lock out after repeated failures (tunable operational
  default), and the parent-token TTL / re-challenge cadence is unchanged;
* the celebration + parent-notification feeds survive a restart;
* structured error `code`s, the `/v1` alias, and the list/detail endpoints
  (all additive, backward-compatible);
* `config.build_app` wires the whole thing from a database URL and a
  file-backed database round-trips across a fresh `build_app`.
"""

from __future__ import annotations

from datetime import date

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import EventSink, OwnershipStage, QuestGrowService, SqliteRepository  # noqa: E402
from questgrow.auth import AuthService  # noqa: E402
from questgrow.api import create_app  # noqa: E402
from questgrow.config import Settings, build_app  # noqa: E402
from questgrow.db import SqliteDatabase  # noqa: E402
from questgrow.events import SqlEventSink  # noqa: E402

DAY = "2026-08-03"


# --------------------------------------------------------------------------- #
# durable auth                                                                 #
# --------------------------------------------------------------------------- #
def _svc_on(path):
    return QuestGrowService(repo=SqliteRepository(path), events=EventSink())


def test_auth_survives_restart(tmp_path):
    db_path = str(tmp_path / "qg.db")
    svc1 = _svc_on(db_path)
    auth1 = AuthService(svc1)
    auth1.signup(email="mum@x.com", password="hunter2horse", pin="2468", account_id="acct-1")
    svc1.add_child(__import__("questgrow").ParentScope("acct-1"),
                   child_id="mia", name="Mia", age_band="5-6")
    sess = auth1.login(email="mum@x.com", password="hunter2horse")
    ptok = auth1.unlock_parent(session_token=sess, pin="2468")
    ctok = auth1.issue_child_token(parent_token=ptok, child_id="mia")
    svc1.repo.close()

    # "restart" — brand-new service + auth on the same DB file
    svc2 = _svc_on(db_path)
    auth2 = AuthService(svc2)
    from questgrow import ChildScope, ParentScope

    assert isinstance(auth2.resolve(ptok), ParentScope)          # parent token still valid
    assert auth2.resolve(ctok) == ChildScope("mia")              # child token still valid
    assert auth2.resolve(sess) is None                           # session ≠ scope (unchanged)
    # can still log in with the persisted credentials
    s2 = auth2.login(email="mum@x.com", password="hunter2horse")
    assert isinstance(auth2.resolve(auth2.unlock_parent(session_token=s2, pin="2468")), ParentScope)
    # no escalation: a child token never resolves to a parent scope
    assert not isinstance(auth2.resolve(ctok), ParentScope)


def test_login_lockout_after_repeated_failures(tmp_path):
    svc = _svc_on(str(tmp_path / "qg.db"))
    auth = AuthService(svc, max_attempts=3, window_s=900, lockout_s=900)
    auth.signup(email="p@x.com", password="rightpass", pin="1234", account_id="a1")
    for _ in range(3):
        with pytest.raises(Exception):
            auth.login(email="p@x.com", password="wrong")
    # now locked — even the correct password is refused until the window passes
    with pytest.raises(Exception) as ei:
        auth.login(email="p@x.com", password="rightpass")
    assert "too many attempts" in str(ei.value).lower()


def test_unlock_lockout_is_isolated_from_login(tmp_path):
    svc = _svc_on(str(tmp_path / "qg.db"))
    auth = AuthService(svc, max_attempts=2)
    auth.signup(email="q@x.com", password="pw", pin="9999", account_id="a2")
    sess = auth.login(email="q@x.com", password="pw")
    for _ in range(2):
        with pytest.raises(Exception):
            auth.unlock_parent(session_token=sess, pin="0000")
    with pytest.raises(Exception) as ei:
        auth.unlock_parent(session_token=sess, pin="9999")
    assert "too many attempts" in str(ei.value).lower()
    # login still works — the lock is keyed per action/subject
    assert auth.login(email="q@x.com", password="pw")


# --------------------------------------------------------------------------- #
# durable events                                                               #
# --------------------------------------------------------------------------- #
def test_event_feeds_survive_restart(tmp_path):
    db_path = str(tmp_path / "qg.db")
    db1 = SqliteDatabase(db_path)
    from questgrow.migrate import run
    run(db1)
    svc1 = QuestGrowService(repo=SqliteRepository(db_path), events=SqlEventSink(db1),
                            advancement_threshold=8)
    from questgrow import ChildScope, ParentScope

    parent, kid = ParentScope("acct-1"), ChildScope("mia")
    svc1.create_account("acct-1", notifications_enabled=True)
    svc1.add_child(parent, child_id="mia", name="Mia", age_band="5-6")
    svc1.create_quest(parent, quest_id="teeth", title="Brush", icon="🪥", points=10)
    from questgrow import QuestSchedule, Recurrence

    svc1.set_schedule(parent, quest_id="teeth", schedule=QuestSchedule("teeth", Recurrence.DAILY))
    svc1.assign_quest(parent, child_id="mia", quest_id="teeth")
    svc1.set_ownership_stage(parent, child_id="mia", quest_id="teeth",
                             target=OwnershipStage.CHILD_OWNED)
    svc1.materialise_day(date(2026, 8, 3))
    svc1.submit_completion(kid, child_id="mia", quest_id="teeth", day=date(2026, 8, 3))
    assert len(svc1.events.celebrations_for("mia")) == 1
    assert len(svc1.events.parent_notifications_since("acct-1", None)) == 1
    db1.close()

    # "restart" — new sink on the same file
    db2 = SqliteDatabase(db_path)
    sink2 = SqlEventSink(db2)
    assert len(sink2.celebrations_for("mia")) == 1
    assert sink2.celebrations_for("mia")[0].points_awarded == 10
    assert len(sink2.parent_notifications_since("acct-1", None)) == 1
    # since-cursor still filters
    at = sink2.celebrations_for("mia")[0].at
    assert sink2.celebrations_since("mia", at) == []


# --------------------------------------------------------------------------- #
# Android-facing API additions                                                 #
# --------------------------------------------------------------------------- #
@pytest.fixture
def api(tmp_path):
    svc = _svc_on(str(tmp_path / "api.db"))
    auth = AuthService(svc)
    client = TestClient(create_app(svc, auth=auth))
    client.post("/auth/signup", json={"email": "m@x.com", "password": "pw123456", "pin": "2468"})
    s = client.post("/auth/login", json={"email": "m@x.com", "password": "pw123456"}).json()
    pt = client.post("/auth/unlock", json={"session_token": s["session_token"], "pin": "2468"}).json()
    ph = {"Authorization": f"Bearer {pt['parent_token']}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    client.post("/children", json={"child_id": "leo", "name": "Leo", "age_band": "7-8"}, headers=ph)
    client.post("/quests", json={"quest_id": "teeth", "title": "Brush", "icon": "🪥"}, headers=ph)
    client.post("/rewards", json={"reward_id": "ice", "name": "Ice cream", "icon": "🍦",
                                  "cost": 30, "mode": "parent_confirmed"}, headers=ph)
    return client, ph


def test_list_and_detail_endpoints(api):
    client, ph = api
    kids = client.get("/children", headers=ph).json()
    assert {k["child_id"] for k in kids} == {"mia", "leo"}
    one = client.get("/children/mia", headers=ph).json()
    assert one["name"] == "Mia"
    assert client.get("/children/nope", headers=ph).status_code == 404
    assert {q["quest_id"] for q in client.get("/quests", headers=ph).json()} == {"teeth"}
    assert {r["reward_id"] for r in client.get("/rewards", headers=ph).json()} == {"ice"}
    # child scope cannot list a parent resource
    ctok = client.post("/auth/child-token", json={"child_id": "mia"}, headers=ph).json()["child_token"]
    assert client.get("/children", headers={"Authorization": f"Bearer {ctok}"}).status_code == 403


def test_structured_error_codes(api):
    client, ph = api
    r = client.get("/children/ghost", headers=ph)
    assert r.status_code == 404 and r.json()["code"] == "not_found"
    r = client.get("/children")  # no token
    assert r.status_code == 401 and r.json()["code"] == "not_authenticated"
    r = client.post("/quests", json={"quest_id": "x", "title": "x", "icon": "x"},
                    headers={"Authorization": "Bearer bad"})
    assert r.status_code == 401 and r.json()["code"] == "not_authenticated"
    # a contract violation carries its code
    client.put("/quests/teeth/schedule", json={"recurrence": "daily"}, headers=ph)
    client.post("/children/mia/quests", json={"quest_id": "teeth"}, headers=ph)
    client.post("/clock/materialise", json={"day": DAY}, headers=ph)
    ctok = client.post("/auth/child-token", json={"child_id": "mia"}, headers=ph).json()["child_token"]
    client.post("/me/quests/teeth/complete", json={"day": DAY},
                headers={"Authorization": f"Bearer {ctok}"})
    dup = client.post("/me/quests/teeth/complete", json={"day": DAY},
                      headers={"Authorization": f"Bearer {ctok}"})
    assert dup.status_code == 409 and dup.json()["code"] == "contract_violation"


def test_v1_alias_serves_the_same_routes(api):
    client, ph = api
    legacy = client.get("/children", headers=ph)
    v1 = client.get("/v1/children", headers=ph)
    assert v1.status_code == 200 and v1.json() == legacy.json()
    schema = client.get("/openapi.json").json()
    assert "/v1/children" in schema["paths"] and "/children" in schema["paths"]


# --------------------------------------------------------------------------- #
# config / build_app                                                           #
# --------------------------------------------------------------------------- #
def test_build_app_round_trips_a_file_database(tmp_path, monkeypatch):
    url = f"sqlite://{tmp_path / 'prod.db'}"
    monkeypatch.setenv("QUESTGROW_DATABASE_URL", url)
    monkeypatch.setenv("QUESTGROW_CORS_ORIGINS", "")

    app1 = build_app(Settings.from_env())
    c1 = TestClient(app1)
    c1.post("/auth/signup", json={"email": "prod@x.com", "password": "pw123456", "pin": "2468"})
    s = c1.post("/auth/login", json={"email": "prod@x.com", "password": "pw123456"}).json()
    pt = c1.post("/auth/unlock", json={"session_token": s["session_token"], "pin": "2468"}).json()
    ph = {"Authorization": f"Bearer {pt['parent_token']}"}
    c1.post("/children", json={"child_id": "z", "name": "Zed", "age_band": "5-6"}, headers=ph)

    # "redeploy" — a brand-new app on the same database URL
    app2 = build_app(Settings.from_env())
    c2 = TestClient(app2)
    s2 = c2.post("/auth/login", json={"email": "prod@x.com", "password": "pw123456"}).json()
    pt2 = c2.post("/auth/unlock", json={"session_token": s2["session_token"], "pin": "2468"}).json()
    ph2 = {"Authorization": f"Bearer {pt2['parent_token']}"}
    assert {k["child_id"] for k in c2.get("/children", headers=ph2).json()} == {"z"}


def test_cors_is_off_by_default(tmp_path):
    svc = _svc_on(str(tmp_path / "c.db"))
    app = create_app(svc, auth=AuthService(svc))          # no cors_origins
    assert not any("CORSMiddleware" in type(m.cls).__name__ or
                   getattr(m, "cls", type(None)).__name__ == "CORSMiddleware"
                   for m in app.user_middleware)

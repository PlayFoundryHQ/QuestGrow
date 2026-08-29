"""C3 — auth + parent gate.

- a child token cannot obtain parent capabilities (no gate → no parent scope);
- tokens are per-child / per-account and cannot be escalated;
- the parent-gate PIN is required before any parent-scope write in a fresh
  session (a session token alone is refused);
- password / PIN are stored hashed (never plaintext).
"""

from __future__ import annotations

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import AuthService, EventSink, InMemoryRepository, QuestGrowService  # noqa: E402
from questgrow.api import create_app  # noqa: E402
from questgrow.auth import _verify_hash  # noqa: E402

DAY = "2026-08-03"


@pytest.fixture
def app_ctx():
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink(), advancement_threshold=8)
    auth = AuthService(svc)
    client = TestClient(create_app(svc, auth=auth))
    return client, svc, auth


def _bootstrap(client) -> tuple[dict, dict]:
    client.post("/auth/signup", json={"email": "p@x.com", "password": "hunter2", "pin": "2468"})
    sess = client.post("/auth/login", json={"email": "p@x.com", "password": "hunter2"}).json()[
        "session_token"
    ]
    ptok = client.post("/auth/unlock", json={"session_token": sess, "pin": "2468"}).json()[
        "parent_token"
    ]
    ph = {"Authorization": f"Bearer {ptok}"}
    client.post("/children", json={"child_id": "mia", "name": "Mia", "age_band": "5-6"}, headers=ph)
    ctok = client.post("/auth/child-token", json={"child_id": "mia"}, headers=ph).json()[
        "child_token"
    ]
    return ph, {"Authorization": f"Bearer {ctok}"}


def test_session_token_is_not_a_parent_scope(app_ctx):
    client, _, _ = app_ctx
    client.post("/auth/signup", json={"email": "p@x.com", "password": "hunter2", "pin": "2468"})
    sess = client.post("/auth/login", json={"email": "p@x.com", "password": "hunter2"}).json()[
        "session_token"
    ]
    # a fresh session, no gate passed → cannot create a quest
    r = client.post("/quests", json={"quest_id": "t", "title": "t", "icon": "t"},
                    headers={"Authorization": f"Bearer {sess}"})
    assert r.status_code in (401, 403)  # session token carries no parent scope


def test_wrong_pin_does_not_unlock(app_ctx):
    client, _, _ = app_ctx
    client.post("/auth/signup", json={"email": "p@x.com", "password": "hunter2", "pin": "2468"})
    sess = client.post("/auth/login", json={"email": "p@x.com", "password": "hunter2"}).json()[
        "session_token"
    ]
    r = client.post("/auth/unlock", json={"session_token": sess, "pin": "0000"})
    assert r.status_code == 403


def test_child_token_cannot_be_escalated(app_ctx):
    client, _, _ = app_ctx
    ph, ch = _bootstrap(client)
    # child token on parent endpoints → 403
    assert client.post("/quests", json={"quest_id": "t", "title": "t", "icon": "t"},
                       headers=ch).status_code == 403
    assert client.post("/auth/child-token", json={"child_id": "mia"}, headers=ch).status_code == 403
    # child token only ever resolves to its own child scope
    assert client.get(f"/me/today?day={DAY}", headers=ch).status_code == 200


def test_child_tokens_are_per_child(app_ctx):
    client, svc, _ = app_ctx
    ph, ch_mia = _bootstrap(client)
    client.post("/children", json={"child_id": "leo", "name": "Leo", "age_band": "3-4"}, headers=ph)
    ch_leo = {
        "Authorization": "Bearer "
        + client.post("/auth/child-token", json={"child_id": "leo"}, headers=ph).json()["child_token"]
    }
    # /me/* is bound to the token's own child
    r = client.get(f"/me/today?day={DAY}", headers=ch_mia)
    assert r.json()["child_id"] == "mia"
    r = client.get(f"/me/today?day={DAY}", headers=ch_leo)
    assert r.json()["child_id"] == "leo"


def test_cross_account_parent_cannot_mint_foreign_child_token(app_ctx):
    client, _, _ = app_ctx
    ph, _ = _bootstrap(client)
    client.post("/auth/signup", json={"email": "q@x.com", "password": "pw", "pin": "1357"})
    sess2 = client.post("/auth/login", json={"email": "q@x.com", "password": "pw"}).json()[
        "session_token"
    ]
    p2 = client.post("/auth/unlock", json={"session_token": sess2, "pin": "1357"}).json()[
        "parent_token"
    ]
    r = client.post("/auth/child-token", json={"child_id": "mia"},
                    headers={"Authorization": f"Bearer {p2}"})
    assert r.status_code in (403, 404)


def test_secrets_are_hashed_not_plaintext(app_ctx):
    _, _, auth = app_ctx
    auth.signup(email="h@x.com", password="s3cret", pin="4444", account_id="acct-h")
    acc = auth._by_email["h@x.com"]
    assert "s3cret" not in acc.pw_hash and "4444" not in acc.pin_hash
    assert _verify_hash("s3cret", acc.pw_hash) and not _verify_hash("nope", acc.pw_hash)


def test_parent_token_expiry(app_ctx):
    client, svc, _ = app_ctx
    auth = AuthService(svc, parent_ttl_s=0)
    client2 = TestClient(create_app(svc, auth=auth))
    client2.post("/auth/signup", json={"email": "e@x.com", "password": "pw", "pin": "9999"})
    sess = client2.post("/auth/login", json={"email": "e@x.com", "password": "pw"}).json()[
        "session_token"
    ]
    ptok = client2.post("/auth/unlock", json={"session_token": sess, "pin": "9999"}).json()[
        "parent_token"
    ]
    r = client2.post("/quests", json={"quest_id": "t", "title": "t", "icon": "t"},
                     headers={"Authorization": f"Bearer {ptok}"})
    assert r.status_code == 401  # expired → unresolvable

"""C5 / C6 — reference web clients.

These are thin static single-file clients (`src/questgrow/webclient/*.html`)
that talk only to the C2/C3/C4 API. Full end-to-end acceptance is D1's job;
here we assert the transport wiring and the copy guarantees that a source scan
can verify without a browser:

* both files are served and are HTML;
* the child client only ever calls `/me/*` and `/auth`-free endpoints
  (child-scoped); it never calls a parent endpoint;
* INV-8 / positive-only: neither client's copy contains streak / downgrade /
  lost-level / failure / "% owned" framing;
* the child client wires an offline queue keyed in localStorage and drops a
  queued item on a 409 (idempotency — INV-11).
"""

from __future__ import annotations

import re

import pytest

pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from questgrow import AuthService, EventSink, InMemoryRepository, QuestGrowService  # noqa: E402
from questgrow.api import create_app  # noqa: E402

import questgrow.api as api_mod  # noqa: E402

CHILD_HTML = (api_mod._WEBCLIENT_DIR / "child.html").read_text()
PARENT_HTML = (api_mod._WEBCLIENT_DIR / "parent.html").read_text()

# Framing that must never appear as copy in either client (case-insensitive).
BANNED = [
    "streak", "downgrade", "demote", "lost a level", "lose your", "you lost",
    "% owned", "percent owned", "independence level", "leaderboard",
    "you failed", "failure", "you missed", "falling behind", "penalty",
]


@pytest.fixture
def client():
    svc = QuestGrowService(repo=InMemoryRepository(), events=EventSink())
    return TestClient(create_app(svc, auth=AuthService(svc)))


def test_both_clients_are_served(client):
    for path in ("/app/child", "/app/parent", "/"):
        r = client.get(path)
        assert r.status_code == 200
        assert "text/html" in r.headers["content-type"]
        assert "<!doctype html>" in r.text.lower()


@pytest.mark.parametrize("html,name", [(CHILD_HTML, "child"), (PARENT_HTML, "parent")])
def test_no_banned_framing_in_copy(html, name):
    low = html.lower()
    for phrase in BANNED:
        assert phrase not in low, f"{name}.html contains banned framing: {phrase!r}"


def test_child_client_only_talks_to_own_surface():
    # every path handed to the client's api() helper
    paths = re.findall(r'api\(\s*[`"](/[^`"]*)[`"]', CHILD_HTML)
    assert paths, "no api() calls found"
    for p in paths:
        assert p.startswith("/me/"), f"child client calls non-/me path: {p}"
    assert "/me/today" in CHILD_HTML and "/me/celebrations" in CHILD_HTML
    # no parent-only path prefixes appear at all
    for parent_only in ("/children/", "/rewards", "/account/", "/clock/", "/auth/"):
        assert parent_only not in CHILD_HTML, parent_only


def test_child_client_has_offline_queue_and_idempotent_drop():
    assert "localStorage" in CHILD_HTML
    assert "qg.child.queue" in CHILD_HTML
    assert "flushQueue" in CHILD_HTML
    # a queued intent that the server reports as already-resolved (409) is dropped
    assert re.search(r"status === 409", CHILD_HTML)


def test_parent_client_has_gate_and_batch_approve():
    assert "/auth/login" in PARENT_HTML and "/auth/unlock" in PARENT_HTML
    assert "Parent PIN" in PARENT_HTML
    assert "Approve all" in PARENT_HTML
    assert "seed-starters" in PARENT_HTML          # one-tap starter templates
    assert "suggestion/accept" in PARENT_HTML and "suggestion/dismiss" in PARENT_HTML


def test_parent_client_covers_the_mvp_screen_inventory():
    # UX_PRINCIPLES parent screen inventory: dashboard, approvals, children, quests,
    # rewards, progress, settings (+ ownership).
    for view in ("dashboard", "approvals", "family", "quests", "rewards", "ownership",
                 "progress", "settings"):
        assert f'"{view}"' in PARENT_HTML, view
    assert "adaptation_overrides" in PARENT_HTML          # per-dimension age overrides
    assert "/account/notifications" in PARENT_HTML        # notifications opt-in control
    assert "birthdate" in PARENT_HTML                     # birthdate OR age band
    # no dead endpoint calls
    assert 'api("/children")' not in PARENT_HTML


def test_child_client_consumes_complexity_profile():
    for field in ("complexity_profile", "quests_shown_at_once", "text_style", "reward_presentation"):
        assert field in CHILD_HTML, field

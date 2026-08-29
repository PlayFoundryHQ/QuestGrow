"""Auth + parent gate (C3).

The smallest thing that realises the §5 actor matrix as a real session model:

* **signup** — email + PBKDF2-hashed password + a PIN (also PBKDF2-hashed).
  Creates the domain ``Account``.
* **login** — email/password → a short-lived *session* token. A session token
  is **not** a parent scope: it carries the account id but ``resolve`` returns
  ``None`` for it, so it cannot perform any parent-scope write.
* **unlock_parent** — session token + PIN → a short-lived ``ParentScope``
  token. This is the parent gate.
* **issue_child_token** — a parent (who owns the child) mints a long-lived
  ``ChildScope`` token for one child. Child tokens are per-child and cannot be
  escalated: ``resolve`` only ever returns ``ChildScope(child_id)`` for them.

Tokens are opaque random strings; the registry is in-memory (single process,
fine for the MVP acceptance run). ``AuthService.resolve`` is the same seam
``api.create_app`` already consumes, so the transport layer is unchanged.

Stdlib only — ``hashlib.pbkdf2_hmac``; no argon2 dependency for the MVP.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from .errors import AuthorizationError, ContractViolation
from .scope import ChildScope, ParentScope, Scope
from .service import QuestGrowService

_PBKDF2_ROUNDS = 200_000


def _hash(secret: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", secret.encode(), salt, _PBKDF2_ROUNDS)


def _make_hash(secret: str) -> str:
    salt = secrets.token_bytes(16)
    return salt.hex() + ":" + _hash(secret, salt).hex()


def _verify_hash(secret: str, stored: str) -> bool:
    try:
        salt_hex, want_hex = stored.split(":", 1)
    except ValueError:
        return False
    return hmac.compare_digest(_hash(secret, bytes.fromhex(salt_hex)).hex(), want_hex)


def _now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class _Account:
    account_id: str
    email: str
    pw_hash: str
    pin_hash: str


@dataclass
class _Token:
    kind: str            # "session" | "parent" | "child"
    account_id: str
    child_id: str | None
    expires_at: datetime | None


class AuthService:
    def __init__(
        self,
        service: QuestGrowService,
        *,
        session_ttl_s: int = 600,
        parent_ttl_s: int = 900,
    ) -> None:
        self._svc = service
        self._session_ttl = session_ttl_s
        self._parent_ttl = parent_ttl_s
        self._by_email: dict[str, _Account] = {}
        self._tokens: dict[str, _Token] = {}

    # -- registration / login ---------------------------------------
    def signup(self, *, email: str, password: str, pin: str, account_id: str | None = None) -> str:
        email = email.strip().lower()
        if email in self._by_email:
            raise ContractViolation("email already registered")
        if len(pin) < 4:
            raise ContractViolation("pin must be at least 4 digits")
        acc_id = account_id or ("acct_" + secrets.token_hex(6))
        self._by_email[email] = _Account(acc_id, email, _make_hash(password), _make_hash(pin))
        self._svc.create_account(acc_id)
        return acc_id

    def login(self, *, email: str, password: str) -> str:
        acc = self._by_email.get(email.strip().lower())
        if acc is None or not _verify_hash(password, acc.pw_hash):
            raise AuthorizationError("bad email or password")
        return self._issue("session", acc.account_id, None, self._session_ttl)

    # -- parent gate ----------------------------------------------
    def unlock_parent(self, *, session_token: str, pin: str) -> str:
        t = self._tokens.get(session_token)
        if t is None or t.kind != "session" or self._expired(t):
            raise AuthorizationError("invalid or expired session")
        acc = next((a for a in self._by_email.values() if a.account_id == t.account_id), None)
        if acc is None or not _verify_hash(pin, acc.pin_hash):
            raise AuthorizationError("incorrect PIN")
        return self._issue("parent", t.account_id, None, self._parent_ttl)

    # -- child tokens -------------------------------------------
    def issue_child_token(self, *, parent_token: str, child_id: str) -> str:
        sc = self.resolve(parent_token)
        if not isinstance(sc, ParentScope):
            raise AuthorizationError("a valid parent-gate token is required")
        self._svc._parent_owns_child(sc, child_id)  # NotFound / AuthorizationError
        return self._issue("child", sc.account_id, child_id, None)

    # -- resolution (the api seam) -----------------------------
    def resolve(self, token: str) -> Scope | None:
        t = self._tokens.get(token)
        if t is None or self._expired(t):
            return None
        if t.kind == "parent":
            return ParentScope(t.account_id)
        if t.kind == "child" and t.child_id is not None:
            return ChildScope(t.child_id)
        return None  # a "session" token is deliberately not a scope

    def revoke(self, token: str) -> None:
        self._tokens.pop(token, None)

    # -- internals --------------------------------------------
    def _issue(self, kind: str, account_id: str, child_id: str | None, ttl_s: int | None) -> str:
        tok = kind[0] + "_" + secrets.token_urlsafe(24)
        exp = _now() + timedelta(seconds=ttl_s) if ttl_s is not None else None
        self._tokens[tok] = _Token(kind, account_id, child_id, exp)
        return tok

    @staticmethod
    def _expired(t: _Token) -> bool:
        return t.expires_at is not None and _now() >= t.expires_at

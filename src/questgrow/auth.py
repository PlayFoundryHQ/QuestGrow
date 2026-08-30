"""Auth + parent gate (C3, hardened in Phase F).

The session model is unchanged from C3:

* **signup** — email + PBKDF2-hashed password + a PBKDF2-hashed PIN. Creates
  the domain ``Account``.
* **login** — email/password → a short-lived *session* token. A session token
  is **not** a scope: ``resolve`` returns ``None`` for it.
* **unlock_parent** — session token + PIN → a short-lived ``ParentScope``
  token. This is the parent gate.
* **issue_child_token** — a parent (who owns the child) mints a long-lived
  per-child ``ChildScope`` token. No escalation path.

Phase F changes the *substrate*, not the semantics:

* credentials, tokens, and failed-attempt counters live in an ``AuthStore``
  (``SqlAuthStore`` for a SQL repository — restart-safe and multi-process;
  ``InMemoryAuthStore`` otherwise);
* ``login`` / ``unlock_parent`` are rate-limited with a lockout after repeated
  failures. The limits (``max_attempts`` / ``window_s`` / ``lockout_s``) are
  **tunable operational defaults**, like ``pending_grace_days`` — not a
  DECISION, and distinct from the parent-token TTL (the re-challenge cadence,
  unchanged at 900 s).

Stdlib crypto only — ``hashlib.pbkdf2_hmac``.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

from .auth_store import AuthAccount, AuthToken, InMemoryAuthStore, SqlAuthStore
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


class AuthService:
    def __init__(
        self,
        service: QuestGrowService,
        *,
        session_ttl_s: int = 600,
        parent_ttl_s: int = 900,
        store=None,
        max_attempts: int = 5,      # tunable operational default (abuse protection)
        window_s: int = 900,
        lockout_s: int = 900,
    ) -> None:
        self._svc = service
        self._session_ttl = session_ttl_s
        self._parent_ttl = parent_ttl_s
        self._max_attempts = max_attempts
        self._window_s = window_s
        self._lockout_s = lockout_s
        if store is not None:
            self._store = store
        else:
            db = getattr(getattr(service, "repo", None), "db", None)
            self._store = SqlAuthStore(db) if db is not None else InMemoryAuthStore()

    # -- registration / login ---------------------------------------
    def signup(self, *, email: str, password: str, pin: str, account_id: str | None = None) -> str:
        email = email.strip().lower()
        if self._store.email_exists(email):
            raise ContractViolation("email already registered")
        if len(pin) < 4:
            raise ContractViolation("pin must be at least 4 digits")
        acc_id = account_id or ("acct_" + secrets.token_hex(6))
        self._store.put_account(AuthAccount(acc_id, email, _make_hash(password), _make_hash(pin)))
        self._svc.create_account(acc_id)
        return acc_id

    def login(self, *, email: str, password: str) -> str:
        email = email.strip().lower()
        key = f"login:{email}"
        self._guard(key)
        acc = self._store.account_by_email(email)
        if acc is None or not _verify_hash(password, acc.pw_hash):
            self._store.record_failure(key, max_attempts=self._max_attempts,
                                       window_s=self._window_s, lockout_s=self._lockout_s)
            raise AuthorizationError("bad email or password")
        self._store.clear_failures(key)
        return self._issue("session", acc.account_id, None, self._session_ttl)

    # -- parent gate ----------------------------------------------
    def unlock_parent(self, *, session_token: str, pin: str) -> str:
        t = self._store.get_token(session_token)
        if t is None or t.kind != "session" or self._expired(t):
            raise AuthorizationError("invalid or expired session")
        key = f"unlock:{t.account_id}"
        self._guard(key)
        acc = self._store.account_by_id(t.account_id)
        if acc is None or not _verify_hash(pin, acc.pin_hash):
            self._store.record_failure(key, max_attempts=self._max_attempts,
                                       window_s=self._window_s, lockout_s=self._lockout_s)
            raise AuthorizationError("incorrect PIN")
        self._store.clear_failures(key)
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
        t = self._store.get_token(token)
        if t is None or self._expired(t):
            return None
        if t.kind == "parent":
            return ParentScope(t.account_id)
        if t.kind == "child" and t.child_id is not None:
            return ChildScope(t.child_id)
        return None  # a "session" token is deliberately not a scope

    def revoke(self, token: str) -> None:
        self._store.delete_token(token)

    def purge_expired(self) -> None:
        self._store.purge_expired()

    # -- internals --------------------------------------------
    def _guard(self, scope_key: str) -> None:
        until = self._store.locked_until(scope_key)
        if until is not None and until > _now():
            raise AuthorizationError("too many attempts — try again later")

    def _issue(self, kind: str, account_id: str, child_id: str | None, ttl_s: int | None) -> str:
        tok = kind[0] + "_" + secrets.token_urlsafe(24)
        exp = _now() + timedelta(seconds=ttl_s) if ttl_s is not None else None
        self._store.put_token(tok, AuthToken(kind, account_id, child_id, exp))
        return tok

    @staticmethod
    def _expired(t: AuthToken) -> bool:
        return t.expires_at is not None and _now() >= t.expires_at

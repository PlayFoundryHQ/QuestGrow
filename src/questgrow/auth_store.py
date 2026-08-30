"""Auth persistence (Phase F).

``AuthService`` used to keep credentials, tokens, and failed-attempt counters
in process-local dicts — a restart lost every account and session. The store
below moves that state to wherever the domain lives:

* ``SqlAuthStore`` — the ``auth_account`` / ``auth_token`` / ``auth_attempt``
  tables (migration ``0002``), so it survives restarts and is shared across
  processes.
* ``InMemoryAuthStore`` — unchanged behaviour for the pure-domain / reference
  path (``InMemoryRepository``), where there is nothing durable anyway.

No auth *semantics* change here: session ≠ scope, unlock → ParentScope,
ParentScope → ChildScope, no escalation, per-child boundary, the same TTLs.
Only the storage substrate and abuse-protection bookkeeping are added.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone


def _now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class AuthAccount:
    account_id: str
    email: str
    pw_hash: str
    pin_hash: str


@dataclass
class AuthToken:
    kind: str            # "session" | "parent" | "child"
    account_id: str
    child_id: str | None
    expires_at: datetime | None


class InMemoryAuthStore:
    def __init__(self) -> None:
        self._by_email: dict[str, AuthAccount] = {}
        self._by_id: dict[str, AuthAccount] = {}
        self._tokens: dict[str, AuthToken] = {}
        self._attempts: dict[str, tuple[int, datetime, datetime | None]] = {}

    # accounts
    def email_exists(self, email: str) -> bool:
        return email in self._by_email

    def put_account(self, a: AuthAccount) -> None:
        self._by_email[a.email] = a
        self._by_id[a.account_id] = a

    def account_by_email(self, email: str) -> AuthAccount | None:
        return self._by_email.get(email)

    def account_by_id(self, account_id: str) -> AuthAccount | None:
        return self._by_id.get(account_id)

    # tokens
    def put_token(self, token: str, t: AuthToken) -> None:
        self._tokens[token] = t

    def get_token(self, token: str) -> AuthToken | None:
        return self._tokens.get(token)

    def delete_token(self, token: str) -> None:
        self._tokens.pop(token, None)

    def purge_expired(self) -> None:
        now = _now()
        for k in [k for k, v in self._tokens.items()
                  if v.expires_at is not None and v.expires_at <= now]:
            self._tokens.pop(k, None)

    # abuse protection
    def locked_until(self, scope_key: str) -> datetime | None:
        rec = self._attempts.get(scope_key)
        return rec[2] if rec else None

    def record_failure(self, scope_key: str, *, max_attempts: int, window_s: int,
                       lockout_s: int) -> None:
        now = _now()
        count, first, _lock = self._attempts.get(scope_key, (0, now, None))
        if (now - first).total_seconds() > window_s:
            count, first = 0, now
        count += 1
        lock = now + timedelta(seconds=lockout_s) if count >= max_attempts else None
        self._attempts[scope_key] = (count, first, lock)

    def clear_failures(self, scope_key: str) -> None:
        self._attempts.pop(scope_key, None)


class SqlAuthStore:
    def __init__(self, db) -> None:
        self.db = db  # a questgrow.db.Database with migration 0002 applied

    # accounts
    def email_exists(self, email: str) -> bool:
        return self.db.fetchone("SELECT 1 FROM auth_account WHERE email = ?", (email,)) is not None

    def put_account(self, a: AuthAccount) -> None:
        self.db.execute(
            "INSERT INTO auth_account (email, account_id, pw_hash, pin_hash, created_at) "
            "VALUES (?, ?, ?, ?, ?) ON CONFLICT (email) DO UPDATE SET "
            "pw_hash = excluded.pw_hash, pin_hash = excluded.pin_hash",
            (a.email, a.account_id, a.pw_hash, a.pin_hash, _now().isoformat()),
        )

    @staticmethod
    def _acc(r) -> AuthAccount:
        return AuthAccount(r["account_id"], r["email"], r["pw_hash"], r["pin_hash"])

    def account_by_email(self, email: str) -> AuthAccount | None:
        r = self.db.fetchone("SELECT * FROM auth_account WHERE email = ?", (email,))
        return self._acc(r) if r else None

    def account_by_id(self, account_id: str) -> AuthAccount | None:
        r = self.db.fetchone("SELECT * FROM auth_account WHERE account_id = ?", (account_id,))
        return self._acc(r) if r else None

    # tokens
    def put_token(self, token: str, t: AuthToken) -> None:
        self.db.execute(
            "INSERT INTO auth_token (token, kind, account_id, child_id, expires_at, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (token, t.kind, t.account_id, t.child_id,
             t.expires_at.isoformat() if t.expires_at else None, _now().isoformat()),
        )

    def get_token(self, token: str) -> AuthToken | None:
        r = self.db.fetchone("SELECT * FROM auth_token WHERE token = ?", (token,))
        if r is None:
            return None
        exp = datetime.fromisoformat(r["expires_at"]) if r["expires_at"] else None
        return AuthToken(r["kind"], r["account_id"], r["child_id"], exp)

    def delete_token(self, token: str) -> None:
        self.db.execute("DELETE FROM auth_token WHERE token = ?", (token,))

    def purge_expired(self) -> None:
        self.db.execute(
            "DELETE FROM auth_token WHERE expires_at IS NOT NULL AND expires_at <= ?",
            (_now().isoformat(),),
        )

    # abuse protection
    def locked_until(self, scope_key: str) -> datetime | None:
        r = self.db.fetchone(
            "SELECT locked_until FROM auth_attempt WHERE scope_key = ?", (scope_key,))
        if r is None or r["locked_until"] is None:
            return None
        return datetime.fromisoformat(r["locked_until"])

    def record_failure(self, scope_key: str, *, max_attempts: int, window_s: int,
                       lockout_s: int) -> None:
        now = _now()
        with self.db.transaction():
            r = self.db.fetchone(
                "SELECT fail_count, first_fail_at FROM auth_attempt WHERE scope_key = ?",
                (scope_key,))
            if r is None:
                count, first = 0, now
            else:
                count = r["fail_count"]
                first = datetime.fromisoformat(r["first_fail_at"]) if r["first_fail_at"] else now
                if (now - first).total_seconds() > window_s:
                    count, first = 0, now
            count += 1
            lock = (now + timedelta(seconds=lockout_s)).isoformat() if count >= max_attempts else None
            self.db.execute(
                "INSERT INTO auth_attempt (scope_key, fail_count, first_fail_at, locked_until) "
                "VALUES (?, ?, ?, ?) ON CONFLICT (scope_key) DO UPDATE SET "
                "fail_count = excluded.fail_count, first_fail_at = excluded.first_fail_at, "
                "locked_until = excluded.locked_until",
                (scope_key, count, first.isoformat(), lock),
            )

    def clear_failures(self, scope_key: str) -> None:
        self.db.execute("DELETE FROM auth_attempt WHERE scope_key = ?", (scope_key,))

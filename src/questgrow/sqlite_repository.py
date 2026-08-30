"""Back-compat shim.

The SQLite backend now lives in :mod:`questgrow.sql_repository` as one
dialect of a portable ``SqlRepository`` (Phase F). This module keeps the
historical import paths working.
"""

from __future__ import annotations

from .sql_repository import SCHEMA, SqliteRepository

__all__ = ["SqliteRepository", "SCHEMA"]

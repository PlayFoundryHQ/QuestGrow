"""ASGI entrypoint — ``uvicorn questgrow.asgi:app``.

Builds the production stack from the environment (``questgrow.config.Settings``).
"""

from __future__ import annotations

from .config import build_app

app = build_app()

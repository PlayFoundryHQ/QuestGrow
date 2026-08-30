# QuestGrow backend — FastAPI + uvicorn, serves /v1 and the reference web clients.
# Multi-stage: build a venv with the postgres extra, copy it into a slim runtime.

FROM python:3.12-slim AS build
ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /src

# deps first (better layer caching), then the package
COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m venv /venv \
    && /venv/bin/pip install --upgrade pip \
    && /venv/bin/pip install ".[postgres]"

FROM python:3.12-slim AS runtime
ENV PATH=/venv/bin:$PATH \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    QUESTGROW_DATABASE_URL=sqlite:///data/questgrow.db

# non-root; /data is the writable volume (SQLite file or nothing when on Postgres)
RUN useradd --system --uid 10001 --home-dir /app questgrow \
    && mkdir -p /app /data \
    && chown -R questgrow:questgrow /app /data
COPY --from=build /venv /venv

USER questgrow
WORKDIR /app
EXPOSE 8000
VOLUME ["/data"]

# build_app() runs pending migrations on startup, so no separate migrate step
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2).status==200 else 1)"

CMD ["uvicorn", "questgrow.asgi:app", "--host", "0.0.0.0", "--port", "8000"]

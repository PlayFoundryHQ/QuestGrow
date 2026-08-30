# QuestGrow

A calm routine-ownership app for young children (~3–8) and their parents. The
child sees today's routines as large picture cards and taps one "I did it";
the parent sets up quests and rewards, approves completions, and gradually
hands ownership of each routine to the child — never framed as a level, a
streak, or a score.

This repo holds the **product foundation** (`docs/`), the **backend +
domain + API** (`src/questgrow/`, Python/FastAPI), two **reference web
clients** served by the backend, and the **native Android client**
(`android/`, Kotlin/Compose).

## Layout

| Path | What |
|---|---|
| `docs/` | Product vision, principles, ownership model, `DECISION_LOG.md`, `TECHNICAL_MODEL.md`, roadmap. The source of truth for *what QuestGrow is*. |
| `src/questgrow/` | Domain layer + FastAPI `/v1` API + auth/PIN gate + SQLite/Postgres persistence + poll-based celebration transport. Entry: `uvicorn questgrow.asgi:app`. |
| `src/questgrow/webclient/` | Reference child + parent web clients (`/app/child`, `/app/parent`). |
| `android/` | Native Android client — see [`android/README.md`](android/README.md). |
| `tests/` | `pytest` — stdlib domain suite + FastAPI/httpx integration suite. |
| `Dockerfile`, `scripts/`, `k8s/` | Container image, local release tooling, and the Helm/ArgoCD deploy for `questgrow.opscale.ir`. |

## Run the backend

```
pip install -e '.[test,postgres]'
python3 -m pytest -q                      # domain + API suites
uvicorn questgrow.asgi:app --port 8000    # http://localhost:8000  (/v1, /app/child, /app/parent, /health)
```

Config is all environment variables (`QUESTGROW_DATABASE_URL`, TTLs, rate
limits, CORS) — see [`docs/product-delivery/DEPLOYMENT.md`](docs/product-delivery/DEPLOYMENT.md).
Migrations run automatically on startup.

## Release & deploy

Local build → GHCR → git tag → GitHub Release; the backend deploys to a
Kubernetes cluster via Helm + ArgoCD, the signed Android APK ships as a
GitHub Release asset. See `scripts/release.sh` and `k8s/`.

## Status

MVP complete and verified end-to-end (domain → API → both clients →
emulator + physical-device). Auth is deliberately simple: email/password +
a numeric parent PIN, no OIDC, no refresh token. Post-MVP direction is in
[`docs/product-delivery/ROADMAP.md`](docs/product-delivery/ROADMAP.md).

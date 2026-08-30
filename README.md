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
| `Dockerfile`, `scripts/`, `deploy/` | Container image, local release tooling (`test.sh`, `release.sh`), and the Helm chart deployed via ArgoCD to `questgrow.opscale.ir`. |
| `docs/PROJECT_STATE.md` | **Canonical current state** — release, architecture, deployment, identity model, test matrix, known gaps. Start here for "what is this today". |

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

Local build → GHCR → git tag → GitHub Release; the backend deploys to the
`nuc-lab` Kubernetes cluster via the `deploy/questgrow/` Helm chart + ArgoCD
(the ArgoCD `Application` lives in `OpScaleLab/nuc-lab-operation`); the signed
Android APK ships as a GitHub Release asset. See `scripts/release.sh`,
`deploy/README.md`, and [`docs/PROJECT_STATE.md` §5](docs/PROJECT_STATE.md).

## Status

**Shipped through `v0.6.3`.** MVP complete and verified end-to-end; the native
Android client (Persian/RTL, kid-first, multi-child on a shared phone,
in-app rewards, TTS narration) is the product surface; the backend is
**deployed and live** at `https://questgrow.opscale.ir`. Auth is deliberately
simple: email/password + a numeric parent PIN, no OIDC, no refresh token.

The **authoritative current-state description** — what is deployed vs merely
released, the test matrix, the identity model, and open gaps — is
[`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md). Post-MVP direction is in
[`docs/product-delivery/ROADMAP.md`](docs/product-delivery/ROADMAP.md).

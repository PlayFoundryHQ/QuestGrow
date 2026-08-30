# QuestGrow — Phase E: Product Readiness & Real-Client Validation

**Run date:** 2026-08-30
**Baseline audited:** `origin/main` == local `HEAD` == `b764412`, tree clean,
0 open issues, `LEADERSHIP_PROTOCOL.md` v2.1, DECISION-001…019 (independent
audit per Appendix A).
**Fixes committed in Phase E:** see §Commits at the foot of the Phase E report.
**Scope:** validation and readiness only. No product decision was made; no
settled decision reopened; MVP boundary unchanged.

---

## E2 — Browser / UX validation

Both reference clients were driven end-to-end in **real headless Chrome**
(Playwright, system `google-chrome`) against a live `uvicorn` on the full
stack (`AuthService` + FastAPI + notifications + `SqliteRepository`).
Screenshots were captured and inspected at each step.

| Area | Verdict | Evidence |
|---|---|---|
| `/app/child` serves & renders | **VERIFIED** | Today screen rendered; 3 large icon-only cards at age 3-4 |
| `/app/parent` serves & renders | **VERIFIED** | Sign-in gate rendered |
| Parent gate (PIN) | **VERIFIED** | login → unlock flow; parent mode entered only after PIN; wrong PIN → no token (`test_auth.py`) |
| Child Today flow | **VERIFIED** | cards, tap → do-it, "I did it" |
| Completion flow (Mode A) | **VERIFIED** | `teeth` (PARENT_GUIDED) → calm "👍 All done — waiting for your grown-up" |
| Completion flow (Mode B) | **VERIFIED** | `get-dressed` (CHILD_OWNED) → immediate celebration |
| Celebration | **VERIFIED** | full-screen ⭐ + "+10", "Back to Today"; animation ~1.6s; **after fix** points display correctly (was blank — see E3-1) |
| Progress (child) | **VERIFIED** | "You showed up 1 day this week", 7-unit week strip, "20 ⭐ / To spend: 20" — progressive-consistency framing, no streak |
| Parent dashboard | **VERIFIED** | per-child "Today: n/m done · k waiting · showed up d days" |
| Approvals + batch approve | **VERIFIED** | queue row + "Approve all (1)"; batch approve fired |
| Quests / starter templates | **VERIFIED** | seed-starters one-tap; create/edit/assign |
| Rewards | **VERIFIED** | define reward; redemption mode select |
| Ownership + suggestions | **VERIFIED** | set stage; suggestion accept/dismiss wired (`test_d1_acceptance.py` s9) |
| Child profile edit + adaptation overrides | **VERIFIED** *(after fix)* | new **Family** screen: set age band 3-4 + `text_style=short_sentence`, `quests_shown_at_once=2` → child payload `complexity_profile` reflected both (E3-3) |
| Notifications opt-in | **VERIFIED** *(after fix)* | new **Settings** screen toggles `PUT /account/notifications` (E3-3) |
| Audio / tap-to-hear | **VERIFIED** *(code + DOM)* | `speechSynthesis` speaker button per card with `aria-label`; `audio_narration:"always"` auto-reads. Actual TTS audio output not captured headless — **the API/markup path is verified; audible playback on a device is NOT VERIFIED** |
| Reduced-motion behaviour | **VERIFIED** | under `prefers-reduced-motion: reduce`, `.celebrate .burst` computed `animation-name` = `none`; under `no-preference` = `pop` |
| Colour-not-sole-signal | **VERIFIED** | instance state shown as text + glyph (`✓ done` / `⏳ waiting`) alongside the border colour |
| Touch-target sizing | **VERIFIED** *(after fix)* | child cards 186×131, "I did it" 388×80, speaker buttons **64×64** (was 44×44 — E3-2). Parent-side controls are desktop-form sized (~31px nav) — UX_PRINCIPLES applies the ≥64pt rule to the **child** surface only |
| Readable typography | **VERIFIED** | child body/label 16px, cue 14px; large friendly cards |
| Contrast | **VERIFIED** (primary text) | label/cue/body `#001858` on `#fef6e4` ≈ 13:1. The pending-screen secondary grey `#5a6a8a` on cream ≈ 4.3:1 on 20px bold text (passes AA-large, below AA-normal) — **acceptable, noted** |
| Age-band visual differentiation | **VERIFIED** | age 3-4: 3 cards, labels hidden (icon-only). age 7-8: 4 cards, labels shown. Same child, same data, switched via the Family screen |
| Offline → reconnect | **VERIFIED** | marked a quest with the browser context offline → intent queued in `localStorage` (`[{"quest_id":"tidy-up",…}]`); back online → `online` event → queue flushed to `[]`, no duplicate award |

**NOT VERIFIED (environment limits, not defects):**
- Audible speech output (headless Chrome produces no audio device).
- Behaviour with a real OS screen reader (TalkBack / VoiceOver).
- Exact WCAG contrast ratios beyond the primary text pair (design/QA audit).
- Real-network airplane-mode cycle on a physical device (context-offline is a
  faithful proxy but not identical).

---

## E3 — Product-readiness findings

| # | Finding | Class | Status |
|---|---|---|---|
| E3-1 | `GET /me/celebrations?since=` with an empty string (a fresh child client has no stored cursor) returned **422**; the client swallowed it and showed a generic "Nice work!" instead of "+N" on the first celebration of a session. | D — implementation defect | **FIXED** — `_since` dependency treats empty as "no cursor"; child client omits the param when empty. Test: `test_api.py::test_e2_empty_since_is_not_a_422`. |
| E3-2 | Child speaker (tap-to-hear) button was 44×44 px — below the UX_PRINCIPLES "touch targets ≥ 64×64 pt" rule for the child surface. | A — accessibility, MVP-required | **FIXED** — `.say` button 64×64. Test updated. |
| E3-3 | The parent reference client did not cover the full UX_PRINCIPLES screen inventory: no way to **edit** a child's profile, no **per-dimension adaptation overrides** UI (MVP feature area 1), no **notifications** control (cross-cutting: opt-in), no birthdate field (feature area 1/11). | A — missing user-facing behaviour; intended behaviour already authoritative | **FIXED** — added **Family** and **Settings** screens; `birthdate` added to `ChildIn`/`ChildProfileIn`/`ChildOut` (existing domain field, no product-model change). Test: `test_webclient.py::test_parent_client_covers_the_mvp_screen_inventory`. |
| E3-4 | Parent client carried a dead `GET /children` call (no such endpoint) — console 404 on load. | D — implementation defect | **FIXED** — removed; 0 4xx/5xx on parent load now. |
| E3-5 | Celebration animation was 0.5s — below UX_PRINCIPLES "joyful but bounded (1–3s)". | E — spec drift | **FIXED** — ~1.6s pop, reduced-motion honoured. |
| E3-6 | `UX_PRINCIPLES` age-adaptation table lists the ~3-4 default ownership stage as `PARENT_MANAGED` / `PARENT_GUIDED` — pre-DECISION-019 wording. (DECISION-019 already flags `OWNERSHIP_MODEL` and `PRODUCT_VISION §13` for the same reconciliation.) | E — documentation drift | **FIXED** (2026-08-30 docs pass) — the UX_PRINCIPLES table row now carries the DECISION-019 on-ramp note; OWNERSHIP_MODEL §6 also reconciled to DECISION-017/018. |
| E3-7 | No accidental technical behaviour leaking into product semantics found. INV-8 holds (no stage in any child payload — re-verified against OpenAPI and live payloads), ledger append-only, ownership not a KPI (schema scan clean), no streak counter surfaced. | — | **CONFIRMED CLEAN** |
| E3-8 | Privacy/security: child tokens are per-child and non-escalatable; parent gate required each session; no child PII beyond first name + age band + optional coarse birthdate; secrets PBKDF2-hashed. `AuthService`/`EventSink` are in-memory (a restart drops tokens + the celebration/notification feed). | B (persistence) / G (ops) | **NOTED** — post-D1, already tracked. |

---

## E4 — Native Android readiness assessment

**Verdict: the backend / domain / API is a sound foundation for a native
Android client.** The domain semantics, the `(child × quest)` ownership model,
verification derivation, the reward/ledger model, the scope model, and
idempotency need **no** product-level change to support Android. The reference
web clients demonstrate the same API is sufficient for both surfaces.

| Dimension | Assessment | Category |
|---|---|---|
| **API completeness** | Core flows complete and OpenAPI-described (codegen-ready). Missing for a from-scratch native client: `GET /children` (list), `GET /children/{id}` (single, to pre-fill an edit form), `GET /quests`, `GET /rewards`. The web client works around this with `localStorage`; a native client needs real list endpoints. | **3 — backend/API change** (additive, non-breaking, no product decision) |
| **Auth & token model** | `session → PIN → parent token (900s) → child token (long-lived)`. Contract is clean and native-friendly. No refresh flow — the parent re-runs login+unlock on expiry (≈ a PIN re-prompt every 15 min). | **1** for the contract; **4 — product decision** on the production re-challenge cadence / whether a refresh token exists (ARCHITECTURE already calls this "policy") |
| **ChildScope / ParentScope boundaries** | Enforced at the HTTP edge and again in the service. A native client simply holds the token it was issued; no client-side authority. | **1 — already sufficient** |
| **Offline semantics** | Client owns the queue; server is authoritative and idempotent. Verified: offline mark → queue → flush → single award. Android replicates the same pattern. Completion endpoint keys on instance identity, not a client idempotency key or client timestamp — fine for "one completion per quest per day". | **1 — already sufficient**; per-occurrence timestamps would be **4 — product decision** (not an MVP need) |
| **Idempotency** | INV-11, server-side, on `(quest@version, child, date)`. Re-verified (4× replay → 1 earn). | **1 — already sufficient** |
| **Sync / reconnect** | Poll-based: `GET /me/today`, `GET /me/celebrations?since=<cursor>` (cursor now works after E3-1), `GET /children/{id}/dashboard`. Full payloads each poll; no delta/ETag. Fine at single-family scale. | **1 — sufficient**; **2 — client-side work** (poll on foreground); delta polling is **4 — post-MVP** |
| **Error semantics** | Consistent HTTP codes (401/403/404/409/422) with `{detail}`. 409 (ContractViolation) multiplexes several conditions behind a string. | **2 — sufficient with client-side work** (parse `detail`); structured machine-readable error `code`s would be **3 — backend change**, nice-to-have |
| **complexityProfile contract** | 8 dimensions, delivered in `today()`, per-dimension parent overrides, no stage/level (INV-8). Native client selects component variants from it. | **1 — already sufficient** |
| **Child-facing contract** | `TodayOut` / `CelebrationOut` / `ProgressOut` — verified stage-free. | **1 — already sufficient** |
| **Parent-facing contract** | Dashboard / approvals / ownership / suggestions / rewards / family / settings all have endpoints. Modulo the list endpoints above. | **1** + the **3** list-endpoint gap |
| **Celebration / event semantics** | Poll `completion.verified` via `/me/celebrations`. No push. Co-present real-time is ROADMAP Layer 1. | **1** for MVP parity; production co-present delivery is **4 — product/infra decision** (already roadmapped) |
| **Data persistence** | `SqliteRepository` — single file, `check_same_thread=False`, no pooling, no migration framework. Fine for local/dev/single-family. | **3 — backend change** for multi-family production (Postgres + migrations + pooling) — known, post-D1 |
| **Versioning implications** | Quests are versioned in the domain and surfaced (`QuestOut.version`). No API path versioning (`/v1/`). OpenAPI is generated. | **3 — minor** additive before a public client ships |
| **Accessibility-relevant API behaviour** | `complexityProfile.audio_narration` + `text_style` + `reading_requirement` carry what a native client needs to drive TalkBack and text density. | **1 — already sufficient** |

**No E4 finding requires changing a product decision, an invariant, the
domain model, or the MVP boundary.**

---

## E5 — Gap classification

| Class | Item | Owner |
|---|---|---|
| **A — must fix before a real client** | E3-1 celebration cursor 422 · E3-2 child 64pt targets · E3-3 parent screen-inventory gaps · E3-4 dead endpoint call · E3-5 animation duration | **Autonomous — DONE in Phase E** |
| **B — must fix before production** | Durable token + event/notification persistence · Postgres + migrations + connection pooling · Hardened parent gate (rate-limit / lockout) | Autonomous *implementation* when granted; **hardened-gate re-challenge policy value is a PO decision** |
| **C — native / mobile client track** | List/detail endpoints (`GET /children`, `/children/{id}`, `/quests`, `/rewards`) · structured error `code`s · API path versioning · push / real-time co-present celebration (ROADMAP L1) | Autonomous *implementation* when the Android grant is given; **not built in Phase E** (evidence-first / no speculative surface) |
| **D — post-MVP enhancement** | Per-occurrence completion timestamps · delta / ETag polling · PIN-change flow · screen-reader-tuned audio · UX_PRINCIPLES age-table wording (E3-6) | Autonomous docs pass for E3-6; the rest await scope |
| **E — deliberately deferred** | `PARENT_MANAGED` assignment UI (DECISION-019) · photo evidence · long-term meta-game · multi-caregiver / verifier roles · localization | PO — future grants |

---

## Recommended next Product-Owner decision

The MVP is **genuinely ready** to serve as the foundation for the real product
and a native Android client. The domain, contracts, invariants, and governance
are sound and needed no change. What remains before an Android build starts:

1. **Grant the "production foundation" scope (Class B)** — durable persistence
   (Postgres + migrations), durable token/event storage, hardened parent gate.
   Mostly autonomous once granted; one policy value (parent-gate re-challenge
   cadence / refresh-token existence) needs your call.
2. **Grant the "Android client track" scope (Class C)** — the additive
   list/detail endpoints, structured error codes, and API versioning are
   small, non-breaking, and can be done autonomously as the first step of that
   track; then the native client itself.

Both are new grants. Phase E stops here (LEADERSHIP_PROTOCOL §22).

---

## Addendum — what shipped after Phase E (2026-08-30)

Both recommended grants were given and completed, plus more:

- **Phase F** — production foundation: portable SQLite/PostgreSQL persistence
  + migration framework, restart-safe ids, durable `SqlAuthStore` /
  `SqlEventSink`, login/unlock rate-limiting, env config + `build_app`,
  additive `/v1` API with list/detail endpoints and structured error codes.
- **Phases G–J** — the native **Android client** (Kotlin/Compose): networking,
  offline queue + read cache, full child + parent surfaces, explicit
  Loading/Empty/Error/Retry states, a clean app relaunch, a 12-test
  MockWebServer instrumented suite, and verification on both an emulator and a
  **physical device** (incl. dark mode and the backend-URL round trip). See
  [`../../android/README.md`](../../android/README.md).

**Auth-policy question resolved by the owner (2026-08-30):** static
email/password + PIN only, no OIDC, no refresh token, no payment plumbing —
proportionate to a personal project. The parent-token TTL is raised from the
900 s default in the deployment config (an operational knob, not a decision
change).

Remaining to close out the MVP as a running product: backend deployment to the
owner's Kubernetes cluster (`questgrow.opscale.ir`), release tooling, signed
APK distribution via GitHub Releases, a light security/ops pass, and the
hands-on accessibility / airplane-mode checks. Tracked in the project memory
store, not as new phase grants.

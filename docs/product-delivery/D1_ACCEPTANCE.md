# QuestGrow — D1 End-to-End MVP Acceptance

**Run date:** 2026-08-30
**Backend:** `SqliteRepository` (the D1 persistence backend), full stack
(`AuthService` + FastAPI + `EventSink` notifications + reference web clients).
**Automated evidence:** `tests/test_d1_acceptance.py` — 17 checks, all passing.
**Verdict:** **MVP loop passes end to end.** Two items are automated at the
contract/data level and carry a *pending visual confirmation* (see §Caveats).

This document is the D1 exit record referenced by
[`ROADMAP.md`](./ROADMAP.md). Per the
[Leadership Protocol §22](../governance/LEADERSHIP_PROTOCOL.md), D1 is a hard
stop: no post-MVP work proceeds without a new Product Owner grant.

---

## Reference family under test

- One account, parent gate = PIN, notifications opted **in** for the
  notification checks (default remains opt-out).
- One child `mia`, age band `5-6` (varied to `3-4` / `7-8` in scenario 8).
- Five quests on a daily schedule — `teeth` (10), `dressed` (10), `tidy` (10),
  `read` (15), `plants` (5) — all assigned (→ `PARENT_GUIDED`, i.e. all
  require verification).
- Child token minted by the parent through the gate.

---

## Acceptance checklist (`MVP.md` → "the loop works end to end")

| # | Scenario | Status | Evidence |
|---|---|---|---|
| 1 | Parent sets up a child and 3–5 quests, some requiring verification | ✅ PASS | 5 quests assigned, all `PARENT_GUIDED` → `waits_for_grownup: true` on every `today` item. `test_s1_*` |
| 2 | Child sees today's quests, does one in the real world, marks it | ✅ PASS | `GET /me/today` returns 5 items; `POST /me/quests/teeth/complete` accepted. `test_s2_*` |
| 3 | `PARENT_GUIDED` → pending; `CHILD_PARTICIPATED`/`CHILD_OWNED` → verified + celebrate immediately | ✅ PASS | `teeth` → `pending`, no celebration; `read` (forced `CHILD_OWNED`) → `verified` + 1 celebration (+15). `test_s3_*` |
| 4 | Parent approves the pending one; child sees celebration + progress increments | ✅ PASS | approvals queue = `[teeth]`; after approve, lifetime +10, celebration (+10) on the child poll, quiet parent notification present. `test_s4_*` |
| 5 | "Not yet" returns a quest to available with no penalty | ✅ PASS | `tidy` → `available`; ledger byte-identical before/after; no `not_yet` string on the child surface. `test_s5_*` |
| 6 | Daily indicator reflects only verified completions; weekly view updates | ✅ PASS | dashboard `verified` stays 0 while `pending`; → 1 after approve; `week_active_days` 1 → 2 across two active days. `test_s6_*` |
| 7 | Child mode has no path to change points/quests/rewards/ownership/settings; server rejects tampered writes | ✅ PASS | child token → 403 on adjustments, quest create, quest patch, reward create, ownership PUT, notifications PUT; forged token → 401; ledger stays empty. `test_s7_*` |
| 8 | Switching age band visibly changes text amount, quests-per-screen, reward presentation | ✅ PASS *(data)* / ⚠ visual pending | `complexity_profile` across `3-4`/`5-6`/`7-8`: `text_style` `icon_only`→`short_label`→`short_sentence`; `quests_shown_at_once` `3`<`5`<`7`; `reward_presentation` differs; per-dimension parent override wins. Child client renders from these fields (`webclient/child.html`). `test_s8_*` + `test_cross_accessibility_*` |
| 9 | After 8 consecutive eligible completions of a `PARENT_GUIDED` quest → advancement suggestion; accepting makes completions verify immediately; points unchanged | ✅ PASS | 8× (mark → approve) → 1 suggestion `PARENT_GUIDED → CHILD_PARTICIPATED`; accept → next completion `verified` with no approval; all `teeth` earns == 10. `test_s9_*` |
| 10 | Moving a quest back to `PARENT_GUIDED` produces no negative child signal and no points change | ✅ PASS | regress accepted (`direction: "regress"`, no error); lifetime unchanged; child `today` contains none of downgrade/regress/lost/back/fail/stage. `test_s10_*` |

---

## Cross-cutting MVP requirements

| Requirement | Status | Evidence |
|---|---|---|
| Positive-only — no failure/missed/late states to the child; regression never shown as loss | ✅ PASS | child-visible instance states ⊆ `{available, pending, verified}`; scenario 10; client copy scan (`test_webclient.py`). `test_cross_positive_only_*` |
| Ownership never a KPI — no dashboards/percentages/nudges toward faster transfer | ✅ PASS | OpenAPI schema scanned: no `owned_count`/`owned_pct`/`ownership_score`/`independence_level`/`readiness_score`. `test_cross_ownership_never_a_kpi_*` |
| Quiet by default — notifications opt-in, informational wording only | ✅ PASS | account default `notifications_enabled=false`; no parent notification without opt-in while the child still celebrates; template banned-phrase scan (`test_notifications.py`). `test_cross_quiet_by_default_*` |
| Offline-tolerant child flow — marking works offline and syncs; no double award | ✅ PASS *(server)* / ⚠ visual pending | server: 4× replay of a completion intent → exactly 1 `earn` (INV-11). Client: `localStorage` queue + flush on `online` + 409-drop (`webclient/child.html`, `test_webclient.py`). Browser offline→online cycle not exercised in-session. `test_cross_offline_tolerant_*` |
| Accessibility baseline — ≥64pt targets, contrast, colour-not-sole-signal, audio narration, reduced-motion | ⚠ PARTIAL / visual pending | present in `child.html`: `prefers-reduced-motion` disables the celebration animation; `speechSynthesis` tap-to-hear with `aria-label`s + `audio_narration: "always"` auto-read; state shown as text + glyph (`✓ done` / `⏳ waiting`), not colour alone; 44px+ touch targets on controls. **Not verified in-session:** exact 64pt target sizing, colour-contrast ratios, screen-reader pass — these need a visual/audit pass on a device. `test_cross_accessibility_baseline_*` |
| Parent gate on by default; trust boundary enforced server-side | ✅ PASS | PIN required each session; wrong PIN → no parent token; child scope cannot escalate; every state write refused at both the HTTP edge and the service guard. `test_s7_*`, `test_auth.py` |

---

## Explicitly-out-of-scope — confirmed absent

| Item | Status | Evidence |
|---|---|---|
| Dedicated `PARENT_MANAGED` assignment path | ✅ absent | assignment always yields `PARENT_GUIDED`; no API parameter to start elsewhere. `test_oos_no_parent_managed_*` |
| Photo evidence / reward marketplace / template library / multi-parent / caregiver / verifier / invite | ✅ absent | no such path in the OpenAPI surface. `test_oos_no_photo_evidence_*` |
| Age-band tuning of the advancement threshold | ✅ absent | ships as one global default (`advancement_threshold`). |
| Web parent dashboard as a *production* deliverable | n/a | the parent client here is an explicit **reference** client for D1 (ARCHITECTURE "Stack (C0)"); production clients are post-D1. |

---

## Caveats — NOT VISUALLY VERIFIED in this run

Per [Leadership Protocol §16](../governance/LEADERSHIP_PROTOCOL.md), the
following are automated at the contract/data layer but were **not** confirmed
by a human look or a browser session:

1. **Scenario 8 — the *visible* age-band change.** The `complexityProfile`
   values are correct and the client reads every one of them; that the child
   *screen* visibly differs across bands (card density, label length, reward
   animation) needs a device/browser look.
2. **Celebration** — the full-screen animation + reduced-motion behaviour is
   coded; not seen rendered.
3. **Offline marking** — the queue/flush/409-drop logic is unit-covered; a
   real airplane-mode → reconnect cycle in a browser was not run.
4. **Accessibility numbers** — 64pt targets and contrast ratios are design
   values, not asserted here.

**Recommended before declaring MVP shippable:** one guided session driving
`/app/parent` and `/app/child` in a browser (with devtools offline toggle and
a reduced-motion OS setting) against a running `uvicorn`, covering items 1–4
above. This is a Product-Owner / QA activity, not blocked work.

> **Update — Phase E (2026-08-30):** items 1–4 above were subsequently driven
> and screenshotted in real headless Chrome and are now **VERIFIED** (age-band
> visual differentiation, celebration render, reduced-motion, offline→reconnect
> queue flush). Phase E also fixed a celebration-cursor `422`, undersized child
> touch targets, and the missing parent Family/Settings screens. What remains
> NOT VERIFIED: audible speech output and a real OS screen-reader pass (headless
> environment limits). Full detail: [`E_READINESS.md`](./E_READINESS.md).

---

## MVP-readiness verdict

**The QuestGrow MVP loop is functionally complete and passes end-to-end
acceptance at the contract and integration level.** All 10 acceptance
scenarios and the enforceable cross-cutting requirements pass automatically on
the D1 backend through the full stack. The residue is **visual/UX
confirmation** of the two reference clients (§Caveats) — a human review pass,
not additional construction.

Per §22, execution **stops here** pending a Product Owner grant for any
post-D1 work (production hardening, real-time delivery, mobile track,
hosting/deployment, post-MVP roadmap).

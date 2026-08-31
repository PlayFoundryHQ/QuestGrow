# QuestGrow — Native Android Client

A native Android **client** of the QuestGrow backend. It consumes the
established `/v1` API and domain contracts (`docs/architecture/TECHNICAL_MODEL.md`,
`DECISION-001…021`, `INV-1…18`, `docs/experience/UX_PRINCIPLES.md`). It does
**not** re-implement QuestGrow's product model — the server stays authoritative
for identity, `complexityProfile`, ownership stage, verification, rewards,
balances and approvals.

**Persian-only, RTL** ([DECISION-020](../docs/governance/DECISION_LOG.md)): all
UI is Farsi, right-to-left, Persian digits (۰۱۲۳…), Vazirmatn font, tap-to-hear
narration in Persian. The app forces `fa` regardless of device locale.

> **Current state:** [`../docs/PROJECT_STATE.md`](../docs/PROJECT_STATE.md) is
> the canonical, up-to-date description of the app, the backend, and the
> deployment. The section below is the shape; details there.

## Shape (Phase L → v0.6.3)

- **Kid-first.** The app opens **straight to the kid's board** — no chooser,
  no login on the family device (`CHILD_JOURNEY` "lands directly on Today").
  A greeting header, a 2-column grid of big illustrated cards; one tap →
  picture + «بشنو» / «انجام دادم» → calm "منتظر بزرگترت" or an instant
  celebration.
- **Parent gate.** A small "بزرگترها ›" pill in the board header → a 4-digit
  **PIN pad**. Email + password are set once at signup and stored on the
  device (`TokenStore`) so the everyday gate is PIN-only; the client replays
  login+unlock behind it. A returning parent on a wiped device signs back in
  via **«قبلاً حساب دارم»** in onboarding (v0.6.1).
- **Parent home** is a **card hub** (v0.6): a per-child glance (avatar +
  progress), the two inboxes with count badges — **approvals** («تأیید» /
  «هنوز نه» / «تأیید همه») and **reward redemptions** («بله، بده» / «الان
  نه») — and a 2-col **تنظیم و مدیریت** grid (routines / rewards / ownership /
  children / settings).
- **Several children on one shared phone.** On the family device (the one
  with a stored account) **every child on the account appears on the kid
  board's avatar switcher automatically** — no per-child "activate" step
  (v0.6.2). `AuthRepository.syncFamilyChildren()` keeps `TokenStore`'s
  per-child token map in sync with the account on every parent-area refresh.
- **The kid spends points in-app.** A **جایزه‌ها** screen shows the child's
  balance and the reward catalogue; «می‌خواهمش» submits a redemption —
  self-service rewards celebrate immediately, `parent_confirmed` ones land in
  the parent's inbox. Backend: `GET /v1/me/rewards`, `GET /v1/redemptions`
  (both additive, read-only).
- **First run** = a 4-step stepper (who → account+PIN → add child → pick
  routine cards; auto-assigns + materialises), plus the sign-in and the
  child-device-pairing branches.
- **A kid's own device** pairs with a **6-digit code** the parent generates in
  Settings (backend: `POST /v1/auth/pairing-code` → `POST /v1/auth/pair`,
  single-use, 15-min TTL). That device holds one child token and no account.

## Design system (v0.6)

One set of primitives every screen draws from — `ui/Theme.kt` (the Ink / Cream
/ Pink / Sky / Leaf palette plus Material-3 container tints and a rounded shape
scale), `ui/Scaffold.kt` (`AppScaffold` — the shared header + back + width cap +
scroll + optional bottom bar; `SectionHeader` with a count badge; `Avatar`
monogram; `StepDots`; the `Space` spacing scale), and `ui/Common.kt`
(`BigButton` / `SecondaryButton` filled-tonal / `GhostButton` / `Field` /
`DigitPad` / `SelectRow` / `SelectPill`). Onboarding is a 4-step stepper; the
kid board leads with a greeting and an avatar switcher; the parent home is a
card hub (glance + inboxes with badges + a 2-col setup grid).

## Architecture

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3), Navigation-Compose | modern, testable, accessible |
| State | `ViewModel` + coroutines / `StateFlow` | lifecycle-safe, unit-testable |
| Networking | Retrofit + OkHttp + `kotlinx.serialization` | typed, `/v1`-pinned; `apiCall` folds every call into `ApiResult` = `Ok` / `Failure(status, code, detail)` / `Offline` |
| Local state | DataStore (tokens) + a **file-backed JSON offline queue** | client concerns only — no second authoritative ledger. Deliberately **no Room/KSP or Hilt/KAPT**: manual DI (`AppContainer`) keeps the client single-pass-buildable and the offline queue JVM-unit-testable |
| min / target SDK | 26 / 35 | parent + child devices from Android 8 |

```
core/        ApiResult
data/net/    QuestGrowApi (every /v1 route) · Dtos (1:1 with api.py) · ApiClient
data/local/  TokenStore (DataStore) · OfflineQueue (FileOfflineQueue)
data/        AuthRepository · ChildRepository (offline-first) · ParentRepository
data/model/  UI domain types — QuestVisualState never carries a stage (INV-8)
adapt/       ComplexityProfile — consumes the server's §13 values, no age logic
ui/          Theme (Vazirmatn) · Fa (Persian digits) · Locale (fa/RTL) ·
             Common (BigButton / Field / DigitPad) · Starters (routine set)
ui/onboarding/ 3-screen wizard + the child-device 6-digit pairing
ui/child/    ChildFlow — board · do-it (+ TTS) · waiting · celebration · progress
ui/parent/   ParentGate (PIN pad) · ParentFlow (home + approvals inbox +
             routines / rewards / ownership / children / settings)
             — list sections keep the Loadable Loading/Empty/Failed states
MainActivity  AppRoot — the Onboarding / Kid / Gate / Parent state machine
```

## Product-truth guarantees held by the client

- **INV-8** — the child surface shows no ownership/stage/level string. Card
  state is `available` / `waiting` / `done` / `will send`, shown as text + glyph.
- **No streaks** — the child progress screen is "you showed up N days this
  week"; there is no streak counter, badge, or loss framing.
- **Verification is the server's** — `complete()` never decides an outcome; a
  `409` means "already resolved" → drop the queued copy, not an error (INV-11).
- **Rewards are server-authoritative** — points shown come from
  `/v1/me/celebrations` and `/v1/me/progress`; nothing is awarded locally.
- **Auth contract** — `login → session → unlock(PIN) → parent token →
  child token`, unchanged. No refresh tokens, no OIDC. The client stores the
  account email+password so the *gate* is PIN-only (it replays login+unlock);
  the server-side re-challenge (`PARENT_TTL_S`) is untouched. The only
  additive server change is `/v1/auth/pairing-code` + `/v1/auth/pair`
  (single-use 6-digit code → child token) for a kid's own device.
- **complexityProfile is consumed, not computed** — `text_style`,
  `quests_shown_at_once`, `audio_narration`, `reward_presentation` drive
  rendering; an unknown value falls back to the middle option.

## Build & run

Requires JDK 17 and an Android SDK (platform 35, build-tools 35).

```
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties      # or set ANDROID_HOME
./gradlew :app:assembleDebug                          # -> app/build/outputs/apk/debug/app-debug.apk
```

Point the app at a backend: `BuildConfig.DEFAULT_BASE_URL` is
`http://10.0.2.2:8000/` (host loopback from an emulator). Run the backend with
`uvicorn questgrow.asgi:app --host 0.0.0.0 --port 8000` (see
`docs/product-delivery/DEPLOYMENT.md`).

A kid on their own device: the parent generates a **6-digit code** in
**Settings → کد ورود کودک**; the kid types it on the first-run
"دستگاه کودک" screen.

## Testing

```
./gradlew :app:testDebugUnitTest            # 24 JVM tests
./gradlew :app:connectedDebugAndroidTest    # instrumented (needs a device/emulator)
```

- **`DtoSerializationTest`** — the DTOs decode the real API JSON; INV-8 scan of
  the child payload shape.
- **`ApiContractTest`** — MockWebServer: calls hit `/v1/*`, the bearer header
  is attached, HTTP status → `code` mapping, `Offline` ≠ `Failure`.
- **`OfflineAndSyncTest`** — queue dedup/persist/reload; `flushQueue` drains,
  drops a `409`, and stops (keeping items) on a mid-drain disconnect;
  `today()` marks a still-queued quest `QUEUED_OFFLINE`.
- **`ComplexityProfileTest`** — band → rendering mapping, unknown-value
  fallback, clamp.
- **`AppFlowTest`** (instrumented, MockWebServer, 9) — drives the real
  `MainActivity`/`AppRoot`: fresh install → "این دستگاه برای کیست؟";
  child-device 6-digit pairing → kid board; kid completion pending →
  "منتظر بزرگترت"; verified → celebration "+۱۰"; **INV-8** (no
  stage/level/مرحله/سطح on the child surface); parent gate wrong PIN →
  "رمز اشتباه" then correct → home; approvals inbox → approve → empty;
  kid rewards → «می‌خواهمش» → "به بزرگترت گفتیم"; parent redemption inbox →
  grant → empties.
- **`TokenStoreTest`** (instrumented, 3) — the multi-child token map:
  add / switch / remove, the single-token paired-device path, and a fresh
  `TokenStore` instance reading the persisted map back.

## Accessibility

Implemented: ≥64 dp touch targets on child controls; `contentDescription` on
quest cards and action buttons (carries the quest name even when `icon_only`
hides the visible label — Persian); state shown as text + glyph
("⏳ منتظر" / "✓ انجام شد"), never colour alone; `TextToSpeech` tap-to-hear
targeting a Persian voice (`Narrator` walks installed engines, e.g. AvaCore;
"بشنو" is hidden when there is no `fa` voice); `audio_narration: "always"`
auto-read; reduced-motion honoured; large readable type; RTL throughout.

> **TTS (v0.6.3):** the manifest now declares `<queries>` for
> `android.intent.action.TTS_SERVICE` (+ an explicit `<package>` for
> `com.github.opscalehub.avacore`) — required on Android 11+ to bind a
> separate-app engine; without it every `TextToSpeech` init failed and "بشنو"
> stayed silent. Parent **Settings → صدای فارسی** shows the resolved engine
> status + an «امتحان صدا» test button. Audible output with AvaCore on a real
> device is **NOT VERIFIED** (AvaCore is not on the emulator).

**Visually verified** — the Cycle 2 / Cycle 3 notes below are from the
pre-Persian Phase-J client and are **historical**. The current
verification matrix is [`../docs/PROJECT_STATE.md` §7](../docs/PROJECT_STATE.md#7-test--verification-matrix).
Historical (`google_apis` emulator under Xvfb):
- *Cycle 2* — child chooser / code-entry / Today / Do-it / celebration /
  progress and parent sign-in / dashboard / approvals.
- *Cycle 3* — parent Family / Quests / Rewards / Ownership / Settings tabs,
  the new Loading/Empty/Failed(+retry) section states, and the clean
  backend-URL relaunch (Settings → "Save & restart app": the process
  restarts and re-enters at the mode chooser — no `killProcess`).
- *Cycle 3, backend-URL round trip* — verified end to end against two live
  local backends: app starts on the `BuildConfig` default, the URL is
  changed in Settings, the app relaunches, and every subsequent
  auth + API call goes to the **new** backend (confirmed by which seeded
  child name renders and by each backend's request log); the old URL
  receives zero requests and `questgrow_prefs.xml` holds only the new
  `base_url`.
- All at font scale 1.0, and re-checked at **1.5× and 2.0×** on the emulator —
  every screen stays scrollable, text wraps, no control is clipped or
  unreachable.

**Verified on a physical device** (Cycle 3 — OnePlus Nord AC2003, Android 12 /
API 31, in **dark mode**): mode chooser, parent sign-in / PIN gate, parent
navigation across all seven tabs, the empty states (Approvals, Rewards), child
code entry + auth, child Today, child completion, both server-authoritative
outcomes (`PARENT_GUIDED` → "waiting for your grown-up"; `CHILD_OWNED` →
celebration "⭐ +10" from `/v1/me/celebrations`), parent approvals → approve →
empty, and the full backend-URL round trip (start on backend A → change URL in
Settings → relaunch, PID changes → sign in → backend B; A receives zero
requests; prefs hold only the new URL). Dark-theme rendering is correct on
every screen seen — no contrast, clipping, or system-bar-inset problems.
`‹ Today` / `Hear it` measured 153 px = 64 dp at the device's display-size
setting.

**Accessibility verified at the AccessibilityNodeInfo / Compose-semantics
layer** (emulator + physical device): icon-only quest cards announce
"`<quest>`, done" / "`<quest>`, waiting" / "`<quest>`" — the name is exposed
even when the label is visually hidden, and no stage/level leaks into it
(INV-8). Child-surface touch targets are ≥64 dp (primary actions via
`BigButton`; secondary buttons — "‹ Today", "Progress", "Grown-up" — raised
from 48 dp to 64 dp in Cycle 3).

**NOT verified / BLOCKED:** audible TTS / TalkBack speech output; live
TalkBack swipe-traversal focus order; on-device font-scale — the test phone
denies `adb shell settings put` (`WRITE_SECURE_SETTINGS`) and `pm clear`, so
TalkBack and font scale cannot be toggled without hands-on access to the
device UI. Font scale 1.5×/2.0× is covered on the emulator.

## Offline behaviour

- **Read cache** (`ReadCache`): the last successful child *Today* board and
  *Progress* are cached on disk, **keyed per child** (`today-<child>.json` /
  `progress-<child>.json`). Opened cold with no network → the active child's
  cached board is shown with a **"📴 offline — showing your last board"** banner
  (`stale = true`); a board whose `child_id` doesn't match the active child is
  never served. A fresh success overwrites the cache and clears the flag; a
  `4xx` is never served from the cache. Cleared on "forget this device", and a
  single child's slot is cleared when the account drops that child.
- **Write queue** (`OfflineQueue`): "I did it" tapped offline (or on a `5xx`)
  is queued **with the child's id** and flushed on reconnect; a `409` on replay
  means "already resolved" → dropped, not surfaced as an error (INV-11).
  `flushQueue()` replays **only the active child's** entries — another child's
  intents wait until that child is the active board — so switching children
  never re-attributes a pending completion. Verified end to end on device
  (debug): offline "I did it" → queued → reconnect → synced → appears in the
  parent approvals queue.

> **Multi-child (DECISION-021):** the cache and queue are `childId`-scoped
> (fixed 2026-08-31 — was §10 CRACK-1). Legacy id-less entries and the
> single-child paired-device path still work (a blank id matches whoever is
> active). See [`../docs/PROJECT_STATE.md` §10](../docs/PROJECT_STATE.md#10-cracks--gaps).

## Release engineering

See [`RELEASE.md`](./RELEASE.md). Debug (`.debug` appId, dev backend, no
minify) and release (R8 + resource shrink, `network_security_config` enforcing
HTTPS except loopback, signed) build side by side. The **R8-minified release
build is verified to run** against a live backend (proguard rules keep
`kotlinx.serialization` + Retrofit). Backup/device-transfer of tokens is
disabled (`data_extraction_rules.xml`). Adaptive launcher icon.

## Known limitations

- No push / real-time celebration (poll only — matches the backend).
- Runtime base-URL change persists synchronously (`.commit()`), then
  `Context.restartApp()` relaunches from the launcher entry point
  (`FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK`, then `exit(0)`) so
  `Application.onCreate` rebuilds `AppContainer` cleanly. The
  DataStore-backed session survives; the PIN gate re-applies (unchanged).
- Single `:app` module; a production split (`:core`, `:data`, `:ui`) is a
  reasonable later refactor.
- iOS, Play Store packaging / `.aab`, analytics, monetization: out of scope.

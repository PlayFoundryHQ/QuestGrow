# QuestGrow — Native Android Client

A native Android **client** of the QuestGrow backend. It consumes the
established `/v1` API and domain contracts (`docs/architecture/TECHNICAL_MODEL.md`,
`DECISION-001…019`, `INV-1…18`, `docs/experience/UX_PRINCIPLES.md`). It does
**not** re-implement QuestGrow's product model — the server stays authoritative
for identity, `complexityProfile`, ownership stage, verification, rewards,
balances and approvals.

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
ui/child/    code entry · Today · Do-it (+ TTS) · waiting · celebration · progress
ui/parent/   sign-in (PIN gate) · dashboard · approvals (+ batch) · family
             (+ adaptation overrides) · quests (+ templates) · rewards · ownership
             (+ suggestions) · settings
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
- **Auth contract verbatim** — `login → session → unlock(PIN) → parent token
  → child token`. No refresh tokens, no new gate semantics (reserved product
  decisions).
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

Child sign-in: a parent creates a code in **Settings → Child sign-in code**
(the app calls `/v1/auth/child-token`); paste it on the child's "enter code"
screen.

## Testing

```
./gradlew :app:testDebugUnitTest            # 19 JVM tests
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
- **`ChooserUiTest`** (instrumented) — Compose semantics + click.

## Accessibility

Implemented: ≥64 dp touch targets on child controls; `contentDescription` on
quest cards and action buttons (carries the quest name even when `icon_only`
hides the visible label); state shown as text + glyph, never colour alone;
`speechSynthesis`/`TextToSpeech` tap-to-hear + `audio_narration: "always"`
auto-read; reduced-motion honoured (celebration animation stilled when the OS
animation scale is 0); large readable type.

**Verified on an emulator** (functional): the flows run against a live backend
and the accessibility labels are present in the view tree.
**NOT independently verified:** a real OS screen-reader (TalkBack) pass, and
pixel/visual layout — the headless emulator's `screencap` returns black frames
in this environment (a GPU limitation, not an app defect).

## Offline behaviour

- **Read cache** (`ReadCache`): the last successful child *Today* board and
  *Progress* are cached on disk. Opened cold with no network → the cached
  board is shown with a **"📴 offline — showing your last board"** banner
  (`stale = true`). A fresh success overwrites the cache and clears the flag;
  a `4xx` is never served from the cache. Cleared on "forget this device".
- **Write queue** (`OfflineQueue`): "I did it" tapped offline (or on a `5xx`)
  is queued and flushed on reconnect; a `409` on replay means "already
  resolved" → dropped, not surfaced as an error (INV-11). Verified end to end
  on device (debug): offline "I did it" → queued → reconnect → synced →
  appears in the parent approvals queue.

## Release engineering

See [`RELEASE.md`](./RELEASE.md). Debug (`.debug` appId, dev backend, no
minify) and release (R8 + resource shrink, `network_security_config` enforcing
HTTPS except loopback, signed) build side by side. The **R8-minified release
build is verified to run** against a live backend (proguard rules keep
`kotlinx.serialization` + Retrofit). Backup/device-transfer of tokens is
disabled (`data_extraction_rules.xml`). Adaptive launcher icon.

## Known limitations

- No push / real-time celebration (poll only — matches the backend).
- Runtime base-URL change persists (`.commit()`) and kills the process to
  reload; a proper task-restart would be smoother.
- Single `:app` module; a production split (`:core`, `:data`, `:ui`) is a
  reasonable later refactor.
- iOS, Play Store packaging / `.aab`, analytics, monetization: out of scope.

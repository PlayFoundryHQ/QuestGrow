# QuestGrow Android — Build & Release

## Build variants

| Variant | `applicationId` | Backend default | Minify | Signing |
|---|---|---|---|---|
| **debug** | `hq.playfoundry.questgrow.debug` | `http://10.0.2.2:8000/` (emulator host) | off | debug keystore |
| **release** | `hq.playfoundry.questgrow` | `$QG_BACKEND_URL` at build time, else `https://questgrow.example/` | R8 + resource shrink | see below |

Debug and release can be installed side by side (different `applicationId`).
Cleartext HTTP is permitted **only** for loopback hosts in release
(`res/xml/network_security_config.xml`); the debug build is fully permissive so
QA can point at any staging host.

## Backend URL

- Baked in at build time: `QG_BACKEND_URL=https://api.questgrow.example/ ./gradlew :app:assembleRelease`.
- Overridable at runtime: **parent Settings → Backend server** (or the
  "Backend server" expander on the sign-in screen). Persisted synchronously;
  the app restarts to apply.

## Building

```
cd android
export ANDROID_HOME=$HOME/Android/Sdk            # or a local.properties sdk.dir
./gradlew :app:assembleDebug                     # app/build/outputs/apk/debug/app-debug.apk
QG_BACKEND_URL=https://api.example/ ./gradlew :app:assembleRelease
```

## Signing the release

`assembleRelease` needs a signing key. Two ways:

1. **`keystore.properties`** at `android/` (gitignored):
   ```
   storeFile=/absolute/path/to/upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
2. **Environment** (CI): `QG_KEYSTORE_FILE`, `QG_KEYSTORE_PASSWORD`,
   `QG_KEY_ALIAS`, `QG_KEY_PASSWORD`.

If **neither** is present the build **falls back to the debug keystore** so the
APK is still produced and installable — do **not** distribute that artifact.
Generate a real upload key with:

```
keytool -genkey -v -keystore upload.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000
```

## Versioning

`versionCode` / `versionName` come from `QG_VERSION_CODE` / `QG_VERSION_NAME`
(defaults `1` / `0.1.0`). Bump per release in CI.

## Verification checklist (per release)

```
./gradlew :app:testDebugUnitTest            # 24 JVM tests
./gradlew :app:lintVitalRelease             # release-blocking lint
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```
Then, on a device/emulator: sign in, load the child Today board (proves R8
kept `kotlinx.serialization` + Retrofit), complete a quest, check the parent
approvals queue.

## Not covered here (needs a separate Product Owner grant)

Play Store publication, app bundle (`.aab`) upload, Play App Signing
enrolment, crash/analytics reporting, staged rollout.

# QuestGrow Android — Build & Release

## Build variants

| Variant | `applicationId` | Backend default | Minify | Signing |
|---|---|---|---|---|
| **debug** | `hq.playfoundry.questgrow.debug` | `http://10.0.2.2:8000/` (emulator host) | off | debug keystore |
| **release** | `hq.playfoundry.questgrow` | `$QG_BACKEND_URL` at build time, else **`https://questgrow.opscale.ir/`** | R8 + resource shrink | see below |

Debug and release can be installed side by side (different `applicationId`).
Cleartext HTTP is permitted **only** for loopback hosts in release
(`res/xml/network_security_config.xml`); the debug build is fully permissive so
QA can point at any staging host.

## Backend URL

- Release defaults to `https://questgrow.opscale.ir/` (the live backend).
  Override at build time: `QG_BACKEND_URL=https://other/ ./gradlew :app:assembleRelease`.
- Overridable at runtime: **parent Settings → Backend server** (or the
  "Backend server" expander on the sign-in screen). Persisted synchronously;
  the app relaunches to apply.

## Building

```
cd android
export ANDROID_HOME=$HOME/Android/Sdk            # or a local.properties sdk.dir
./gradlew :app:assembleDebug                     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease                   # signed, points at questgrow.opscale.ir
```

## Signing the release

The QuestGrow **upload key** is a 4096-bit RSA key
(`CN=QuestGrow, O=PlayFoundryHQ, C=IR`,
SHA-256 `b3:2d:71:12:bd:aa:82:21:9d:65:95:88:f6:f6:81:f8:ab:59:14:b7:fa:66:30:bd:bb:14:2e:a1:8b:51:49:a8`).
Keep the `.jks` and its passwords **off-repo and backed up** — losing it means
a new `applicationId` for any future store listing.

`build.gradle.kts` reads the key from either:

1. **`android/keystore.properties`** (gitignored):
   ```
   storeFile=/absolute/path/to/questgrow-release.jks
   storePassword=…
   keyAlias=questgrow
   keyPassword=…
   ```
2. **Environment**: `QG_KEYSTORE_FILE`, `QG_KEYSTORE_PASSWORD`,
   `QG_KEY_ALIAS`, `QG_KEY_PASSWORD`.

If neither is present the build **falls back to the debug keystore** — the
APK is produced but must **not** be distributed. `scripts/release.sh --with-apk`
verifies the signer is the QuestGrow key and aborts otherwise.

To regenerate (only if the key is lost — this changes app identity):
```
keytool -genkeypair -v -keystore questgrow-release.jks -storetype PKCS12 \
        -alias questgrow -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=QuestGrow, O=PlayFoundryHQ, C=IR"
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

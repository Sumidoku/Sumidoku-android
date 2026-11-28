# AGENTS.md

Guidance for coding agents working in this repository.

## Project overview

**Sumidoku for Android** is the official native Android client for the
self-hosted [Sumidoku manga server](https://github.com/Sumidoku/Sumidoku).
Kotlin + Jetpack Compose (Material 3), Hilt DI, Room/DataStore for local
state, Coil for images, Retrofit against the server's REST `/api/v1`.

- Min SDK 26, target SDK 36, compile SDK 37. JDK 17 required.
- The server does the computation (WASM sources, tracker pushes, update
  scheduler, server-side download queue); the client is a native interface
  to all of it, plus true offline reading, background downloads and update
  checks.

Architecture record: `docs/android-client.md` (written before the code,
kept as the design document; source comments cite its sections). The
README carries the current module map and build instructions.

## Module map

26 Gradle modules, strict dependency direction `app` → `feature` → `core`.
Do not introduce upward dependencies (feature → feature, core → feature).

| Layer | Modules | Responsibility |
| --- | --- | --- |
| `app` | 1 | Application shell, navigation host, theme, Coil image-loader factory, update worker |
| `feature:*` | 15 | One Compose screen + ViewModel per feature: `servers`, `library`, `browse`, `search`, `manga`, `reader`, `source`, `repos`, `updates`, `history`, `downloads`, `migrate`, `trackers`, `settings`, `backups` |
| `core:*` | 10 | `api` (generated Retrofit client), `network` (client factory, auth, failover), `data` (Room, DataStore, repositories), `source`, `stream` (SSE), `download`, `image` (waifu2x upscaling: ncnn+Vulkan JNI, enhancement cache, Coil decoder, priority queue), `model`, `common`, `ui` (design system) |

Modules are declared in `settings.gradle.kts`; add new ones there.

## API contract — read this before touching `core:api`

- `api/openapi.json` is a **pinned snapshot** of the server's OpenAPI
  document; `api/openapi.json.sha256` pins its hash.
- The Retrofit client (kotlinx-serialization) is **generated at build
  time**. Never edit generated output by hand.
- Known generator quirks are fixed by a small, deterministic
  `patchGeneratedSources` step (sealed `oneOf` bodies, `kotlin.Any` →
  `JsonElement`, union fields sent as omitted, free-form scalar wrappers).
  Extend that patch set rather than post-editing generated files.
- Regenerate deliberately, only when the server API moves, and update the
  pinned hash together with the snapshot.

## Build, test, verify

Use the Gradle wrapper from the repo root. On Windows use `gradlew.bat`
under PowerShell; under Git Bash `./gradlew` also works.

```bash
./gradlew assembleDebug      # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # unsigned unless signing env vars are set
./gradlew test               # unit tests
```

- The Android SDK is located via `ANDROID_HOME` / `ANDROID_SDK_ROOT` or
  `sdk.dir` in `local.properties`.
- `core:image` compiles C++ (ncnn + Vulkan waifu2x). It needs **NDK
  `27.2.12479018`** and **CMake `3.22.1`** installed, and downloads the
  prebuilt `ncnn-20260113-android-vulkan` SDK into `third_party/ncnn/`
  (gitignored) via `:core:image:fetchNcnnSdk`, sha256-pinned. A
  pre-existing SDK directory is reused without re-downloading, so an offline
  checkout can be primed by copying it in. Only `arm64-v8a` and
  `armeabi-v7a` are built; x86 emulators get no engine and the reader falls
  back to the source page.
- Release signing is driven by four env vars: `KEYSTORE_FILE`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Missing any → unsigned
  build. **Never commit keystores, secrets, or `local.properties`.**
- Before considering a change done: run `./gradlew test` and, if you
  touched UI or build logic, `assembleDebug`.

## CI and releases

- `.github/workflows/ci.yml` — every push to `master` and every PR:
  `test`, `assembleDebug`, `assembleRelease`. Keep all three green.
- **Lint**: no detekt / ktlint / spotless, and CI has no standalone lint
  step. The only gate is Android lint in `app/build.gradle.kts` (one
  check disabled: `PropertyEscape`, which misfires on `sdk.dir` under
  Windows). `assembleRelease` runs AGP's `lintVitalRelease`, so
  fatal-level lint issues fail the release build locally and in CI.
  If you add a static-analysis gate, wire it into `ci.yml` too.
- `.github/workflows/release.yml` — fires when `app/build.gradle.kts`
  changes on `master` (or manual dispatch). It reads `versionName` /
  `versionCode`, skips if tag `v<versionName>` exists, builds, checksums
  and publishes a GitHub release. To cut a release: bump `versionCode`
  and `versionName` in `app/build.gradle.kts`.
- Release secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD`. Without them the workflow publishes a debug-signed APK.

## Conventions

- **Language**: Kotlin everywhere; UI is Jetpack Compose with Material 3.
  No new Java or View/XML-based UI.
- **DI**: Hilt. Constructor injection for ViewModels and repositories.
- **Networking**: only through `core:api` + `core:network`. Do not create
  ad-hoc Retrofit/OkHttp instances in feature modules. Bearer auth and
  mirror failover live in `core:network`.
- **Streaming**: SSE lives in `core:stream` (`Last-Event-ID` + exponential
  backoff); NDJSON progressive rendering must fall back to the plain
  response when streaming fails.
- **Local state**: Room + DataStore via `core:data` repositories. Features
  consume repositories, not databases directly.
- **Images**: page rendering goes through Coil's single image loader, whose
  decoder is registered in `SumidokuApp`. Waifu2x upscaling lives in
  `core:image` and is requested per page through
  `EnhancementDecoder.applyTo(...)`; never bypass it with a direct
  `BitmapFactory` decode in a feature module. Upscaling settings are
  device-only (global DataStore keys, no per-manga override).
- **Background work**: WorkManager (offline download resume, 6-hour update
  checks). No raw threads or `GlobalScope`.
- **UI**: use the `core:ui` design system. Adaptive layout: bottom bar on
  phones, navigation rail on wide screens; library has a two-pane layout.
- **Localization**: English default; `values-zh-rCN` (Simplified) and
  `values-zh-rTW` (Traditional) exist. Add strings to all three
  `strings.xml` files.

## Git and files

- Do not touch `.workbuddy/` — it stores project-related data.
- `.gradle/`, `.kotlin/`, `build/`, `local.properties` are
  machine-local; ignore them.
- `gradle.properties` and wrapper files are shared config; change with
  care.

## Known limitations (don't "fix" silently; confirm intent first)

- `usesCleartextTraffic` is enabled deliberately for LAN self-hosted
  servers; per-host `networkSecurityConfig` is the intended pre-release
  fix.
- Button-type source settings render as a plain row (fallback).
- No "clear source cache" action yet; deep links not parsed yet; settings
  lack dual-pane/foldable handling.

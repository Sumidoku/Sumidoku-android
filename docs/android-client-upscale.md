# Android Client — Waifu2x Upscaling (design and implementation plan)

Status: implemented on `feat/waifu2x-upscale` (P0–P4, P6); see §11 for the deviations.
Reference implementation: `KMReader-android` (`core/image/upscale/*`, `cpp/waifu2x_jni.cpp`).
This document is the design record for adding the same capability to this client; when accepted it
should be treated like `docs/android-client.md` — kept in step with the code, not treated as a
snapshot.

## 1. Scope

Add client-side 2x image upscaling for reader pages, using the same engine the iOS app and
KMReader use: waifu2x `upconv7` weights, run through ncnn on Vulkan.

In scope:

- a native engine (ncnn + Vulkan, JNI) bundled in the APK, with graceful degradation when the
  device has no usable Vulkan driver;
- a disk + memory cache for upscaled pages, keyed so that a model/denoise/precision change never
  serves a stale or mismatched variant;
- a trigger rule ("only upscale when the screen would enlarge this page"), so crisp pages are
  never re-processed;
- a single-worker priority queue with prefetch, so page turns stay fast even when a page takes
  seconds on the GPU;
- settings that live where the rest of the reader settings live (defaults screen + in-reader
  sheet), device-scoped rather than per-manga;
- cache management and diagnostics that fit this app's existing settings surfaces.

Out of scope (deliberate):

- upscaling covers, thumbnails, library grids, or text/EPUB chapters;
- server-side upscaling (no new Sumidoku server endpoint; the server keeps sending original bytes,
  scaling stays a client duty — `docs/android-client.md` §12);
- an "always" trigger mode, Real-CUGAN / Real-ESRGAN models, or a shader-only Anime4K post-process;
- upscaling already-downloaded CBZ *files* on disk (only the reader's decoded pages are enhanced).

## 2. What the reference implementation actually is

KMReader's upscaler is not a decoder tweak; it is a subsystem of six Kotlin types plus one
translation unit. Anatomy, in dependency order:

| Piece | File | Responsibility |
| --- | --- | --- |
| Native engine | `app/src/main/cpp/waifu2x_jni.cpp` (1345 lines), `fused_shaders.h` | ncnn+Vulkan inference for one page: fused path (preproc/postproc compute shaders sample a padded tile out of the page and write the 2x result straight back) and staged CPU fallback; tiling (128px tiles, 7px prepadding), banded processing for tall pages, FP16 self-test plus per-pixel range check, generation/pause hooks |
| Build plumbing | `app/src/main/cpp/CMakeLists.txt`, `app/build.gradle.kts` | prebuilt `ncnn-20260113-android-vulkan` SDK fetched + sha256-verified by a `fetchNcnnSdk` Gradle task, static glslang libs, NDK `libomp.a`, `-fno-rtti` to match the prebuilt SDK, ABI filters `arm64-v8a` + `armeabi-v7a` |
| Bridge | `core/image/upscale/Waifu2xBridge.kt` | JNI surface: `ensureInitialized(context, model, precision)`, `process(input)`, `cancelInFlight()`, `setPaused()`, `release()`; model assets copied from `assets/upscale-models/waifu2x-upconv7/` into the cache dir once per `MODEL_CACHE_VERSION`; output budget constants (`MAX_OUTPUT_WIDTH=4096`, `MAX_OUTPUT_HEIGHT=16384`, `MAX_OUTPUT_PIXELS=20M`) that the decision layer reuses so queue and engine can never disagree |
| Cache | `core/image/upscale/EnhancementCache.kt` | Coil `DiskCache` (512 MB) + 64 MB in-memory LRU, PNG lossless, per-page key = `sha256(pageUrl, modelSignature)` with `#denoise@precision` variant suffix; `readNearest` serves another denoise level while the requested one regenerates; max 2 variants per page |
| Decoder | `core/image/upscale/EnhancementDecoder.kt` | a Coil 3 `Decoder.Factory` registered only on the page loader and only for requests marked via typed extras. Flow: bounds-only decode → cache hit → trigger decision → stale-variant fallback → full decode → queue → return the source bitmap immediately, swap in the enhanced one later |
| Queue | `core/image/upscale/ImageEnhancer.kt` | one worker; priorities VISIBLE > ADJACENT > ELEVATED > PRELOAD, then distance, then FIFO; duplicate keys coalesce and a more urgent request boosts a queued one; `MAX_PENDING_TASKS=6`; circuit breaker after 3 consecutive failures; `SLOW_TASK_MILLIS=5s` stops admitting preloads; `nextGeneration()` drops the previous reading session and aborts the in-flight tile loop |
| Config + trigger | `UpscaleConfig.kt`, `UpscaleDecision.kt` | defaults (Auto, denoise Low, threshold 1.05, precision Auto); decision = `fitsEngineBudget && fitsTexture && displayScale > threshold`, with a `cover` flag for `ContentScale.Crop` halves |
| Settings | `DivinaSettings`/`DivinaSettingsStore`, `ui/settings/ReaderDefaultsScreen.kt` | `UpscaleMode` (Off/Auto), `DenoiseLevel` (None/Low/Medium/High/Max), `UpscalePrecision` (Auto/Fp16/Fp32), auto-trigger slider 1.0–1.5, preload-ahead 0–5 |

Facts worth carrying over verbatim:

- five model pairs ship in `assets` (`scale2.0x`, `noise0..3_scale2.0x`), ~1.06 MB `.bin` + ~1 KB
  `.param` each — about 5.3 MB total;
- the engine refuses anything whose 2x output exceeds the budget, and that refusal counts as a
  failed run, so the decision layer must apply the same budget before queueing;
- the enhanced page is returned as the *decode result*, so the reader never blocks its first frame
  on a multi-second GPU run; `ImageEnhancer.readyKeys` is what tells the reader to re-request and
  swap in the finished page;
- on `ContentScale.Crop` halves the painted scale is the larger of the two ratios, not the smaller
  one — KMReader had a bug here that silently disabled upscaling for every split spread.

## 3. Options considered

| Option | What it is | Verdict |
| --- | --- | --- |
| **A. Port KMReader's ncnn+Vulkan engine** | Same weights, same quality, proven on mid-range mobile GPUs; fused shader path is 2–4x faster than staged CPU; the port is mostly mechanical (rename packages, swap the settings/cache plumbing) | **Recommended** |
| B. TFLite / ONNX Runtime with a GPU delegate | One dependency, no NDK work; but no equivalent upconv7 Vulkan kernel, larger binary, and mobile GPU delegates are notoriously driver-sensitive on exactly the devices this app targets | Rejected — worse on device, more work to match quality |
| C. Server-side upscale (`/chapter/{id}/page/{i}?scale=2`) | No client work beyond URL params; but it moves a per-page GPU/CPU job onto a self-hosted box (often a NAS or a Pi), breaks offline reading of downloaded pages, and contradicts §12's "scaling is a client duty" | Rejected for this milestone; noted as a possible server feature later |
| D. Anime4K-style GLSL post-process in Compose | Cheap, no NDK; but it sharpens/denoises without adding real detail, cannot run on `ContentScale.Fit` bitmaps cheaply in Compose, and produces a different look from the iOS app | Rejected |

## 4. Design for Sumidoku

### 4.1 Module placement

The upscaler needs a decoder (used by the app's Coil loader), the reader (extras + priorities), and
the settings surfaces. It must not create an upward dependency, so it becomes a new core module
rather than code inside `feature:reader` or `app`:

```
app  ──▶ core:image            (Coil ImageLoader factory registers the decoder)
feature:reader ──▶ core:image  (request extras, priorities, ready/processing flows)
feature:settings ──▶ core:image (device cache usage + clear, optional phase 5)
core:image ──▶ core:common     (coroutine guards / logging conventions)
core:image ──▶ core:data       (active server profile for cache namespacing)
```

`settings.gradle.kts` gains `include(":core:image")`; the module map in `AGENTS.md`, the README
table and `docs/android-client.md` §4/§5 move from 25 to 26 modules. The native sources, the CMake
file and the five model pairs live in this module (`core/image/src/main/cpp`,
`core/image/src/main/assets/upscale-models/waifu2x-upconv7/`), which keeps the app module free of
build plumbing and lets library assets merge into the APK as usual.

### 4.2 Native stack

- Copy `waifu2x_jni.cpp` and `fused_shaders.h` unchanged except for the log tag and the
  attribution header (MIT `nihui/waifu2x-ncnn-vulkan`, Apache-2.0 `HaoweiLi97/mihon_img_upscale`);
  the engine is device-agnostic and already debugged.
- `core/image/build.gradle.kts` gets `ndkVersion = "27.2.12479018"`, `externalNativeBuild.cmake
  path = "src/main/cpp/CMakeLists.txt"`, `abiFilters += listOf("arm64-v8a", "armeabi-v7a")`, and
  the `fetchNcnnSdk` task (download + sha256 verify + extract into `third_party/ncnn/`), with
  `preBuild.dependsOn(fetchNcnnSdk)`. `third_party/ncnn/` is added to `.gitignore`.
- Model assets are the exact five KU pairs from KMReader (§2). They are MIT-licensed; add a
  `THIRD_PARTY_NOTICES.md` entry (ncnn BSD-3, glslang BSD/Apache, waifu2x models MIT).
- **minSdk question.** This app targets API 26; KMReader targets 28. The prebuilt SDK is linked
  against a modern NDK and may not load on API 26/27. The plan is: attempt initialization only
  when `Build.VERSION.SDK_INT >= 28` *and* `System.loadLibrary` succeeded, and treat every other
  case as "upscaling unavailable" (settings show a hint, pages render untouched). If a 26/27
  device test proves the library loads, drop the SDK_INT guard. Bumping `minSdk` to 28 is the fallback
  only if the library hard-fails at load time *and* the product decides 26 is not worth a separate
  build; it is not part of this milestone.
- APK size: two ABIs × ~4–6 MB stripped plus 5.3 MB models. Keep the universal APK for now;
  `splits.abi` is a later optimisation, recorded in the README as a follow-up.

### 4.3 Engine bridge (`core:image/.../upscale/Waifu2xBridge.kt`)

Same public surface as KMReader, minus the app-log path:

- `DenoiseModel` enum (NONE/LOW/MEDIUM/HIGH/MAX) mapping to the file suffixes;
- `ensureInitialized(context, model, precision)`, serialized through a `Mutex` because the ncnn
  `Net` is not thread-safe and the GPU has one compute queue;
- `process(bitmap): Bitmap?` with the budget check *before* the output allocation, and OOM caught;
- `cancelInFlight()` / `setPaused()` for the reader's lifecycle hooks;
- `isVulkan` / `isFp16` / `isFused` flags for diagnostics, plus `lastError()` / `lastStats()`.

Load guard: `System.loadLibrary("waifu2x-jni")` wrapped in `runCatching`; a failed load only sets
`available = false`, never crashes the app (x86 emulators and ABI-split APKs will hit this).

### 4.4 Cache (`EnhancementCache.kt`)

Port with three Sumidoku-specific changes:

1. **Namespacing.** One `DiskCache` per server profile, at
   `cacheDir/upscale/<instanceKey>/`, where `instanceKey` is the normalised base URL hash (the same
   identity the profile list uses), rebuilt on profile switch with the previous cache shut down
   first (two `DiskCache` instances must never share a journal). Cache dir, not files dir: entries
   are regenerable and Android may reclaim them.
2. **Key material.** `pageKey = sha256(pageUrl, MODEL_SIGNATURE)`; `pageUrl` is exactly what the
   reader requests — the HTTP URL with its `?v=<cacheRevision>` for remote chapters, the
   `file://` URI for offline chapters. A new `cacheRevision` therefore misses naturally; a
   re-download that lands at a new path orphans the old entry to LRU.
3. **Budget.** `MAX_SIZE_BYTES = 512 MB` (constant, not yet a setting), `MAX_MEMORY_BYTES = 64 MB`,
   `MAX_VARIANTS_PER_PAGE = 2`, variant suffix `#<denoise>@<precision>`, `readNearest()` restricted
   to the same precision.

`clear()`, `evictPages(urls)` and `pruneVariants()` come across unchanged. Exact eviction when a
downloaded chapter is deleted is *not* in the core milestone (the page URLs are not always
enumerable offline); LRU plus the clear action covers it. If the product wants exact eviction, a
follow-up can record `chapterId → keys` in a small Room table (`upscale_keys`) written at cache
write time — noted as P2 in §5.

### 4.5 Trigger (`UpscaleDecision.kt`)

`shouldUpscale(srcW, srcH, displayScale, config, maxTexture)` where `displayScale` is the pixels
the page is actually painted at, per source pixel. Sumidoku computes it in the reader, because the
layout modes differ from KMReader's:

| Reader mode | Painted scale |
| --- | --- |
| paged, `scale = fit` (single or double cell, `ContentScale.Fit`) | `min(cellW / srcW, cellH / srcH)` |
| paged, `scale = width` (`FillWidth`) | `cellW / srcW` |
| paged, `scale = height` (`FillHeight`) | `cellH / srcH` |
| paged, `scale = original` (`ContentScale.None`) | `1.0` (never triggers) |
| paged, split wide half (`crop != 0`) | `2 × min(cellW / srcW, cellH / srcH)` — the reader
  stretches the fitted page horizontally by 2 before clipping to its half |
| webtoon / continuous (`FillWidth` in a scrolling column) | `screenW / srcW` |

Guards, same as KMReader: `displayScale > threshold` (default 1.05, range 1.0–1.5),
`fitsEngineBudget`, `fitsTexture`. Source dimensions come from the page metadata
(`Page.width/height`, present for server-known pages) — no bounds decode on the common path; the
decoder still does a bounds pass when the metadata is absent (local/unmeasured pages).

Tall webtoon strips (e.g. 800×12000) are correctly refused by the budget; they are never painted
wider than the screen anyway once the strip is scaled down, and the engine budgets are not negotiable.

### 4.6 Decoder and Coil registration (`EnhancementDecoder.kt`)

Sumidoku has a single Coil singleton loader today (`context.imageLoader`, used by every screen). So
instead of KMReader's separate per-instance page loader:

- `SumidokuApp` implements `coil3.SingletonImageLoader.Factory` and builds the loader with
  `components { add(EnhancementDecoder.Factory(...)) }`, injecting the cache and enhancer from
  Hilt (the Application is `@HiltAndroidApp`, so constructor-free field injection works; an
  `@EntryPoint` is the fallback if composition-order problems appear).
- The factory returns `null` unless the request carries the typed extras set by `applyTo(...)`,
  so covers, avatars and text pages keep the default decoder and pay nothing.
- Extras carry: enabled flag, page URL, mode, denoise, precision, explicit `displayScale` (null =
  compute from `options.size` + display metrics, the prefetch case), priority, and whether the
  result is already ready (so the request identity changes when the enhanced bitmap lands).
- Decoder flow is KMReader's, in the same order (cache → trigger → stale variant → full decode →
  queue → return source), with the source bitmap handed back immediately.
- Upscaled page requests set `memoryCachePolicy(DISABLED)`: 2x bitmaps belong in the enhancement
  cache and its 64 MB memory LRU, not in Coil's memory cache (a source bitmap there would otherwise
  be served for a request that wants the enhanced one).

### 4.7 Scheduler (`ImageEnhancer.kt`)

Port as-is: one worker on `Dispatchers.Default`, `PriorityQueue` with the four priorities, key
coalescing + boost, `MAX_PENDING_TASKS = 6`, circuit breaker at 3 consecutive failures, slow-task
throttle (5 s / 1.5 s recovery), `readyKeys` and `processingKeys` state flows, `nextGeneration()`
that also aborts the in-flight tile loop.

Sumidoku wiring:

- chapter open / chapter switch → `nextGeneration()` (via `ReaderViewModel.openChapter`, after
  cancelling `chapterJob`, so the old chapter's pages do not finish into the new session);
- reader exit (`DisposableEffect` on the reader route) → `nextGeneration()` + `Waifu2xBridge.release()`;
- pager drag (`pagerState.isScrollInProgress` or the vertical list's `isScrollInProgress`) →
  `setPaused(true/false)`, so the GPU yields to the compositor while a page is under the finger.

### 4.8 Reader integration

`ReaderScreen` currently builds one `ImageRequest` in `ReaderImage` and prefetches ±1..+2 slots
with `enqueue`. Changes:

- `ReaderImage` gains `page`, `priority`, `displayScale` and `ready` parameters; when upscaling is
  on it calls `EnhancementDecoder.applyTo(builder, ...)` and puts the ready flag + config in
  `memoryCacheKeyExtra` (Coil compares typed extras in request identity only via that string),
  then disables the memory cache for that request.
- The request is remembered on `(url, config, ready, retryKey, displayScale, priority)`; when
  `ready` flips, the painter restarts and the cache hit returns the enhanced bitmap — that is the
  swap, no bitmap mutation, no layout jump (the 2x page has exactly double the dimensions, so
  aspect-fit geometry is identical).
- Priorities: current index → `PRIORITY_VISIBLE`; the other page of a spread and ±1 slot →
  `PRIORITY_ADJACENT`; anything beyond → `PRIORITY_PRELOAD`.
- Prefetch (`LaunchedEffect(state.index, displayPages.size)`) switches from plain `enqueue` to
  `execute` with the decoder extras at `PRIORITY_PRELOAD`, for the page indices covered by
  `preloadAhead` (0–5, default 3), rounded up to whole spreads in double layout; pages that fail
  the trigger decode 512 px and are discarded, keeping the source bytes in Coil's disk cache.
- Optional "2x" badge on the current page while `processingKeys` contains its key
  (`UpscaleProcessingBadge`, KMReader's pulsing capsule); cheap, helps users understand a slow
  first page.
- `ReaderViewModel` injects `ImageEnhancer` and exposes `enhancedKeys` / `processingKeys` as
  `StateFlow<Set<String>>`, and a `cacheKeyFor(page)` helper so the composable does not re-derive
  the key itself.

Split halves and double spreads need no special decoder branch: the split path passes
`2 × fit` as the display scale (§4.5), the double spread passes its own half-cell constraints
through `options.size` and the trigger's `min` rule.

### 4.9 Settings

Upscaling is a property of the device, not of the book, so — following the `ReaderField.VOLUME`
precedent ("the rocker is a property of the device") — the new fields are **global-only**: they live
in `ReaderSettingsStore` under `reader.g.upscale.*`, are written by both the defaults screen and the
in-reader sheet, and carry no per-manga override (the sheet's override marker never appears for
them).

New `ReaderField` entries and `ReaderPrefs` fields:

| Field | Values | Default |
| --- | --- | --- |
| `UPSCALE` | `off` \| `auto` | `off` |
| `UPSCALE_DENOISE` | `none` \| `low` \| `medium` \| `high` \| `max` | `low` |
| `UPSCALE_THRESHOLD` | 1.0–1.5 (`float` as string, clamped both on write and read) | `1.05` |
| `UPSCALE_PRECISION` | `auto` \| `fp16` \| `fp32` | `auto` |
| `UPSCALE_PRELOAD` | 0–5 (`int`) | `3` |

`UpscaleSettings` stays in `core:image` (mode/denoise/threshold/precision + `preloadAhead`) so the
decoder never depends on `feature:reader`; the reader maps its string prefs onto it in one function
(`ReaderPrefs.upscaleConfig()`), which is the single place a clamp can go wrong and therefore the
one place unit tests target.

UI: one `SettingsGroupCard` in `ReaderDefaultsScreen` ("Image upscaling") with a mode `ChipRow`
(off/auto), the threshold slider (visible only in Auto), denoise `ChipRow`, precision `ChipRow`,
preload-ahead `ChipRow` (0 = "Current page only"), and a footer caption that states the trade-off
(GPU time, first-page latency, ~4x cache size) and that unsupported devices will fall back to the
source page. The in-reader sheet gets the same controls as a compact section under the existing
filters block, sharing the same store writes. Strings go into all three `strings.xml` files
(`values`, `values-zh-rCN`, `values-zh-rTW`), per the repo convention.

Default is **off**: this is a GPU-heavy feature on a client whose selling point is a battery-friendly
reader; opting in is a conscious action, and the iOS app's "auto + low" is one tap away.

### 4.10 Cache management and diagnostics

- `CacheScreen` gains a "Device upscaling cache" card (usage bytes + entry count, "Clear" action,
  optional "Clear all servers"), read from `EnhancementCache.stats()` — same shape as the server
  cache cards it already shows, so the user sees all local storage in one place.
- Diagnostics: `android.util.Log` with `TAG = "Sumidoku/Upscale"` for every decision, cache hit,
  engine init (vulkan/fp16/fused), per-page timing, refusal, circuit-breaker trip. There is no in-app
  log screen on this client, so the messages are written to be useful from `adb logcat`, and the
  timing string is bounded (one line per page).
- Settings shows a one-line engine status when upscaling is enabled: "Vulkan ready (FP16, fused
  shaders)" / "Unavailable on this device (no Vulkan / unsupported ABI)" — from `Waifu2xBridge`
  flags after the first initialization.

## 5. Phases

Estimates are developer-days for someone who knows both code bases; device testing is not included
and is assumed to be available throughout.

| Phase | Content | Acceptance | Est. |
| --- | --- | --- | --- |
| P0 Native plumbing | `:core:image` skeleton, CMake, JNI source, model assets, `fetchNcnnSdk`, ABI filters, `.gitignore`, notices | `assembleDebug` produces an APK whose `lib/arm64-v8a/libwaifu2x-jni.so` and `lib/armeabi-v7a/...` exist; `System.loadLibrary` succeeds on one Vulkan device and one non-Vulkan/x86 case fails soft | 1–2 d |
| P1 Engine bridge | `Waifu2xBridge`, `DenoiseModel`, budget constants, load guard, error/stats strings | instrumented smoke test upscales a 200×300 PNG to 400×600 with pixel-level sample checks; FP16 self-test falls back to FP32 on a bad driver (can be simulated by forcing FP32) | 1 d |
| P2 Cache + decision + decoder | `EnhancementCache`, `UpscaleConfig`, `UpscaleDecision`, `EnhancementDecoder`, `ImageEnhancer`, Coil factory in `SumidokuApp` | JVM tests: cache key/variant/prune/readNearest, decision matrix (fit/width/height/original/split/webtoon/budget/texture), threshold clamping; on device: a page with the extras decodes to the 2x bitmap, the same page without extras decodes normally | 1.5 d |
| P3 Reader integration | request extras, priorities, ready swap, prefetch window, pause/resume, generation on chapter switch and exit, optional badge | on device: first frame shows the source page immediately, the 2x page replaces it in place; swiping while a page is processing keeps >55 fps; chapter switch never paints the previous chapter's enhanced page; leaving the reader stops GPU work | 2 d |
| P4 Settings | `ReaderField`/`ReaderPrefs`/`ReaderUiState`/VM plumbing, defaults-screen card, in-reader section, strings ×3, engine status line | prefs round-trip (write/read/reset), clamping, all three locales render, defaults screen and sheet agree | 1 d |
| P5 Cache UI + diagnostics | cache card in `CacheScreen`, stats, clear; log lines | card shows plausible usage after reading a chapter, clear drops it to zero and pages re-enhance afterwards | 0.5–1 d |
| P6 Verification, CI, docs | NDK/CMake in `ci.yml` + `third_party` cache, README/AGENTS/docs updates, release notes | CI green on a clean runner (native build cached under 60 s after first run); full manual matrix (§7) executed | 1 d |
| P2 optional | exact per-chapter eviction index (Room `upscale_keys`) | deleting a downloaded chapter frees its enhanced entries | 0.5 d |

Total: roughly 7–8 developer-days plus device time.

## 6. File-level change map

New:

- `core/image/build.gradle.kts`, `core/image/src/main/AndroidManifest.xml`
- `core/image/src/main/cpp/{CMakeLists.txt, waifu2x_jni.cpp, fused_shaders.h}`
- `core/image/src/main/assets/upscale-models/waifu2x-upconv7/{scale2.0x,noise0..3_scale2.0x}_model.{bin,param}`
- `core/image/src/main/kotlin/org/sumidoku/android/core/image/upscale/{Waifu2xBridge, ImagingModel, UpscaleSettings, UpscaleConfig, UpscaleDecision, EnhancementCache, EnhancementDecoder, ImageEnhancer}.kt`
- `core/image/src/main/kotlin/org/sumidoku/android/core/image/di/ImageModule.kt`
- `core/image/src/test/kotlin/.../{UpscaleDecisionTest, EnhancementCacheKeyTest, UpscaleSettingsTest}.kt`
- `core/image/src/androidTest/kotlin/.../Waifu2xEngineTest.kt` (not wired into CI; run manually)
- `docs/android-client-upscale.md` (this file), `THIRD_PARTY_NOTICES.md` entry

Changed:

- `settings.gradle.kts` — `include(":core:image")`
- `app/build.gradle.kts` — nothing native; `implementation(project(":core:image"))`
- `app/src/main/kotlin/org/sumidoku/android/SumidokuApp.kt` — `SingletonImageLoader.Factory`,
  loader components, decoder registration
- `feature/reader/src/main/kotlin/.../ReaderSettingsStore.kt` — 5 new `ReaderField`s, `ReaderPrefs`
  fields, `ReaderPrefs.upscaleConfig()`
- `feature/reader/src/main/kotlin/.../ReaderViewModel.kt` — inject cache/enhancer, expose
  `enhancedKeys`/`processingKeys`, `cacheKeyFor`, generation calls on chapter change/exit
- `feature/reader/src/main/kotlin/.../ReaderScreen.kt` — `ReaderImage` signature + request extras,
  display-scale computation per mode, priorities, prefetch via decoder, pause on scroll, badge
- `feature/reader/src/main/kotlin/.../ReaderDefaultsScreen.kt` + `ReaderDefaultsViewModel.kt` —
  upscaling card and setters
- `feature/reader/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml` — new strings
- `feature/settings/.../CacheScreen.kt` — device cache card (P5)
- `.github/workflows/ci.yml` — install `ndk;27.2.12479018` and `cmake;3.22.1`, cache
  `third_party/ncnn` keyed on the SDK version + hash
- `.gitignore` — `third_party/ncnn/`
- `README.md` (feature bullet + module table), `AGENTS.md` (module map, build note),
  `docs/android-client.md` (§4 module table, §5 tech stack, §12 reader bullet, §13 risk row for
  "page images are original bytes" gets a pointer here)

## 7. Testing and verification

JVM (runs in CI, no device):

- `UpscaleDecisionTest` — trigger matrix: below/at/above threshold per layout mode, split halves,
  webtoon width ratio, engine budget edges (4096/16384/20M), texture limit, unknown dimensions;
- `EnhancementCacheKeyTest` — same page different denoise → different variant, same variant
  different precision → different key, model signature change invalidates, prune keeps ≤ 2 variants;
- `UpscaleSettingsTest` — string ↔ config mapping, clamping of threshold/preload, unknown enum
  values fall back.

Instrumented (manual, documented in the test file header):

- engine smoke test: init on the device, upscale a synthetic gradient page, assert dimensions and
  that the range check passes; assert `isVulkan` and print the timing breakdown;
- a second run forced to FP32 to prove the escape hatch.

Manual device matrix (record results in the PR):

1. arm64 Vulkan phone, 1080×2400: 800×1200 page in `fit` → 2x badge → 1600×2400 result, swap without
   layout jump; crisp 1600×2400 page → no upscale.
2. Same phone, split wide page: halves upscale (regression guard for the `cover` bug).
3. Webtoon: normal strip upscales, 800×12000 strip refused without tripping the breaker across pages.
4. Double-page layout: both halves enhance, priorities favour the visible spread.
5. Denoise switch mid-chapter: previously read page served from a neighbouring variant instantly and
   regenerated in the background; second switch back is a hit.
6. Chapter switch during processing: GPU goes idle, new chapter never shows the old page.
7. Offline downloaded chapter: pages enhance from `file://` URIs, and deleting the download leaves
   the app functional (orphan entries reclaimed by LRU).
8. Non-Vulkan device or x86 emulator: app runs, settings show "unavailable", no crash, no badge.
9. Memory: 40 webtoon pages with upscaling, no OOM; cache grows to the cap and evicts.

CI: `./gradlew test`, `:app:assembleDebug`, `:app:assembleRelease` must stay green, now including the
native build. `lintVitalRelease` already runs; adding a native lib does not change that, but the
release APK check (ABI filters, no debug symbols shipped) should be part of the PR checklist.

## 8. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| ncnn prebuilt SDK will not load on API 26/27 | Upscaling silently unavailable on old devices | SDK_INT ≥ 28 guard, soft failure; verify on a 26 device before release; minSdk bump only if the product accepts it |
| Device Vulkan drivers vary (FP16 overflow → "flower screen", hangs) | Corrupted pages, GPU stalls | Keep KMReader's FP16 self-test + per-pixel range check, FP32 escape hatch as a user setting, circuit breaker after 3 failures, `kTileYieldMs` + pause-on-scroll |
| Native build slows CI and needs network | Red CI, long builds | `fetchNcnnSdk` with pinned sha256, `actions/cache` on `third_party/ncnn`, `-ffunction-sections`/`--gc-sections` already in CMake |
| APK grows by ~10 MB | Install size complaints | Measure; then consider `splits.abi` (arm64 alone) or dropping the three extra denoise models to `NONE` + `LOW` + `MEDIUM` (saves ~2 MB) |
| Coil singleton now has a decoder on every request | Unintended work on covers | Decoder returns `null` unless the typed extras are present; covers never set them; add a one-line log when the factory declines |
| Cache key drift (URL scheme changes, revision semantics) | Entries unreachable, cache grows to cap uselessly | Key scheme documented in one place (`EnhancementCache`), layout version marker clears the cache once on scheme change, `cacheRevision` already part of the URL |
| Enhanced bitmap used with a stale layout | Visual jump | 2x is exactly double in both dimensions, so fit/height/width geometry is preserved; `original` mode never triggers |
| 64 MB memory LRU plus double spreads on low-RAM devices | OOM | LRU cap measured, `MAX_VARIANTS_PER_PAGE = 2`, memory cache policy disabled for upscaled requests, budget refusal before allocation |

## 9. Open decisions

1. **minSdk**: verify the SDK on API 26 before writing the guard as permanent. If it loads, upscaling
   is available from 26 and the guard is dead code to remove. If not, keep the guard (no product
   change) and note it in the README limitations.
2. **Default mode**: proposal is `off` for battery reasons; `auto` + `low` is the iOS default and one
   tap away. Confirm before P4.
3. **Cache budget as a setting**: 512 MB constant vs a reader-settings stepper (128/256/512 MB/1 GB).
   Constant for the milestone; a stepper is P5-optional.
4. **Exact per-chapter eviction** (Room index): P2-optional, only if orphan entries prove to be a real
   storage complaint.
5. **Model set**: all five denoise levels vs `NONE`/`LOW`/`MEDIUM` (smaller APK, fewer variants to
   test). All five keeps full parity with KMReader and iOS.

## 10. Attribution

- ncnn — BSD-3-Clause, Tencent.
- glslang — BSD-3-Clause / Apache-2.0 (as shipped in the ncnn SDK).
- waifu2x `upconv7` weights — MIT, `nihui/waifu2x-ncnn-vulkan`.
- JNI translation unit — derived from `nihui/waifu2x-ncnn-vulkan` (MIT) and
  `HaoweiLi97/mihon_img_upscale` (Apache-2.0), header preserved.
- Kotlin side (decoder, queue, cache, decision) — adapted from `KMReader-android`; this repository is
  MIT, KMReader's license terms apply to the adapted code as noted in the file headers.

## 11. Implementation notes (what actually shipped)

- **Module**: `:core:image` (`org.sumidoku.android.core.image.upscale`), 26 modules total. The
  Application registers the decoder through `SingletonImageLoader.Factory` and an
  `UpscaleEntryPoint` rather than field injection, so the loader can be created before/after any
  ViewModel without ordering assumptions.
- **No `SDK_INT` guard.** The plan proposed trying only on API ≥ 28; instead `Waifu2xBridge` loads
  the library unconditionally and treats a load failure (missing ABI, no driver) as
  "upscaling unavailable". That keeps the feature reachable on API 26/27 devices whose drivers do
  work, with the same soft-failure behaviour everywhere. Hardware verification on 26/27 is still
  outstanding — if the SDK turns out not to install there, add the guard as in §4.2.
- **Display scale lives in the decoder.** The reader passes `DisplayScaleMode` plus the page's
  metadata dimensions (now `Long?` in the generated model, converted to `Int` at the call site); the
  decoder derives the ratio from Coil's constraint size. This keeps the split-half (`2×fit`),
  width-bound webtoon and `ContentScale.None` cases in one tested place instead of four composables.
- **Pre-existing SDK reuse.** `fetchNcnnSdk` also accepts a manually placed
  `third_party/ncnn/ncnn-20260113-android-vulkan` (verified by the presence of
  `arm64-v8a/lib/libncnn.a`) since networks that cannot reach GitHub (proxied CI runners,
  restricted dev machines) would otherwise fail before any code compiles. The sha256 pin applies
  to downloaded archives only.
- **P5 delivered**: `CacheScreen` gained a "Waifu2x cache (this device)" card — per-profile
  `EnhancementCache.stats()` (page count + formatted size) plus a clear action that drops the
  variant files but keeps the layout/index bookkeeping. It sits next to the chapter and cover
  cards so the whole on-device footprint is visible in one place.
- **Page badge delivered**: `UpscaleBadge` draws a "2x" chip in the page's own box (paged,
  spread half, webtoon strip) while the page's key is in `processingKeys`, and fades it out as the
  enhanced bitmap swaps in. Completed pages are not labelled — `readyKeys` is deliberately not
  rendered, so an already enhanced page looks like any other page. Pages the decision matrix
  skipped, and pages with the config off, never show the chip. The chip is pinned to the viewport
  corner for width/height-fit and split pages so zooming or scrolling cannot crop it out of sight;
  spread halves push it to their outer edge.
- **Not shipped from the plan**: exact per-chapter eviction (the P2-optional Room index). It stays
  additive and independent of the engine.
- **Webtoon scroll jump on swap (fixed)**: Coil transitions to a `Loading` state while it
  decodes the enhanced request, and a `Loading` slot has no intrinsic size — inside a webtoon
  strip that made the `LazyColumn` item collapse for a frame, so the list re-anchored and the
  view jumped. `ReaderImage(reserveHeightWhileLoading = true)` (webtoon call site) now remembers
  the height the page actually rendered at and applies `heightIn(min = …)` across the state
  change, keeping the item's geometry identical while the 2x bitmap decodes. Fixed-size paged and
  spread slots are unaffected.
- **Defaults confirmed**: mode `off`, all five denoise models bundled (5 × ~1.06 MB), threshold
  `1.05`, precision `auto`, preload `3`.

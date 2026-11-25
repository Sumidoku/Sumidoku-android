# Sumidoku for Android

The official Android client for **[Sumidoku](https://github.com/Sumidoku/Sumidoku)** — a self-hosted manga server that exposes a REST `/api/v1` API, runs its sources as WASM modules and streams events over SSE.

The app brings the Sumidoku web UI to Android natively, and adds what a browser cannot do: real offline reading, background downloads, background update checks and hardware image decoding. Computation stays on the server — sources, tracker pushes, the update scheduler and the server-side download queue all keep running there; the client is a native interface to every one of them.

Targets Android 8.0 (API 26) and up, built with Kotlin and Jetpack Compose (Material 3).

- Server: **[Sumidoku/Sumidoku](https://github.com/Sumidoku/Sumidoku)** — run it before pointing the app at it
- Documentation: **[sumidoku.github.io](https://sumidoku.github.io)** — installation, sources, API reference
- Docker image: **[sumidoku/sumidoku](https://hub.docker.com/r/sumidoku/sumidoku)**

## Features

- **Servers** — multiple profiles, API-key bearer authentication, mirror failover on connection-level failures, server version detection, reachability indicator when a server is offline.
- **Library** — cover grid with categories and filter groups, five sort modes, pull-to-refresh, sentinel paging, unread badges, batch selection, top-bar search, per-orientation grid density (compact / comfortable / list), cover-seeded theme colour.
- **Browse and sources** — grouped source list (updatable / pinned / installed), one-tap install from aggregate repository lists, source home built from the server's home components (carousels, scrollers, image scrollers, chapter lists, links, filters), schema-driven source settings editor, saved search filter groups, internal sources (local and OPDS catalogs), `.aix` import, three login flows (basic form, WebView cookie capture, OAuth redirect interception), pin / disable / reorder, local trash.
- **Manga detail and reader** — continue-reading, three-state chapter rows with batch operations, chapter filter/sort sheet; paged reader (HorizontalPager with zoom arbitration), webtoon continuous scrolling, text pages, chapter transition screens, pull-to-next-chapter, double pages including a no-cover variant, wide-page splitting, prefetch, debounced progress sync with an offline retry queue; five tap-zone layouts with a graphical preview, four page-fit modes, reader background options, brightness / grayscale / sepia filters.
- **Search** — single-source and cross-source search with dynamic filters, tri-state filter UI, NDJSON progressive rendering (falls back to the plain response when streaming fails).
- **Downloads** — server-side queue management driven by SSE; on-device offline engine (CBZ fetch and unpack, offline reading straight from local files, storage checks, WorkManager resume).
- **Updates and history** — SSE live channel with `Last-Event-ID` and exponential backoff, WorkManager update checks every six hours with system notifications, history list.
- **Metadata and trackers** — metadata editor with field locks, match-metadata preview with binding, cover picker, source-metadata restore; tracker bind / sync / unbind and account binding via WebView OAuth, sync-pull preview before applying.
- **Migration and backups** — batch migration wizard (source → sources → targets → auto-match → confirm → run) and single-manga migration; full JSON backup export and import, restore from server-side scheduled backups, Aidoku `.aib` and Mihon `.tachibk` import wizards with dry-run preview and per-source mapping.
- **Settings and polish** — server settings editor, categories, filter groups, cache management, API-key management and password change, incognito mode, theme (follow system / dark / light, AMOLED black), adaptive layout (bottom bar on phones, navigation rail on wide screens), library two-pane layout on large screens.

## Architecture

25 Gradle modules with a strict dependency direction (`app` → `feature` → `core`):

| Layer | Modules | Responsibility |
| --- | --- | --- |
| `app` | 1 | Application shell, navigation host, theme, update worker |
| `feature:*` | 15 | One Compose screen plus its ViewModel per feature: `servers`, `library`, `browse`, `search`, `manga`, `reader`, `source`, `repos`, `updates`, `history`, `downloads`, `migrate`, `trackers`, `settings`, `backups` |
| `core:*` | 9 | `api` (generated Retrofit client), `network` (client factory, auth, failover), `data` (Room, DataStore, repositories), `source`, `stream` (SSE), `download`, `model`, `common`, `ui` (design system) |

Hilt provides dependency injection; Room and DataStore hold local state; Coil decodes covers; Retrofit talks to the server.

### API contract

`api/openapi.json` is a pinned snapshot of the server's OpenAPI document (`api/openapi.json.sha256` pins its hash). The `core:api` module generates a kotlinx-serialization Retrofit client from it at build time and applies a small, deterministic set of `patchGeneratedSources` fixes for known generator quirks (sealed `oneOf` bodies, `kotlin.Any` properties that need `JsonElement`, union fields the wire sends as omitted, and free-form scalar wrappers).

The spec is the contract: regenerate deliberately when the server moves, never edit generated output by hand.

## Requirements

- JDK 17
- Android SDK with platform 37 (`compileSdk = 37`, `minSdk = 26`, `targetSdk = 36`)
- Android Studio with AGP 9.5 support, or the Gradle wrapper on its own

Point the build at your SDK either with `ANDROID_HOME` / `ANDROID_SDK_ROOT` or with a `local.properties` file:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Build

```bash
./gradlew assembleDebug          # PowerShell: .\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Release builds are unsigned — no signing configuration ships with the repository:

```bash
./gradlew assembleRelease
```

Unit tests:

```bash
./gradlew test
```

## Localization

English is the default, with Simplified Chinese (`values-zh-rCN`) and Traditional Chinese (`values-zh-rTW`) translations. The app follows the system language.

## Known limitations

- `usesCleartextTraffic` is enabled because self-hosted servers on a LAN are the primary target; a per-host `networkSecurityConfig` is the intended pre-release fix.
- Button-type source settings fall back to a plain row instead of a rendered button.
- No "clear source cache" action yet.
- Incoming deep links (opening a shared manga or chapter) are not parsed yet.
- Settings do not yet use a dual-pane layout or handle foldable hinges.

## License

MIT — see [LICENSE](LICENSE).

---

## 中文说明

Sumidoku 官方 Android 客户端，把服务端 web UI 的全部能力搬到原生界面，并补上浏览器做不到的部分：真正的离线阅读、后台下载、后台更新检查与硬件解码。计算仍然留在服务端（WASM 图源、tracker 推送、更新调度、服务端下载队列），客户端是它们的原生入口。

工程分为 `app`、15 个 `feature` 模块与 9 个 `core` 模块；接口客户端由 `api/openapi.json` 快照在构建期生成。

相关仓库与文档：[服务端](https://github.com/Sumidoku/Sumidoku) · [文档站](https://sumidoku.github.io) · [Docker 镜像](https://hub.docker.com/r/sumidoku/sumidoku)。

构建（需要 JDK 17 与 Android SDK platform 37）：

```powershell
.\gradlew.bat assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。签名需自行配置，仓库不含签名信息。

许可证：MIT。

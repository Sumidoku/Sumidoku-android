# Android Client Architecture

Status: design proposal (no client code exists yet)
Scope: a native Android client for the Sumidoku server with **feature
parity with the web UI**, modeled architecturally on Aidoku's
built-in-source pattern.

## 1. Context

The server exposes a complete REST surface: an OpenAPI 3.0.3 document
(`crates/server/assets/api-docs/openapi.json`, 108 paths / 127
operations, kept in lockstep with the router and guarded by CI),
bearer-token authentication designed for non-browser clients, an SSE
event stream, and NDJSON partial-result streaming.

The product rule for this client: **everything the web UI can do, the
Android client does natively** — library, browsing, reading, downloads,
updates, history, trackers, metadata matching, migration, source
administration, backups and system settings included. "Thin" here
describes *where computation lives*, not feature scope: the server
still runs the WASM sources, tracker pushes, update scheduler and
server-side download engine; the client adds a native interface to all
of it, plus what a browser cannot do at all (true offline reading,
background downloads and update checks, hardware image decoding, OS
integration).

Aidoku (the iOS/macOS reader whose WASM source ecosystem this server
runs) has already validated the architecture this client needs. Its
`Core/Sources/BuiltIn/` directory wraps self-hosted servers (Komga,
Kavita, Suwayomi) as *built-in sources* implementing the same `Runner`
protocol as its sandboxed WASM sources; every layer above (browse,
library, reader, downloads, tracking) is coded against that
abstraction. `KomgaSourceRunner`
(`Aidoku/Core/Sources/BuiltIn/Komga/KomgaSource.swift:83`) is the
closest existing template for the client core.

An Android client also beats the browser on several flows the web UI
can only do manually: source web-login cookie capture and tracker
OAuth can run in an in-app WebView that reads cookies and intercepts
redirects directly (§8), instead of the paste-cookies/paste-callback
workarounds the SPA needs.

## 2. Goals and non-goals

Goals — full parity with the web UI's feature set:

- Library: grid, ad-hoc filters, filter groups, categories, sorts,
  unread badges
- Browse: installed sources (pin/reorder/enable), source home and
  listings, single-source and cross-source search with dynamic filters
  and saved filter groups
- Source administration: Aidoku-style install flow — an "Add source"
  sheet aggregating installable sources across all repositories with
  one-tap install (repository URLs themselves are managed under
  Settings → Source repositories), plus updates, source settings
  (schema-driven editor), source login
  (basic/web/oauth), internal sources (local instances and OPDS
  catalogs), local trash management
- Manga detail: chapters with batch operations, category assignment,
  tracker binding/search/sync-preview, metadata edit and match dialogs
  with field locks, cover picker, migration entry
- Reader: paged/webtoon/text modes with the web reader's full behavior
  contract (§12)
- Downloads: server-side queue management plus on-device offline
  downloads
- Updates and history, with live state via SSE
- Migration wizard (batch and single-manga)
- Trackers: account connect (OAuth), bindings, remote state
- Settings: server settings editor, filter groups, categories,
  backups (document export/import, Aidoku `.aib` and Mihon `.tachibk`
  import wizards, scheduled backups), system (caches, API keys,
  password), task queue viewer
- Android-native additions on top: offline reading, background update
  notifications, share/deep-link handling, WebView-assisted logins

Non-goals:

- Running Aidoku WASM sources on-device (no interpreter, no Cloudflare
  solving, no source store — the server owns all of that)
- Multi-user / family sharing (the server is single-admin by design)
- OPDS output interop (the server consumes OPDS; it does not serve it)
- An iOS port

## 3. Design lineage

| Aidoku pattern | Reference | Android mapping |
| --- | --- | --- |
| Built-in source implementing the `Runner` protocol, indistinguishable from WASM sources above | `Core/Sources/BuiltIn/Komga/KomgaSource.swift:83-601` | One `Source` interface; `SumidokuSource` is the only built-in implementation; UI codes against the interface |
| REST pagination mapped to `MangaPageResult(hasNextPage)` | `KomgaSource.swift:113` | `/api/v1/library` and `POST /api/v1/search` already return `hasNextPage`; Paging 3 `PagingSource` wraps it |
| Image requests carry auth headers via `getImageRequest` | `KomgaSource.swift:422` | Coil `Fetcher` on an OkHttp client with an auth interceptor; every media route accepts bearer |
| Declarative settings tree rendered into UI | `KomgaSource.swift:430` | Source/server settings schemas rendered by one descriptor-driven settings screen (§11) |
| Multi-instance sources: unique key, per-instance credential namespace | `KomgaHelper.swift:14-22`, `SourceManager.swift:597-666` | `ServerProfile` entities; per-profile namespaced caches, downloads, preferences |
| Mirror failover: last-working mirror first, 5 s timeout, ordered retry | `KomgaHelper.swift:44-152` | `ServerProfile.mirrors` with the same last-working-first policy |
| Download engine: per-source serial queue, 5 concurrent pages per chapter, 3 retries with exponential backoff, tmp-dir-as-queue, `.metadata.json` sidecar, CBZ packing | `Core/Downloads/DownloadQueue.swift:13-15`, `DownloadTask.swift:39-43` | `:core:download` reproduces these semantics against the page/CBZ endpoints (§9) |
| History + reading-session entities; progress with scroll position | CoreData model, `HistoryManager` | Progress writes go to the server (§8); sessions are a local-only statistic |
| Backup as one decodable document, staged restore | `Core/Backup/Models/Backup.swift` | Client-side settings export; server backups are driven through their REST wizards |

Engine behavior that stays on the server (the client renders and
drives it, never reimplements it): tracker push/pull merging and
chapter offsets, library update scheduling and skip rules, source key
migration, backup document construction and restore, local-library
scanning/trash diffing, cover/page caches.

## 4. Architecture overview

Layers, in dependency order (each layer depends only on layers below
it — the same direction as Aidoku's Features → Core → AidokuRunner):

```
:app                       navigation shell, DI graph, theming
:feature:servers           server profile management, login
:feature:library           library grid, categories, filter groups
:feature:browse            source home/listings/search, source admin
                           (repos, settings, login, internal sources)
:feature:manga             manga detail, chapters, trackers, metadata
:feature:reader            paged/webtoon/text readers, progress
:feature:history           reading history
:feature:updates           new-chapter feed
:feature:downloads         server queue + on-device downloads
:feature:migrate           cross-source migration wizard
:feature:trackers          tracker accounts and bindings
:feature:settings          server settings, backups, system, tasks
--------- feature layer (UI + ViewModel, Compose) ---------
:core:source               Source interface, SumidokuSource, failover
:core:data                 repositories, Room, DataStore, profiles
:core:download             DownloadManager/Queue/Task, offline store
:core:stream               SSE client, NDJSON decoder
:core:network              OkHttp/Retrofit setup, auth, errors
:core:api                  generated client from openapi.json
:core:model                hand-written domain types over the DTOs
:core:common               dispatchers, Result types, logging
```

Rationale for the split:

- `:core:api` is machine-generated and regenerated often; nothing else
  may depend on its DTOs directly. `:core:model` maps DTOs to stable
  domain types so a spec regeneration never touches feature code.
- `:core:source` is the architectural seam borrowed from Aidoku. With
  exactly one implementation it may look redundant; its value is
  testability (fake sources in feature tests), a single place for
  pagination/auth/failover semantics, and room for a second
  implementation later without touching UI.
- `:core:stream` is separate from `:core:network` because SSE and
  NDJSON are hand-written protocols outside the OpenAPI document
  (the web UI does the same split: generated `types.ts` vs
  hand-maintained `protocol.ts`).

## 5. Tech stack

| Area | Choice | Rationale |
| --- | --- | --- |
| Language | Kotlin 2.x, coroutines + Flow | standard |
| UI | Jetpack Compose (BOM), Material 3, Navigation Compose with type-safe routes | matches the feature-module split; adaptive layouts for tablets come free with `WindowSizeClass` |
| DI | Hilt | compile-time safety, mature Compose integration |
| HTTP | OkHttp + Retrofit, kotlinx.serialization | first-party kotlinx-serialization converter; OkHttp interceptors cover auth, failover, logging |
| API codegen | openapi-generator Gradle plugin, `kotlin` generator, Retrofit2 library (see §6) | the spec is the contract; the same spec already generates the web UI's types |
| SSE | okhttp-sse | the server stream is plain SSE with `Last-Event-ID`; no extra protocol work needed |
| Images | Coil 3 on the shared OkHttp instance | disk cache, `ImageRequest` headers, hardware bitmaps; prefetch API for reader lookahead |
| Local DB | Room | download registry, server profiles, offline chapter metadata, reading sessions |
| Preferences | DataStore (Preferences + Proto) | reader prefs, UI state; versioned schema via Proto |
| Credentials | API key in Keystore-backed encrypted storage | the key is shown once by the server; treat it like a password |
| Background | WorkManager | update polling, download continuation across process death; foreground service with notification while a download batch is user-visible |
| Pagination | Paging 3 | `/library` and `/history` are offset+`hasNextPage`; `PagingSource` maps directly |
| WebView flows | in-app WebView with CookieManager | source web-login cookie capture and tracker OAuth (§8) — strictly better UX than the web UI's paste flows |
| Min/target SDK | minSdk 26, target latest | ~95 % device coverage, still allows modern media codecs |

## 6. API contract and codegen

The contract is the server's OpenAPI document:

- `crates/server/assets/api-docs/openapi.json` — OpenAPI 3.0.3,
  108 paths / 127 operations, version field tracks the server release
  (`"version": "0.23.0"` at the time of writing). It is hand-maintained
  and CI-checked against drift; the web UI generates its `types.ts`
  from the same file.
- Out of scope for codegen: the SSE stream (`GET /api/v1/events`) and
  the NDJSON partial-result envelope (`Accept: application/x-ndjson`).
  Both are implemented by hand in `:core:stream`.

Codegen pipeline:

1. Pin a snapshot of `openapi.json` in the client repo at
   `api/openapi.json` with a recorded SHA-256.
2. The Gradle task `generateSumidokuApi` runs openapi-generator
   (`generatorName=kotlin`, `library=jvm-retrofit2`,
   `serializationLibrary=kotlinx_serialization` — verify exact option
   names against the generator version at spike time) into
   `:core:api/build/generated`.
3. CI fails if the pinned snapshot changes without regeneration —
   mirroring the server-side `git diff --exit-code` guard on
   `webui/src/api/types.ts`.
4. Updating the pinned snapshot is a deliberate PR step that also runs
   the API-compatibility checklist (§13).

Version contract with the server:

- `GET /api/v1/version` is unauthenticated and returns
  `{"version": "x.y.z"}` (`crates/server/src/api/mod.rs:258-260`).
  The client probes it before adding a server profile and on app
  start, warns below a declared minimum server version, and refuses
  nothing above it (forward compatibility by default).
- The client declares `minServerVersion` per release; bumping it is a
  changelog entry.

## 7. Source layer

`Source` interface (names kept close to Aidoku's `Runner` family so
the design transfers in both directions):

```text
getListings(): List<Listing>
getHome(): Home                                  // components model
getSearchMangaList(query, page, filters): MangaPageResult
getMangaUpdate(manga, needsDetails, needsChapters): Manga
getPageList(chapter): List<Page>                 // url + width/height + cacheRevision
getImageRequest(page): ImageRequest              // url + auth headers
getSettings(): List<Setting>                     // settings schema
handleLogin(credentials): LoginResult
```

Endpoint coverage (all under `/api/v1`) — the mapping is exhaustive
because the client targets web-UI feature parity:

| Client surface | Sumidoku endpoints |
| --- | --- |
| Reading core | `GET /sources/{id}/listings|home|filters`, `POST /sources/{id}/search`, `POST /search`, `GET/PATCH /manga/{id}`, `GET /manga/{id}/chapters`, `GET /chapter/{id}/pages`, `GET /chapter/{id}/page/{index}`, `PATCH /chapter/{id}`, `POST /chapter/batch` |
| Library | `GET /library` (+`groupId`, `source-counts`, `authors`, `tags`), categories CRUD, filter-groups CRUD + reorder |
| History / updates | `GET /history`, `POST /update/fetch`, `GET /update/summary|recent` |
| Downloads | `/downloads/*` (enqueue/list/pause/delete/clear), `GET /chapter/{id}/file` (CBZ) |
| Source admin | `GET/PATCH/DELETE /sources/{id}`, `POST /sources/reorder-pinned`, `/sources/{id}/settings` GET/PUT/reset, `/notify`, `/login/basic|web|oauth/logout`, `/cache/clear`, repos: `/source-repos*` (add/refresh/install/update-all), `POST /sources/import` (.aix sideload) |
| Internal sources | `POST /sources/internal` (local/opds), `GET/DELETE /local/trash`, `POST /local/trash/{id}/restore`, `POST /local/scan` |
| Manga metadata | `GET/POST /manga/{id}/metadata/preview|apply|restore`, `PATCH /manga/{id}` metadata fields + `metadataUnlock`, `GET /manga/{id}/covers/alternates`, `PUT /manga/{id}/cover` |
| Migration | `GET /migrate/sources`, `GET /migrate/lookup`, `POST /migrate` |
| Trackers | `/trackers/links*` CRUD, `/ledger`, `/search`, `/state`, `/update`, `/sync` (preview), `/preferences`, `/oauth/{provider}/start|status|logout` |
| Settings / system | `GET/PATCH /settings`, caches (`/cache/covers*`, `/cache/chapters*`), `/auth/api-keys*`, password change, `GET/DELETE /tasks*` |
| Backups | `GET/POST /backups/document`, `/backups/files*`, `POST /backups/import/aidoku`, `POST /backups/import/suwayomi` (dry-run + sourceMapping) |

Server profiles and failover:

- `ServerProfile(id, name, baseUrl, mirrors[], apiKeyRef)` stored in
  Room; the active profile is a DataStore key. Caches and the download
  store are namespaced by profile id so profiles never share bytes.
- Failover follows `KomgaHelper.request`: try the last working address
  first, then the primary, then mirrors in order; per-attempt connect
  timeout 5 s; the first address to answer `/api/v1/version` becomes
  the new last-working address.
- TLS: system trust store by default; per-profile opt-in for
  self-signed certificates with a visible warning (common for
  self-hosted servers).

## 8. Authentication, login flows, and reading progress

Authentication:

- The server is single-admin. Two credential kinds exist: the 30-day
  rolling session from `POST /auth/login` (browser-oriented, also
  plants a cookie), and API keys from the server UI (plaintext shown
  once, server stores a SHA-256 fingerprint).
- The client authenticates API requests with **API keys**, sent as
  `Authorization: Bearer <key>`. `require_auth` accepts bearer on
  every protected route including images and SSE, and the server
  rejects `?access_token=` in URLs — so no query-token workarounds
  exist or are needed.
- When server auth is disabled, all requests are implicitly admin; the
  client detects this via `GET /auth/status` and skips the key
  requirement for that profile.
- 401 handling: mark the profile's key invalid and prompt for a new
  key. Key creation can be done in-client: `POST /auth/login` with the
  admin credentials (rate-limited: 5 failures then exponential
  backoff, `429 + Retry-After` — surface the wait to the user) obtains
  a session token, which can then call `POST /auth/api-keys`; the
  resulting key replaces the stored one and the session token is
  discarded.

WebView-assisted flows (where Android outdoes the web UI):

- **Tracker OAuth connect.** The server flow: `oauth/{provider}/start`
  (authenticated) → provider authorization → project-hosted relay →
  instance `callback`, which sits inside `require_auth` and expects
  the session cookie. The client runs this in an in-app WebView: it
  obtains a session token via `POST /auth/login`, plants it as the
  `sumidoku.session` cookie for the instance origin through
  `CookieManager`, then navigates to the start URL and polls
  `oauth/{provider}/status` until connected. No server change needed;
  a future server-side one-time callback token would let this move to
  Chrome Custom Tabs.
- **Source web login.** Same WebView: navigate to the source's login
  page, let the user log in, then read the login host's cookies from
  `CookieManager` and submit them to `/sources/{id}/login/web` —
  replacing the web UI's manual cookie paste.
- **Source OAuth login.** The WebView intercepts the custom-scheme or
  cross-origin redirect and hands the callback URL to
  `/login/oauth/complete` — replacing the manual callback-URL paste.

Reading progress:

- The server merges progress: `PATCH /chapter/{id}` takes a
  `ProgressPatch` and returns the merged state; progress only moves
  forward, `is_read` is OR-ed. The client therefore writes freely and
  never computes merge outcomes itself.
- Writes are throttled (on page settle, at most once every few
  seconds, plus a final write on exit) and queued offline with
  at-least-once delivery on reconnect.
- Incognito (global and per-manga) is a server flag; the reader shows
  its state and toggles it via `PATCH /manga/{id}` / settings, exactly
  as the web UI does.
- Reading sessions (start/end/pages) are a local-only statistic in
  v1; per-chapter history (`last_read_at`) comes from the server and
  feeds the History screen via `GET /history`.

## 9. Downloads and offline

Two tiers, deliberately:

- **Tier A — server-side downloads.** The server downloads chapters to
  its own storage; any client reads them offline-readable. The Android
  client manages these through `/downloads/*` exactly like the web UI
  (enqueue selected/unread/newest-N/all, pause, cancel, delete, clear
  finished, reader-ahead settings).
- **Tier B — on-device downloads.** The client stores chapters locally
  so reading works with the server unreachable. Modeled directly on
  Aidoku's engine:
  - `DownloadManager` (singleton) + `DownloadQueue` + one
    `DownloadTask` per chapter. Queue persisted in Room *and*
    reconstructed from the tmp directory on launch (Aidoku's
    tmp-dir-as-queue rule: a leftover tmp directory means the chapter
    was queued).
  - Inside a task: 5 concurrent page fetches, 3 retries with
    exponential backoff capped at 60 s, per-page tmp files renamed
    into place on success (atomic page commit).
  - Status machine: `queued / downloading / paused / cancelled /
    finished / failed`, mirrored into Room and surfaced over Flow.
  - Storage layout:
    `filesDir/downloads/<profileId>/<mangaId>/<chapterId>/`
    with `metadata.json` (chapter + manga snapshot, schema version)
    and `001.<ext>…` pages; optional `.cbz` repack for export/sharing.
  - **CBZ fast path:** for chapters already downloaded server-side,
    `GET /chapter/{id}/file` returns the finished CBZ in one request
    (200 / 404 / 415); the client downloads and unzips it instead of
    fetching pages one by one. Otherwise it falls back to the page
    endpoint.
  - Execution: foreground service with a notification while the user
    is watching; WorkManager continuation for background completion.
  - Reader resolution order for a page: on-device store → server
    stream. Deleting a local download never touches server-side
    downloads and vice versa.
- Storage management screen: usage per profile and per manga, LRU
  hint list (downloaded chapters of fully-read series), bulk delete.

## 10. Realtime: SSE and NDJSON

SSE (`GET /api/v1/events`):

- The stream opens with a full `snapshot` (download queue + update
  summary), then `download`, `running`, `update`, `updateSummary`,
  `updateComplete` and `task` events. A 1024-event in-memory ring plus
  `Last-Event-ID` allows replay after reconnect; the server sends a
  15 s heartbeat and drops lagging clients.
- `:core:stream` owns one lifecycle-aware connection: active while the
  app is in the foreground, torn down when backgrounded. The last
  event id is persisted; on reconnect the client resumes, and on a
  server-side lag drop it refetches affected state instead of trusting
  the gap. Events map to `StateFlow`s consumed by the Downloads,
  Updates and task-queue screens and by notification badges.
- Background (app closed): WorkManager polls `GET /update/recent` on a
  user-configured interval (default off; suggested 6–24 h with
  network/unmetered constraints) and posts a "N new chapters"
  notification. No FCM push exists server-side; this is the ceiling
  until one does.

NDJSON partial streaming (phase 2, progressive enhancement):

- Endpoints for home/search/manga refresh accept
  `Accept: application/x-ndjson` and stream per-source components;
  the terminal `{"type":"final","payload":…}` equals the non-streamed
  body. The client ships non-streaming first and opts in per screen
  where progressive rendering matters (cross-source search, home).

Cross-source search filters (Aidoku `FilterHeaderView` / webui
`SearchPanel` parity): content rating, language and source are tri-state
(include/exclude) filters that only decide which sources participate —
filtering is client-side, the server endpoint is unfiltered. Selections
persist across sessions (webui: `sumidoku.globalSearch.filters` in
localStorage) and are shared with the metadata-match dialog's source
search tab. The `multi` language sentinel sorts first, same as Aidoku.

## 11. Settings

Three kinds, clearly separated:

- **Server settings** (`GET/PATCH /api/v1/settings`): rendered from
  the server's settings payload; edits are explicit saves. Covers
  downloads, updates, cache budgets, net policy, backup scheduling,
  etc. The client is a remote control, not an owner.
- **Source settings** (`GET/PUT /api/v1/sources/{id}/settings`):
  schema-driven editor generated from the merged static+dynamic
  setting tree — this is where Aidoku's declarative-Settings model
  (`KomgaSource.swift:430`) applies verbatim, including groups, pages,
  links, buttons (`/notify`), and `refreshes` invalidations. The same
  descriptor-driven renderer serves the server settings above, so one
  component family covers both.
- **Local preferences** (Proto DataStore): theme, reader behavior,
  download policy, notification cadence. Reader defaults mirror the
  web UI's contract so users moving between clients see the same
  behavior (§12). Keys keep the web UI's semantics so a future sync of
  reader preferences is a mapping, not a redesign.

## 12. Reader

The reader is the highest-risk module and gets the most direct
lineage from existing work:

- Modes: paged (RTL/LTR/vertical), webtoon (continuous vertical), and
  text — matching the server's `viewer` flags, per-manga overrides,
  and EPUB text chapters.
- Implementation: Compose `HorizontalPager`/`VerticalPager` for paged
  modes; a lazy continuous column with measured placeholders for
  webtoon; text pages rendered as columns with the typography controls
  (font family/size/line-height) the web UI persists. Page
  placeholders use the width/height from `GET /chapter/{id}/pages`
  (the same intrinsic-aspect reservation the web UI uses) so layout is
  stable before decode.
- Images: the server sends original bytes
  (`GET /chapter/{id}/page/{index}`,
  `Cache-Control: private, max-age=3600`); scaling is a client duty by
  design. Coil decodes with viewport-appropriate sampling. Double-page
  spread composition follows the web UI's `spreads.ts` (ported from
  KMReader): pairs in reading direction, cover and closing page paired
  with a blank on their outer side in `double`, straight pairing in
  `doubleNoCover`, blank-page fallback at a 2:3 ratio.
- Wide-page detection: one shared rule — strictly `width > height`
  from the page's `width`/`height` in `GET /chapter/{id}/pages`
  (`spreads.ts:isLandscapePage`, `splitWide.ts:isSplittable`). Pages
  without dimensions (unmeasured remote pages) are treated as
  portrait until measurement lands; when it does, the layout
  re-composes in place without moving the current position.
- Wide-page handling is two mutually exclusive layouts, not one
  feature: in a double-page layout a landscape page gets a solo
  spread (and a landscape second page pushes its mate to a blank);
  the wide-page *split* (`splitWide.ts`, after Mihon's "split double
  pages") exists only in paged single layout. A splittable page
  becomes two half-pages in reading order (`ltr` reads the left half
  first, `rtl` the right). The split is a navigation concept only:
  position is `{page, half}`, progress/slider/restore stay in real
  page indexes so a chapter never reports fractional counts; backward
  entry lands on the second half; mark-read, tracker push and
  download cleanup fire only when the *last* half of the last page is
  reached (`splitEndReached`).
- Swipe-to-turn: 1:1 drag follow on the spread track, release decides
  (`swipeTurn.ts`, port the constants verbatim). A release turns when
  the drag's *projected landing* — offset + release velocity × 150ms —
  crosses `min(25% viewport width, 96px)` in a direction, or on a
  flick: velocity ≥ 0.2px/ms with at least 16px of travel. Anything
  shorter springs back over the 300ms track animation. Release
  velocity is measured over the last ~100ms of the drag only (a
  finger slows before it lifts; a whole-drag average under-reads
  flicks). Offsets are screen-space mapped into reading direction by
  a forward sign, so RTL needs no separate code path. Compose:
  `detectHorizontalDragGestures` + `VelocityTracker` with the same
  window math; spring-back via `Animatable`.
- Prefetch: Coil `enqueue` lookahead ±3 pages in reading direction,
  generation-tagged so stale prefetches are dropped on chapter change
  (Aidoku's `ReaderPagePrefetcher` policy).
- Chapter transition: in-flow transition page with gesture
  confirmation (same-direction flip confirms, reverse cancels),
  matching the web UI's `--transition-slot` behavior; pull-to-next-
  chapter with the rubber-band curve; tap zones (`lr/rl/edge/kindle/
  off`); page scale modes; image filters as the reader matures.
- Progress: §8. Incognito and per-manga reader overrides respected.

## 13. Gaps and risk register

| # | Gap / risk | Impact | Mitigation |
| --- | --- | --- | --- |
| 1 | `/library`, `/history` are offset+`hasNextPage`, no cursor | offset drift under concurrent writes | acceptable for a single-user server; re-fetch first page on invalidation |
| 2 | No server push (no FCM) | background update checks are polling | WorkManager intervals; document battery cost |
| 3 | Page images are original bytes; no thumbnail tiers | decode cost and traffic on slow networks | Coil sampling; optional "data saver" prefetch disable; a server-side width variant is a future server feature, not a client hack |
| 4 | Single admin user | no family sharing | product decision; out of scope |
| 5 | Spec version moves with the server; no formal deprecation policy | breaking change could ship silently | pinned spec snapshot + regeneration guard; `/api/v1/version` probe; compatibility checklist per client release |
| 6 | SSE/NDJSON outside the OpenAPI doc | codegen misses them | hand-written `:core:stream` with contract tests against a recorded stream |
| 7 | Tracker OAuth callback expects the session cookie (browser navigation) | Custom Tabs cannot carry it | in-app WebView with planted cookie (§8); a server-side one-time callback token is the clean future fix |
| 8 | No one-shot chapter-pack endpoint for non-server-downloaded chapters | page-by-page fetches for Tier B | CBZ fast path where available; page pipeline otherwise (same as Aidoku) |
| 9 | Large feature-parity surface (127 operations, dynamic settings schemas, three backup import formats) | long tail of screens | descriptor-driven settings renderer; milestone order puts admin surfaces after the reading core; wizards follow the web UI's proven step sequences |
| 10 | WebView flows require an admin password login (`POST /auth/login`) to plant the session cookie | profile needs both key and (optionally) password | store the password only when the user enables tracker connect; key-only profiles keep full reading features |

## 14. Milestones

Each slice is independently shippable; M2 is the MVP cut for daily
reading, M3+ reach full web-UI parity.

- **M0 — skeleton and contract.** Gradle module graph, codegen
  pipeline against the pinned spec, server profile storage, version
  probe, API key auth, `/library` rendered as a debug list.
- **M1 — library and browse.** Library grid (Paging 3, filters,
  categories, filter groups, sorts), source home/listings,
  single-source search with dynamic filters and saved filter groups,
  cross-source search, source list with pin/reorder/enable.
- **M2 — manga detail and reader (MVP).** Detail with chapters,
  sorting, batch read/unread, library toggle, categories; reader with
  paged RTL/LTR + webtoon + text, prefetch, progress write-back with
  offline queue, chapter transition, incognito indicator.
- **M3 — downloads and offline.** Tier A queue management; Tier B
  engine (queue, CBZ fast path, page pipeline), offline reader
  resolution, storage management, WorkManager continuation.
- **M4 — updates, history, realtime.** Updates screen, history,
  foreground SSE wiring, WorkManager update checks with notification,
  NDJSON opt-in for search/home.
- **M5 — source administration.** Add-source sheet (aggregated
  installable list, one-tap install/update), repository management
  under Settings (add/refresh/delete, `.aix` sideload), schema-driven
  source settings, source login (basic + WebView-assisted web/oauth),
  internal sources (local instances, OPDS catalogs), local trash
  management, task queue viewer.
- **M6 — metadata and trackers.** Metadata edit dialog and match
  wizard (preview diff, field locks, restore), cover picker with
  alternates, tracker accounts (WebView OAuth connect), bindings,
  remote state, sync-pull preview.
- **M7 — migration and management polish.** Batch and single-manga
  migration wizards, category and filter-group management screens,
  server settings editor, cache management, API key management,
  password change.
- **M8 — backups.** Document export/import, Aidoku `.aib` and Mihon
  `.tachibk` import wizards (upload → dry-run preview → source
  mapping → confirm), scheduled-backup settings, restore from server
  files.
- **M9 — polish.** Tablet/foldable adaptive layouts, double-page and
  wide-split parity with the web reader, tap-zone/scale/filter
  options, theming, release pipeline (GitHub APK; evaluate F-Droid).

## 15. Repository and release strategy

- Separate repository (suggested `Sumidoku/android`), independent
  versioning. The server monorepo owns the spec; the client pins a
  snapshot (§6). Nothing in the server repo needs to change for M0–M2.
- CI: build, unit tests, detekt/ktlint, and the spec-regeneration
  guard. Release builds signed from CI secrets; GitHub Releases for
  APKs, F-Droid evaluation after M3.
- This document lives in the server repo because it designs against
  the server's API contract; the client repo links to it.

## 16. Optional Aidoku-parity enhancements

Features Aidoku has that neither the server nor the webui provides.
None is required for the webui-parity red line; each needs an explicit
product decision and, for the server-backed ones, a server change
first. Listed here so the milestone plan can pick them up deliberately
instead of by accident.

- **Pinned manga in the library.** Aidoku keeps a pinned set at the top
  of the library (`LibraryViewModel` pinnedManga). The server has no
  pinned field; options are a device-local pin list (lost on reinstall,
  not synced) or abusing a reserved category (visible in webui). Decide
  before M4 polish.
- **Crop borders.** `CropBordersProcessor` trims scan margins at render
  time. Pure client-side image pipeline work (Coil transformation or a
  custom decoder step); no server involvement. Reader milestone M9
  candidate.
- **Upscaling.** `UpscaleProcessor` (waifu2x-family) enhances pages in
  the reader. The server only has a waifu2x spike under `spike/`; doing
  this on-device means bundling an NCNN/TFLite model with real battery
  and size costs. Defer; revisit after M9.
- **OCR dictionary.** Aidoku's `Features/Dictionary` runs OCR on a page
  region for lookup/translation. Large standalone feature; Android has
  ML Kit text recognition as a cheap first step. Out of scope until the
  reader is stable.
- **GIF pages.** Aidoku renders animated pages (`GIFImageNode`).
  Trivial on Android — Coil already decodes GIF; just do not strip
  animation in the reader image pipeline.

## Appendix A — Aidoku references

All paths under the Aidoku checkout root, `Aidoku/` subdirectory:

- `Core/Sources/BuiltIn/Komga/KomgaSource.swift` — built-in-source
  template: `:83` runner actor, `:89-99` feature flags, `:113` search
  mapping, `:422` image auth, `:430` settings tree, `:528` login,
  `:551` notification handling
- `Core/Sources/BuiltIn/Komga/KomgaHelper.swift` — `:14-22` credential
  namespace, `:44-152` mirror failover request pipeline
- `Core/Sources/SourceManager.swift:597-666` — multi-instance creation
- `Core/Downloads/DownloadQueue.swift:13-15`,
  `Core/Downloads/DownloadTask.swift:14-22,39-43` — queue semantics
- `Features/Reader/` — `ReaderViewController`,
  `Readers/ReaderReaderDelegate.swift`, Paged/Webtoon/Text readers,
  `ReaderPagePrefetcher`
- `Core/Tracking/TrackerManager.swift:340-508,672-702` — pull merge,
  pending queue
- `Core/Backup/Models/Backup.swift` — single-document backup

## Appendix B — Sumidoku contract references

- `crates/server/assets/api-docs/openapi.json` — the REST contract
- `docs/architecture.md` — router, SSE/NDJSON protocols, sync, cache,
  download engine, web reader semantics
- `docs/authentication.md` — auth model, API keys, rate limiting
- `crates/server/src/api/mod.rs:254-260` — health and version handlers
- `webui/src/api/protocol.ts` — hand-written SSE/NDJSON client protocol
  (the model for `:core:stream`)
- `webui/src/reader/spreads.ts` — double-page spread rules to mirror

package org.sumidoku.android.feature.manga

import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.ChapterPrefs
import org.sumidoku.android.core.data.ChapterPrefsStore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.DownloadsApi
import org.sumidoku.android.core.api.MetadataApi
import org.sumidoku.android.core.api.MigrationApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.TrackersApi
import org.sumidoku.android.core.api.model.AddToLibraryRequest
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.ContentRating
import org.sumidoku.android.core.api.model.DeleteDownloadsRequest
import org.sumidoku.android.core.api.model.EnqueueDownloadsRequest
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.FilterValueOneOf4MultiSelect
import org.sumidoku.android.core.api.model.FilterValueOneOfText
import org.sumidoku.android.core.api.model.Manga
import org.sumidoku.android.core.api.model.MangaStatus
import org.sumidoku.android.core.api.model.MigrateEntry
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.MigrateRequest
import org.sumidoku.android.core.api.model.MigrationSource
import org.sumidoku.android.core.api.model.MetadataApplyRequest
import org.sumidoku.android.core.api.model.MetadataField
import org.sumidoku.android.core.api.model.MetadataPreview
import org.sumidoku.android.core.api.model.MetadataRestoreRequest
import org.sumidoku.android.core.api.MangaSeedStore
import org.sumidoku.android.core.api.model.RegisterMangaRequest
import org.sumidoku.android.core.api.model.RemoteState
import org.sumidoku.android.core.api.model.TrackStatus
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.SourceChapter
import org.sumidoku.android.core.api.model.SelectCoverRequest
import org.sumidoku.android.core.api.model.SourceConfig
import org.sumidoku.android.core.api.model.SourceFilter
import org.sumidoku.android.core.api.model.TrackSearchItem
import org.sumidoku.android.core.api.model.TrackerLink
import org.sumidoku.android.core.api.model.TrackerUpdateRequest
import org.sumidoku.android.core.api.model.TrackerLinkUpsert
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.api.model.TrackerSyncRequest
import org.sumidoku.android.core.api.model.UpdateMangaRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.download.LocalDownloadEngine
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await

/** Which author/artist/tag taps on the detail header are searchable, and
 *  with which filter value — webui MangaDetailPage parity
 *  (creatorSearchable / genreFilterValue). */
data class SourceSearchMeta(
    val filters: List<SourceFilter> = emptyList(),
    val config: SourceConfig? = null,
) {
    /** Aidoku: the manifest declaration wins; only when it is undeclared
     *  does a text filter with the same id make the field searchable. */
    fun creatorFilterValue(name: String, isAuthor: Boolean): FilterValue? {
        if (name.isBlank()) return null
        val id = if (isAuthor) AUTHOR_FILTER_ID else ARTIST_FILTER_ID
        val declared = if (isAuthor) config?.supportsAuthorSearch else config?.supportsArtistSearch
        val searchable = declared ?: filters.any { it.type == "text" && it.id == id }
        if (!searchable) return null
        return FilterValue(text = FilterValueOneOfText(id = id, value = name))
    }

    /** supportsTagSearch sends the raw tag as a "genre" select; otherwise the
     *  first genre option equal to the tag is used (ids[i] ?? options[i]). */
    fun tagFilterValue(tag: String): FilterValue? {
        if (tag.isBlank()) return null
        if (config?.supportsTagSearch == true) {
            return FilterValue(select = FilterValueOneOfText(id = GENRE_FILTER_ID, value = tag))
        }
        filters.forEach { filter ->
            if (filter.type != "select" && filter.type != "multi-select") return@forEach
            if (filter.isGenre != true) return@forEach
            val options = filter.options ?: return@forEach
            val index = options.indexOf(tag)
            if (index < 0) return@forEach
            val value = filter.ids?.getOrNull(index) ?: options[index]
            if (value.isEmpty()) return@forEach
            return if (filter.type == "multi-select") {
                FilterValue(
                    multiSelect = FilterValueOneOf4MultiSelect(
                        id = filter.id,
                        included = listOf(value),
                        excluded = emptyList(),
                    ),
                )
            } else {
                FilterValue(select = FilterValueOneOfText(id = filter.id, value = value))
            }
        }
        return null
    }

    /** Authors then artists, deduped in order; a name in both stays author. */
    fun creators(authors: List<String>?, artists: List<String>?): List<Pair<String, Boolean>> {
        val out = LinkedHashMap<String, Boolean>()
        authors.orEmpty().forEach { if (it.isNotBlank()) out[it] = true }
        artists.orEmpty().forEach { if (it.isNotBlank() && !out.containsKey(it)) out[it] = false }
        return out.map { it.key to it.value }
    }

    private companion object {
        const val AUTHOR_FILTER_ID = "author"
        const val ARTIST_FILTER_ID = "artist"
        const val GENRE_FILTER_ID = "genre"
    }
}

data class MangaUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val manga: Manga? = null,
    val chapters: List<ChapterWithDownload> = emptyList(),
    /** Per-manga chapter list prefs (webui chapterPrefs parity); [chapters]
     *  stays the raw API order, [visibleChapters] is the filtered/sorted
     *  view the list actually renders. */
    val chapterPrefs: ChapterPrefs = ChapterPrefs(),
    val visibleChapters: List<ChapterWithDownload> = emptyList(),
    /** Tier B local offline status per chapter: queued/downloading/done/error. */
    val localStatus: Map<Long, String> = emptyMap(),
    val error: String? = null,
    /** Explicit "refresh from source" actions (webui refreshDetails/refreshChapters parity). */
    val refreshingDetails: Boolean = false,
    val refreshingChapters: Boolean = false,
    /** Live progress line while a chapter refresh streams partials. */
    val refreshNote: String? = null,
    /** True while an unregistered source entry is registering in the
     *  background: the header already renders the browse entry (Aidoku
     *  parity) and the chapter section shows an inline loading row. */
    val resolving: Boolean = false,
) {
    /** "Continue reading" target (webui MangaDetailPage continueCh parity):
     *  the most recently opened unfinished chapter, else the first unread
     *  chapter in reading order (ascending in the active sort key), else
     *  the first chapter in reading order. Trashed chapters and locked
     *  chapters without a download are not readable and are skipped. */
    val resumeChapter: ChapterWithDownload?
        get() {
            fun readable(c: ChapterWithDownload) = !c.trashed && (!c.locked || c.isDownloaded)
            val resume = visibleChapters
                .filter { readable(it) && !it.isRead && (it.lastReadAt ?: 0L) > 0L }
                .maxByOrNull { it.lastReadAt ?: 0L }
            if (resume != null) return resume
            val order = if (chapterPrefs.asc) visibleChapters else visibleChapters.asReversed()
            return order.firstOrNull { readable(it) && !it.isRead } ?: order.firstOrNull()
        }
}

@HiltViewModel(assistedFactory = MangaViewModel.Factory::class)
class MangaViewModel @AssistedInject constructor(
    @ApplicationContext private val appContext: Context,
    @Assisted("mangaId") requestedMangaId: Long,
    @Assisted("sourceId") private val requestedSourceId: Long,
    @Assisted("mangaKey") private val requestedMangaKey: String?,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val localDownloads: LocalDownloadEngine,
    private val chapterPrefsStore: ChapterPrefsStore,
) : ViewModel() {

    /** Assisted factory so the screen works both inside a nav route and
     *  inside the expanded-width list-detail pane (design §17). sourceId +
     *  mangaKey describe an unregistered source entry (Aidoku open-first:
     *  navigate immediately, register in the background). */
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("mangaId") mangaId: Long,
            @Assisted("sourceId") sourceId: Long,
            @Assisted("mangaKey") mangaKey: String?,
        ): MangaViewModel
    }

    /** Resolved server manga id. -1 while an unregistered source entry is
     *  still registering in the background. */
    private var mangaId: Long = requestedMangaId

    private val _state = MutableStateFlow(MangaUiState())
    val state: StateFlow<MangaUiState> = _state

    private val _searchMeta = MutableStateFlow(SourceSearchMeta())

    /** Source filters + manifest config behind author/artist/tag taps. */
    val searchMeta: StateFlow<SourceSearchMeta> = _searchMeta
    private var searchMetaLoaded = false

    /** Filter value for tapping an author/artist name, or null when this
     *  source cannot search by that field. */
    fun creatorFilterValue(name: String, isAuthor: Boolean): FilterValue? =
        _searchMeta.value.creatorFilterValue(name, isAuthor)

    /** Filter value for tapping a tag chip, or null when not searchable. */
    fun tagFilterValue(tag: String): FilterValue? = _searchMeta.value.tagFilterValue(tag)

    init {
        if (mangaId > 0) {
            refresh()
            observeLocalDownloads()
        } else {
            resolveUnregistered()
        }
    }

    private fun observeLocalDownloads() {
        viewModelScope.launchGuarded {
            localDownloads.init()
            localDownloads.observeForManga(mangaId).collect { rows ->
                _state.value = _state.value.copy(
                    localStatus = rows.associate { it.chapterId to it.status },
                )
            }
        }
    }

    /** Aidoku-style open: the detail page is already on screen while the
     *  source entry registers in the background (webui's register-then-
     *  navigate gate is exactly what this avoids). The tapped browse entry
     *  renders instantly (title/cover from [MangaSeedStore]); NDJSON
     *  partial `manga` snapshots progressively refine it — header first,
     *  chapter rows as the source discovers them — until the `final`
     *  payload resolves the server id and the authoritative refresh runs.
     *  An already-registered hit — and local/opds sources — answer a
     *  buffered `{"id": n}` object with no type tag (terminal). */
    private fun resolveUnregistered() {
        val key = requestedMangaKey
        if (requestedSourceId <= 0 || key.isNullOrBlank()) {
            _state.value = MangaUiState(loading = false, error = appContext.getString(R.string.error_invalid_entry))
            return
        }
        viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = MangaUiState(loading = false, error = appContext.getString(R.string.error_no_server))
                return@launchGuarded
            }
            val seed = MangaSeedStore.take(requestedSourceId, key)
            _state.value = _state.value.copy(
                profile = profile,
                loading = seed == null,
                error = null,
                resolving = true,
                manga = seed?.toPlaceholderManga(requestedSourceId),
            )
            try {
                var resultId: Long? = null
                var sawEvent = false
                clientFactory.ndjsonCall(
                    profile,
                    "/manga",
                    buildJsonObject {
                        put("sourceId", requestedSourceId)
                        put("mangaKey", key)
                    },
                ).collect { ev ->
                    sawEvent = true
                    when (ev["type"]?.jsonPrimitive?.contentOrNull) {
                        "final" -> resultId =
                            ev["payload"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
                        "error" -> throw IOException(
                            ev["error"]?.jsonObject?.get("msg")?.jsonPrimitive?.contentOrNull
                                ?: appContext.getString(R.string.error_register_failed),
                        )
                        // Partial manga snapshot (source still working):
                        // full snapshot per partial.rs — replace wholesale
                        // so the header/chapters update live, Aidoku-style.
                        "manga" -> ev["manga"]?.let { partial ->
                            val entry = clientFactory.json.decodeFromJsonElement(
                                SearchManga.serializer(), partial,
                            )
                            _state.value = _state.value.copy(
                                loading = false,
                                manga = entry.toPlaceholderManga(requestedSourceId),
                            )
                            entry.chapters?.takeIf { it.isNotEmpty() }?.let { source ->
                                setChapters(
                                    source.mapIndexed { i, ch -> ch.toPlaceholderChapter(i) },
                                )
                            }
                        }
                        // Buffered terminal (already registered / local /
                        // opds / old server): bare {"id": n}, no type tag.
                        else -> if (ev.containsKey("id")) {
                            resultId = ev["id"]?.jsonPrimitive?.longOrNull
                        }
                    }
                }
                val resolved = resultId ?: run {
                    if (sawEvent) throw IOException(appContext.getString(R.string.error_register_incomplete))
                    clientFactory.create<MangaApi>(profile)
                        .registerManga(
                            RegisterMangaRequest(sourceId = requestedSourceId, mangaKey = key),
                        )
                        .await().id
                }
                mangaId = resolved
                _state.value = _state.value.copy(resolving = false)
                observeLocalDownloads()
                refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(
                    loading = false,
                    resolving = false,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }

    /** Tier B enqueue: download the chapter's CBZ to device storage for
     *  offline reading (design §6 Tier B). */
    fun enqueueLocal(chapterId: Long) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        val profile = _state.value.profile ?: return
        val manga = _state.value.manga ?: return
        val chapter = _state.value.chapters.firstOrNull { it.id == chapterId } ?: return
        val label = chapterLabel(chapter.volumeNumber, chapter.chapterNumber, chapter.title)
        viewModelScope.launchGuarded {
            localDownloads.enqueue(profile, chapterId, mangaId, manga.title, label)
        }
    }

    /** Replace the raw chapter list and re-derive the visible view. */
    private fun setChapters(chapters: List<ChapterWithDownload>) {
        _state.value = _state.value.copy(
            chapters = chapters,
            visibleChapters = _state.value.chapterPrefs.applyTo(chapters),
        )
    }

    /** Placeholder detail page while registration streams in (Aidoku
     *  parity): the search entry carries every display field, only the
     *  server-side bookkeeping is stubbed. id = -1 marks the entry as
     *  unresolved — mutating actions no-op until [refresh] lands. */
    private fun SearchManga.toPlaceholderManga(sourceId: Long): Manga = Manga(
        key = key,
        title = title,
        cover = cover,
        artists = artists,
        authors = authors,
        description = description,
        url = url,
        tags = tags,
        status = status,
        contentRating = contentRating,
        viewer = viewer,
        updateStrategy = updateStrategy,
        id = -1L,
        sourceId = sourceId,
        initialized = false,
        trashed = false,
        chapterCount = chapters?.size?.toLong() ?: 0L,
        inLibrary = false,
        incognito = false,
        categoryIds = emptyList(),
        trackerProviders = emptyList(),
    )

    /** Streaming chapter placeholder: negative index ids (unique, clearly
     *  not server ids) and zeroed reading/download state; the registered
     *  refresh replaces them with real rows. */
    private fun SourceChapter.toPlaceholderChapter(index: Int): ChapterWithDownload =
        ChapterWithDownload(
            key = key,
            title = title,
            chapterNumber = chapterNumber,
            volumeNumber = volumeNumber,
            dateUploaded = dateUploaded,
            scanlators = scanlators,
            url = url,
            language = language,
            thumbnail = thumbnail,
            locked = locked,
            id = -(index + 1).toLong(),
            trashed = false,
            pageCount = 0L,
            lastPageRead = 0L,
            isRead = false,
            isBookmarked = false,
            lastReadAt = null,
            isDownloaded = false,
            hasFinishedDownload = false,
        )

    /** Chapter list prefs changed from the filter sheet: apply locally and
     *  persist per manga (webui chapterPrefs.ts parity — everything is
     *  client-side, the server default order stays the fetch order). */
    fun setChapterPrefs(prefs: ChapterPrefs) {
        _state.value = _state.value.copy(
            chapterPrefs = prefs,
            visibleChapters = prefs.applyTo(_state.value.chapters),
        )
        viewModelScope.launchGuarded { chapterPrefsStore.save(mangaId, prefs) }
    }

    fun refresh() {
        // Retry on the unregistered path must re-run the registration
        // resolve, not fetch with the -1 placeholder id.
        if (mangaId <= 0) {
            resolveUnregistered()
            return
        }
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = MangaUiState(loading = false, error = appContext.getString(R.string.error_no_server))
                return@launchGuarded
            }
            try {
                val api = clientFactory.create<MangaApi>(profile)
                // Fetch details + chapters in parallel: the two requests run
                // against separate source calls server-side, and sequential
                // awaits doubled the detail-screen latency (webui fires both
                // queries independently as well).
                //
                // `coroutineScope` is load-bearing, not decoration: an `async`
                // child started directly in the view-model coroutine that throws
                // reports to the scope's exception handler (there is none)
                // instead of surfacing at `.await()`, which killed the process
                // when the server was unreachable. Inside coroutineScope the
                // failure is contained, cancels the sibling request and is
                // rethrown here.
                coroutineScope {
                    val mangaDeferred = async { api.getManga(mangaId).await() }
                    val chaptersDeferred = async { api.getMangaChapters(mangaId).await() }
                    val manga = mangaDeferred.await()
                    _state.value = _state.value.copy(
                        loading = false,
                        profile = profile,
                        manga = manga,
                    )
                    loadSearchMeta(profile, manga.sourceId)
                    loadLinks()
                    val chapters = chaptersDeferred.await()
                    val prefs = chapterPrefsStore.load(mangaId) ?: _state.value.chapterPrefs
                    _state.value = _state.value.copy(
                        chapterPrefs = prefs,
                        chapters = chapters,
                        visibleChapters = prefs.applyTo(chapters),
                    )
                    chaptersLoadedOnce = true
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false, profile = profile, error = e.message ?: e.toString())
            }
        }
    }

    /** "Refresh details from source" (webui refreshDetails parity). The server streams
     *  partial manga snapshots over NDJSON so a slow source keeps the
     *  connection alive past the read timeout; local/opds sources (and old
     *  servers) answer buffered, which shows up as zero events and falls
     *  back to the plain refresh call. The terminal state overwrites the
     *  detail, mirroring the webui mutation. */
    fun refreshDetailsFromSource() {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        if (_state.value.refreshingDetails) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launchGuarded
            _state.value = _state.value.copy(refreshingDetails = true, refreshNote = null)
            try {
                var result: Manga? = null
                var sawEvent = false
                clientFactory.ndjsonGet(profile, "/manga/$mangaId?refresh").collect { ev ->
                    sawEvent = true
                    when (ev["type"]?.jsonPrimitive?.contentOrNull) {
                        "final" -> result = ev["payload"]?.let {
                            clientFactory.json.decodeFromJsonElement(Manga.serializer(), it)
                        }
                        "error" -> throw IOException(
                            ev["error"]?.jsonObject?.get("msg")?.jsonPrimitive?.contentOrNull
                                ?: appContext.getString(R.string.error_refresh_details_failed),
                        )
                    }
                }
                val manga = result ?: run {
                    if (sawEvent) throw IOException(appContext.getString(R.string.error_refresh_details_incomplete))
                    clientFactory.create<MangaApi>(profile).getManga(mangaId, refresh = "1").await()
                }
                _state.value = _state.value.copy(manga = manga, refreshingDetails = false)
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_details_refreshed))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(refreshingDetails = false, refreshNote = null)
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** "Refresh chapters from source" (webui refreshChapters parity). Partial snapshots
     *  carry source-side chapters without library ids, so they drive the
     *  progress note instead of the list; the terminal chapter array
     *  overwrites everything, same as the webui cache write. */
    fun refreshChaptersFromSource() {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        if (_state.value.refreshingChapters) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launchGuarded
            _state.value = _state.value.copy(refreshingChapters = true, refreshNote = appContext.getString(R.string.msg_refreshing_chapters))
            try {
                var result: List<ChapterWithDownload>? = null
                var sawEvent = false
                clientFactory.ndjsonGet(profile, "/manga/$mangaId/chapters?refresh").collect { ev ->
                    sawEvent = true
                    when (ev["type"]?.jsonPrimitive?.contentOrNull) {
                        "manga" -> {
                            val count = (ev["manga"]?.jsonObject?.get("chapters") as? JsonArray)?.size
                            if (count != null) {
                                _state.value = _state.value.copy(refreshNote = appContext.getString(R.string.msg_chapters_found, count))
                            }
                        }
                        "final" -> result = ev["payload"]?.let {
                            clientFactory.json.decodeFromJsonElement(
                                ListSerializer(ChapterWithDownload.serializer()),
                                it,
                            )
                        }
                        "error" -> throw IOException(
                            ev["error"]?.jsonObject?.get("msg")?.jsonPrimitive?.contentOrNull
                                ?: appContext.getString(R.string.error_refresh_chapters_failed),
                        )
                    }
                }
                val chapters = result ?: run {
                    if (sawEvent) throw IOException(appContext.getString(R.string.error_refresh_chapters_incomplete))
                    clientFactory.create<MangaApi>(profile).getMangaChapters(mangaId, refresh = "1").await()
                }
                setChapters(chapters)
                _state.value = _state.value.copy(
                    refreshingChapters = false,
                    refreshNote = null,
                )
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_chapters_refreshed))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(refreshingChapters = false, refreshNote = null)
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** Author/artist/tag search support: the source row carries the manifest
     *  config, the filters endpoint says which text/genre filters exist. A
     *  failed request leaves the flags null, i.e. nothing becomes tappable. */
    private fun loadSearchMeta(profile: ServerProfile, sourceId: Long) {
        if (searchMetaLoaded) return
        searchMetaLoaded = true
        viewModelScope.launchGuarded {
            // `create` already sits inside runCatching: it throws on an invalid
            // base URL, and an exception escaping a launched coroutine is
            // uncaught (no scope handler) — i.e. a process crash, not a no-op.
            val api = runCatching { clientFactory.create<SourcesApi>(profile) }.getOrNull()
            if (api == null) {
                searchMetaLoaded = false
                return@launchGuarded
            }
            val config = runCatching {
                api.listSources().await().firstOrNull { it.id == sourceId }?.config
            }.getOrNull()
            val filters = runCatching { api.getSourceFilters(sourceId).await() }.getOrDefault(emptyList())
            _searchMeta.value = SourceSearchMeta(filters = filters, config = config)
        }
    }

    fun toggleLibrary() {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        val manga = _state.value.manga ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                if (manga.inLibrary) {
                    api.removeFromLibrary(mangaId).await()
                    _state.value = _state.value.copy(manga = manga.copy(inLibrary = false))
                } else {
                    val updated = api.addToLibrary(AddToLibraryRequest(mangaId = mangaId)).await()
                    _state.value = _state.value.copy(manga = updated)
                }
            } catch (_: Exception) {
                // Leave the state as-is; the next refresh corrects any drift.
            }
        }
    }

    fun toggleIncognito() {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        val manga = _state.value.manga ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val updated = api.updateManga(mangaId, UpdateMangaRequest(incognito = !manga.incognito)).await()
                _state.value = _state.value.copy(manga = updated)
            } catch (_: Exception) {
            }
        }
    }

    /** M2: whole-manga read/unread via the batch progress endpoint. */
    fun markAllChapters(read: Boolean) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val api = clientFactory.create<MangaApi>(profile)
                api.batchUpdateChapters(
                    org.sumidoku.android.core.api.model.BatchProgressRequest(
                        isRead = read,
                        mangaIds = listOf(mangaId),
                    ),
                ).await()
                refresh()
            } catch (_: Exception) {
            }
        }
    }

    /** True after the first successful chapter fetch: the resume-refresh
     *  below must not double the initial load (ON_RESUME fires on first
     *  display too). */
    private var chaptersLoadedOnce = false

    /** Returning from the reader (or any pushed page) silently re-syncs the
     *  chapter rows: progress written elsewhere does not propagate by
     *  itself, so without this the list stayed stale until re-entering the
     *  page (webui's chapters-query invalidation equivalent). */
    fun onResume() {
        if (mangaId <= 0 || !chaptersLoadedOnce || _state.value.loading) return
        viewModelScope.launchGuarded {
            runCatching { reloadChapters() }
        }
    }

    /** Re-fetch the chapter list after a batch mutation (webui invalidates
     *  the chapters query; the prefs-derived visible view follows). */
    private suspend fun reloadChapters() {
        val profile = _state.value.profile ?: return
        val chapters = clientFactory.create<MangaApi>(profile).getMangaChapters(mangaId).await()
        setChapters(chapters)
    }

    /** Batch read/unread over explicit ids (webui applyRead parity): marking
     *  read only touches readable chapters (unlocked or downloaded); marking
     *  unread wipes the reading trace server-side, progress included. */
    fun batchMarkRead(chapterIds: List<Long>, read: Boolean) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            val targets = if (read) {
                chapterIds.filter { id ->
                    _state.value.chapters.firstOrNull { it.id == id }
                        ?.let { !it.locked || it.isDownloaded } == true
                }
            } else {
                chapterIds
            }
            if (targets.isEmpty()) {
                _meta.value = _meta.value.copy(
                    error = if (read) {
                        appContext.getString(R.string.error_locked_need_download)
                    } else {
                        appContext.getString(R.string.error_no_actionable_chapters)
                    },
                )
                return@launchGuarded
            }
            try {
                val res = clientFactory.create<MangaApi>(profile).batchUpdateChapters(
                    org.sumidoku.android.core.api.model.BatchProgressRequest(
                        isRead = read,
                        chapterIds = targets,
                    ),
                ).await()
                _meta.value = _meta.value.copy(
                    message = if (read) {
                        appContext.getString(R.string.msg_marked_read, res.updated)
                    } else {
                        appContext.getString(R.string.msg_marked_unread, res.updated)
                    },
                )
                reloadChapters()
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** Aidoku MARK_PREVIOUS: everything displayed below the tapped row in the
     *  current (filtered/sorted) visible list. */
    fun markPrevious(chapter: ChapterWithDownload, read: Boolean) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        val visible = _state.value.visibleChapters
        val idx = visible.indexOfFirst { it.id == chapter.id }
        if (idx < 0) return
        val ids = visible.drop(idx + 1).map { it.id }
        if (ids.isEmpty()) return
        batchMarkRead(ids, read)
    }

    fun batchBookmark(chapterIds: List<Long>, bookmarked: Boolean) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val res = clientFactory.create<MangaApi>(profile).batchUpdateChapters(
                    org.sumidoku.android.core.api.model.BatchProgressRequest(
                        isBookmarked = bookmarked,
                        chapterIds = chapterIds,
                    ),
                ).await()
                _meta.value = _meta.value.copy(
                    message = if (bookmarked) {
                        appContext.getString(R.string.msg_bookmarked, res.updated)
                    } else {
                        appContext.getString(R.string.msg_unbookmarked, res.updated)
                    },
                )
                reloadChapters()
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** Tier A batch enqueue — POST /downloads {chapterIds}. */
    fun downloadChapters(chapterIds: List<Long>) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val res = clientFactory.create<DownloadsApi>(profile)
                    .enqueueDownloads(EnqueueDownloadsRequest(chapterIds = chapterIds)).await()
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_enqueued, res.queued.size))
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** Remove finished downloads for the given chapters (webui
     *  deleteFinished parity: POST /downloads/delete {finishedOnly: true}). */
    fun deleteDownloadsFor(chapterIds: List<Long>) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        val finished = chapterIds.filter { id ->
            _state.value.chapters.firstOrNull { it.id == id }?.hasFinishedDownload == true
        }
        if (finished.isEmpty()) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val res = clientFactory.create<DownloadsApi>(profile).deleteDownloads(
                    DeleteDownloadsRequest(chapterIds = finished, finishedOnly = true),
                ).await()
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_downloads_deleted, res.deleted))
                reloadChapters()
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** M2/M7: categories editor data and save. */
    fun loadCategories() {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val categories = clientFactory.create<org.sumidoku.android.core.api.CategoriesApi>(profile)
                    .listCategories()
                    .await()
                _meta.value = _meta.value.copy(categories = categories)
            } catch (_: Exception) {
            }
        }
    }

    fun saveCategories(ids: Set<Long>) {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val updated = clientFactory.create<org.sumidoku.android.core.api.CategoriesApi>(profile)
                    .setMangaCategories(
                        mangaId,
                        org.sumidoku.android.core.api.model.SetCategoriesRequest(categoryIds = ids.toList()),
                    )
                    .await()
                _state.value = _state.value.copy(manga = updated)
            } catch (_: Exception) {
            }
        }
    }

    /** Tier A enqueue: server downloads the chapter — POST /downloads.
     *  The row's cloud icon flips to the downloaded state on next refresh. */
    fun enqueueChapter(chapterId: Long) {
        // Unregistered placeholder page: no server ids yet, so no-op.
        if (mangaId <= 0) return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                val api = clientFactory.create<DownloadsApi>(profile)
                api.enqueueDownloads(EnqueueDownloadsRequest(chapterIds = listOf(chapterId))).await()
            } catch (_: Exception) {
            }
        }
    }

    // --- M6: metadata editing / matching / cover / tracker links ---

    private val _meta = MutableStateFlow(MetaUiState())
    val meta: StateFlow<MetaUiState> = _meta

    fun consumeMessage() {
        _meta.value = _meta.value.copy(message = null, error = null)
    }

    private fun fail(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        _meta.value = _meta.value.copy(
            matchLoading = false, applyBusy = false, coverBusy = false,
            editBusy = false, linkBusy = false,
            error = e.message ?: e.toString(),
        )
    }

    /** Manual metadata edit; every touched field becomes a manual lock
     *  (server-side semantics), unlock carries fields the user released. */
    fun saveMetadata(
        title: String?, authors: List<String>?, artists: List<String>?,
        description: String?, tags: List<String>?,
        status: MangaStatus?, contentRating: ContentRating?,
        unlock: List<MetadataField>,
    ) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(editBusy = true)
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val updated = api.updateManga(
                    mangaId,
                    UpdateMangaRequest(
                        title = title, authors = authors, artists = artists,
                        description = description, tags = tags,
                        status = status, contentRating = contentRating,
                        metadataUnlock = unlock.ifEmpty { null },
                    ),
                ).await()
                _state.value = _state.value.copy(manga = updated)
                _meta.value = _meta.value.copy(editBusy = false, message = appContext.getString(R.string.msg_saved))
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Restore source metadata and drop locks (webui "restore source metadata"). */
    fun restoreMetadata() {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(editBusy = true)
            try {
                val result = clientFactory.create<MetadataApi>(profile)
                    .restoreMangaMetadata(mangaId, MetadataRestoreRequest(fields = null, refresh = null))
                    .await()
                _state.value = _state.value.copy(manga = result.manga)
                _meta.value = _meta.value.copy(
                    editBusy = false,
                    message = appContext.getString(R.string.msg_metadata_restored, result.unlockedFields.size),
                )
            } catch (e: Exception) { fail(e) }
        }
    }

    fun loadAlternates() {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(coverBusy = true)
            try {
                val urls = clientFactory.create<MangaApi>(profile)
                    .getAlternateCovers(mangaId).await()
                    .map { it.toString() }
                _meta.value = _meta.value.copy(coverBusy = false, alternates = urls)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun selectCover(url: String) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(coverBusy = true)
            try {
                val updated = clientFactory.create<MangaApi>(profile)
                    .selectMangaCover(mangaId, SelectCoverRequest(url = java.net.URI(url)))
                    .await()
                _state.value = _state.value.copy(manga = updated)
                _meta.value = _meta.value.copy(coverBusy = false, message = appContext.getString(R.string.msg_cover_updated))
            } catch (e: Exception) { fail(e) }
        }
    }

    // --- metadata match (preview → apply) ---

    fun setMatchProvider(provider: TrackerProvider) {
        _meta.value = _meta.value.copy(
            matchProvider = provider, matchResults = null, preview = null,
        )
    }

    fun setMatchQuery(query: String) {
        _meta.value = _meta.value.copy(matchQuery = query)
    }

    fun matchSearch() {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val query = meta.matchQuery.ifBlank { _state.value.manga?.title ?: return }
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(matchLoading = true, preview = null)
            try {
                val results = clientFactory.create<TrackersApi>(profile)
                    .trackerSearch(meta.matchProvider, query)
                    .await()
                _meta.value = _meta.value.copy(matchLoading = false, matchResults = results)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun loadPreview(item: TrackSearchItem) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(matchLoading = true)
            try {
                val preview = clientFactory.create<MetadataApi>(profile)
                    .previewMangaMetadata(mangaId, _meta.value.matchProvider.value, item.id)
                    .await()
                val selected = preview.fields
                    .filter { it.selectable && it.incoming != null }
                    .map { it.`field` }
                    .toSet()
                _meta.value = _meta.value.copy(
                    matchLoading = false, preview = preview, previewSelected = selected,
                )
            } catch (e: Exception) { fail(e) }
        }
    }

    fun togglePreviewField(field: MetadataField) {
        val current = _meta.value.previewSelected
        _meta.value = _meta.value.copy(
            previewSelected = if (field in current) current - field else current + field,
        )
    }

    fun backToMatchResults() {
        _meta.value = _meta.value.copy(preview = null)
    }

    /** Apply selected fields; bind also creates/updates the tracker link
     *  (server-side upsert semantics from the webui match dialog). */
    fun applyMatch(bind: Boolean) {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val preview = meta.preview ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(applyBusy = true)
            try {
                val result = clientFactory.create<MetadataApi>(profile)
                    .applyMangaMetadata(
                        mangaId,
                        MetadataApplyRequest(
                            provider = meta.matchProvider.value,
                            remoteId = preview.candidate.remoteId,
                            fields = meta.previewSelected.toList(),
                            propertyValues = null,
                            bind = bind,
                        ),
                    )
                    .await()
                _state.value = _state.value.copy(manga = result.manga)
                _meta.value = _meta.value.copy(
                    applyBusy = false, preview = null, matchResults = null,
                    message = appContext.getString(R.string.msg_fields_applied, result.appliedFields.size),
                )
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    // --- tracker links ---

    fun loadLinks() {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                val links = clientFactory.create<TrackersApi>(profile)
                    .listTrackerLinks(mangaId)
                    .await()
                _meta.value = _meta.value.copy(linkBusy = false, links = links)
                loadRemoteStates(links)
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Remote progress/status per bound provider — shown on the detail-page
     *  pills (webui TrackerSection's trackerState query). A provider whose
     *  state cannot be read is flagged instead of failing the whole list. */
    private fun loadRemoteStates(links: List<TrackerLink>) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            if (links.isEmpty()) {
                _meta.value = _meta.value.copy(remoteStates = emptyMap())
                return@launchGuarded
            }
            _meta.value = _meta.value.copy(
                remoteStates = links.associate { it.provider to TrackerRemote(loading = true) },
            )
            // Same hazard as loadSearchMeta: `create` throws on an invalid base
            // URL, and an exception escaping viewModelScope.launchGuarded is uncaught.
            val api = runCatching { clientFactory.create<TrackersApi>(profile) }.getOrNull()
            if (api == null) {
                _meta.value = _meta.value.copy(
                    remoteStates = links.associate { it.provider to TrackerRemote(failed = true) },
                )
                return@launchGuarded
            }
            val loaded = LinkedHashMap<TrackerProvider, TrackerRemote>()
            links.forEach { link ->
                val remote = try {
                    TrackerRemote(state = api.trackerState(link.provider, link.trackId).await())
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("MangaVM", "trackerState ${link.provider} failed", e)
                    TrackerRemote(failed = true)
                }
                loaded[link.provider] = remote
                _meta.value = _meta.value.copy(remoteStates = loaded.toMap())
                // Reseed the editor's remote fields when its provider's state
                // arrives, unless the user already typed (webui initialState).
                if (_meta.value.editingLink?.provider == link.provider && !_meta.value.editRemoteTouched) {
                    val s = remote.state
                    _meta.value = _meta.value.copy(
                        editStatus = s?.status,
                        editScore = s?.score?.toString() ?: "",
                        editChapterProgress = formatProgress(s?.lastReadChapter),
                        editVolumeProgress = s?.lastReadVolume?.toString() ?: "",
                    )
                }
            }
        }
    }

    /** Open the tracker-link editor for a pill. Local track mode/offset and
     *  the remote status/score/progress fields are all editable; the remote
     *  fields seed from the last loaded state (reseeded when it arrives,
     *  unless the user already typed — webui TrackerEditForm initialState). */
    fun openLinkEditor(link: TrackerLink) {
        val initial = _meta.value.remoteStates[link.provider]?.state
        _meta.value = _meta.value.copy(
            editingLink = link,
            editTrackMode = link.trackMode,
            editChapterOffset = link.chapterOffset.toString(),
            editStatus = initial?.status,
            editScore = initial?.score?.toString() ?: "",
            editChapterProgress = formatProgress(initial?.lastReadChapter),
            editVolumeProgress = initial?.lastReadVolume?.toString() ?: "",
            editRemoteTouched = false,
        )
        if (_meta.value.remoteStates[link.provider]?.state == null) {
            loadRemoteStates(listOf(link))
        }
    }

    fun closeLinkEditor() {
        _meta.value = _meta.value.copy(editingLink = null)
    }

    fun setEditTrackMode(mode: TrackerLink.TrackMode) {
        _meta.value = _meta.value.copy(editTrackMode = mode)
    }

    fun setEditChapterOffset(text: String) {
        _meta.value = _meta.value.copy(editChapterOffset = text)
    }

    fun setEditStatus(status: TrackStatus) {
        _meta.value = _meta.value.copy(editStatus = status, editRemoteTouched = true)
    }

    fun setEditScore(text: String) {
        _meta.value = _meta.value.copy(editScore = text, editRemoteTouched = true)
    }

    fun setEditChapterProgress(text: String) {
        _meta.value = _meta.value.copy(editChapterProgress = text, editRemoteTouched = true)
    }

    fun setEditVolumeProgress(text: String) {
        _meta.value = _meta.value.copy(editVolumeProgress = text, editRemoteTouched = true)
    }

    /** "12" / "12.5" rendering of a remote progress number ("" when null). */
    private fun formatProgress(value: Double?): String =
        value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

    /** Save through webui buildTrackerSave semantics: PUT the link only when
     *  trackMode/chapterOffset changed, then POST the remote fields that
     *  differ from the loaded remote state to /trackers/{provider}/update. */
    fun saveLinkSettings() {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val link = meta.editingLink ?: return
        val offset = meta.editChapterOffset.trim().toIntOrNull() ?: link.chapterOffset
        val linkChanged = meta.editTrackMode != link.trackMode || offset != link.chapterOffset
        val initial = meta.remoteStates[link.provider]?.state
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                val api = clientFactory.create<TrackersApi>(profile)
                if (linkChanged) {
                    api.putTrackerLink(
                        mangaId,
                        TrackerLinkUpsert(
                            provider = link.provider,
                            trackId = link.trackId,
                            title = link.title,
                            // The generated models nest two distinct enums.
                            trackMode = when (meta.editTrackMode) {
                                TrackerLink.TrackMode.AUTO -> TrackerLinkUpsert.TrackMode.AUTO
                                TrackerLink.TrackMode.CHAPTER -> TrackerLinkUpsert.TrackMode.CHAPTER
                                TrackerLink.TrackMode.VOLUME -> TrackerLinkUpsert.TrackMode.VOLUME
                            },
                            chapterOffset = offset,
                        ),
                    ).await()
                }
                // webui: remote update only when the initial state is known.
                if (initial != null) {
                    buildRemoteUpdate(meta, initial)?.let { update ->
                        api.trackerUpdate(link.provider, update.copy(trackId = link.trackId)).await()
                    }
                }
                _meta.value = _meta.value.copy(
                    linkBusy = false, editingLink = null, message = appContext.getString(R.string.msg_link_settings_saved),
                )
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    /** webui buildTrackUpdate: only edited fields are pushed. A blank number
     *  counts as 0 when the field was changed; unparsable input is skipped. */
    private fun buildRemoteUpdate(meta: MetaUiState, initial: RemoteState): TrackerUpdateRequest? {
        val status = meta.editStatus?.takeIf { it != initial.status }
        val score = editedInt(meta.editScore, initial.score?.toString() ?: "")
            ?.takeIf { it != initial.score }
        val chapter = editedDouble(meta.editChapterProgress, formatProgress(initial.lastReadChapter))
            ?.takeIf { it != initial.lastReadChapter }
        val volume = editedInt(meta.editVolumeProgress, initial.lastReadVolume?.toString() ?: "")
            ?.takeIf { it != initial.lastReadVolume }
        if (status == null && score == null && chapter == null && volume == null) return null
        return TrackerUpdateRequest(
            trackId = "",
            score = score,
            status = status,
            lastReadChapter = chapter,
            lastReadVolume = volume,
        )
    }

    private fun editedInt(value: String, initialText: String): Int? {
        val trimmed = value.trim()
        if (trimmed == initialText) return null
        return if (trimmed.isEmpty()) 0 else trimmed.toIntOrNull()
    }

    private fun editedDouble(value: String, initialText: String): Double? {
        val trimmed = value.trim()
        if (trimmed == initialText) return null
        return if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull()
    }

    // Add-binding search keeps its own state so the metadata-match flow
    // (matchProvider/matchQuery/matchResults) is not overwritten.

    fun setLinkAddProvider(provider: TrackerProvider) {
        _meta.value = _meta.value.copy(linkAddProvider = provider, linkAddResults = null)
    }

    fun setLinkAddQuery(query: String) {
        _meta.value = _meta.value.copy(linkAddQuery = query)
    }

    fun linkAddSearch() {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val query = meta.linkAddQuery.ifBlank { _state.value.manga?.title ?: return }
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkAddLoading = true)
            try {
                val results = clientFactory.create<TrackersApi>(profile)
                    .trackerSearch(meta.linkAddProvider, query)
                    .await()
                _meta.value = _meta.value.copy(linkAddLoading = false, linkAddResults = results)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _meta.value = _meta.value.copy(
                    linkAddLoading = false, error = e.message ?: e.toString(),
                )
            }
        }
    }

    /** Bind the manga to a tracker entry (PUT upsert semantics). */
    fun addLink(provider: TrackerProvider, item: TrackSearchItem) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                clientFactory.create<TrackersApi>(profile)
                    .putTrackerLink(
                        mangaId,
                        TrackerLinkUpsert(provider = provider, trackId = item.id, title = item.title),
                    )
                    .await()
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_link_added, item.title))
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun removeLink(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                clientFactory.create<TrackersApi>(profile)
                    .deleteTrackerLink(mangaId, provider)
                    .await()
                _meta.value = _meta.value.copy(
                    editingLink = null, message = appContext.getString(R.string.msg_link_removed),
                )
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Pull remote progress — preview first (design §14 M6); the
     *  sheet asks for confirmation, then applySync applies for real. */
    fun syncLink(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true, syncPreview = null)
            try {
                val result = clientFactory.create<TrackersApi>(profile)
                    .syncTrackerLink(mangaId, provider, TrackerSyncRequest(preview = true))
                    .await()
                _meta.value = _meta.value.copy(linkBusy = false, syncPreview = provider to result)
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Apply the previewed sync-pull for real. */
    fun applySync(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            _meta.value = _meta.value.copy(linkBusy = true, syncPreview = null)
            try {
                val result = clientFactory.create<TrackersApi>(profile)
                    .syncTrackerLink(mangaId, provider, TrackerSyncRequest(preview = false))
                    .await()
                _meta.value = _meta.value.copy(message = appContext.getString(R.string.msg_sync_applied, result.marked))
                refresh()
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun dismissSyncPreview() {
        _meta.value = _meta.value.copy(syncPreview = null)
    }

    // ---- M7: single-manga migration ----

    /** Load installed sources other than the manga's own that can
     *  host the migrated entry. */
    fun startMigration() {
        val manga = _state.value.manga ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            _meta.value = _meta.value.copy(
                migrationSources = null,
                migrationTargetId = null,
                migrationCandidates = null,
                migrationSelected = null,
            )
            try {
                val sources = clientFactory.create<MigrationApi>(profile)
                    .getMigrationSources()
                    .await()
                    .filter { it.id != manga.sourceId }
                _meta.value = _meta.value.copy(migrationSources = sources)
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Look up matches for this manga in the chosen target source. */
    fun selectMigrationTarget(targetSourceId: Long) {
        val manga = _state.value.manga ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            _meta.value = _meta.value.copy(
                migrationTargetId = targetSourceId,
                migrationCandidates = null,
                migrationSelected = null,
                migrationBusy = true,
            )
            try {
                val lookup = clientFactory.create<MigrationApi>(profile)
                    .lookupMigrationCandidates(manga.id, targetSourceId)
                    .await()
                _meta.value = _meta.value.copy(
                    migrationBusy = false,
                    migrationCandidates = lookup.candidates,
                    migrationSelected = lookup.candidates.firstOrNull(),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _meta.value = _meta.value.copy(migrationBusy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun selectMigrationCandidate(candidate: SearchManga?) {
        _meta.value = _meta.value.copy(migrationSelected = candidate)
    }

    /** Execute the migration and refresh (the entry may have been
     *  re-homed to the target source). */
    fun runMigration(mode: MigrateMode) {
        val manga = _state.value.manga ?: return
        val targetId = _meta.value.migrationTargetId ?: return
        val candidate = _meta.value.migrationSelected ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            _meta.value = _meta.value.copy(migrationBusy = true)
            try {
                val response = clientFactory.create<MigrationApi>(profile)
                    .migrateManga(
                        MigrateRequest(
                            mode = mode,
                            propertyEntries = listOf(
                                MigrateEntry(
                                    fromMangaId = manga.id,
                                    toSourceId = targetId,
                                    toMangaKey = candidate.key,
                                ),
                            ),
                        ),
                    )
                    .await()
                val result = response.results.firstOrNull()
                if (result?.ok == true) {
                    _meta.value = _meta.value.copy(migrationBusy = false, message = appContext.getString(R.string.msg_migration_done))
                    refresh()
                } else {
                    _meta.value = _meta.value.copy(
                        migrationBusy = false,
                        error = result?.message ?: result?.reason?.toString() ?: appContext.getString(R.string.error_migration_failed),
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _meta.value = _meta.value.copy(migrationBusy = false, error = e.message ?: e.toString())
            }
        }
    }
}

/** Remote state of one bound tracker entry, as shown on the detail pill and
 *  in the link editor (webui trackerState query). */
data class TrackerRemote(
    val state: RemoteState? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Sub-state for the metadata/tracker sheets (M6). */
data class MetaUiState(
    val alternates: List<String>? = null,
    val coverBusy: Boolean = false,
    val editBusy: Boolean = false,
    val matchProvider: TrackerProvider = TrackerProvider.ANILIST,
    val matchQuery: String = "",
    val matchResults: List<TrackSearchItem>? = null,
    val matchLoading: Boolean = false,
    val preview: MetadataPreview? = null,
    val previewSelected: Set<MetadataField> = emptySet(),
    val applyBusy: Boolean = false,
    val links: List<TrackerLink>? = null,
    val linkBusy: Boolean = false,
    /** Remote status/progress per bound provider (detail-page pills). */
    val remoteStates: Map<TrackerProvider, TrackerRemote> = emptyMap(),
    /** Link whose trackMode/chapterOffset editor is open, null when closed. */
    val editingLink: TrackerLink? = null,
    val editTrackMode: TrackerLink.TrackMode = TrackerLink.TrackMode.AUTO,
    val editChapterOffset: String = "0",
    /** Manual remote-progress edits (webui TrackerEditForm's TrackEditValues). */
    val editStatus: TrackStatus? = null,
    val editScore: String = "",
    val editChapterProgress: String = "",
    val editVolumeProgress: String = "",
    /** True once the user typed into a remote edit field — the async state
     *  load then reseeds nothing, so typed values are never clobbered. */
    val editRemoteTouched: Boolean = false,
    // Add-binding search — deliberately separate from the match* fields so
    // the metadata-match dialog and the tracker sheet never overwrite each other.
    val linkAddProvider: TrackerProvider = TrackerProvider.ANILIST,
    val linkAddQuery: String = "",
    val linkAddResults: List<TrackSearchItem>? = null,
    val linkAddLoading: Boolean = false,
    /** Preview of a sync-pull (design §14 M6): the remote progress
     *  that would be applied, awaiting user confirmation. */
    val syncPreview: Pair<TrackerProvider, org.sumidoku.android.core.api.model.TrackerSyncResult>? = null,
    /** Server categories for the categories editor sheet (M2/M7). */
    val categories: List<org.sumidoku.android.core.api.model.Category>? = null,
    // M7 single-manga migration sheet state.
    val migrationSources: List<MigrationSource>? = null,
    val migrationTargetId: Long? = null,
    val migrationCandidates: List<SearchManga>? = null,
    val migrationSelected: SearchManga? = null,
    val migrationBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

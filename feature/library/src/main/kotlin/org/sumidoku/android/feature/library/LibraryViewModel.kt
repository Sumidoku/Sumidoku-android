package org.sumidoku.android.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.openapitools.client.infrastructure.CollectionFormats.CSVParams
import org.sumidoku.android.core.api.CategoriesApi
import org.sumidoku.android.core.api.DownloadsApi
import org.sumidoku.android.core.api.FilterGroupsApi
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.BatchProgressRequest
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.FilterGroup
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.api.model.LibrarySort
import org.sumidoku.android.core.api.model.NameCount
import org.sumidoku.android.core.api.model.SetCategoriesRequest
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceCount
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.CatalogCache
import org.sumidoku.android.core.data.LibraryDisplay
import org.sumidoku.android.core.data.LibraryDisplayStore
import org.sumidoku.android.core.data.LibraryFilterStore
import org.sumidoku.android.core.data.LibraryFilters
import org.sumidoku.android.core.data.LIBRARY_FORMS
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.ServerReachability
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** Tri-state verdict of one library filter key (webui `TriStateSelection`:
 *  1 = include, -1 = exclude, key absent = no verdict). */
enum class LibraryTri { NONE, INCLUDE, EXCLUDE }

/** none → include → exclude → none, the cycle a filter row click performs. */
fun LibraryTri.next(): LibraryTri = when (this) {
    LibraryTri.NONE -> LibraryTri.INCLUDE
    LibraryTri.INCLUDE -> LibraryTri.EXCLUDE
    LibraryTri.EXCLUDE -> LibraryTri.NONE
}

private fun LibraryTri.wire(): Int = when (this) {
    LibraryTri.INCLUDE -> LibraryFilters.INCLUDE
    LibraryTri.EXCLUDE -> LibraryFilters.EXCLUDE
    LibraryTri.NONE -> 0
}

/** Unknown values (legacy/hand-edited storage) map to "no verdict". */
private fun Int.toTri(): LibraryTri? = when (this) {
    LibraryFilters.INCLUDE -> LibraryTri.INCLUDE
    LibraryFilters.EXCLUDE -> LibraryTri.EXCLUDE
    else -> null
}

/** Unfiled manga: the API spells "no category" as category id 0. */
const val UNCATEGORIZED_CATEGORY = "0"

/** Pause between the last keystroke and the server round-trip (Komikku
 *  LibraryScreenModel SEARCH_DEBOUNCE_MILLIS). */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

/** The four rows of the panel's "more filters" section, each opening its own
 *  tri-state sheet (webui TriStateDropdown set). */
enum class LibraryFilterDimension { RATINGS, SOURCES, AUTHORS, TAGS }

/** Cover marks the display sheet can switch off (design 34: cover badges). */
enum class LibraryBadge { UNREAD, DOWNLOAD, SOURCE }

data class LibraryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val profile: ServerProfile? = null,
    val entries: List<LibraryEntry> = emptyList(),
    val hasNextPage: Boolean = false,
    val error: String? = null,
    /** Categories the category sheet offers; the selection itself lives in [categories]. */
    val categoryOptions: List<Category> = emptyList(),
    val filterGroups: List<FilterGroup> = emptyList(),
    /** Every enabled source, for the source sheet names and the summary chips. */
    val sourceOptions: List<Source> = emptyList(),
    val sourceCounts: List<SourceCount> = emptyList(),
    /** Counts arrived at least once: sources owning no entries hide (webui `counted`). */
    val sourceCountsLoaded: Boolean = false,
    val authorCounts: List<NameCount> = emptyList(),
    val tagCounts: List<NameCount> = emptyList(),
    val sort: LibrarySort = LibrarySort.ADDED,
    /** Status vocabulary (LibraryFilter values) → verdict. */
    val filters: Map<String, LibraryTri> = emptyMap(),
    /** Manga content rating 1/2/3 → verdict. */
    val ratings: Map<String, LibraryTri> = emptyMap(),
    /** Source id (string) → verdict. */
    val sources: Map<String, LibraryTri> = emptyMap(),
    /** Category id (string), [UNCATEGORIZED_CATEGORY] = unfiled → verdict. */
    val categories: Map<String, LibraryTri> = emptyMap(),
    /** Author name → verdict. */
    val authors: Map<String, LibraryTri> = emptyMap(),
    /** Tag name → verdict. */
    val tags: Map<String, LibraryTri> = emptyMap(),
    val groupId: Long? = null,
    /** Top-bar search (Komikku SearchToolbar): null = closed, "" = open and
     *  empty; a non-blank value is sent as the server's `search` parameter. */
    val query: String? = null,
    /** Display shape from the display sheet (design 34): form, per-orientation
     *  column count and which cover badges to draw. */
    val display: LibraryDisplay = LibraryDisplay(),
    /** Manga ids with at least one download; drives the cover's left-corner mark. */
    val downloads: Set<Long> = emptySet(),
    /** Batch mode (design 23): entered by long-pressing a cover. */
    val selectionMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val batchBusy: Boolean = false,
    /** One-line result of the last batch action, shown as a Snackbar. */
    val notice: String? = null,
) {
    /** Exactly what the filter button badge counts; categories stay out
     *  (its own control is always on screen). */
    val activeFilterCount: Int
        get() = filters.size + ratings.size + sources.size + authors.size + tags.size +
            if (groupId != null) 1 else 0

    /** Drives the empty state: "no manga match" vs a genuinely empty library. */
    val hasAnyFilter: Boolean
        get() = categories.isNotEmpty() || activeFilterCount > 0 || !query.isNullOrBlank()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val filterStore: LibraryFilterStore,
    private val displayStore: LibraryDisplayStore,
    private val catalogCache: CatalogCache,
    reachability: ServerReachability,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private var page = 1
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launchGuarded {
            // Restore before the first page so the restored controls are honoured, then the
            // option lists (which also prune stale restored ids), then the listing itself.
            //
            // Wrapped: an exception escaping this coroutine is uncaught (no
            // scope handler) and would kill the process. Offline the cached page
            // is already seeded below; a failed option fetch must not escalate.
            try {
                restoreFilters()
                _state.update { it.copy(display = displayStore.load()) }
                // Paint the cached page before ANY network work — the filter-option
                // fetches below included: on a slow or dead server those round-trips
                // must not delay the first pixels. refresh() replaces it once the
                // (pruned) filter state and the response are in.
                repository.activeProfile.first()?.let { seedFromCache(it) }
                loadFilters()
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false) }
            }
        }
        // Back online → silently re-fetch; the banner already hid itself. A failed
        // refresh keeps the cached content on screen, so this is safe to fire-and-forget.
        viewModelScope.launchGuarded {
            reachability.offline.drop(1).filter { offline -> !offline }.collect { refresh() }
        }
    }

    fun setForm(form: String) {
        if (form !in LIBRARY_FORMS || _state.value.display.form == form) return
        updateDisplay { it.copy(form = form) }
    }

    /** Density slider: stores the column count for the current orientation only,
     *  so the portrait and landscape choices do not overwrite each other. */
    fun setColumns(landscape: Boolean, columns: Int?) {
        val display = _state.value.display
        if (display.columns(landscape) == columns) return
        updateDisplay {
            if (landscape) it.copy(landscapeColumns = columns) else it.copy(portraitColumns = columns)
        }
    }

    fun setBadge(badge: LibraryBadge, enabled: Boolean) {
        updateDisplay {
            when (badge) {
                LibraryBadge.UNREAD -> it.copy(badgeUnread = enabled)
                LibraryBadge.DOWNLOAD -> it.copy(badgeDownload = enabled)
                LibraryBadge.SOURCE -> it.copy(badgeSource = enabled)
            }
        }
    }

    private fun updateDisplay(transform: (LibraryDisplay) -> LibraryDisplay) {
        val next = transform(_state.value.display).sanitized()
        _state.update { it.copy(display = next) }
        viewModelScope.launchGuarded { displayStore.save(next) }
    }

    // ── Batch mode (design 23) ────────────────────────────────────────────────

    fun beginSelection(id: Long) {
        _state.update { it.copy(selectionMode = true, selected = setOf(id)) }
    }

    fun toggleSelected(id: Long) {
        _state.update { s ->
            if (!s.selectionMode) return@update s
            val next = if (id in s.selected) s.selected - id else s.selected + id
            // Deselecting the last item leaves batch mode, like Komikku's library.
            s.copy(selected = next, selectionMode = next.isNotEmpty())
        }
    }

    fun selectAll(select: Boolean) {
        _state.update { s ->
            s.copy(selected = if (select) s.entries.map { it.id }.toSet() else emptySet())
        }
    }

    fun exitSelection() {
        _state.update { it.copy(selectionMode = false, selected = emptySet()) }
    }

    /** Marks every chapter of the selected manga read: one call, because the
     *  batch endpoint accepts `mangaIds` directly (design 23: mark as read). */
    fun batchMarkRead() = runBatch { profile, ids ->
        clientFactory.create<MangaApi>(profile)
            .batchUpdateChapters(BatchProgressRequest(isRead = true, mangaIds = ids))
            .await()
        appContext.getString(R.string.library_batch_marked_read, ids.size)
    }

    /** Replaces the category list of each selected manga (SetCategoriesRequest
     *  is a full replacement, so the sheet is a "set", not an "add"). */
    fun batchSetCategories(categoryIds: List<Long>) = runBatch { profile, ids ->
        val api = clientFactory.create<MangaApi>(profile)
        coroutineScope {
            ids.map { id ->
                async { runCatching { api.setMangaCategories(id, SetCategoriesRequest(categoryIds)).await() } }
            }.awaitAll()
        }
        appContext.getString(R.string.library_batch_categories_set, ids.size)
    }

    /** Removes the selection from the library; progress and downloads stay on
     *  the server (same policy as webui's remove). */
    fun batchRemove() = runBatch { profile, ids ->
        val api = clientFactory.create<LibraryApi>(profile)
        coroutineScope {
            ids.map { id -> async { runCatching { api.removeFromLibrary(id).await() } } }.awaitAll()
        }
        appContext.getString(R.string.library_batch_removed, ids.size)
    }

    /** Runs one batch action over the current selection, then reloads the first
     *  page so removed / re-categorised entries disappear in one step. */
    private fun runBatch(action: suspend (ServerProfile, List<Long>) -> String) {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty() || _state.value.batchBusy) return
        viewModelScope.launchGuarded {
            _state.update { it.copy(batchBusy = true) }
            val profile = _state.value.profile ?: repository.activeProfile.first()
            val message = if (profile == null) {
                appContext.getString(R.string.library_no_server)
            } else {
                runCatching { action(profile, ids) }.getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    appContext.getString(R.string.library_batch_failed, e.message ?: e.toString())
                }
            }
            _state.update { it.copy(batchBusy = false, notice = message) }
            exitSelection()
            refresh()
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** Top-bar search (Komikku SearchToolbar parity): null closes the bar,
     *  "" opens it. Typing re-queries after a short debounce; opening or
     *  closing an empty bar changes no results and costs no request. */
    fun setQuery(query: String?) {
        val previous = _state.value.query
        if (previous == query) return
        _state.update { it.copy(query = query) }
        val term = query?.takeIf { it.isNotBlank() }
        if (previous?.takeIf { it.isNotBlank() } == term) return
        searchJob?.cancel()
        searchJob = viewModelScope.launchGuarded {
            delay(SEARCH_DEBOUNCE_MILLIS)
            refresh()
        }
    }

    fun refresh() {
        page = 1
        loadPage(replace = true, refreshing = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasNextPage || s.error != null) return
        loadPage(replace = false, refreshing = false)
    }

    fun setSort(sort: LibrarySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        persist()
        refresh()
    }

    fun setGroupId(id: Long?) {
        if (_state.value.groupId == id) return
        _state.update { it.copy(groupId = id) }
        persist()
        refresh()
    }

    fun cycleStatusFilter(key: String) {
        _state.update { it.copy(filters = it.filters.cycled(key)) }
        applyFilterChange()
    }

    fun cycleCategory(key: String) {
        _state.update { it.copy(categories = it.categories.cycled(key)) }
        applyFilterChange()
    }

    fun cycleDimension(dimension: LibraryFilterDimension, key: String) {
        _state.update { s ->
            when (dimension) {
                LibraryFilterDimension.RATINGS -> s.copy(ratings = s.ratings.cycled(key))
                LibraryFilterDimension.SOURCES -> s.copy(sources = s.sources.cycled(key))
                LibraryFilterDimension.AUTHORS -> s.copy(authors = s.authors.cycled(key))
                LibraryFilterDimension.TAGS -> s.copy(tags = s.tags.cycled(key))
            }
        }
        applyFilterChange()
    }

    fun resetStatusFilters() {
        _state.update { it.copy(filters = emptyMap()) }
        applyFilterChange()
    }

    fun resetCategories() {
        _state.update { it.copy(categories = emptyMap()) }
        applyFilterChange()
    }

    fun resetDimension(dimension: LibraryFilterDimension) {
        _state.update { s ->
            when (dimension) {
                LibraryFilterDimension.RATINGS -> s.copy(ratings = emptyMap())
                LibraryFilterDimension.SOURCES -> s.copy(sources = emptyMap())
                LibraryFilterDimension.AUTHORS -> s.copy(authors = emptyMap())
                LibraryFilterDimension.TAGS -> s.copy(tags = emptyMap())
            }
        }
        applyFilterChange()
    }

    /** Summary-row chip removal; the filter group has no tri-state key, only [setGroupId]. */
    fun removeStatusFilter(key: String) {
        _state.update { it.copy(filters = it.filters - key) }
        applyFilterChange()
    }

    fun removeSelection(dimension: LibraryFilterDimension, key: String) {
        _state.update { s ->
            when (dimension) {
                LibraryFilterDimension.RATINGS -> s.copy(ratings = s.ratings - key)
                LibraryFilterDimension.SOURCES -> s.copy(sources = s.sources - key)
                LibraryFilterDimension.AUTHORS -> s.copy(authors = s.authors - key)
                LibraryFilterDimension.TAGS -> s.copy(tags = s.tags - key)
            }
        }
        applyFilterChange()
    }

    /** The panel's "clear filters": the six advanced dimensions plus the group.
     *  Categories keeps its own control, like webui's panel button. */
    fun clearFilters() {
        _state.update {
            it.copy(
                filters = emptyMap(),
                ratings = emptyMap(),
                sources = emptyMap(),
                authors = emptyMap(),
                tags = emptyMap(),
                groupId = null,
            )
        }
        applyFilterChange()
    }

    /** The empty state's "clear filters" resets everything, categories included. */
    fun clearAllFilters() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                filters = emptyMap(),
                ratings = emptyMap(),
                sources = emptyMap(),
                categories = emptyMap(),
                authors = emptyMap(),
                tags = emptyMap(),
                groupId = null,
                query = null,
            )
        }
        applyFilterChange()
    }

    private fun applyFilterChange() {
        persist()
        refresh()
    }

    private fun Map<String, LibraryTri>.cycled(key: String): Map<String, LibraryTri> {
        val next = (this[key] ?: LibraryTri.NONE).next()
        return if (next == LibraryTri.NONE) this - key else this + (key to next)
    }

    private fun loadPage(replace: Boolean, refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launchGuarded {
            _state.update {
                it.copy(
                    loading = replace && it.entries.isEmpty(),
                    refreshing = refreshing,
                    loadingMore = !replace,
                    error = null,
                )
            }
            val profile = _state.value.profile ?: repository.activeProfile.first()
            if (profile == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = appContext.getString(R.string.library_no_server),
                    )
                }
                return@launchGuarded
            }
            val cacheKey = listKey(_state.value)
            if (replace && _state.value.entries.isEmpty()) {
                // Cold start (or a fresh filter combination): paint the cached page
                // first so an offline launch is never a spinner; the response below
                // replaces it as soon as it arrives.
                seedFromCache(profile)
            }
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val s = _state.value
                val result = api.listLibrary(
                    page = page,
                    search = s.query?.takeIf { it.isNotBlank() },
                    sort = s.sort,
                    filter = s.filters.csvFor(LibraryTri.INCLUDE)?.let { CSVParams(it) },
                    filterExclude = s.filters.csvFor(LibraryTri.EXCLUDE)?.let { CSVParams(it) },
                    contentRating = s.ratings.csvFor(LibraryTri.INCLUDE)?.joinToString(","),
                    contentRatingExclude = s.ratings.csvFor(LibraryTri.EXCLUDE)?.joinToString(","),
                    source = s.sources.csvFor(LibraryTri.INCLUDE)?.joinToString(","),
                    sourceExclude = s.sources.csvFor(LibraryTri.EXCLUDE)?.joinToString(","),
                    category = s.categories.csvFor(LibraryTri.INCLUDE)?.joinToString(","),
                    categoryExclude = s.categories.csvFor(LibraryTri.EXCLUDE)?.joinToString(","),
                    author = s.authors.csvFor(LibraryTri.INCLUDE)?.joinToString(","),
                    authorExclude = s.authors.csvFor(LibraryTri.EXCLUDE)?.joinToString(","),
                    tag = s.tags.csvFor(LibraryTri.INCLUDE)?.joinToString(","),
                    tagExclude = s.tags.csvFor(LibraryTri.EXCLUDE)?.joinToString(","),
                    groupId = s.groupId,
                ).await()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        profile = profile,
                        entries = if (replace) result.propertyEntries else it.entries + result.propertyEntries,
                        hasNextPage = result.hasNextPage,
                    )
                }
                page += 1
                if (replace) {
                    // Only a successful response is written; an empty page is the
                    // truth and clears the slot, so stale rows can never be seeded.
                    runCatching {
                        catalogCache.save(
                            profile.id,
                            cacheKey,
                            result.propertyEntries,
                            LibraryEntry.serializer(),
                        )
                    }
                }
                loadDownloaded(profile, replace)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        // A failed refresh must not blank cached content; the global
                        // offline banner carries the connectivity signal. Only an
                        // empty screen shows the error (with its retry button).
                        error = if (it.entries.isEmpty()) e.message ?: e.toString() else null,
                    )
                }
            }
        }
    }

    /** Paint the cached first page of the current filter state, if one exists.
     *  The profile goes into the state along with the rows: covers are resolved
     *  through it, so seeded content without it would render coverless. */
    private suspend fun seedFromCache(profile: ServerProfile) {
        val cached = runCatching {
            catalogCache.seed(profile.id, listKey(_state.value), LibraryEntry.serializer())
        }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) {
            _state.update { it.copy(profile = profile, loading = false, entries = cached) }
        }
    }

    /** Cache slot of the first page: the sort plus every tri-state filter
     *  dimension, so a changed toolbar never resurrects another view's rows.
     *  Keys and verdicts are sorted to keep the string stable. */
    private fun listKey(s: LibraryUiState): String = buildString {
        fun Map<String, LibraryTri>.enc(): String =
            entries.sortedBy { it.key }.joinToString(",") { (key, tri) -> "$key:${tri.ordinal}" }
        append("library|sort=").append(s.sort.value)
        append("|f=").append(s.filters.enc())
        append("|r=").append(s.ratings.enc())
        append("|s=").append(s.sources.enc())
        append("|c=").append(s.categories.enc())
        append("|a=").append(s.authors.enc())
        append("|t=").append(s.tags.enc())
        append("|g=").append(s.groupId ?: "-")
        append("|q=").append(s.query?.takeIf { it.isNotBlank() } ?: "-")
    }

    /** Manga ids that have at least one download, so the cover can show the
     *  left-corner mark (design 34). Only asked for while the badge is on, and
     *  a failure just means "no badges" rather than an error banner. */
    private suspend fun loadDownloaded(profile: ServerProfile, replace: Boolean) {
        if (!_state.value.display.badgeDownload) {
            if (_state.value.downloads.isNotEmpty()) _state.update { it.copy(downloads = emptySet()) }
            return
        }
        runCatching {
            clientFactory.create<DownloadsApi>(profile).listDownloads().await()
        }.getOrNull()?.let { downloads ->
            val ids = downloads.map { it.mangaId }.toSet()
            if (replace || ids != _state.value.downloads) {
                _state.update { it.copy(downloads = ids) }
            }
        }
    }

    /** Keys of one verdict as a CSV list; an empty set sends null (never an empty CSV). */
    private fun Map<String, LibraryTri>.csvFor(state: LibraryTri): List<String>? =
        entries.filter { it.value == state }.map { it.key }.sorted().takeIf { it.isNotEmpty() }

    /** Restore the persisted toolbar before the first request (webui loadLibraryFilters). */
    private suspend fun restoreFilters() {
        val saved = runCatching { filterStore.load() }.getOrDefault(LibraryFilters())
        _state.update {
            it.copy(
                sort = LibrarySort.values().firstOrNull { s -> s.value == saved.sort } ?: LibrarySort.ADDED,
                filters = saved.filters.triMap(),
                ratings = saved.ratings.triMap(),
                sources = saved.sources.triMap(),
                categories = saved.categories.triMap(),
                authors = saved.authors.triMap(),
                tags = saved.tags.triMap(),
                groupId = saved.group,
            )
        }
    }

    private fun Map<String, Int>.triMap(): Map<String, LibraryTri> =
        mapNotNull { (key, state) -> state.toTri()?.let { key to it } }.toMap()

    /** Persist the whole toolbar on every change (webui saveLibraryFilters). */
    private fun persist() {
        val s = _state.value
        val snapshot = LibraryFilters(
            sort = s.sort.value,
            filters = s.filters.wireMap(),
            ratings = s.ratings.wireMap(),
            sources = s.sources.wireMap(),
            categories = s.categories.wireMap(),
            authors = s.authors.wireMap(),
            tags = s.tags.wireMap(),
            group = s.groupId,
        )
        viewModelScope.launchGuarded { runCatching { filterStore.save(snapshot) } }
    }

    private fun Map<String, LibraryTri>.wireMap(): Map<String, Int> =
        mapNotNull { (key, state) -> state.wire().takeIf { it != 0 }?.let { key to it } }.toMap()

    /** Option lists for the panel, fetched together; a failure keeps whatever the
     *  screen already had (a missing list only hides options, never filters). */
    private suspend fun loadFilters() {
        val profile = repository.activeProfile.first() ?: return
        coroutineScope {
            val categoriesApi = async {
                runCatching { clientFactory.create<CategoriesApi>(profile).listCategories().await() }.getOrNull()
            }
            val groupsApi = async {
                runCatching { clientFactory.create<FilterGroupsApi>(profile).listFilterGroups().await() }.getOrNull()
            }
            val sourcesApi = async {
                runCatching { clientFactory.create<SourcesApi>(profile).listSources().await() }.getOrNull()
            }
            val libraryApi = async { runCatching { clientFactory.create<LibraryApi>(profile) }.getOrNull() }
            val counts = async { runCatching { libraryApi.await()?.getLibrarySourceCounts()?.await() }.getOrNull() }
            val authors = async { runCatching { libraryApi.await()?.getLibraryAuthors()?.await() }.getOrNull() }
            val tags = async { runCatching { libraryApi.await()?.getLibraryTags()?.await() }.getOrNull() }

            val loadedCategories = categoriesApi.await()
            val loadedGroups = groupsApi.await()
            val loadedSources = sourcesApi.await()
                ?.filter { it.enabled }
                ?.sortedBy { it.name.lowercase() }
            val loadedCounts = counts.await()
            val loadedAuthors = authors.await()?.sortedBy { it.name.lowercase() }
            val loadedTags = tags.await()?.sortedBy { it.name.lowercase() }

            _state.update { s ->
                s.copy(
                    categoryOptions = loadedCategories ?: s.categoryOptions,
                    filterGroups = loadedGroups ?: s.filterGroups,
                    sourceOptions = loadedSources ?: s.sourceOptions,
                    sourceCounts = loadedCounts ?: s.sourceCounts,
                    sourceCountsLoaded = s.sourceCountsLoaded || loadedCounts != null,
                    authorCounts = loadedAuthors ?: s.authorCounts,
                    tagCounts = loadedTags ?: s.tagCounts,
                )
            }
            pruneStale(loadedCategories, loadedSources, loadedAuthors, loadedTags, loadedGroups)
        }
    }

    /** A restored id that no longer exists would filter the library down to
     *  nothing with no visible reason, so unknown keys are dropped once their
     *  option list has loaded (webui pruneSelection) and the result is re-saved. */
    private fun pruneStale(
        categories: List<Category>?,
        sources: List<Source>?,
        authors: List<NameCount>?,
        tags: List<NameCount>?,
        groups: List<FilterGroup>?,
    ) {
        var changed = false
        _state.update { s ->
            var next = s
            categories?.let { list ->
                val valid = list.mapTo(mutableSetOf()) { it.id.toString() }.apply { add(UNCATEGORIZED_CATEGORY) }
                val pruned = next.categories.pruned(valid)
                if (pruned !== next.categories) {
                    changed = true
                    next = next.copy(categories = pruned)
                }
            }
            sources?.let { list ->
                val pruned = next.sources.pruned(list.mapTo(mutableSetOf()) { it.id.toString() })
                if (pruned !== next.sources) {
                    changed = true
                    next = next.copy(sources = pruned)
                }
            }
            authors?.let { list ->
                val pruned = next.authors.pruned(list.mapTo(mutableSetOf()) { it.name })
                if (pruned !== next.authors) {
                    changed = true
                    next = next.copy(authors = pruned)
                }
            }
            tags?.let { list ->
                val pruned = next.tags.pruned(list.mapTo(mutableSetOf()) { it.name })
                if (pruned !== next.tags) {
                    changed = true
                    next = next.copy(tags = pruned)
                }
            }
            groups?.let { list ->
                if (next.groupId != null && list.none { it.id == next.groupId }) {
                    changed = true
                    next = next.copy(groupId = null)
                }
            }
            next
        }
        if (changed) persist()
    }

    private fun Map<String, LibraryTri>.pruned(valid: Set<String>): Map<String, LibraryTri> {
        if (isEmpty()) return this
        val kept = filterKeys { it in valid }
        return if (kept.size == size) this else kept
    }
}


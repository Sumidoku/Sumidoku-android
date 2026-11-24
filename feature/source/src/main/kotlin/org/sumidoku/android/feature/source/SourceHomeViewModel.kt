package org.sumidoku.android.feature.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.FilterValueOneOf1Sort
import org.sumidoku.android.core.api.model.FilterValueOneOf2Check
import org.sumidoku.android.core.api.model.FilterValueOneOf4MultiSelect
import org.sumidoku.android.core.api.model.FilterValueOneOf5Range
import org.sumidoku.android.core.api.model.FilterValueOneOfText
import org.sumidoku.android.core.api.model.HomeComponent
import org.sumidoku.android.core.api.model.HomeFilterItem
import org.sumidoku.android.core.api.model.HomeLayout
import org.sumidoku.android.core.api.model.Listing
import org.sumidoku.android.core.api.model.MangaPage
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceFilter
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.HttpException
import retrofit2.await
import java.io.IOException
import javax.inject.Inject

/** Modes of the source screen: home layout, one listing's paged grid, or a
 *  search/filter result grid — mirrors webui SourceHomePage/SourceBrowsePage.
 *  SearchMode covers both the plain query search and the filter-panel results
 *  (query == null): the webui submits `{query, filters}` together and only
 *  omits the query when it is empty — Aidoku treats Some("") as a search, so
 *  the query must be *absent*, never an empty string. */
sealed interface SourceMode {
    data object Home : SourceMode
    data class ListingMode(val listing: Listing, val entries: MangaPage? = null, val page: Int = 1) : SourceMode
    data class SearchMode(
        val query: String? = null,
        val filters: List<FilterValue> = emptyList(),
        val entries: MangaPage? = null,
        val page: Int = 1,
    ) : SourceMode
}

/** Aidoku saved-search group: named query + filter values, kept per source
 *  (webui sourceSavedFilters parity). */
@Serializable
data class SavedFilterGroup(
    val name: String,
    val query: String = "",
    val filters: List<FilterValue> = emptyList(),
)

data class SourceHomeUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val source: Source? = null,
    val listings: List<Listing> = emptyList(),
    val home: HomeLayout? = null,
    /** True only when the server answered 501 (unsupported) — any other
     *  failure lands in [homeError] and gets a retry. */
    val home501: Boolean = false,
    val homeError: String? = null,
    val mode: SourceMode = SourceMode.Home,
    val loadingMore: Boolean = false,
    /** Paged-grid failure (listing/search/filters); retry re-runs the page. */
    val modeError: String? = null,
    /** Dynamic filter schema (GET /sources/{id}/filters); empty when
     *  the source has none (501). */
    val filters: List<SourceFilter> = emptyList(),
    /** Filter draft keyed by filter id (webui FilterStates parity): seeded
     *  from the source defaults, overlaid with the per-source persisted
     *  values, edited by the header chips and the panel. */
    val activeFilters: Map<String, FilterValue> = emptyMap(),
    /** Aidoku saved searches for this source. */
    val savedGroups: List<SavedFilterGroup> = emptyList(),
    val error: String? = null,
)

/** Per-source filter draft + saved-search persistence (webui
 *  `sumidoku.sourceFilters.{id}` / sourceSavedFilters parity). One JSON
 *  string per key in the shared `sumidoku` preferences DataStore. */
class SourceFilterPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun valuesKey(sourceId: Long) = stringPreferencesKey("source_filters_$sourceId")
    private fun savedKey(sourceId: Long) = stringPreferencesKey("source_saved_filters_$sourceId")

    suspend fun loadValues(sourceId: Long): List<FilterValue> {
        val raw = dataStore.data.first()[valuesKey(sourceId)] ?: return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(FilterValue.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveValues(sourceId: Long, values: List<FilterValue>) {
        dataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(valuesKey(sourceId))
            else prefs[valuesKey(sourceId)] =
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FilterValue.serializer()), values)
        }
    }

    suspend fun loadSavedGroups(sourceId: Long): List<SavedFilterGroup> {
        val raw = dataStore.data.first()[savedKey(sourceId)] ?: return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedFilterGroup.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSavedGroups(sourceId: Long, groups: List<SavedFilterGroup>) {
        dataStore.edit { prefs ->
            if (groups.isEmpty()) prefs.remove(savedKey(sourceId))
            else prefs[savedKey(sourceId)] =
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedFilterGroup.serializer()), groups)
        }
    }
}

@HiltViewModel
class SourceHomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val filterPrefs: SourceFilterPrefs,
) : ViewModel() {

    val sourceId: Long = checkNotNull(savedStateHandle["sourceId"])

    private val json = Json { ignoreUnknownKeys = true }

    /** Deep links land directly on the grid (Aidoku parity): the author/tag
     *  route carries `filters`, the global-search "view all" route carries
     *  `query`. Both arrive via SavedStateHandle, so the very first state is
     *  already SearchMode — home is neither fetched nor painted until the
     *  user explicitly picks the home tab. */
    private val deepLinkFilters: List<FilterValue> =
        savedStateHandle.get<String>("filters")?.let { raw ->
            runCatching {
                json.decodeFromString(ListSerializer(FilterValue.serializer()), raw)
            }.getOrDefault(emptyList())
        }.orEmpty()
    private val deepLinkQuery: String? =
        savedStateHandle.get<String>("query")?.takeIf { it.isNotBlank() }
    private val hasDeepLink = deepLinkFilters.isNotEmpty() || deepLinkQuery != null

    private val _state = MutableStateFlow(
        SourceHomeUiState(
            activeFilters = deepLinkFilters
                .mapNotNull { v -> filterValueId(v)?.let { id -> id to v } }
                .toMap(),
            mode = when {
                deepLinkFilters.isNotEmpty() ->
                    SourceMode.SearchMode(query = null, filters = deepLinkFilters)
                deepLinkQuery != null -> SourceMode.SearchMode(query = deepLinkQuery)
                else -> SourceMode.Home
            },
        ),
    )
    val state: StateFlow<SourceHomeUiState> = _state

    init {
        refresh()
        if (hasDeepLink) {
            // The grid fetch needs the profile that refresh() is loading.
            viewModelScope.launch {
                _state.first { it.profile != null }
                loadMore()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, homeError = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = SourceHomeUiState(
                    loading = false,
                    error = appContext.getString(R.string.source_no_server),
                )
                return@launch
            }
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                val source = api.listSources().await().firstOrNull { it.id == sourceId }
                // Each capability fails independently: 501 = unsupported (silent),
                // anything else is a real, visible error (webui emptyOnUnsupported).
                val listings = runCatching { api.getSourceListings(sourceId).await() }.getOrDefault(emptyList())
                val filters = runCatching { api.getSourceFilters(sourceId).await() }.getOrDefault(emptyList())
                // Home is fetched only when the user can actually see it: a
                // deep link lands on the grid (Aidoku parity), so spending a
                // home request here would be pure background waste. A source
                // that declares hasHome=false (zh.relamanhua ships only
                // search) skips the doomed request entirely.
                val homeOutcome = when {
                    _state.value.mode != SourceMode.Home ->
                        HomeOutcome(home = null, unsupported = false, error = null)
                    source?.hasHome == false ->
                        HomeOutcome(home = null, unsupported = true, error = null)
                    else -> streamHome(profile)
                }
                val savedGroups = runCatching { filterPrefs.loadSavedGroups(sourceId) }.getOrDefault(emptyList())
                // Filter draft: source defaults, then persisted values by id
                // (deep-link filters arriving before the refresh lands win:
                //  they set mode != Home, which this refresh must not clobber).
                val current = _state.value
                val restoreDraft = current.activeFilters.isEmpty() && current.mode == SourceMode.Home
                val draft = if (!restoreDraft) current.activeFilters else {
                    val persisted = runCatching { filterPrefs.loadValues(sourceId) }.getOrDefault(emptyList())
                    val base = defaultFilterValues(filters)
                    persisted.forEach { v -> filterValueId(v)?.let { base[it] = v } }
                    base
                }
                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    source = source,
                    listings = listings,
                    home = homeOutcome.home,
                    home501 = homeOutcome.unsupported,
                    homeError = homeOutcome.error,
                    filters = filters,
                    activeFilters = draft,
                    savedGroups = savedGroups,
                )
                applyHomeFallback(homeOutcome.unsupported)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = friendlyError(e))
            }
        }
    }

    /** Result of one home fetch: the layout, 501-unsupported, or an error. */
    private data class HomeOutcome(
        val home: HomeLayout?,
        val unsupported: Boolean,
        val error: String?,
    )

    /** Home goes through NDJSON streaming (webui useSourceHome parity):
     *  partial-result sources (zh.picacomic, zh.wnacg) send every component
     *  via send_partial_result and RETURN an empty HomeLayout::default(), so
     *  the buffered GET yields zero components — the stream's accumulated
     *  final event is the only complete payload. Partial events render
     *  incrementally. */
    private suspend fun streamHome(profile: ServerProfile): HomeOutcome {
        var home: HomeLayout? = null
        var unsupported = false
        var error: String? = null
        try {
            clientFactory.ndjsonGet(profile, "/sources/$sourceId/home").collect { event ->
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "layout" -> event["layout"]?.let {
                        home = clientFactory.json.decodeFromJsonElement<HomeLayout>(it)
                        publishHomeProgress(home)
                    }
                    "component" -> {
                        val index = event["index"]?.jsonPrimitive?.intOrNull
                        val component = event["component"]?.let { raw ->
                            runCatching {
                                clientFactory.json.decodeFromJsonElement<HomeComponent>(raw)
                            }.getOrNull()
                        }
                        val current = home
                        if (index != null && component != null && current != null &&
                            index in current.components.indices
                        ) {
                            home = current.copy(
                                components = current.components.toMutableList()
                                    .also { it[index] = component },
                            )
                            publishHomeProgress(home)
                        }
                    }
                    "final" -> event["payload"]?.let {
                        home = clientFactory.json.decodeFromJsonElement<HomeLayout>(it)
                        publishHomeProgress(home)
                    }
                    "error" -> {
                        val err = event["error"]?.jsonObject
                        throw HomeStreamException(
                            code = err?.get("code")?.jsonPrimitive?.contentOrNull,
                            msg = err?.get("msg")?.jsonPrimitive?.contentOrNull
                                ?: appContext.getString(R.string.source_home_load_failed),
                        )
                    }
                    else -> {
                        // Buffered fallback (local sources ignore the Accept
                        // header): a bare HomeLayout body has no type tag.
                        if (event.containsKey("components")) {
                            home = clientFactory.json.decodeFromJsonElement<HomeLayout>(event)
                            publishHomeProgress(home)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            unsupported = (e as? HomeStreamException)?.code == "unsupported" ||
                e.message?.contains("501") == true
            if (!unsupported) error = (e as? HomeStreamException)?.msg ?: friendlyError(e)
        }
        return HomeOutcome(home = home, unsupported = unsupported, error = error)
    }

    /** Aidoku parity: a source without home (zh.relamanhua ships only
     *  search + dynamic filters) rests on its browse grid, not a dead
     *  "unsupported" note — first listing when there are listings,
     *  otherwise the unfiltered search grid. */
    private fun applyHomeFallback(unsupported: Boolean) {
        if (!unsupported || _state.value.mode != SourceMode.Home) return
        openRestingFallback()
    }

    /** The resting mode for a source with no usable home: first listing,
     *  or the unfiltered browse grid when there are no listings. */
    private fun openRestingFallback() {
        val listings = _state.value.listings
        if (listings.isNotEmpty()) {
            openListing(listings.first())
        } else {
            _state.value = _state.value.copy(
                mode = SourceMode.SearchMode(query = null, filters = currentFilterValues()),
                modeError = null,
            )
            loadMore()
        }
    }

    /** Progressive home rendering: each layout/component/final stream event
     *  lands on screen as it arrives (webui setQueryData parity). */
    private fun publishHomeProgress(layout: HomeLayout?) {
        _state.value = _state.value.copy(loading = false, home = layout)
    }

    /** Terminal {"type":"error"} event of an NDJSON home stream. */
    private class HomeStreamException(val code: String?, val msg: String) : Exception(msg)

    /** Streamed search page: partial manga events land on the grid as they
     *  arrive (webui setQueryData append parity); skipped when the user
     *  navigated away mid-stream. */
    /** Identity check for an in-flight grid request: same mode type and same
     *  request parameters (entries/page may have advanced meanwhile). Stale
     *  commits are dropped — e.g. the search bar was closed back to home while
     *  the search was still loading, and the late response must not yank the
     *  UI back into the search grid. */
    private fun sameRequestMode(a: SourceMode, b: SourceMode): Boolean = when {
        a is SourceMode.SearchMode && b is SourceMode.SearchMode ->
            a.query == b.query && a.filters == b.filters
        a is SourceMode.ListingMode && b is SourceMode.ListingMode ->
            a.listing.id == b.listing.id
        else -> a == b
    }

    private fun publishSearchProgress(mode: SourceMode.SearchMode, entries: List<SearchManga>) {
        if (!sameRequestMode(_state.value.mode, mode)) return
        _state.value = _state.value.copy(
            mode = mode.copy(entries = MangaPage(entries, hasNextPage = true)),
        )
    }

    fun openListing(listing: Listing) {
        _state.value = _state.value.copy(mode = SourceMode.ListingMode(listing), modeError = null)
        loadMore()
    }

    /** Open a listing by id even when the listings probe failed — a home
     *  component "view all" link must never be dead (webui navigates by id). */
    fun openListingRef(id: String, name: String) {
        val found = _state.value.listings.firstOrNull { it.id == id }
        openListing(found ?: Listing(id = id, name = name, kind = Listing.Kind.DEFAULT))
    }

    fun search(query: String) {
        _state.value = _state.value.copy(
            mode = SourceMode.SearchMode(
                // Blank box = unfiltered browse grid (webui runSearch parity:
                // the request omits an empty query, never sends query="").
                query = query.trim().ifEmpty { null },
                filters = currentFilterValues(),
            ),
            modeError = null,
        )
        loadMore()
    }

    fun backToHome() {
        val s = _state.value
        // The source declares no home (or home already 501'd): the resting
        // state is the grid/first listing — routing through mode=Home first
        // would flash the home tab and a doomed fetch (Aidoku: the browse
        // grid simply stays).
        if (s.source?.hasHome == false || s.home501) {
            openRestingFallback()
            return
        }
        _state.value = _state.value.copy(mode = SourceMode.Home, modeError = null)
        // Deep-linked landings never fetched home — load it the first time
        // the user actually opens the home tab (Aidoku lazy parity).
        if (s.home == null && !s.home501 && s.homeError == null) {
            viewModelScope.launch {
                val profile = s.profile ?: repository.activeProfile.first() ?: return@launch
                val outcome = streamHome(profile)
                _state.value = _state.value.copy(
                    home = outcome.home,
                    home501 = outcome.unsupported,
                    homeError = outcome.error,
                )
                applyHomeFallback(outcome.unsupported)
            }
        }
    }

    /** Edit one filter value; null removes it from the draft. */
    fun setFilter(id: String, value: FilterValue?) {
        val next = _state.value.activeFilters.toMutableMap()
        if (value == null) next.remove(id) else next[id] = value
        _state.value = _state.value.copy(activeFilters = next)
    }

    fun clearFilters() {
        _state.value = _state.value.copy(activeFilters = emptyMap())
        persistFilterValues(emptyList())
    }

    fun currentFilterValues(): List<FilterValue> {
        val order = _state.value.filters.map { it.id }
        return _state.value.activeFilters.values.sortedBy { v ->
            order.indexOf(filterValueId(v)).let { if (it < 0) Int.MAX_VALUE else it }
        }
    }

    /** Submit the draft: a non-empty query keeps the search mode (filters ride
     *  along, unless the source hides them while searching); otherwise the
     *  filtered grid opens. Mirrors webui runSearch. */
    fun applyFilters() {
        val values = currentFilterValues()
        persistFilterValues(values)
        val mode = _state.value.mode
        val query = (mode as? SourceMode.SearchMode)?.query?.takeIf { it.isNotBlank() }
        _state.value = _state.value.copy(
            mode = SourceMode.SearchMode(query = query, filters = values),
            modeError = null,
        )
        if (query == null && values.isEmpty()) {
            backToHome()
            return
        }
        loadMore()
    }

    /** Home "filters" component (M5B): open the filtered list for one entry.
     *  Mirrors Aidoku HomeFiltersView.openFilteredPage — the pre-built
     *  values go straight into a source search with no query. */
    fun openFilteredSearch(values: List<FilterValue>) {
        val active = values.mapNotNull { value -> filterValueId(value)?.let { id -> id to value } }.toMap()
        _state.value = _state.value.copy(
            activeFilters = active,
            mode = SourceMode.SearchMode(query = null, filters = values),
            modeError = null,
        )
        loadMore()
    }

    /** One home filter entry: its explicit values, else the source's genre
     *  filter matching the title, else a plain title search. */
    fun openFilterItem(item: HomeFilterItem) {
        item.propertyValues?.let { values ->
            openFilteredSearch(values)
            return
        }
        viewModelScope.launch {
            val value = genreFilterValue(ensureFiltersLoaded(), item.title)
            if (value != null) openFilteredSearch(listOf(value)) else search(item.title)
        }
    }

    // ---- Saved filter groups (Aidoku saved searches) ----

    /** Returns false on an empty or duplicate name (webui refuses both). */
    fun saveFilterGroup(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val groups = _state.value.savedGroups
        if (groups.any { it.name == trimmed }) return false
        val query = (_state.value.mode as? SourceMode.SearchMode)?.query?.trim() ?: ""
        val next = groups + SavedFilterGroup(trimmed, query, currentFilterValues())
        _state.value = _state.value.copy(savedGroups = next)
        viewModelScope.launch { runCatching { filterPrefs.saveSavedGroups(sourceId, next) } }
        return true
    }

    fun deleteFilterGroup(name: String) {
        val next = _state.value.savedGroups.filterNot { it.name == name }
        _state.value = _state.value.copy(savedGroups = next)
        viewModelScope.launch { runCatching { filterPrefs.saveSavedGroups(sourceId, next) } }
    }

    /** Applying a saved group refills the draft and submits at once. The
     *  caller refills the query box from the returned query. */
    fun applyFilterGroup(group: SavedFilterGroup): String {
        val active = group.filters.mapNotNull { v -> filterValueId(v)?.let { it to v } }.toMap()
        _state.value = _state.value.copy(activeFilters = active)
        if (group.query.isNotBlank()) search(group.query) else applyFilters()
        return group.query
    }

    /** The home refresh pre-loads the filter schema; refetch once when it
     *  came back empty so genre filters still resolve. */
    private suspend fun ensureFiltersLoaded(): List<SourceFilter> {
        val loaded = _state.value.filters
        if (loaded.isNotEmpty()) return loaded
        val profile = _state.value.profile ?: return emptyList()
        val filters = runCatching {
            clientFactory.create<SourcesApi>(profile).getSourceFilters(sourceId).await()
        }.getOrDefault(emptyList())
        if (filters.isNotEmpty()) _state.value = _state.value.copy(filters = filters)
        return filters
    }

    /** Draft seeded from the filter defaults (webui initFilterStates). */
    private fun defaultFilterValues(filters: List<SourceFilter>): MutableMap<String, FilterValue> {
        val out = mutableMapOf<String, FilterValue>()
        for (f in filters) {
            when (f.type) {
                "sort" -> {
                    val def = f.default?.let { d ->
                        runCatching { d.jsonObject }.getOrNull()
                    }
                    val index = def?.get("index")?.jsonPrimitive?.intOrNull
                    if (index != null) {
                        val ascending = def["ascending"]?.jsonPrimitive?.booleanOrNull ?: true
                        out[f.id] = FilterValue(sort = FilterValueOneOf1Sort(f.id, index, ascending))
                    }
                }
                "check" -> {
                    if (f.default?.jsonPrimitive?.booleanOrNull == true) {
                        out[f.id] = FilterValue(check = FilterValueOneOf2Check(f.id, 1))
                    }
                }
                "select" -> {
                    val def = f.default?.jsonPrimitive?.contentOrNull
                    if (!def.isNullOrEmpty()) {
                        out[f.id] = FilterValue(select = FilterValueOneOfText(f.id, def))
                    }
                }
                "multi-select" -> {
                    val included = f.defaultIncluded ?: emptyList()
                    val excluded = f.defaultExcluded ?: emptyList()
                    if (included.isNotEmpty() || excluded.isNotEmpty()) {
                        out[f.id] = FilterValue(
                            multiSelect = FilterValueOneOf4MultiSelect(f.id, included, excluded),
                        )
                    }
                }
            }
        }
        return out
    }

    /** First genre select/multi-select filter whose options contain the
     *  title — webui genreFilterValue parity (exact, case-sensitive). */
    private fun genreFilterValue(filters: List<SourceFilter>, title: String): FilterValue? {
        for (filter in filters) {
            if (filter.type != "select" && filter.type != "multi-select") continue
            if (filter.isGenre != true) continue
            val options = filter.options ?: continue
            val index = options.indexOf(title)
            if (index < 0) continue
            val value = filter.ids?.getOrNull(index) ?: options[index]
            if (value.isEmpty()) continue
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

    /** The filter id of any FilterValue union arm (wire model is tagged). */
    private fun filterValueId(value: FilterValue): String? =
        value.text?.id ?: value.sort?.id ?: value.check?.id
            ?: value.select?.id ?: value.multiSelect?.id ?: value.range?.id

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore) return
        val profile = current.profile ?: return
        val mode = current.mode
        val page = when (mode) {
            is SourceMode.Home -> return
            is SourceMode.ListingMode -> if (mode.entries != null && !mode.entries.hasNextPage) return else mode.page + (if (mode.entries == null) 0 else 1)
            is SourceMode.SearchMode -> if (mode.entries != null && !mode.entries.hasNextPage) return else mode.page + (if (mode.entries == null) 0 else 1)
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, modeError = null)
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                val result = when (mode) {
                    is SourceMode.ListingMode -> api.getSourceListing(sourceId, mode.listing.id, page).await()
                    is SourceMode.SearchMode -> {
                        val query = mode.query?.trim()?.takeIf { it.isNotEmpty() }
                        // hidesFiltersWhileSearching: a non-empty query drops the
                        // filters entirely (AidokuRunner clears them first).
                        val hideFilters = query != null &&
                            _state.value.source?.config?.get("hidesFiltersWhileSearching") == true
                        val filters = if (hideFilters) null else mode.filters.ifEmpty { null }
                        // NDJSON (webui useSourceSearch parity): partial manga
                        // events append one by one so a slow source fills the
                        // grid progressively; the terminal payload is the
                        // authoritative page.
                        val body = buildJsonObject {
                            query?.let { put("query", it) }
                            put("page", page)
                            filters?.let {
                                put(
                                    "filters",
                                    clientFactory.json.encodeToJsonElement(
                                        ListSerializer(FilterValue.serializer()), it,
                                    ),
                                )
                            }
                        }
                        val baseEntries = mode.entries?.propertyEntries ?: emptyList()
                        val streamed = mutableListOf<SearchManga>()
                        var result: MangaPage? = null
                        try {
                            clientFactory.ndjsonCall(profile, "/sources/$sourceId/search", body)
                                .collect { event ->
                                    when (event["type"]?.jsonPrimitive?.contentOrNull) {
                                        "manga" -> {
                                            val manga = event["manga"]?.let { raw ->
                                                runCatching {
                                                    clientFactory.json.decodeFromJsonElement<SearchManga>(raw)
                                                }.getOrNull()
                                            }
                                            if (manga != null) {
                                                streamed += manga
                                                publishSearchProgress(mode, baseEntries + streamed)
                                            }
                                        }
                                        "final" -> result = event["payload"]?.let {
                                            clientFactory.json.decodeFromJsonElement<MangaPage>(it)
                                        }
                                        "error" -> {
                                            val err = event["error"]?.jsonObject
                                            throw IOException(
                                                err?.get("msg")?.jsonPrimitive?.contentOrNull
                                                    ?: appContext.getString(R.string.source_search_failed),
                                            )
                                        }
                                        // Buffered fallback (older server without
                                        // streaming): the bare page body has no type.
                                        else -> if (event.containsKey("entries")) {
                                            result = clientFactory.json.decodeFromJsonElement<MangaPage>(event)
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            // A mid-stream failure after the final event is harmless.
                            if (result == null) throw e
                        }
                        result ?: throw IOException(
                            appContext.getString(R.string.source_search_empty_response),
                        )
                    }
                    SourceMode.Home -> return@launch
                }
                val merged = when (mode) {
                    is SourceMode.ListingMode -> mode.copy(
                        entries = result.copy(
                            propertyEntries = (mode.entries?.propertyEntries ?: emptyList()) + result.propertyEntries,
                        ),
                        page = page,
                    )
                    is SourceMode.SearchMode -> mode.copy(
                        entries = result.copy(
                            propertyEntries = (mode.entries?.propertyEntries ?: emptyList()) + result.propertyEntries,
                        ),
                        page = page,
                    )
                    SourceMode.Home -> mode
                }
                if (sameRequestMode(_state.value.mode, mode)) {
                    _state.value = _state.value.copy(loadingMore = false, mode = merged)
                } else {
                    // Stale response: the user navigated away while this page
                    // was loading (e.g. closed the search bar back to home).
                    // Dropping the commit keeps the current mode; kicking a
                    // fresh load covers the mode that replaced this request.
                    _state.value = _state.value.copy(loadingMore = false)
                    loadMore()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (sameRequestMode(_state.value.mode, mode)) {
                    _state.value = _state.value.copy(loadingMore = false, modeError = friendlyError(e))
                } else {
                    _state.value = _state.value.copy(loadingMore = false)
                    loadMore()
                }
            }
        }
    }

    private fun persistFilterValues(values: List<FilterValue>) {
        viewModelScope.launch { runCatching { filterPrefs.saveValues(sourceId, values) } }
    }

    private fun friendlyError(e: Throwable): String =
        (e as? HttpException)?.let { "HTTP ${it.code()}" } ?: (e.message ?: e.toString())

    /** Source images go through the server proxy (7-day cache + ETag). */
    fun proxiedImage(url: String): String {
        val base = _state.value.profile?.baseUrl?.trimEnd('/') ?: return url
        return if (url.startsWith("http")) {
            "$base/api/v1/sources/$sourceId/image?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        } else {
            base.trimEnd('/') + url
        }
    }
}

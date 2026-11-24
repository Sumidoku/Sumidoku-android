package org.sumidoku.android.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.GlobalSearchRequest
import org.sumidoku.android.core.api.model.GlobalSearchResult
import org.sumidoku.android.core.api.model.MangaInput
import org.sumidoku.android.core.api.model.RegisterMangaRequest
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.data.GlobalSearchFilterStore
import org.sumidoku.android.core.data.GlobalSearchFilters
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import retrofit2.await
import javax.inject.Inject

/** Per-source search section: aggregated results carry per-source
 *  pagination and per-source errors — one failing source never takes
 *  the whole search down (server fan-out semantics). */
data class SourceSearchSection(
    val sourceId: Long,
    val sourceName: String,
    val entries: List<SearchManga>,
    val hasNextPage: Boolean,
    val page: Int,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Tri-state filter entry, matching webui's `TriState` (-1 exclude / 1 include). */
enum class TriFilter { NONE, INCLUDE, EXCLUDE }

fun TriFilter.next(): TriFilter = when (this) {
    TriFilter.NONE -> TriFilter.INCLUDE
    TriFilter.INCLUDE -> TriFilter.EXCLUDE
    TriFilter.EXCLUDE -> TriFilter.NONE
}

/** Wire form shared with webui globalSearchPrefs: 1 include / -1 exclude. */
fun TriFilter.wire(): Int = when (this) {
    TriFilter.INCLUDE -> GlobalSearchFilters.INCLUDE
    TriFilter.EXCLUDE -> GlobalSearchFilters.EXCLUDE
    TriFilter.NONE -> 0
}

/** Unknown values (legacy/hand-edited storage) map to "no verdict". */
private fun Int.toTriFilter(): TriFilter? = when (this) {
    GlobalSearchFilters.INCLUDE -> TriFilter.INCLUDE
    GlobalSearchFilters.EXCLUDE -> TriFilter.EXCLUDE
    else -> null
}

/** Per-dimension verdict (webui triAllows): with any include selected the
 *  entry must hit one of them; hitting any exclude knocks it out. */
fun triAllows(states: Map<*, TriFilter>, keys: List<Any>): Boolean {
    var includes = 0
    var includeHit = false
    for ((k, v) in states) {
        if (v == TriFilter.INCLUDE) {
            includes += 1
            if (keys.contains(k)) includeHit = true
        } else if (v == TriFilter.EXCLUDE && keys.contains(k)) {
            return false
        }
    }
    return includes == 0 || includeHit
}

data class SearchUiState(
    val profile: ServerProfile? = null,
    val sources: List<Source> = emptyList(),
    /** Tri-state filter groups (webui globalSearchPrefs): ratings / languages / sources. */
    val ratingFilter: Map<Long, TriFilter> = emptyMap(),
    val langFilter: Map<String, TriFilter> = emptyMap(),
    val sourceFilter: Map<Long, TriFilter> = emptyMap(),
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val sections: List<SourceSearchSection> = emptyList(),
    val registeringKey: String? = null,
    val error: String? = null,
) {
    /** Sources passing all three filter groups — these take part in the search. */
    val participatingSources: List<Source>
        get() = sources.filter { s ->
            triAllows(ratingFilter, listOf(s.contentRating)) &&
                triAllows(langFilter, s.languages.map { it.trim().lowercase() }) &&
                triAllows(sourceFilter, listOfNotNull(s.id))
        }

    val participatingSourceIds: List<Long>
        get() = participatingSources.mapNotNull { it.id }

    /** webui SearchPanel `visible`: a source with no entries and no error
     *  takes no section at all (other sources are still streaming in, so
     *  empty shells would flood the screen). */
    val visibleSections: List<SourceSearchSection>
        get() = sections.filter { it.entries.isNotEmpty() || it.error != null }

    /** Language options: the multi-language sentinel first (Aidoku convention),
     *  then a stable code order. The screen sorts the options by their
     *  localized display name, so ordering stays alphabetical per locale. */
    val langOptions: List<String>
        get() {
            val set = linkedSetOf<String>()
            for (s in sources) {
                for (l in s.languages) {
                    val code = l.trim().lowercase()
                    if (code.isNotEmpty() && code != "multi" && code != "all") set.add(code)
                }
            }
            return listOf("multi") + set.sorted()
        }

    val activeFilterCount: Int
        get() = ratingFilter.size + langFilter.size + sourceFilter.size
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val filterStore: GlobalSearchFilterStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val streamJson = Json { ignoreUnknownKeys = true }

    /** Set by any tri-state cycle/reset, consumed when the filter sheet
     *  closes (webui refetches the search when the sourceIds key changes). */
    private var filtersDirty = false

    /** Only the newest search may append sections: a filter change can
     *  start a second search while the previous stream is still open. */
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            restoreFilters()
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(profile = profile)
            runCatching {
                clientFactory.create<SourcesApi>(profile).listSources().await()
            }.onSuccess { sources ->
                _state.value = _state.value.copy(
                    sources = sources
                        .filter { it.enabled }
                        .sortedWith(compareBy({ it.name ?: it.key }, { it.key })),
                )
            }
        }
    }

    /** Restore the persisted tri-state filters before anything else so the
     *  first search already honours them (webui loadGlobalSearchFilters). */
    private suspend fun restoreFilters() {
        val saved = runCatching { filterStore.load() }.getOrDefault(GlobalSearchFilters())
        val ratings = saved.ratings.longKeyed()
        val langs = saved.langs.mapNotNull { (code, state) ->
            state.toTriFilter()?.let { code to it }
        }.toMap()
        val sources = saved.sources.longKeyed()
        if (ratings.isEmpty() && langs.isEmpty() && sources.isEmpty()) return
        _state.value = _state.value.copy(
            ratingFilter = ratings,
            langFilter = langs,
            sourceFilter = sources,
        )
    }

    private fun Map<String, Int>.longKeyed(): Map<Long, TriFilter> =
        mapNotNull { (raw, state) -> raw.toLongOrNull()?.let { key -> state.toTriFilter()?.let { key to it } } }
            .toMap()

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun cycleRating(level: Long) {
        updateFilters { it.copy(ratingFilter = it.ratingFilter.cycle(level)) }
    }

    fun cycleLanguage(code: String) {
        updateFilters { it.copy(langFilter = it.langFilter.cycle(code)) }
    }

    fun cycleSource(id: Long) {
        updateFilters { it.copy(sourceFilter = it.sourceFilter.cycle(id)) }
    }

    fun resetRatings() {
        updateFilters { it.copy(ratingFilter = emptyMap()) }
    }

    fun resetLanguages() {
        updateFilters { it.copy(langFilter = emptyMap()) }
    }

    fun resetSources() {
        updateFilters { it.copy(sourceFilter = emptyMap()) }
    }

    /** Every filter mutation is persisted immediately (webui useEffect →
     *  saveGlobalSearchFilters), regardless of whether the sheet is closed. */
    private fun updateFilters(transform: (SearchUiState) -> SearchUiState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        filtersDirty = true
        persistFilters()
    }

    private fun persistFilters() {
        val current = _state.value
        val snapshot = GlobalSearchFilters(
            ratings = current.ratingFilter.wireForm(),
            langs = current.langFilter.filterValues { it != TriFilter.NONE }
                .mapValues { (_, value) -> value.wire() },
            sources = current.sourceFilter.wireForm(),
        )
        viewModelScope.launch { runCatching { filterStore.save(snapshot) } }
    }

    private fun Map<Long, TriFilter>.wireForm(): Map<String, Int> =
        filterValues { it != TriFilter.NONE }
            .map { (key, value) -> key.toString() to value.wire() }
            .toMap()

    /** Filter sheet closed: a pending change with a live query re-runs the
     *  search against the new participating-source set. */
    fun commitFilterChanges() {
        if (!filtersDirty) return
        filtersDirty = false
        val current = _state.value
        if (current.searched && current.query.isNotBlank() && current.participatingSourceIds.isNotEmpty()) {
            search()
        }
    }

    private fun <K> Map<K, TriFilter>.cycle(key: K): Map<K, TriFilter> {
        val next = (this[key] ?: TriFilter.NONE).next()
        return if (next == TriFilter.NONE) this - key else this + (key to next)
    }

    fun search() {
        val profile = _state.value.profile ?: return
        val query = _state.value.query.trim()
        val ids = _state.value.participatingSourceIds
        if (query.isEmpty() || ids.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, searched = true, sections = emptyList(), error = null)
            val names = _state.value.sources.associate { it.id to (it.name ?: appContext.getString(R.string.search_source_fallback, it.id)) }
            try {
                // NDJSON opt-in (design §14): sources appear as each one
                // finishes instead of waiting for the slowest fan-out leg.
                var received = false
                clientFactory.ndjsonCall(
                    profile,
                    "/search",
                    kotlinx.serialization.json.buildJsonObject {
                        put("query", query)
                        put("page", 1)
                        putJsonArray("sourceIds") { ids.forEach { add(it) } }
                    },
                ).collect { event ->
                    val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return@collect
                    if (type == "source" || type == "sourceError") received = true
                    when (type) {
                        "source" -> {
                            val sourceId = event["sourceId"]!!.jsonPrimitive.long
                            val entriesEl = event["entries"] ?: event["results"]
                            val entries = entriesEl?.let {
                                runCatching { streamJson.decodeFromJsonElement<List<SearchManga>>(it) }.getOrNull()
                            } ?: emptyList()
                            val hasNext = event["hasNextPage"]?.jsonPrimitive?.booleanOrNull ?: false
                            _state.value = _state.value.copy(
                                sections = _state.value.sections + SourceSearchSection(
                                    sourceId = sourceId,
                                    sourceName = names[sourceId] ?: appContext.getString(R.string.search_source_fallback, sourceId),
                                    entries = entries,
                                    hasNextPage = hasNext,
                                    page = 1,
                                ),
                            )
                        }
                        "sourceError" -> {
                            val sourceId = event["sourceId"]!!.jsonPrimitive.long
                            val message = (event["error"] as? kotlinx.serialization.json.JsonObject)
                                ?.get("message")?.jsonPrimitive?.contentOrNull
                                ?: event["message"]?.jsonPrimitive?.contentOrNull
                                ?: appContext.getString(R.string.search_failed)
                            _state.value = _state.value.copy(
                                sections = _state.value.sections + SourceSearchSection(
                                    sourceId = sourceId,
                                    sourceName = names[sourceId] ?: appContext.getString(R.string.search_source_fallback, sourceId),
                                    entries = emptyList(),
                                    hasNextPage = false,
                                    page = 1,
                                    error = message,
                                ),
                            )
                        }
                        "final" -> _state.value = _state.value.copy(searching = false)
                    }
                }
                if (!received) {
                    // Server answered without streaming — fall back to the
                    // plain aggregated response.
                    val response = clientFactory.create<SourcesApi>(profile)
                        .searchAllSources(GlobalSearchRequest(sourceIds = ids.toList(), query = query, page = 1))
                        .await()
                    _state.value = _state.value.copy(
                        searching = false,
                            sections = response.sources.map { it.toSection(names[it.sourceId] ?: appContext.getString(R.string.search_source_fallback, it.sourceId)) },
                    )
                } else {
                    _state.value = _state.value.copy(searching = false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Streaming failed mid-way: if nothing arrived yet, retry
                // through the plain endpoint before surfacing the error.
                if (_state.value.sections.isEmpty()) {
                    try {
                        val response = clientFactory.create<SourcesApi>(profile)
                            .searchAllSources(GlobalSearchRequest(sourceIds = ids.toList(), query = query, page = 1))
                            .await()
                        _state.value = _state.value.copy(
                            searching = false,
                                sections = response.sources.map { it.toSection(names[it.sourceId] ?: appContext.getString(R.string.search_source_fallback, it.sourceId)) },
                        )
                        return@launch
                    } catch (e2: Exception) {
                        if (e2 is kotlinx.coroutines.CancellationException) throw e2
                    }
                }
                _state.value = _state.value.copy(searching = false, error = e.message ?: e.toString())
            }
        }
    }

    fun loadMore(sourceId: Long) {
        val profile = _state.value.profile ?: return
        val section = _state.value.sections.firstOrNull { it.sourceId == sourceId } ?: return
        if (section.loading || !section.hasNextPage) return
        viewModelScope.launch {
            updateSection(sourceId) { it.copy(loading = true, error = null) }
            try {
                val response = clientFactory.create<SourcesApi>(profile)
                    .searchAllSources(
                        GlobalSearchRequest(
                            sourceIds = listOf(sourceId),
                            query = _state.value.query.trim(),
                            page = section.page + 1,
                        ),
                    ).await()
                val result = response.sources.firstOrNull()
                updateSection(sourceId) { current ->
                    if (result == null) current.copy(loading = false)
                    else current.copy(
                        entries = current.entries + result.propertyEntries,
                        hasNextPage = result.hasNextPage,
                        page = result.let { current.page + 1 },
                        loading = false,
                        error = result.error?.message,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateSection(sourceId) { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private fun updateSection(sourceId: Long, transform: (SourceSearchSection) -> SourceSearchSection) {
        _state.value = _state.value.copy(
            sections = _state.value.sections.map { if (it.sourceId == sourceId) transform(it) else it },
        )
    }

    /** Open a result: registered entries navigate directly; source-only
     *  entries register first (webui useRegisterManga semantics) so the
     *  detail page can work with a local manga id. */
    fun openResult(manga: SearchManga, onOpenManga: (Long) -> Unit) {
        val localId = manga.id
        if (localId != null) {
            onOpenManga(localId)
            return
        }
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(registeringKey = manga.key)
            try {
                val input = MangaInput(
                    key = manga.key,
                    title = manga.title,
                    status = manga.status,
                    contentRating = manga.contentRating,
                    viewer = manga.viewer,
                    updateStrategy = manga.updateStrategy,
                    cover = manga.cover,
                    artists = manga.artists,
                    authors = manga.authors,
                    description = manga.description,
                    url = manga.url,
                    tags = manga.tags,
                    nextUpdateTime = manga.nextUpdateTime,
                    chapters = manga.chapters?.map { chapter ->
                        org.sumidoku.android.core.api.model.ChapterInput(
                            key = chapter.key,
                            locked = false,
                            title = chapter.title,
                            chapterNumber = chapter.chapterNumber,
                            volumeNumber = chapter.volumeNumber,
                            dateUploaded = chapter.dateUploaded,
                            url = chapter.url,
                            language = chapter.language,
                        )
                    },
                )
                val id = clientFactory.create<MangaApi>(profile)
                    .registerManga(RegisterMangaRequest(sourceId = manga.sourceId, mangaKey = manga.key, manga = input))
                    .await()
                onOpenManga(id.id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message ?: e.toString())
            } finally {
                _state.value = _state.value.copy(registeringKey = null)
            }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}

private fun GlobalSearchResult.toSection(sourceName: String) = SourceSearchSection(
    sourceId = sourceId,
    sourceName = sourceName,
    entries = propertyEntries,
    hasNextPage = hasNextPage,
    page = 1,
    error = error?.message,
)

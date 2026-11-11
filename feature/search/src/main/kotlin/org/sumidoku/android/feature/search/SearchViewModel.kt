package org.sumidoku.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class SearchUiState(
    val profile: ServerProfile? = null,
    val sources: List<Source> = emptyList(),
    /** Sources participating in the next search; default = all enabled. */
    val selectedSourceIds: Set<Long> = emptySet(),
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val sections: List<SourceSearchSection> = emptyList(),
    val registeringKey: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val streamJson = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(profile = profile)
            runCatching {
                clientFactory.create<SourcesApi>(profile).listSources().await()
            }.onSuccess { sources ->
                val enabled = sources.filter { it.enabled }
                _state.value = _state.value.copy(
                    sources = enabled,
                    selectedSourceIds = enabled.mapNotNull { it.id }.toSet(),
                )
            }
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun toggleSource(id: Long) {
        val current = _state.value.selectedSourceIds.toMutableSet()
        if (!current.remove(id)) current.add(id)
        _state.value = _state.value.copy(selectedSourceIds = current)
    }

    fun search() {
        val profile = _state.value.profile ?: return
        val query = _state.value.query.trim()
        val ids = _state.value.selectedSourceIds
        if (query.isEmpty() || ids.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, searched = true, sections = emptyList(), error = null)
            val names = _state.value.sources.associate { it.id to (it.name ?: "图源 ${it.id}") }
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
                                    sourceName = names[sourceId] ?: "图源 $sourceId",
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
                                ?: "搜索失败"
                            _state.value = _state.value.copy(
                                sections = _state.value.sections + SourceSearchSection(
                                    sourceId = sourceId,
                                    sourceName = names[sourceId] ?: "图源 $sourceId",
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
                        sections = response.sources.map { it.toSection(names[it.sourceId] ?: "图源 ${it.sourceId}") },
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
                            sections = response.sources.map { it.toSection(names[it.sourceId] ?: "图源 ${it.sourceId}") },
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

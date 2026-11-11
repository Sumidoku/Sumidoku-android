package org.sumidoku.android.feature.source

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.HomeLayout
import org.sumidoku.android.core.api.model.Listing
import org.sumidoku.android.core.api.model.MangaPage
import org.sumidoku.android.core.api.model.RegisterMangaRequest
import org.sumidoku.android.core.api.model.SearchRequest
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceFilter
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import java.net.URLEncoder
import javax.inject.Inject

/** Modes of the source screen: home layout, one listing's paged grid, or
 *  a search result grid — mirrors webui SourceHomePage/SourceBrowsePage. */
sealed interface SourceMode {
    data object Home : SourceMode
    data class ListingMode(val listing: Listing, val entries: MangaPage? = null, val page: Int = 1) : SourceMode
    data class SearchMode(val query: String, val entries: MangaPage? = null, val page: Int = 1) : SourceMode
    /** Filter panel results (M1): a source search whose query is empty
     *  and whose filters carry the chosen values — mirrors the webui
     *  filters sheet on SourceBrowsePage. */
    data class FilteredMode(
        val filters: List<FilterValue>,
        val entries: MangaPage? = null,
        val page: Int = 1,
    ) : SourceMode
}

data class SourceHomeUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val source: Source? = null,
    val listings: List<Listing> = emptyList(),
    val home: HomeLayout? = null,
    val home501: Boolean = false,
    val mode: SourceMode = SourceMode.Home,
    val loadingMore: Boolean = false,
    /** Dynamic filter schema (GET /sources/{id}/filters); empty when
     *  the source has none (501). */
    val filters: List<SourceFilter> = emptyList(),
    /** Edited filter values keyed by filter id; null value = unset. */
    val activeFilters: Map<String, FilterValue> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class SourceHomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    val sourceId: Long = checkNotNull(savedStateHandle["sourceId"])

    private val _state = MutableStateFlow(SourceHomeUiState())
    val state: StateFlow<SourceHomeUiState> = _state

    /** One-shot navigation: server manga id resolved via registerManga. */
    private val _openManga = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val openManga: SharedFlow<Long> = _openManga

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = SourceHomeUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                val source = api.listSources().await().firstOrNull { it.id == sourceId }
                val listings = runCatching { api.getSourceListings(sourceId).await() }.getOrDefault(emptyList())
                val home = runCatching { api.getSourceHome(sourceId).await() }.getOrNull()
                val filters = runCatching { api.getSourceFilters(sourceId).await() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    source = source,
                    listings = listings,
                    home = home,
                    home501 = home == null,
                    mode = SourceMode.Home,
                    filters = filters,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun openListing(listing: Listing) {
        _state.value = _state.value.copy(mode = SourceMode.ListingMode(listing))
        loadMore()
    }

    fun search(query: String) {
        _state.value = _state.value.copy(mode = SourceMode.SearchMode(query))
        loadMore()
    }

    fun backToHome() {
        _state.value = _state.value.copy(mode = SourceMode.Home)
    }

    /** Edit one filter value; null removes it from the active set. */
    fun setFilter(id: String, value: FilterValue?) {
        val next = _state.value.activeFilters.toMutableMap()
        if (value == null) next.remove(id) else next[id] = value
        _state.value = _state.value.copy(activeFilters = next)
    }

    fun clearFilters() {
        _state.value = _state.value.copy(activeFilters = emptyMap())
    }

    /** Run the source search with the edited filters (M1 filter panel). */
    fun applyFilters() {
        val values = _state.value.activeFilters.values.toList()
        if (values.isEmpty()) {
            backToHome()
            return
        }
        _state.value = _state.value.copy(mode = SourceMode.FilteredMode(values))
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore) return
        val profile = current.profile ?: return
        val mode = current.mode
        val page = when (mode) {
            is SourceMode.Home -> return
            is SourceMode.ListingMode -> if (mode.entries != null && !mode.entries.hasNextPage) return else mode.page + (if (mode.entries == null) 0 else 1)
            is SourceMode.SearchMode -> if (mode.entries != null && !mode.entries.hasNextPage) return else mode.page + (if (mode.entries == null) 0 else 1)
            is SourceMode.FilteredMode -> if (mode.entries != null && !mode.entries.hasNextPage) return else mode.page + (if (mode.entries == null) 0 else 1)
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                val result = when (mode) {
                    is SourceMode.ListingMode -> api.getSourceListing(sourceId, mode.listing.id, page).await()
                    is SourceMode.SearchMode -> api.searchSource(sourceId, SearchRequest(query = mode.query, page = page)).await()
                    is SourceMode.FilteredMode -> api.searchSource(
                        sourceId,
                        SearchRequest(query = "", page = page, filters = mode.filters),
                    ).await()
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
                    is SourceMode.FilteredMode -> mode.copy(
                        entries = result.copy(
                            propertyEntries = (mode.entries?.propertyEntries ?: emptyList()) + result.propertyEntries,
                        ),
                        page = page,
                    )
                    SourceMode.Home -> mode
                }
                _state.value = _state.value.copy(loadingMore = false, mode = merged)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false, error = e.message)
            }
        }
    }

    /** Resolve a source-space manga key to a server manga id, then navigate. */
    fun openMangaKey(mangaKey: String) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val id = api.registerManga(RegisterMangaRequest(sourceId = sourceId, mangaKey = mangaKey)).await().id
                _openManga.emit(id)
            } catch (_: Exception) {
            }
        }
    }

    /** Source images go through the server proxy (7-day cache + ETag). */
    fun proxiedImage(url: String): String {
        val base = _state.value.profile?.baseUrl?.trimEnd('/') ?: return url
        return if (url.startsWith("http")) {
            "$base/api/v1/sources/$sourceId/image?url=${URLEncoder.encode(url, "UTF-8")}"
        } else {
            base.trimEnd('/') + url
        }
    }
}

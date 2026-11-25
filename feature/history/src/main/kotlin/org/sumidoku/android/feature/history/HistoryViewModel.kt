package org.sumidoku.android.feature.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.model.HistoryEntry
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.CatalogCache
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.ServerReachability
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** History tab: GET /api/v1/history paged feed — M4 scope. */
data class HistoryUiState(
    val profile: ServerProfile? = null,
    val entries: List<HistoryEntry> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val catalogCache: CatalogCache,
    reachability: ServerReachability,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state

    init {
        refresh()
        // Back online → silently re-fetch; a failed refresh keeps the cached
        // content on screen, so this is safe to fire-and-forget.
        viewModelScope.launchGuarded {
            reachability.offline.drop(1).filter { offline -> !offline }.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = HistoryUiState(
                    loading = false,
                    error = appContext.getString(R.string.history_no_server),
                )
                return@launchGuarded
            }
            if (_state.value.entries.isEmpty()) {
                // Cold start: paint the cached page first so an offline launch is
                // never a spinner; the response below replaces it.
                val cached = runCatching {
                    catalogCache.seed(profile.id, HISTORY_LIST_KEY, HistoryEntry.serializer())
                }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(profile = profile, loading = false, entries = cached)
                }
            }
            _state.value = _state.value.copy(profile = profile, loading = _state.value.entries.isEmpty(), error = null)
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val page = api.getReadingHistory(page = 1).await()
                _state.value = _state.value.copy(
                    loading = false,
                    entries = page.propertyEntries,
                    page = 1,
                    hasNextPage = page.hasNextPage,
                )
                // Only a successful response is written; an empty page is the truth
                // and clears the slot, so stale rows can never be seeded.
                runCatching {
                    catalogCache.save(profile.id, HISTORY_LIST_KEY, page.propertyEntries, HistoryEntry.serializer())
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    // A failed refresh must not blank cached content; the global
                    // offline banner carries the connectivity signal.
                    error = if (_state.value.entries.isEmpty()) e.message ?: e.toString() else null,
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasNextPage) return
        viewModelScope.launchGuarded {
            val profile = current.profile ?: return@launchGuarded
            _state.value = current.copy(loadingMore = true)
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val next = api.getReadingHistory(page = current.page + 1).await()
                _state.value = _state.value.copy(
                    loadingMore = false,
                    entries = current.entries + next.propertyEntries,
                    page = current.page + 1,
                    hasNextPage = next.hasNextPage,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false, error = e.message)
            }
        }
    }

    private companion object {
        /** Single-slot cache key: the history feed has no filters. */
        const val HISTORY_LIST_KEY = "history"
    }
}

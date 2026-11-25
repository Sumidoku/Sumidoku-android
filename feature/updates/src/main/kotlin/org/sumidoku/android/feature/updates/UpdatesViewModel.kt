package org.sumidoku.android.feature.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.UpdatesApi
import org.sumidoku.android.core.api.model.RecentEntry
import org.sumidoku.android.core.api.model.UpdateSummary
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.CatalogCache
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.ServerReachability
import org.sumidoku.android.core.network.SumidokuClientFactory
import org.sumidoku.android.core.stream.EventsStream
import org.sumidoku.android.core.stream.ServerEvent
import retrofit2.await
import javax.inject.Inject

/** Updates tab: update-run summary + recent-chapters feed.
 *  GET /update/summary and /update/recent, POST /update/fetch — M4 scope.
 *  While a run is active the summary follows the SSE stream
 *  (updateSummary events, snapshot on connect) instead of polling,
 *  mirroring webui UpdatesPage. */
data class UpdatesUiState(
    val profile: ServerProfile? = null,
    val summary: UpdateSummary? = null,
    val entries: List<RecentEntry> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val fetching: Boolean = false,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val eventsStream: EventsStream,
    private val catalogCache: CatalogCache,
    reachability: ServerReachability,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesUiState())
    val state: StateFlow<UpdatesUiState> = _state

    private var refreshJob: Job? = null
    private var streamJob: Job? = null

    init {
        refresh()
        // Back online → silently re-fetch; a failed refresh keeps the cached
        // content on screen, so this is safe to fire-and-forget.
        viewModelScope.launchGuarded {
            reachability.offline.drop(1).filter { offline -> !offline }.collect { refresh() }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        streamJob?.cancel()
        refreshJob = viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = UpdatesUiState(
                    loading = false,
                    error = appContext.getString(R.string.updates_error_no_server),
                )
                return@launchGuarded
            }
            if (_state.value.entries.isEmpty() && _state.value.summary == null) {
                // Cold start: paint the cached feed and summary first so an
                // offline launch is never a spinner; the response below replaces them.
                val cachedEntries = runCatching {
                    catalogCache.seed(profile.id, RECENT_LIST_KEY, RecentEntry.serializer())
                }.getOrDefault(emptyList())
                val cachedSummary = runCatching {
                    catalogCache.seed(profile.id, SUMMARY_LIST_KEY, UpdateSummary.serializer()).firstOrNull()
                }.getOrNull()
                if (cachedEntries.isNotEmpty() || cachedSummary != null) {
                    _state.value = _state.value.copy(
                        profile = profile,
                        loading = false,
                        entries = cachedEntries,
                        summary = cachedSummary,
                    )
                }
            }
            _state.value = _state.value.copy(
                profile = profile,
                loading = _state.value.entries.isEmpty() && _state.value.summary == null,
                error = null,
            )
            val api = clientFactory.create<UpdatesApi>(profile)
            try {
                val recent = api.getRecentUpdates(page = 1).await()
                val summary = api.getUpdateSummary().await()
                _state.value = _state.value.copy(
                    loading = false,
                    entries = recent.propertyEntries,
                    page = 1,
                    hasNextPage = recent.hasNextPage,
                    summary = summary,
                    fetching = summary.running,
                )
                // Only successful responses are written; an empty page is the
                // truth and clears the slot, so stale rows can never be seeded.
                runCatching {
                    catalogCache.save(profile.id, RECENT_LIST_KEY, recent.propertyEntries, RecentEntry.serializer())
                }
                runCatching {
                    catalogCache.save(profile.id, SUMMARY_LIST_KEY, listOf(summary), UpdateSummary.serializer())
                }
                // Fallback polling only while a run is active and the
                // stream is unavailable; SSE events usually preempt this.
                while (_state.value.summary?.running == true && streamJob?.isActive != true) {
                    delay(3000)
                    val next = api.getUpdateSummary().await()
                    _state.value = _state.value.copy(summary = next, fetching = next.running)
                    if (!next.running) break
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(
                    loading = false,
                    // A failed refresh must not blank cached content; the global
                    // offline banner carries the connectivity signal.
                    error = if (_state.value.entries.isEmpty()) e.message ?: e.toString() else null,
                )
            }
        }
        streamJob = viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first() ?: return@launchGuarded
            // Guarded: `events()` builds an OkHttp client up front and throws on a
            // malformed stored base URL; anything escaping this coroutine is
            // uncaught (no scope handler) and would kill the process. Transport
            // failures themselves never throw — the stream reconnects with backoff.
            try {
                eventsStream.events(profile).collect { event ->
                    when (event) {
                        is ServerEvent.Snapshot -> event.updateSummary?.let { summary ->
                            _state.value = _state.value.copy(summary = summary, fetching = summary.running)
                            cacheSummary(profile.id, summary)
                        }
                        is ServerEvent.SummaryChanged -> {
                            _state.value = _state.value.copy(
                                summary = event.summary,
                                fetching = event.summary.running,
                            )
                            cacheSummary(profile.id, event.summary)
                        }
                        is ServerEvent.UpdateComplete -> {
                            _state.value = _state.value.copy(fetching = false)
                            // New chapters may have landed: refresh the feed.
                            refresh()
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(fetching = false)
            }
        }
    }

    /** Stream-pushed summaries are server truth too: keep the cached summary
     *  in step so the next cold start reflects the latest run state. */
    private fun cacheSummary(profileId: Long, summary: UpdateSummary) {
        viewModelScope.launchGuarded {
            runCatching {
                catalogCache.save(profileId, SUMMARY_LIST_KEY, listOf(summary), UpdateSummary.serializer())
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
                val api = clientFactory.create<UpdatesApi>(profile)
                val next = api.getRecentUpdates(page = current.page + 1).await()
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

    fun fetchNow() {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            runCatching { clientFactory.create<UpdatesApi>(profile).fetchLibraryUpdates().await() }
            refresh()
        }
    }

    private companion object {
        const val RECENT_LIST_KEY = "updates:recent"
        /** The single summary object is stored as a one-item list in the
         *  generic row cache. */
        const val SUMMARY_LIST_KEY = "updates:summary"
    }
}

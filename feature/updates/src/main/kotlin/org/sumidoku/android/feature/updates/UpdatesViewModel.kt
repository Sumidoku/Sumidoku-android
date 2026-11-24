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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.UpdatesApi
import org.sumidoku.android.core.api.model.RecentEntry
import org.sumidoku.android.core.api.model.UpdateSummary
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
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
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesUiState())
    val state: StateFlow<UpdatesUiState> = _state

    private var refreshJob: Job? = null
    private var streamJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        streamJob?.cancel()
        refreshJob = viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = UpdatesUiState(
                    loading = false,
                    error = appContext.getString(R.string.updates_error_no_server),
                )
                return@launch
            }
            _state.value = _state.value.copy(profile = profile, loading = true, error = null)
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
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
        streamJob = viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            eventsStream.events(profile).collect { event ->
                when (event) {
                    is ServerEvent.Snapshot -> event.updateSummary?.let { summary ->
                        _state.value = _state.value.copy(summary = summary, fetching = summary.running)
                    }
                    is ServerEvent.SummaryChanged -> _state.value = _state.value.copy(
                        summary = event.summary,
                        fetching = event.summary.running,
                    )
                    is ServerEvent.UpdateComplete -> {
                        _state.value = _state.value.copy(fetching = false)
                        // New chapters may have landed: refresh the feed.
                        refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasNextPage) return
        viewModelScope.launch {
            val profile = current.profile ?: return@launch
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
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            runCatching { clientFactory.create<UpdatesApi>(profile).fetchLibraryUpdates().await() }
            refresh()
        }
    }
}

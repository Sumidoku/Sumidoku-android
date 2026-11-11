package org.sumidoku.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.model.HistoryEntry
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
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
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = HistoryUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            _state.value = _state.value.copy(profile = profile, loading = true, error = null)
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val page = api.getReadingHistory(page = 1).await()
                _state.value = _state.value.copy(
                    loading = false,
                    entries = page.propertyEntries,
                    page = 1,
                    hasNextPage = page.hasNextPage,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
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
}

package org.sumidoku.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** M0 debug surface: proves the pinned OpenAPI codegen + auth + failover
 *  pipeline end-to-end against GET /api/v1/library. Replaced by the real
 *  feature:library screen in M1. */
data class LibraryDebugState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val entries: List<LibraryEntry> = emptyList(),
    val hasNextPage: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LibraryDebugViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryDebugState())
    val state: StateFlow<LibraryDebugState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = LibraryDebugState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val page = api.listLibrary().await()
                _state.value = LibraryDebugState(
                    loading = false,
                    profile = profile,
                    entries = page.propertyEntries,
                    hasNextPage = page.hasNextPage,
                )
            } catch (e: Exception) {
                _state.value = LibraryDebugState(
                    loading = false,
                    profile = profile,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }
}

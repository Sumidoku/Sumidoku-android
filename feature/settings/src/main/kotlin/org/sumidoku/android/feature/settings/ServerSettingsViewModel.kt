package org.sumidoku.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.SettingsApi
import org.sumidoku.android.core.api.model.BackupSettings
import org.sumidoku.android.core.api.model.ChapterCacheSettings
import org.sumidoku.android.core.api.model.DownloadSettings
import org.sumidoku.android.core.api.model.LibrarySettings
import org.sumidoku.android.core.api.model.PutSettingsRequest
import org.sumidoku.android.core.api.model.ServerSettings
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await

data class ServerSettingsUiState(
    val loading: Boolean = true,
    val settings: ServerSettings? = null,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/** Structured server-settings editor (design §14 M7): mirrors the
 *  /api/v1/settings object; PUT sends the complete structured object
 *  with the user's edits applied (the endpoint ignores read-only
 *  keys). */
@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val factory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerSettingsUiState())
    val state: StateFlow<ServerSettingsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val p = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val settings = factory.create<SettingsApi>(p).getSettings().await()
                _state.value = _state.value.copy(loading = false, settings = settings)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun update(transform: (ServerSettings) -> ServerSettings) {
        val current = _state.value.settings ?: return
        _state.value = _state.value.copy(settings = transform(current), saved = false)
    }

    fun save() {
        val s = _state.value.settings ?: return
        viewModelScope.launch {
            val p = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(busy = true, error = null, saved = false)
            try {
                val effective = factory.create<SettingsApi>(p).putSettings(
                    PutSettingsRequest(
                        incognito = s.incognito,
                        library = s.library,
                        download = s.download,
                        backup = s.backup,
                        browse = s.browse,
                        cache = s.cache,
                    ),
                ).await()
                _state.value = _state.value.copy(busy = false, settings = effective, saved = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun consumeTransient() {
        _state.value = _state.value.copy(error = null, saved = false)
    }
}

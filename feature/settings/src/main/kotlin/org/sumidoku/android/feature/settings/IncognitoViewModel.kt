package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.SettingsApi
import org.sumidoku.android.core.api.model.PutSettingsRequest
import org.sumidoku.android.core.api.model.ServerSettings
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await

data class IncognitoUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/** The "incognito reading" toggle on the settings home page. The webui places
 *  incognito in the general group, and the manga detail page can still override
 *  it per title; PUT /settings writes the whole structured object, so this
 *  ViewModel caches the full server settings and only replaces the incognito
 *  group before submitting. */
@HiltViewModel
class IncognitoViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val factory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(IncognitoUiState())
    val state: StateFlow<IncognitoUiState> = _state.asStateFlow()

    private var settings: ServerSettings? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val p = repository.activeProfile.first()
            if (p == null) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val loaded = factory.create<SettingsApi>(p).getSettings().await()
                settings = loaded
                _state.value = IncognitoUiState(loading = false, enabled = loaded.incognito.enabled)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val p = repository.activeProfile.first() ?: return@launch
            val previous = settings?.incognito?.enabled ?: _state.value.enabled
            _state.value = _state.value.copy(busy = true, enabled = enabled, error = null)
            try {
                // Fetch the latest settings first: the server settings page may
                // have just changed other groups, and PUTting the old snapshot
                // back would overwrite those changes.
                val current = try {
                    factory.create<SettingsApi>(p).getSettings().await()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    settings ?: throw e
                }
                settings = current
                val effective = factory.create<SettingsApi>(p).putSettings(
                    PutSettingsRequest(
                        incognito = current.incognito.copy(enabled = enabled),
                        library = current.library,
                        download = current.download,
                        backup = current.backup,
                        browse = current.browse,
                        cache = current.cache,
                    ),
                ).await()
                settings = effective
                _state.value = _state.value.copy(busy = false, enabled = effective.incognito.enabled)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, enabled = previous, error = e.message ?: e.toString())
            }
        }
    }
}

package org.sumidoku.android.feature.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.TrackersApi
import org.sumidoku.android.core.api.model.TrackerOAuthStatus
import org.sumidoku.android.core.api.model.TrackerPreferences
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class TrackersUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val statuses: Map<TrackerProvider, TrackerOAuthStatus> = emptyMap(),
    val defaultTracker: TrackerProvider? = null,
    val busyProvider: TrackerProvider? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackersUiState())
    val state: StateFlow<TrackersUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.statuses.isEmpty(), error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = TrackersUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val api = clientFactory.create<TrackersApi>(profile)
                val statuses = TrackerProvider.entries.associateWith { provider ->
                    runCatching { api.trackerOauthStatus(provider).await() }
                        .getOrElse { TrackerOAuthStatus(loggedIn = false, username = null) }
                }
                val prefs = runCatching { api.trackerPreferences().await() }.getOrNull()
                _state.value = TrackersUiState(
                    loading = false,
                    profile = profile,
                    statuses = statuses,
                    defaultTracker = prefs?.defaultTracker,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, profile = profile, error = e.message ?: e.toString())
            }
        }
    }

    /** Complete the OAuth binding with the code/state intercepted from
     *  the WebView callback navigation (API-key bearer — no cookie flow). */
    fun completeOauth(provider: TrackerProvider, code: String, state: String) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProvider = provider)
            try {
                clientFactory.create<TrackersApi>(profile)
                    .trackerOauthCallback(provider, code, state)
                    .await()
                _state.value = _state.value.copy(message = "已绑定")
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busyProvider = null, error = e.message ?: e.toString())
            }
        }
    }

    fun logout(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProvider = provider)
            try {
                clientFactory.create<TrackersApi>(profile).trackerOauthLogout(provider).await()
                _state.value = _state.value.copy(message = "已解绑")
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busyProvider = null, error = e.message ?: e.toString())
            }
        }
    }

    fun setDefault(provider: TrackerProvider?) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            try {
                clientFactory.create<TrackersApi>(profile)
                    .putTrackerPreferences(TrackerPreferences(defaultTracker = provider))
                    .await()
                _state.value = _state.value.copy(defaultTracker = provider, message = "已保存")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}

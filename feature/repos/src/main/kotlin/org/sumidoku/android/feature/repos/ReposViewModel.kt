package org.sumidoku.android.feature.repos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.ReposApi
import org.sumidoku.android.core.api.model.AddRepoRequest
import org.sumidoku.android.core.api.model.SourceRepo
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class ReposUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val repos: List<SourceRepo> = emptyList(),
    val addInFlight: Boolean = false,
    val addError: String? = null,
    val error: String? = null,
    /** Row-level refreshes never flip the whole screen into a spinner. */
    val refreshing: Set<Long> = emptySet(),
    val deleting: Set<Long> = emptySet(),
    /** Transient result text (snackbar) for refresh/delete. */
    val message: String? = null,
)

@HiltViewModel
class ReposViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ReposUiState())
    val state: StateFlow<ReposUiState> = _state

    init {
        refresh()
    }

    /** Whole-screen load: only the first fetch (or an explicit retry) shows
     *  the spinner. `refreshRepo` reloads the list in place instead. */
    fun refresh(reload: Boolean = true) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = reload && _state.value.repos.isEmpty(), error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = ReposUiState(
                    loading = false,
                    error = appContext.getString(R.string.repos_error_no_server),
                )
                return@launch
            }
            try {
                val repos = clientFactory.create<ReposApi>(profile).listSourceRepos().await()
                _state.value = _state.value.copy(loading = false, profile = profile, repos = repos)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun addRepo(url: String, onDone: () -> Unit) {
        val profile = _state.value.profile ?: return
        validateIndexUrl(url)?.let { problem ->
            _state.value = _state.value.copy(addError = problem)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(addInFlight = true, addError = null)
            try {
                clientFactory.create<ReposApi>(profile)
                    .addSourceRepo(AddRepoRequest(url = java.net.URI(url.trim())))
                    .await()
                _state.value = _state.value.copy(addInFlight = false)
                onDone()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(addInFlight = false, addError = e.message)
            }
        }
    }

    /** Returns a user-facing localized reason, or null when the URL looks valid.
     *  The server still fetches the index, so this only rejects obvious typos. */
    private fun validateIndexUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return appContext.getString(R.string.repos_validation_url_empty)
        val uri = try {
            java.net.URI(value)
        } catch (_: Exception) {
            return appContext.getString(R.string.repos_validation_url_invalid)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return appContext.getString(R.string.repos_validation_url_scheme)
        }
        if (uri.host.isNullOrBlank()) return appContext.getString(R.string.repos_validation_url_no_host)
        if (!uri.path.orEmpty().endsWith(".json")) {
            return appContext.getString(R.string.repos_validation_url_not_json)
        }
        return null
    }

    fun refreshRepo(id: Long) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = _state.value.refreshing + id, message = null)
            try {
                clientFactory.create<ReposApi>(profile).refreshSourceRepo(id).await()
                _state.value = _state.value.copy(
                    refreshing = _state.value.refreshing - id,
                    message = appContext.getString(R.string.repos_msg_index_refreshed),
                )
                refresh(reload = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(
                    refreshing = _state.value.refreshing - id,
                    message = appContext.getString(
                        R.string.repos_msg_refresh_failed,
                        e.message ?: appContext.getString(R.string.repos_error_unknown),
                    ),
                )
            }
        }
    }

    fun deleteRepo(id: Long) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(deleting = _state.value.deleting + id, message = null)
            try {
                clientFactory.create<ReposApi>(profile).deleteSourceRepo(id).await()
                _state.value = _state.value.copy(
                    deleting = _state.value.deleting - id,
                    message = appContext.getString(R.string.repos_msg_deleted),
                )
                refresh(reload = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(
                    deleting = _state.value.deleting - id,
                    message = appContext.getString(
                        R.string.repos_msg_delete_failed,
                        e.message ?: appContext.getString(R.string.repos_error_unknown),
                    ),
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

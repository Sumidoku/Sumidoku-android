package org.sumidoku.android.feature.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class ReposViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ReposUiState())
    val state: StateFlow<ReposUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = ReposUiState(loading = false, error = "未选择服务器")
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

    fun refreshRepo(id: Long) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            try {
                clientFactory.create<ReposApi>(profile).refreshSourceRepo(id).await()
                refresh()
            } catch (_: Exception) {
            }
        }
    }

    fun deleteRepo(id: Long) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            try {
                clientFactory.create<ReposApi>(profile).deleteSourceRepo(id).await()
                refresh()
            } catch (_: Exception) {
            }
        }
    }
}

package org.sumidoku.android.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.ReposApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.InstallSourceRequest
import org.sumidoku.android.core.api.model.RepoSource
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class BrowseUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val sources: List<Source> = emptyList(),
    val error: String? = null,
    val updateAllRunning: Boolean = false,
    val updateAllResult: String? = null,
) {
    val updatable: List<Source> get() = sources.filter { it.updateVersion != null }
    val pinned: List<Source> get() =
        sources.filter { it.pinned && it.updateVersion == null }.sortedBy { it.pinOrder ?: Long.MAX_VALUE }
    val installed: List<Source> get() =
        sources.filter { !it.pinned && it.updateVersion == null }.sortedBy { it.name.lowercase() }
}

data class AddSourceUiState(
    val loading: Boolean = true,
    val repoSources: List<RepoSource> = emptyList(),
    val query: String = "",
    val installingKeys: Set<String> = emptySet(),
    val installedKeys: Set<String> = emptySet(),
    val error: String? = null,
) {
    val visible: List<RepoSource>
        get() {
            val q = query.trim().lowercase()
            return repoSources
                .filter { it.installedId == null || installedKeys.contains(it.key) }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.languages.any { l -> l.contains(q) } }
                .sortedBy { it.name.lowercase() }
        }
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state

    private val _addState = MutableStateFlow(AddSourceUiState())
    val addState: StateFlow<AddSourceUiState> = _addState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.sources.isEmpty(), error = null) }
            val profile = repository.activeProfile.first() ?: run {
                _state.update { it.copy(loading = false, error = "未选择服务器") }
                return@launch
            }
            try {
                val sources = clientFactory.create<SourcesApi>(profile).listSources().await()
                _state.update { it.copy(loading = false, profile = profile, sources = sources) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun updateAll() {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            _state.update { it.copy(updateAllRunning = true, updateAllResult = null) }
            try {
                val result = clientFactory.create<ReposApi>(profile).updateAllSources().await()
                val text = buildString {
                    append("已更新 ${result.updated.size} 个")
                    if (result.failed.isNotEmpty()) append("，失败 ${result.failed.size} 个")
                    if (result.migrationRequired.isNotEmpty()) append("，${result.migrationRequired.size} 个需迁移")
                }
                _state.update { it.copy(updateAllRunning = false, updateAllResult = text) }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(updateAllRunning = false, updateAllResult = "更新失败：${e.message ?: e}")
                }
            }
        }
    }

    // --- add source (flat aggregated list, Aidoku AddSource style) ---

    fun loadRepoSources() {
        viewModelScope.launch {
            _addState.update { it.copy(loading = true, error = null) }
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: run {
                _addState.update { it.copy(loading = false, error = "未选择服务器") }
                return@launch
            }
            try {
                val repoSources = clientFactory.create<ReposApi>(profile).browseRepoSources().await()
                _addState.update {
                    it.copy(
                        loading = false,
                        repoSources = repoSources,
                        installedKeys = repoSources.filter { s -> s.installedId != null }.map { s -> s.key }.toSet(),
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun setAddQuery(query: String) {
        _addState.update { it.copy(query = query) }
    }

    fun install(source: RepoSource) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            _addState.update { it.copy(installingKeys = it.installingKeys + source.key) }
            try {
                clientFactory.create<ReposApi>(profile)
                    .installRepoSource(InstallSourceRequest(repoId = source.repoId, sourceKey = source.key))
                    .await()
                _addState.update {
                    it.copy(
                        installingKeys = it.installingKeys - source.key,
                        installedKeys = it.installedKeys + source.key,
                    )
                }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.update { it.copy(installingKeys = it.installingKeys - source.key, error = e.message ?: e.toString()) }
            }
        }
    }
}

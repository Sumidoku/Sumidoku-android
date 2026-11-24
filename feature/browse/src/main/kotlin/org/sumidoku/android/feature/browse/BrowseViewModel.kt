package org.sumidoku.android.feature.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.sumidoku.android.core.api.ReposApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.InstallSourceRequest
import org.sumidoku.android.core.api.model.ReorderPinnedRequest
import org.sumidoku.android.core.api.model.RepoSource
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourcePatch
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class BrowseUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val sources: List<Source> = emptyList(),
    /** source id → has configurable settings; absent = not probed yet. */
    val settingsAvailable: Map<Long, Boolean> = emptyMap(),
    val error: String? = null,
    val updateAllRunning: Boolean = false,
    val updateAllResult: String? = null,
    /** Source ids with an in-flight single-source update (design 03 row pill). */
    val updatingIds: Set<Long> = emptySet(),
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
    /** webui/Aidoku-style language filter; empty = all. */
    val selectedLangs: Set<String> = emptySet(),
    val installingKeys: Set<String> = emptySet(),
    val installedKeys: Set<String> = emptySet(),
    val error: String? = null,
) {
    /** Distinct repositories currently feeding the aggregated list. */
    val repoCount: Int
        get() = repoSources.map { it.repoId }.distinct().size

    /** Distinct languages present across the aggregated repo listing. */
    val availableLangs: List<String>
        get() = repoSources.flatMap { it.languages }.distinct().sorted()

    val visible: List<RepoSource>
        get() {
            val q = query.trim().lowercase()
            return repoSources
                .filter { it.installedId == null || installedKeys.contains(it.key) }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.languages.any { l -> l.contains(q) } }
                .filter {
                    selectedLangs.isEmpty() || it.languages.any { l -> l in selectedLangs }
                }
                .sortedBy { it.name.lowercase() }
        }
}

/** Local internal source draft. Defaults match the server config layer
 *  (`local_scan_force_modified_time` off, page/metadata analysis on), so the
 *  request only carries the fields the user actually changed. */
data class LocalSourceDraft(
    val name: String,
    val paths: List<String>,
    val oneshotsDir: String? = null,
    val exclusions: List<String> = emptyList(),
    val forceModifiedTime: Boolean = false,
    val analyzePages: Boolean = true,
    val analyzeMetadata: Boolean = true,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
                _state.update { it.copy(loading = false, error = appContext.getString(R.string.browse_error_no_server)) }
                return@launch
            }
            try {
                val sources = clientFactory.create<SourcesApi>(profile).listSources().await()
                _state.update { it.copy(loading = false, profile = profile, sources = sources) }
                probeSettingsAvailability(profile, sources)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /** Single-source update (design 03 row pill). The endpoint reports business
     *  failures as 409 with a `no_update`/`busy`/`resync_required` code, so the
     *  reason is surfaced verbatim when the transport layer exposes it. */
    fun updateSource(source: Source) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            _state.update { it.copy(updatingIds = it.updatingIds + source.id) }
            try {
                clientFactory.create<SourcesApi>(profile).updateSource(source.id).await()
                _state.update {
                    it.copy(
                        updatingIds = it.updatingIds - source.id,
                        updateAllResult = appContext.getString(R.string.browse_updated_source, source.name),
                    )
                }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        updatingIds = it.updatingIds - source.id,
                        updateAllResult = appContext.getString(R.string.browse_update_source_failed, source.name, e.message ?: e.toString()),
                    )
                }
            }
        }
    }

    fun updateAll() {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            _state.update { it.copy(updateAllRunning = true, updateAllResult = null) }
            try {
                val result = clientFactory.create<ReposApi>(profile).updateAllSources().await()
                // webui BrowsePage onUpdateAll: summary + first failure reason
                // + first migration warning so the toast is actionable.
                // Each summary part is its own localized resource; they are
                // joined with ", " to keep every locale grammatically intact.
                val parts = mutableListOf(
                    appContext.getString(R.string.browse_updated_count, result.updated.size),
                )
                if (result.failed.isNotEmpty()) {
                    val failedPart = buildString {
                        append(appContext.getString(R.string.browse_failed_count, result.failed.size))
                        result.failed.firstOrNull()?.let { f ->
                            append(appContext.getString(R.string.browse_first_failure, f.key, f.reason))
                        }
                    }
                    parts.add(failedPart)
                }
                if (result.migrationRequired.isNotEmpty()) {
                    parts.add(appContext.getString(R.string.browse_migrate_required_count, result.migrationRequired.size))
                }
                val text = buildString {
                    append(parts.joinToString(", "))
                    result.migrationWarnings.firstOrNull()?.let { w ->
                        append(appContext.getString(R.string.browse_first_migration_warning, w.key, w.message))
                    }
                }
                _state.update { it.copy(updateAllRunning = false, updateAllResult = text) }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(updateAllRunning = false, updateAllResult = appContext.getString(R.string.browse_update_all_failed, e.message ?: e.toString()))
                }
            }
        }
    }

    // --- pin management (Aidoku pinned section + reorder-pinned) ---

    fun togglePin(source: Source) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile)
                    .patchSource(source.id, SourcePatch(pinned = !source.pinned))
                    .await()
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    fun toggleEnabled(source: Source) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile)
                    .patchSource(source.id, SourcePatch(enabled = !source.enabled))
                    .await()
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    /** Persist a new pinned order — full replacement, server semantics. */
    fun savePinOrder(orderedIds: List<Long>) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile)
                    .reorderPinnedSources(ReorderPinnedRequest(ids = orderedIds.map { it.toString() }))
                    .await()
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    // --- add source (flat aggregated list, Aidoku AddSource style) ---

    fun loadRepoSources() {
        viewModelScope.launch {
            _addState.update { it.copy(loading = true, error = null) }
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: run {
                _addState.update { it.copy(loading = false, error = appContext.getString(R.string.browse_error_no_server)) }
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

    /** Whether each source has configurable settings (drives the gear icon).
     *  A probe failure used to hide the gear entirely — which made the
     *  settings look "completely unavailable" whenever the settings payload
     *  failed to decode. Fail-open instead (webui parity: the gear always
     *  shows and the settings screen itself surfaces any error with a
     *  retry). OPDS sources have no settings schema. */
    private fun probeSettingsAvailability(profile: ServerProfile, sources: List<Source>) {
        viewModelScope.launch {
            val api = clientFactory.create<SourcesApi>(profile)
            val probed = coroutineScope {
                sources
                    .filter { it.kind != Source.Kind.OPDS }
                    .map { source ->
                        async {
                            val has = try {
                                api.getSourceSettings(source.id).await().settings.isNotEmpty()
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                true // fail-open: show the gear, let the settings screen report the error
                            }
                            source.id to has
                        }
                    }
                    .map { it.await() }
                    .toMap()
            }
            _state.update { it.copy(settingsAvailable = it.settingsAvailable + probed) }
        }
    }

    /** Uninstall a source — DELETE /sources/{id}, with a confirm dialog in the UI. */
    fun uninstall(source: Source) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile).deleteSource(source.id).await()
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = appContext.getString(R.string.browse_uninstall_failed, e.message ?: e.toString())) }
            }
        }
    }

    fun toggleLang(lang: String) {
        _addState.update {
            it.copy(selectedLangs = if (lang in it.selectedLangs) it.selectedLangs - lang else it.selectedLangs + lang)
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

    // --- internal sources (local folders / OPDS catalogs) & .aix import ---

    /** Create a built-in local source. Dirs are server filesystem paths.
     *  Body mirrors webui `RegisterLocalInstance`: internally tagged on
     *  `type` with camelCase fields, and optional overrides are only sent
     *  when they deviate from the server defaults (webui InternalSourceModal). */
    fun createInternalLocal(draft: LocalSourceDraft, onDone: () -> Unit) {
        val body = buildJsonObject {
            put("type", "local")
            put("name", draft.name)
            putJsonArray("paths") { draft.paths.forEach { add(it) } }
            draft.oneshotsDir?.takeIf { it.isNotBlank() }?.let { put("oneshotsDir", it) }
            if (draft.exclusions.isNotEmpty()) {
                putJsonArray("exclusions") { draft.exclusions.forEach { add(it) } }
            }
            if (draft.forceModifiedTime) put("forceModifiedTime", true)
            if (!draft.analyzePages) put("analyzePages", false)
            if (!draft.analyzeMetadata) put("analyzeMetadata", false)
        }
        registerInternal(body, onDone)
    }

    /** Create a built-in OPDS source from an external catalog URL.
     *  username/password are optional HTTP basic credentials the server
     *  accepts on the same register body (webui InternalSourceModal). */
    fun createInternalOpds(
        name: String,
        url: String,
        username: String? = null,
        password: String? = null,
        onDone: () -> Unit,
    ) {
        val body = buildJsonObject {
            put("type", "opds")
            put("name", name)
            put("url", url)
            username?.takeIf { it.isNotBlank() }?.let { put("username", it) }
            password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
        }
        registerInternal(body, onDone)
    }

    private fun registerInternal(body: JsonObject, onDone: () -> Unit) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile).registerInternalSource(body).await()
                refresh()
                onDone()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.update { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    /** Upload a local .aix package — server installs it through the same
     *  scan/register path as repo installs. */
    fun importAix(fileName: String, bytes: ByteArray, onDone: () -> Unit) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: repository.activeProfile.first() ?: return@launch
            _addState.update { it.copy(loading = true, error = null) }
            try {
                val part = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    bytes.toRequestBody("application/octet-stream".toMediaType()),
                )
                clientFactory.create<SourcesApi>(profile).importSource(part).await()
                _addState.update { it.copy(loading = false) }
                refresh()
                onDone()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }
}

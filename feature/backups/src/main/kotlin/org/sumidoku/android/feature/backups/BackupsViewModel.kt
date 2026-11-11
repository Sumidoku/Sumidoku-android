package org.sumidoku.android.feature.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.sumidoku.android.core.api.BackupsApi
import org.sumidoku.android.core.api.model.BackupDoc
import org.sumidoku.android.core.api.model.BackupFile
import org.sumidoku.android.core.api.model.BackupRestoreResponse
import org.sumidoku.android.core.api.model.RestoreBackupRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import java.io.File
import javax.inject.Inject

data class BackupsUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val dir: String = "",
    val files: List<BackupFile> = emptyList(),
    val busy: Boolean = false,
    val exportJson: String? = null,
    val restoreResult: BackupRestoreResponse? = null,
    val aidokuSummary: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(BackupsUiState())
    val state: StateFlow<BackupsUiState> = _state

    init {
        refresh()
    }

    private fun api(): BackupsApi? =
        _state.value.profile?.let { clientFactory.create<BackupsApi>(it) }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.files.isEmpty(), error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = BackupsUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val files = clientFactory.create<BackupsApi>(profile).listBackups().await()
                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    dir = files.dir,
                    files = files.files,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, profile = profile, error = e.message ?: e.toString())
            }
        }
    }

    /** Export the full admin backup document as a JSON string the screen
     *  hands to the Storage Access Framework (createDocument). */
    fun export() {
        val api = api() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val doc = api.exportBackup().await()
                _state.value = _state.value.copy(
                    busy = false,
                    exportJson = json.encodeToString(BackupDoc.serializer(), doc),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun restoreFile(name: String) {
        val api = api() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val result = api.restoreBackup(RestoreBackupRequest(name = name)).await()
                _state.value = _state.value.copy(busy = false, restoreResult = result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    /** Restore a Sumidoku backup document picked from device storage. */
    fun restoreDocument(bytes: ByteArray) {
        val api = api() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val doc = json.decodeFromString(BackupDoc.serializer(), bytes.decodeToString())
                val result = api.importBackup(doc).await()
                _state.value = _state.value.copy(busy = false, restoreResult = result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    /** Import an Aidoku .aib backup picked from device storage. */
    fun importAidoku(bytes: ByteArray, fileName: String) {
        val api = api() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val tmp = File.createTempFile("aidoku-import", ".aib").apply {
                    writeBytes(bytes)
                    deleteOnExit()
                }
                val result = api.importAidokuBackup(tmp).await()
                _state.value = _state.value.copy(
                    busy = false,
                    aidokuSummary = "已导入：漫画 ${result.imported.library}、进度 ${result.imported.progress}、分类 ${result.imported.categories}、追踪绑定 ${result.imported.trackers}（跳过 ${result.skipped.size} 条）",
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun consumeTransient() {
        _state.value = _state.value.copy(
            message = null,
            error = null,
            exportJson = null,
            restoreResult = null,
            aidokuSummary = null,
        )
    }
}

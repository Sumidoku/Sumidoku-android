package org.sumidoku.android.feature.backups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.sumidoku.android.core.api.BackupsApi
import org.sumidoku.android.core.api.model.AidokuImportResponse
import org.sumidoku.android.core.api.model.BackupDoc
import org.sumidoku.android.core.api.model.BackupFile
import org.sumidoku.android.core.api.model.BackupRestoreResponse
import org.sumidoku.android.core.api.model.RestoreBackupRequest
import org.sumidoku.android.core.api.model.SuwayomiImportResponse
import org.sumidoku.android.core.common.launchGuarded
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
    /** Aidoku .aib dry-run result awaiting confirmation (M8). */
    val aidokuDryRun: AidokuImportResponse? = null,
    /** Mihon/.tachibk wizard state (M8): dry-run report + mapping. */
    val suwayomiDryRun: SuwayomiImportResponse? = null,
    val suwayomiMapping: Map<String, String> = emptyMap(),
    val suwayomiSummary: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BackupsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(loading = _state.value.files.isEmpty(), error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = BackupsUiState(loading = false, error = appContext.getString(R.string.backups_error_no_server))
                return@launchGuarded
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
        viewModelScope.launchGuarded {
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
        viewModelScope.launchGuarded {
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
        viewModelScope.launchGuarded {
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

    /** Import an Aidoku .aib backup picked from device storage.
     *  dryRun=true shows the would-be counts and asks the screen to
     *  confirm; confirmAidokuImport() then runs the real import. */
    private var aidokuBytes: ByteArray? = null

    fun importAidoku(bytes: ByteArray, fileName: String, dryRun: Boolean = true) {
        val api = api() ?: return
        if (!dryRun && bytes.isNotEmpty()) aidokuBytes = bytes
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(busy = true)
            try {
                val payload = if (dryRun) bytes else aidokuBytes ?: bytes
                val tmp = File.createTempFile("aidoku-import", ".aib").apply {
                    writeBytes(payload)
                    deleteOnExit()
                }
                val result = api.importAidokuBackup(tmp, dryRun = dryRun).await()
                if (dryRun) {
                    aidokuBytes = bytes
                    _state.value = _state.value.copy(busy = false, aidokuDryRun = result)
                } else {
                    aidokuBytes = null
                    _state.value = _state.value.copy(
                        busy = false,
                        aidokuDryRun = null,
                        aidokuSummary = appContext.getString(
                            R.string.backups_aidoku_import_summary,
                            result.imported.library,
                            result.imported.progress,
                            result.imported.categories,
                            result.imported.trackers,
                            result.skipped.size,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    /** Run the confirmed real import after the dry-run preview. */
    fun confirmAidokuImport() {
        importAidoku(bytes = aidokuBytes ?: return, fileName = "", dryRun = false)
    }

    // ---- Mihon/Suwayomi .tachibk wizard (design §14 M8) ----

    private var tachibkBytes: ByteArray? = null
    private var tachibkName: String = "backup.tachibk"

    /** Step 1: dry-run the .tachibk to get the per-source report. */
    fun startTachibk(bytes: ByteArray, fileName: String) {
        val api = api() ?: return
        tachibkBytes = bytes
        tachibkName = fileName
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(busy = true, suwayomiDryRun = null, suwayomiMapping = emptyMap())
            try {
                val tmp = File.createTempFile("suwayomi-import", ".tachibk").apply {
                    writeBytes(bytes)
                    deleteOnExit()
                }
                val result = api.importSuwayomiBackup(tmp, dryRun = true).await()
                _state.value = _state.value.copy(busy = false, suwayomiDryRun = result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    /** Map one Mihon source to an installed Aidoku source key (or to
     *  skip when null). */
    fun setSuwayomiMapping(mihonSourceId: String, aidokuSourceKey: String?) {
        val mapping = _state.value.suwayomiMapping.toMutableMap()
        if (aidokuSourceKey == null) mapping.remove(mihonSourceId) else mapping[mihonSourceId] = aidokuSourceKey
        _state.value = _state.value.copy(suwayomiMapping = mapping)
    }

    /** Step 2: run the real import with the chosen mapping. */
    fun runTachibk() {
        val api = api() ?: return
        val bytes = tachibkBytes ?: return
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(busy = true)
            try {
                val tmp = File.createTempFile("suwayomi-import", ".tachibk").apply {
                    writeBytes(bytes)
                    deleteOnExit()
                }
                val mapping = _state.value.suwayomiMapping
                val mappingJson = if (mapping.isEmpty()) null else {
                    kotlinx.serialization.json.buildJsonObject {
                        mapping.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
                    }.toString()
                }
                val result = api.importSuwayomiBackup(tmp, dryRun = false, sourceMapping = mappingJson).await()
                tachibkBytes = null
                _state.value = _state.value.copy(
                    busy = false,
                    suwayomiDryRun = null,
                    suwayomiSummary = appContext.getString(
                        R.string.backups_tachibk_import_summary,
                        result.imported.library,
                        result.imported.progress,
                        result.imported.categories,
                        result.imported.trackers,
                        result.skipped.size,
                    ),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun cancelTachibk() {
        tachibkBytes = null
        _state.value = _state.value.copy(suwayomiDryRun = null, suwayomiMapping = emptyMap())
    }

    fun cancelAidokuDryRun() {
        _state.value = _state.value.copy(aidokuDryRun = null)
    }

    fun consumeTransient() {
        _state.value = _state.value.copy(
            message = null,
            error = null,
            exportJson = null,
            restoreResult = null,
            aidokuSummary = null,
            suwayomiSummary = null,
        )
    }
}

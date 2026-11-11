package org.sumidoku.android.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.DownloadsApi
import org.sumidoku.android.core.api.model.DeleteDownloadsRequest
import org.sumidoku.android.core.api.model.Download
import org.sumidoku.android.core.api.model.DownloadStatus
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** Tier A (server-side) downloads queue management — design §14 M3.
 *  Polls while the queue runs; start/stop/clear map to
 *  POST /downloads/start|stop and DELETE /downloads?finishedOnly. */
data class DownloadsUiState(
    val profile: ServerProfile? = null,
    val running: Boolean = false,
    val downloads: List<Download> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = DownloadsUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            _state.value = _state.value.copy(profile = profile)
            val api = clientFactory.create<DownloadsApi>(profile)
            while (true) {
                try {
                    val downloads = api.listDownloads().await()
                    val running = downloads.any {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        downloads = downloads,
                        running = running,
                    )
                    if (!running) return@launch
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: e.toString(),
                    )
                    return@launch
                }
                delay(3000)
            }
        }
    }

    fun start() = viewModelScope.launch {
        runCatching { api()?.startDownloads()?.await() }
        refresh()
    }

    fun stop() = viewModelScope.launch {
        runCatching { api()?.stopDownloads()?.await() }
        refresh()
    }

    fun clearFinished() = viewModelScope.launch {
        try {
            val result = api()?.deleteDownloads(DeleteDownloadsRequest(finishedOnly = true))?.await()
            _state.value = _state.value.copy(message = "已清理 ${result?.deleted ?: 0} 项")
        } catch (e: Exception) {
            _state.value = _state.value.copy(message = e.message)
        }
        refresh()
    }

    fun delete(id: Long) = viewModelScope.launch {
        runCatching { api()?.deleteDownloads(DeleteDownloadsRequest(chapterIds = listOf(id)))?.await() }
        refresh()
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun api(): DownloadsApi? {
        val profile = _state.value.profile ?: repository.activeProfile.first() ?: return null
        return clientFactory.create<DownloadsApi>(profile)
    }
}

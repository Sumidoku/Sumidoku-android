package org.sumidoku.android.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import org.sumidoku.android.core.stream.EventsStream
import org.sumidoku.android.core.stream.ServerEvent
import retrofit2.await
import javax.inject.Inject

/** Tier A (server-side) downloads queue management — design §14 M3.
 *  Live state comes from the shared SSE stream (snapshot replaces the
 *  list, download events patch rows, running toggles the pause/start
 *  button); REST is the initial load fallback and the mutation channel,
 *  mirroring webui DownloadsPage. */
data class DownloadsUiState(
    val profile: ServerProfile? = null,
    val running: Boolean = false,
    val online: Boolean = false,
    val downloads: List<Download> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val eventsStream: EventsStream,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state

    private var streamJob: Job? = null
    private var restFallbackLoaded = false

    init {
        refresh()
    }

    fun refresh() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = DownloadsUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            _state.value = _state.value.copy(profile = profile)
            restFallbackLoaded = false
            launch { restFallback(profile) }
            eventsStream.events(profile).collect { event -> apply(event) }
        }
    }

    /** Initial REST load, only applied if no SSE snapshot beat it (same
     *  ordering guard as the webui page). */
    private suspend fun restFallback(profile: ServerProfile) {
        try {
            val downloads = clientFactory.create<DownloadsApi>(profile).listDownloads().await()
            if (restFallbackLoaded) return
            _state.value = _state.value.copy(
                loading = false,
                error = null,
                downloads = downloads,
                running = downloads.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (restFallbackLoaded) return
            _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
        }
    }

    private suspend fun apply(event: ServerEvent) {
        when (event) {
            is ServerEvent.Snapshot -> {
                restFallbackLoaded = true
                _state.value = _state.value.copy(
                    loading = false,
                    error = null,
                    downloads = event.downloads,
                    running = event.running,
                )
            }
            is ServerEvent.Running -> {
                restFallbackLoaded = true
                _state.value = _state.value.copy(running = event.running)
            }
            is ServerEvent.DownloadProgress -> {
                restFallbackLoaded = true
                val current = _state.value.downloads
                val idx = current.indexOfFirst { it.chapterId == event.chapterId }
                if (idx < 0) {
                    // Unknown chapter: a full refetch is cheaper than guessing.
                    val profile = _state.value.profile ?: return
                    restFallback(profile)
                    return
                }
                val item = current[idx]
                val next = when (event.kind) {
                    ServerEvent.DownloadProgress.Kind.FINISHED -> item.copy(
                        status = DownloadStatus.FINISHED,
                        downloaded = event.total.toLong(),
                        total = event.total.toLong(),
                    )
                    ServerEvent.DownloadProgress.Kind.ERROR -> item.copy(
                        status = DownloadStatus.ERROR,
                        downloaded = event.downloaded.toLong(),
                        total = event.total.toLong(),
                    )
                    ServerEvent.DownloadProgress.Kind.PAUSED -> item.copy(
                        status = DownloadStatus.PAUSED,
                        downloaded = event.downloaded.toLong(),
                        total = event.total.toLong(),
                    )
                    ServerEvent.DownloadProgress.Kind.QUEUED -> item.copy(
                        status = DownloadStatus.QUEUED,
                        downloaded = event.downloaded.toLong(),
                        total = event.total.toLong(),
                    )
                    else -> item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloaded = event.downloaded.toLong(),
                        total = event.total.toLong(),
                    )
                }
                _state.value = _state.value.copy(
                    downloads = current.toMutableList().also { it[idx] = next },
                )
            }
            is ServerEvent.Connected -> _state.value = _state.value.copy(online = true)
            is ServerEvent.Disconnected -> _state.value = _state.value.copy(online = false)
            else -> Unit
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
    }

    fun delete(id: Long) = viewModelScope.launch {
        runCatching { api()?.deleteDownloads(DeleteDownloadsRequest(chapterIds = listOf(id)))?.await() }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun api(): DownloadsApi? {
        val profile = _state.value.profile ?: repository.activeProfile.first() ?: return null
        return clientFactory.create<DownloadsApi>(profile)
    }
}

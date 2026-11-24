package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.CacheApi
import org.sumidoku.android.core.api.model.ChapterCacheStats
import org.sumidoku.android.core.api.model.CoverCacheStats
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** Cache management — design §14 M7: chapter page cache usage against
 *  its budget plus the cover cache, with clear actions, mirroring the
 *  webui settings cache section. */
data class CacheUiState(
    val loading: Boolean = true,
    val chapter: ChapterCacheStats? = null,
    val cover: CoverCacheStats? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CacheViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(CacheUiState())
    val state: StateFlow<CacheUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = CacheUiState(
                    loading = false,
                    error = appContext.getString(R.string.misc_no_server_selected),
                )
                return@launch
            }
            try {
                val api = clientFactory.create<CacheApi>(profile)
                val chapter = api.chapterCacheStats().await()
                val cover = runCatching { api.coverCacheStats().await() }.getOrNull()
                _state.value = _state.value.copy(loading = false, chapter = chapter, cover = cover)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun clearChapterCache() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(busy = true)
            try {
                val result = clientFactory.create<CacheApi>(profile).clearChapterCache().await()
                _state.value = _state.value.copy(
                    busy = false,
                    message = appContext.getString(R.string.cache_cleared_bytes, formatBytes(result.clearedBytes)),
                )
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun clearCoverCache() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(busy = true)
            try {
                val result = clientFactory.create<CacheApi>(profile).clearCoverCache().await()
                _state.value = _state.value.copy(
                    busy = false,
                    message = appContext.getString(R.string.cache_cleared_covers, result.clearedEntries),
                )
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    onBack: () -> Unit = {},
    viewModel: CacheViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cache_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.chapter?.let { chapter ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.cache_chapter_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.cache_chapter_usage,
                                        formatBytes(chapter.usedBytes),
                                        formatBytes(chapter.maxBytes),
                                        chapter.chapterCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        (chapter.usedBytes.toFloat() / chapter.maxBytes.coerceAtLeast(1L))
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                chapter.lastError?.let {
                                    Text(
                                        stringResource(R.string.cache_last_error, it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Button(
                                    onClick = { viewModel.clearChapterCache() },
                                    enabled = !state.busy,
                                ) { Text(stringResource(R.string.cache_clear_chapters)) }
                            }
                        }
                    }
                }
                state.cover?.let { cover ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.cache_cover_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.cache_cover_usage,
                                        cover.propertyEntries,
                                        formatBytes(cover.usedBytes),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.clearCoverCache() },
                                    enabled = !state.busy,
                                ) { Text(stringResource(R.string.cache_clear_covers)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

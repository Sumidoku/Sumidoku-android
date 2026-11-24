package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.LocalTrashEntry
import org.sumidoku.android.core.common.relativeEpochMs
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.Call
import retrofit2.await
import retrofit2.http.DELETE
import javax.inject.Inject

/** DELETE /local/trash is absent from the OpenAPI document (webui
 *  calls it directly), so declare it by hand: queues a server task
 *  that purges every trashed entry. */
interface TrashOpsApi {
    @DELETE("api/v1/local/trash")
    fun emptyTrash(): Call<List<org.sumidoku.android.core.api.model.Task>>
}

/** Local-source trash management — design §14 M5: restore entries or
 *  permanently delete them, with a server task for empty-trash. */
data class TrashUiState(
    val loading: Boolean = true,
    val propertyEntries: List<LocalTrashEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = TrashUiState(loading = false, error = appContext.getString(R.string.misc_no_server_selected))
                return@launch
            }
            try {
                val propertyEntries = clientFactory.create<SourcesApi>(profile).listLocalTrash().await()
                _state.value = _state.value.copy(loading = false, propertyEntries = propertyEntries)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile).restoreLocalTrash(id.toLong()).await()
                _state.value = _state.value.copy(message = appContext.getString(R.string.trash_restored))
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<SourcesApi>(profile).purgeLocalTrash(id.toLong()).await()
                _state.value = _state.value.copy(message = appContext.getString(R.string.trash_deleted_permanently))
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    /** Queue the server's empty-trash task (DELETE /local/trash — not
     *  in the generated client, so a tiny hand-written interface). */
    fun emptyTrash() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<TrashOpsApi>(profile).emptyTrash().await()
                _state.value = _state.value.copy(message = appContext.getString(R.string.trash_empty_queued))
                refresh()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit = {},
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }

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
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = state.propertyEntries.isNotEmpty(),
                        onClick = { confirmEmpty = true },
                    ) { Text(stringResource(R.string.trash_empty_action)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.propertyEntries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.propertyEntries, key = { it.manga.id }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(entry.manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.trash_entry_summary,
                                    entry.trashedChapters,
                                    relativeEpochMs(entry.trashedAt),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.restore(entry.manga.id) }) {
                                    Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.trash_restore))
                                }
                                IconButton(onClick = { confirmDeleteId = entry.manga.id }) {
                                    Icon(
                                        Icons.Outlined.DeleteForever,
                                        contentDescription = stringResource(R.string.trash_delete_forever),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_dialog_title)) },
            text = { Text(stringResource(R.string.trash_empty_dialog_text, state.propertyEntries.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.emptyTrash()
                }) { Text(stringResource(R.string.trash_empty_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.misc_cancel)) }
            },
        )
    }

    val deleteId = confirmDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.trash_delete_forever)) },
            text = { Text(stringResource(R.string.trash_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    viewModel.delete(deleteId)
                }) { Text(stringResource(R.string.misc_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text(stringResource(R.string.misc_cancel)) }
            },
        )
    }
}

package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.sumidoku.android.core.api.TasksApi
import org.sumidoku.android.core.api.model.Task
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.common.relativeEpochMs
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import org.sumidoku.android.core.ui.SettingsGroupCard
import retrofit2.await
import javax.inject.Inject

/** Task queue viewer — design §14 M5: server background tasks with
 *  status filter and cancel for pending/running rows. */
data class TasksUiState(
    val loading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val statusFilter: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state

    init {
        refresh()
    }

    fun setFilter(status: String?) {
        _state.value = _state.value.copy(statusFilter = status)
        refresh()
    }

    fun refresh() {
        viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = TasksUiState(
                    loading = false,
                    error = appContext.getString(R.string.misc_no_server_selected),
                )
                return@launchGuarded
            }
            try {
                val tasks = clientFactory.create<TasksApi>(profile)
                    .listTasks(status = _state.value.statusFilter)
                    .await()
                _state.value = _state.value.copy(loading = false, tasks = tasks)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first() ?: return@launchGuarded
            try {
                clientFactory.create<TasksApi>(profile).cancelTask(id).await()
                _state.value = _state.value.copy(message = appContext.getString(R.string.tasks_cancelled))
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
fun TasksScreen(
    onBack: () -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val filters = listOf(
        null to stringResource(R.string.tasks_filter_all),
        "pending" to stringResource(R.string.tasks_filter_pending),
        "running" to stringResource(R.string.tasks_filter_running),
        "done" to stringResource(R.string.tasks_filter_done),
        "failed" to stringResource(R.string.tasks_filter_failed),
    )

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters.size) { i ->
                    val (value, label) = filters[i]
                    FilterChip(
                        selected = state.statusFilter == value,
                        onClick = { viewModel.setFilter(value) },
                        label = { Text(label) },
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.tasks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tasks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    item {
                        SettingsGroupCard {
                            state.tasks.forEach { task ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(taskStatusIcon(task.status), contentDescription = null) },
                            headlineContent = {
                                Text(task.kind, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val sourceId = task.sourceId
                                val sourceLabel = if (sourceId != null) {
                                    stringResource(R.string.tasks_source_number, sourceId)
                                } else {
                                    null
                                }
                                val errorLabel = task.error
                                Text(
                                    buildString {
                                        append(relativeEpochMs(task.createdAt))
                                        sourceLabel?.let { append(" · $it") }
                                        errorLabel?.let { append(" · $it") }
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                if (task.status == Task.Status.PENDING || task.status == Task.Status.RUNNING) {
                                    IconButton(onClick = { viewModel.cancel(task.id) }) {
                                        Icon(Icons.Outlined.Cancel, contentDescription = stringResource(R.string.tasks_cancel_task))
                                    }
                                }
                            },
                        )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun taskStatusIcon(status: Task.Status): ImageVector = when (status) {
    Task.Status.PENDING -> Icons.Outlined.HourglassTop
    Task.Status.RUNNING -> Icons.Outlined.PlayCircle
    Task.Status.DONE -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.ErrorOutline
}

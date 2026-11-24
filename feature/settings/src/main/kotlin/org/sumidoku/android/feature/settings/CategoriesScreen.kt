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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
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
import org.sumidoku.android.core.api.CategoriesApi
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.CategoryNameRequest
import org.sumidoku.android.core.api.model.ReorderRequest
import org.sumidoku.android.core.api.model.UpdateCategoryRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import org.sumidoku.android.core.ui.SettingsGroupCard
import retrofit2.await
import javax.inject.Inject

/** Category management — design §14 M7: create/rename/delete and
 *  reorder (up/down arrows, full-order POST on save). */
data class CategoriesUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = CategoriesUiState(
                    loading = false,
                    error = appContext.getString(R.string.misc_no_server_selected),
                )
                return@launch
            }
            try {
                val categories = clientFactory.create<CategoriesApi>(profile).listCategories().await()
                _state.value = _state.value.copy(loading = false, categories = categories)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun create(name: String) {
        mutate(appContext.getString(R.string.categories_created)) { it.createCategory(CategoryNameRequest(name)) }
    }

    fun rename(id: Long, name: String) {
        mutate(appContext.getString(R.string.categories_renamed)) { it.updateCategory(id, UpdateCategoryRequest(name = name)) }
    }

    fun delete(id: Long) {
        mutate(appContext.getString(R.string.categories_deleted)) { it.deleteCategory(id) }
    }

    fun move(id: Long, delta: Int) {
        val list = _state.value.categories.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val moved = list.removeAt(index)
        list.add(target, moved)
        mutate(appContext.getString(R.string.categories_order_saved)) { it.reorderCategories(ReorderRequest(ids = list.map { c -> c.id })) }
    }

    private fun mutate(message: String, call: (CategoriesApi) -> retrofit2.Call<*>) {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            try {
                call(clientFactory.create(profile)).await()
                _state.value = _state.value.copy(message = message)
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
fun CategoriesScreen(
    onBack: () -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Pair<Long?, String>?>(null) }

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
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = null to "" }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.categories_new))
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.categories.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.categories_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    SettingsGroupCard {
                        state.categories.forEach { category ->
                    val index = state.categories.indexOfFirst { it.id == category.id }
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(category.name) },
                        supportingContent = {
                            Text(
                                if (category.isDefault) {
                                    stringResource(R.string.categories_manga_count_default, category.mangaCount)
                                } else {
                                    stringResource(R.string.categories_manga_count, category.mangaCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { viewModel.move(category.id, -1) },
                                ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.misc_move_up)) }
                                IconButton(
                                    enabled = index < state.categories.lastIndex,
                                    onClick = { viewModel.move(category.id, 1) },
                                ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.misc_move_down)) }
                                IconButton(onClick = { dialog = category.id to category.name }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.misc_rename))
                                }
                                IconButton(
                                    enabled = !category.isDefault,
                                    onClick = { viewModel.delete(category.id) },
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.misc_delete),
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
        }
    }

    dialog?.let { (id, initial) ->
        var name by remember(id) { mutableStateOf(initial) }
        val dialogTitle = if (id == null) {
            stringResource(R.string.categories_new)
        } else {
            stringResource(R.string.categories_rename_title)
        }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(dialogTitle) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.misc_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (id == null) viewModel.create(name.trim()) else viewModel.rename(id, name.trim())
                        dialog = null
                    },
                ) { Text(stringResource(R.string.misc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.misc_cancel)) }
            },
        )
    }
}

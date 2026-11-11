package org.sumidoku.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
                _state.value = CategoriesUiState(loading = false, error = "未选择服务器")
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
        mutate("已创建") { it.createCategory(CategoryNameRequest(name)) }
    }

    fun rename(id: Long, name: String) {
        mutate("已重命名") { it.updateCategory(id, UpdateCategoryRequest(name = name)) }
    }

    fun delete(id: Long) {
        mutate("已删除") { it.deleteCategory(id) }
    }

    fun move(id: Long, delta: Int) {
        val list = _state.value.categories.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val moved = list.removeAt(index)
        list.add(target, moved)
        mutate("已保存顺序") { it.reorderCategories(ReorderRequest(ids = list.map { c -> c.id })) }
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
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = null to "" }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建分类")
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.categories.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    val index = state.categories.indexOfFirst { it.id == category.id }
                    ListItem(
                        headlineContent = { Text(category.name) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("${category.mangaCount} 部漫画")
                                    if (category.isDefault) append(" · 默认")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { viewModel.move(category.id, -1) },
                                ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移") }
                                IconButton(
                                    enabled = index < state.categories.lastIndex,
                                    onClick = { viewModel.move(category.id, 1) },
                                ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移") }
                                IconButton(onClick = { dialog = category.id to category.name }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "重命名")
                                }
                                IconButton(
                                    enabled = !category.isDefault,
                                    onClick = { viewModel.delete(category.id) },
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "删除",
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

    dialog?.let { (id, initial) ->
        var name by remember(id) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (id == null) "新建分类" else "重命名分类") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名称") },
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
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
            },
        )
    }
}

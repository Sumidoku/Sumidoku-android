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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.sumidoku.android.core.api.AuthApi
import org.sumidoku.android.core.api.model.ApiKeyCreateRequest
import org.sumidoku.android.core.api.model.ApiKeyEntry
import org.sumidoku.android.core.api.model.PasswordChangeRequest
import org.sumidoku.android.core.common.formatEpochMs
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** API key management + password change — design §14 M7. The plaintext
 *  key is shown exactly once after creation (server stores a SHA-256
 *  fingerprint), mirroring the webui settings panel. */
data class ApiKeysUiState(
    val loading: Boolean = true,
    val keys: List<ApiKeyEntry> = emptyList(),
    val busy: Boolean = false,
    val createdKey: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ApiKeysUiState())
    val state: StateFlow<ApiKeysUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: run {
                _state.value = ApiKeysUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val keys = clientFactory.create<AuthApi>(profile).listApiKeys().await()
                _state.value = _state.value.copy(loading = false, keys = keys)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(busy = true)
            try {
                val created = clientFactory.create<AuthApi>(profile)
                    .createApiKey(ApiKeyCreateRequest(name = name.trim()))
                    .await()
                _state.value = _state.value.copy(busy = false, createdKey = created.key)
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            try {
                clientFactory.create<AuthApi>(profile).revokeApiKey(id).await()
                _state.value = _state.value.copy(message = "已吊销")
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message ?: e.toString())
            }
        }
    }

    fun changePassword(current: String, next: String) {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            _state.value = _state.value.copy(busy = true)
            try {
                clientFactory.create<AuthApi>(profile)
                    .changePassword(PasswordChangeRequest(currentPassword = current, newPassword = next))
                    .await()
                _state.value = _state.value.copy(busy = false, message = "密码已更新")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun consumeTransient() {
        _state.value = _state.value.copy(message = null, error = null, createdKey = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onBack: () -> Unit = {},
    viewModel: ApiKeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeTransient()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("API 密钥") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showPassword = true }) {
                        Icon(Icons.Outlined.Lock, contentDescription = "修改密码")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建密钥")
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.keys.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有 API 密钥，点右下角新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.keys, key = { it.id }) { key ->
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        headlineContent = { Text(key.name) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("创建于 ${formatEpochMs(key.createdAt)}")
                                    key.lastUsedAt?.let { append(" · 最近使用 ${formatEpochMs(it)}") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.revoke(key.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "吊销")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建 API 密钥") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：手机、平板）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !state.busy,
                    onClick = {
                        showCreate = false
                        viewModel.create(name)
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }

    state.createdKey?.let { key ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTransient() },
            title = { Text("保存好这个密钥") },
            text = {
                Column {
                    Text("密钥只显示这一次，服务器只保存指纹。请立即复制保存：")
                    SelectionContainer {
                        Text(
                            key,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeTransient() }) { Text("我已保存") }
            },
        )
    }

    if (showPassword) {
        var current by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text("修改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("当前密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = next,
                        onValueChange = { next = it },
                        label = { Text("新密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = current.isNotBlank() && next.isNotBlank() && !state.busy,
                    onClick = {
                        showPassword = false
                        viewModel.changePassword(current, next)
                    },
                ) { Text("修改") }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text("取消") } },
        )
    }
}

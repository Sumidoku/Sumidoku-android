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
import androidx.compose.material3.ListItemDefaults
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
import org.sumidoku.android.core.api.AuthApi
import org.sumidoku.android.core.api.model.ApiKeyCreateRequest
import org.sumidoku.android.core.api.model.ApiKeyEntry
import org.sumidoku.android.core.api.model.PasswordChangeRequest
import org.sumidoku.android.core.common.formatEpochMs
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import org.sumidoku.android.core.ui.SettingsGroupCard
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
    @ApplicationContext private val appContext: Context,
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
                _state.value = ApiKeysUiState(loading = false, error = appContext.getString(R.string.misc_no_server_selected))
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
                _state.value = _state.value.copy(message = appContext.getString(R.string.api_keys_revoked))
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
                _state.value = _state.value.copy(busy = false, message = appContext.getString(R.string.api_keys_password_updated))
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
                title = { Text(stringResource(R.string.api_keys_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showPassword = true }) {
                        Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.api_keys_change_password))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.api_keys_new_key))
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.keys.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.api_keys_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    SettingsGroupCard {
                        state.keys.forEach { key ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        headlineContent = { Text(key.name) },
                        supportingContent = {
                            Text(
                                key.lastUsedAt?.let {
                                    stringResource(
                                        R.string.api_keys_dates_with_last_used,
                                        formatEpochMs(key.createdAt),
                                        formatEpochMs(it),
                                    )
                                } ?: stringResource(R.string.api_keys_dates, formatEpochMs(key.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.revoke(key.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.api_keys_revoke))
                            }
                        },
                    )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.api_keys_create_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.api_keys_name_label)) },
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
                ) { Text(stringResource(R.string.misc_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.misc_cancel)) } },
        )
    }

    state.createdKey?.let { key ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTransient() },
            title = { Text(stringResource(R.string.api_keys_save_key_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.api_keys_save_key_text))
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
                TextButton(onClick = { viewModel.consumeTransient() }) { Text(stringResource(R.string.api_keys_saved_confirm)) }
            },
        )
    }

    if (showPassword) {
        var current by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text(stringResource(R.string.api_keys_change_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text(stringResource(R.string.api_keys_current_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = next,
                        onValueChange = { next = it },
                        label = { Text(stringResource(R.string.api_keys_new_password)) },
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
                ) { Text(stringResource(R.string.api_keys_change_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text(stringResource(R.string.misc_cancel)) } },
        )
    }
}

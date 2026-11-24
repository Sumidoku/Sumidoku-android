package org.sumidoku.android.feature.backups

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.common.formatEpochMs

/** Backups hub: export the full admin backup JSON to device storage
 *  (SAF), restore a document picked from storage, restore one of the
 *  server's scheduled backup files, and import an Aidoku .aib backup.
 *  Mirrors the webui settings backup section. Preview 16-backups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    onBack: () -> Unit = {},
    viewModel: BackupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmRestoreName by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = state.exportJson
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }.onSuccess { viewModel.consumeTransient() }
                .onFailure { /* surfaced through state next time */ }
        }
        viewModel.consumeTransient()
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.readBytes() }.getOrNull()
            if (bytes != null) viewModel.restoreDocument(bytes)
        }
    }

    val aidokuLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.readBytes() }.getOrNull()
            if (bytes != null) viewModel.importAidoku(bytes, uri.lastPathSegment ?: "backup.aib")
        }
    }

    val tachibkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.readBytes() }.getOrNull()
            if (bytes != null) viewModel.startTachibk(bytes, uri.lastPathSegment ?: "backup.tachibk")
        }
    }

    LaunchedEffect(state.exportJson) {
        state.exportJson?.let { exportLauncher.launch("sumidoku-backup.json") }
    }

    LaunchedEffect(state.message, state.error, state.aidokuSummary, state.suwayomiSummary) {
        val text = state.message ?: state.error ?: state.aidokuSummary ?: state.suwayomiSummary
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeTransient()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.backups_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.backups_export_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.backups_export_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { viewModel.export() },
                                enabled = !state.busy,
                            ) { Text(stringResource(R.string.backups_export_button)) }
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.backups_restore_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.backups_restore_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { restoreLauncher.launch("application/json") },
                                    enabled = !state.busy,
                                ) { Text(stringResource(R.string.backups_restore_json_button)) }
                                OutlinedButton(
                                    onClick = { aidokuLauncher.launch("*/*") },
                                    enabled = !state.busy,
                                ) { Text(stringResource(R.string.backups_import_aidoku_button)) }
                            }
                            OutlinedButton(
                                onClick = { tachibkLauncher.launch("*/*") },
                                enabled = !state.busy,
                            ) { Text(stringResource(R.string.backups_import_tachibk_button)) }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.backups_server_backups_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (state.dir.isNotEmpty()) {
                        Text(
                            state.dir,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.loading) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.files.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.backups_no_scheduled_backups),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.files.size) { i ->
                        val file = state.files[i]
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        "${formatSize(file.propertySize)} · ${formatEpochMs(file.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    enabled = !state.busy,
                                    onClick = { confirmRestoreName = file.name },
                                ) {
                                    Icon(Icons.Outlined.Restore, contentDescription = null)
                                    Text(" ${stringResource(R.string.backups_restore)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val restoreName = confirmRestoreName
    if (restoreName != null) {
        AlertDialog(
            onDismissRequest = { confirmRestoreName = null },
            title = { Text(stringResource(R.string.backups_restore_dialog_title)) },
            text = { Text(stringResource(R.string.backups_restore_dialog_message, restoreName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestoreName = null
                    viewModel.restoreFile(restoreName)
                }) { Text(stringResource(R.string.backups_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreName = null }) { Text(stringResource(R.string.backups_cancel)) }
            },
        )
    }

    state.restoreResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTransient() },
            title = { Text(stringResource(R.string.backups_restore_done_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.backups_restore_result_counts,
                            result.restored.library,
                            result.restored.progress,
                            result.restored.categories,
                            result.restored.settings,
                            result.restored.sourceSettings,
                        ),
                    )
                    result.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeTransient() }) { Text(stringResource(R.string.backups_ok)) }
            },
        )
    }

    // Aidoku dry-run: show what would be imported, then confirm (M8).
    state.aidokuDryRun?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelAidokuDryRun() },
            title = { Text(stringResource(R.string.backups_aidoku_preview_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backups_aidoku_preview_message,
                        result.imported.library,
                        result.imported.progress,
                        result.imported.categories,
                        result.imported.trackers,
                        result.skipped.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.consumeTransient()
                    viewModel.confirmAidokuImport()
                }) { Text(stringResource(R.string.backups_import)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAidokuDryRun() }) { Text(stringResource(R.string.backups_cancel)) }
            },
        )
    }

    // Mihon/.tachibk wizard sheet: per-source mapping then import (M8).
    state.suwayomiDryRun?.let { report ->
        TachibkWizardSheet(
            report = report,
            mapping = state.suwayomiMapping,
            onMap = { id, key -> viewModel.setSuwayomiMapping(id, key) },
            onImport = { viewModel.runTachibk() },
            onDismiss = { viewModel.cancelTachibk() },
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

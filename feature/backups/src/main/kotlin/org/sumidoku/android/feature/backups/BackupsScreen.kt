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

    LaunchedEffect(state.exportJson) {
        state.exportJson?.let { exportLauncher.launch("sumidoku-backup.json") }
    }

    LaunchedEffect(state.message, state.error, state.aidokuSummary) {
        val text = state.message ?: state.error ?: state.aidokuSummary
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeTransient()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                            Text("导出完整备份", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "导出当前服务器的完整备份 JSON（书库、进度、分类、设置、图源设置），保存到本机存储。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { viewModel.export() },
                                enabled = !state.busy,
                            ) { Text("导出到文件…") }
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("从文件恢复", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "选择本机上的 Sumidoku 备份 JSON 恢复；也可以导入 Aidoku (.aib) 备份。恢复会合并进现有数据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { restoreLauncher.launch("application/json") },
                                    enabled = !state.busy,
                                ) { Text("恢复备份 JSON…") }
                                OutlinedButton(
                                    onClick = { aidokuLauncher.launch("*/*") },
                                    enabled = !state.busy,
                                ) { Text("导入 Aidoku .aib…") }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        "服务器定时备份",
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
                            "还没有定时备份文件（在服务器配置 backup 计划后自动生成）。",
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
                                    Text(" 恢复")
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
            title = { Text("恢复服务器备份") },
            text = { Text("将用 $restoreName 合并恢复书库、进度、分类与设置。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestoreName = null
                    viewModel.restoreFile(restoreName)
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreName = null }) { Text("取消") }
            },
        )
    }

    state.restoreResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTransient() },
            title = { Text("恢复完成") },
            text = {
                Column {
                    Text(
                        "漫画 ${result.restored.library} · 进度 ${result.restored.progress} · 分类 ${result.restored.categories} · 设置 ${result.restored.settings} · 图源设置 ${result.restored.sourceSettings}",
                    )
                    result.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeTransient() }) { Text("好") }
            },
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

package org.sumidoku.android.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.Download
import org.sumidoku.android.core.api.model.DownloadStatus
import org.sumidoku.android.core.common.relativeEpochMs

/** Downloads queue screen — preview 09-downloads.html (Tier A). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.running) {
                        IconButton(onClick = { viewModel.stop() }) {
                            Icon(Icons.Outlined.Pause, contentDescription = "暂停队列")
                        }
                    } else {
                        IconButton(onClick = { viewModel.start() }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "开始队列")
                        }
                    }
                    TextButton(onClick = { viewModel.clearFinished() }) { Text("清理已完成") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.downloads.isEmpty() && state.local.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                    }
                }
            state.downloads.isEmpty() && state.local.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("下载队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.local.isNotEmpty()) {
                    item(key = "local-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "本机离线（${state.local.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "占用 ${formatSize(state.storageUsedBytes)} · 剩余 ${formatSize(state.storageFreeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            var confirmClear by remember { mutableStateOf(false) }
                            TextButton(onClick = { confirmClear = true }) { Text("全部清除") }
                            if (confirmClear) {
                                AlertDialog(
                                    onDismissRequest = { confirmClear = false },
                                    title = { Text("清除全部本机离线内容？") },
                                    text = { Text("将删除设备上所有已下载的章节档案与解压页面。") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            confirmClear = false
                                            viewModel.clearAllLocal()
                                        }) { Text("清除") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmClear = false }) { Text("取消") }
                                    },
                                )
                            }
                        }
                    }
                    items(state.local, key = { "local-${it.chapterId}" }) { row ->
                        LocalDownloadRow(
                            row = row,
                            onRemove = { viewModel.removeLocal(row.chapterId) },
                            onRetry = { viewModel.retryLocal(row.chapterId) },
                        )
                    }
                    item(key = "server-header") {
                        Text(
                            "服务器队列",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                items(state.downloads, key = { it.chapterId }) { download ->
                    DownloadRow(
                        download = download,
                        onDelete = { viewModel.delete(download.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalDownloadRow(
    row: org.sumidoku.android.core.download.LocalDownloadEntity,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    ListItem(
        leadingContent = {
            when (row.status) {
                "done" -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                "error" -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        headlineContent = { Text("${row.mangaTitle}  ·  ${row.chapterLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                when (row.status) {
                    "done" -> "已存到本机 · ${formatBytes(row.bytes)}"
                    "error" -> row.error ?: "下载失败"
                    "downloading" -> "下载中…"
                    else -> "排队中"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.status == "error") {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除本机副本")
                }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun DownloadRow(download: Download, onDelete: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(statusIcon(download.status), contentDescription = null, tint = statusTint(download.status))
        },
        headlineContent = {
            Text(
                buildString {
                    append(download.mangaTitle)
                    download.chapterNumber?.let { append("  ·  第 $it 话") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                if (download.status == DownloadStatus.DOWNLOADING &&
                    download.downloaded != null && download.total != null && download.total!! > 0
                ) {
                    LinearProgressIndicator(
                        progress = { (download.downloaded!!.toFloat() / download.total!!.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                Text(
                    buildString {
                        append(statusLabel(download))
                        append("  ·  ${relativeEpochMs(download.updatedAt)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (download.status == DownloadStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private fun statusIcon(status: DownloadStatus): ImageVector = when (status) {
    DownloadStatus.QUEUED -> Icons.Outlined.HourglassTop
    DownloadStatus.DOWNLOADING -> Icons.Outlined.CloudDownload
    DownloadStatus.FINISHED -> Icons.Outlined.CheckCircle
    DownloadStatus.ERROR -> Icons.Outlined.ErrorOutline
    else -> Icons.Outlined.HourglassTop
}

@Composable
private fun statusTint(status: DownloadStatus) = when (status) {
    DownloadStatus.FINISHED -> MaterialTheme.colorScheme.primary
    DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(download: Download): String = when (download.status) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.DOWNLOADING -> "下载中 ${download.downloaded ?: 0}/${download.total ?: "?"} 页"
    DownloadStatus.FINISHED -> "已完成"
    DownloadStatus.ERROR -> "失败：${download.error ?: "未知错误"}"
    else -> download.status.value
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

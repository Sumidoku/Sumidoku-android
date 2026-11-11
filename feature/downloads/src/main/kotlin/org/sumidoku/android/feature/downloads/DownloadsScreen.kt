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
import androidx.compose.runtime.remember
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
            state.error != null && state.downloads.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                    }
                }
            state.downloads.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("下载队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
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

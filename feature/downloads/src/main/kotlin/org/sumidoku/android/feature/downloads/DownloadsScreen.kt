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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.Download
import org.sumidoku.android.core.api.model.DownloadStatus
import org.sumidoku.android.core.common.hasChapterNumber
import org.sumidoku.android.core.common.relativeEpochMs

/** Downloads queue screen — preview 09-downloads.html (Tier A). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenCache: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var confirmClearAll by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.downloads_back),
                        )
                    }
                },
                actions = {
                    if (state.running) {
                        IconButton(onClick = { viewModel.stop() }) {
                            Icon(
                                Icons.Outlined.Pause,
                                contentDescription = stringResource(R.string.downloads_pause_queue),
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.start() }) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(R.string.downloads_start_queue),
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.clearFinished() }) {
                        Text(stringResource(R.string.downloads_clear_finished))
                    }
                },
            )
        },

    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Design 09: a two-chip switch instead of two stacked sections, so
            // the server queue stays short and local copies get their own space.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(stringResource(R.string.downloads_tab_server_queue)) },
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = {
                        Text(
                            if (state.local.isNotEmpty()) {
                                stringResource(R.string.downloads_tab_local_with_count, state.local.size)
                            } else {
                                stringResource(R.string.downloads_tab_local)
                            },
                        )
                    },
                )
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tab == 0 -> if (state.downloads.isEmpty()) {
                    EmptyDownloads(state.error, onRetry = viewModel::refresh)
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.downloads, key = { it.chapterId }) { download ->
                            DownloadRow(
                                download = download,
                                onDelete = { viewModel.delete(download.chapterId) },
                                onRetry = { viewModel.retry(download.chapterId) },
                            )
                        }
                        item(key = "cache-card") {
                            Spacer(Modifier.height(8.dp))
                            if (state.cacheUsedBytes > 0 || state.cacheChapters > 0) {
                                ChapterCacheCard(
                                    chapters = state.cacheChapters,
                                    usedBytes = state.cacheUsedBytes,
                                    maxBytes = state.cacheMaxBytes,
                                    onManage = onOpenCache,
                                )
                            }
                        }
                    }
                }
                else -> if (state.local.isEmpty()) {
                    EmptyDownloads(state.error, onRetry = viewModel::refresh)
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        item(key = "local-header") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(
                                            R.string.downloads_storage_usage,
                                            formatSize(state.storageUsedBytes),
                                            formatSize(state.storageFreeBytes),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { confirmClearAll = true }) {
                                    Text(stringResource(R.string.downloads_clear_all))
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
                    }
                }
            }
        }
        if (confirmClearAll) {
            AlertDialog(
                onDismissRequest = { confirmClearAll = false },
                title = { Text(stringResource(R.string.downloads_clear_all_dialog_title)) },
                text = { Text(stringResource(R.string.downloads_clear_all_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClearAll = false
                        viewModel.clearAllLocal()
                    }) { Text(stringResource(R.string.downloads_clear)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearAll = false }) {
                        Text(stringResource(R.string.downloads_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyDownloads(error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                error ?: stringResource(R.string.downloads_empty_queue),
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.downloads_retry)) }
            }
        }
    }
}

/** Design 09 bottom card: server chapter-page cache accounting. */
@Composable
private fun ChapterCacheCard(
    chapters: Long,
    usedBytes: Long,
    maxBytes: Long,
    onManage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.downloads_cache_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onManage) { Text(stringResource(R.string.downloads_manage)) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    stringResource(R.string.downloads_cache_chapters, chapters),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatSize(usedBytes)} / ${formatSize(maxBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val fraction = if (maxBytes > 0) (usedBytes.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
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
                    "done" -> stringResource(R.string.downloads_local_saved, formatBytes(row.bytes))
                    "error" -> row.error ?: stringResource(R.string.downloads_local_failed)
                    "downloading" -> stringResource(R.string.downloads_status_downloading)
                    else -> stringResource(R.string.downloads_status_queued)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.status == "error") {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.downloads_retry)) }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.downloads_delete_local_copy),
                    )
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
private fun DownloadRow(download: Download, onDelete: () -> Unit, onRetry: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(statusIcon(download.status), contentDescription = null, tint = statusTint(download.status))
        },
        headlineContent = {
            val chapterNumber = download.chapterNumber?.takeIf(::hasChapterNumber)
            val headline = if (chapterNumber != null) {
                stringResource(R.string.downloads_headline_with_chapter, download.mangaTitle, chapterNumber)
            } else {
                download.mangaTitle
            }
            Text(
                headline,
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
                val status = statusLabel(download)
                Text(
                    "$status  ·  ${relativeEpochMs(download.updatedAt)}",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (download.status == DownloadStatus.ERROR) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.downloads_retry)) }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.downloads_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

@Composable
private fun statusLabel(download: Download): String = when (download.status) {
    DownloadStatus.QUEUED -> stringResource(R.string.downloads_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(
        R.string.downloads_status_downloading_pages,
        download.downloaded ?: 0,
        download.total?.toString() ?: "?",
    )
    DownloadStatus.FINISHED -> stringResource(R.string.downloads_status_finished)
    DownloadStatus.ERROR -> {
        val reason = download.error ?: stringResource(R.string.downloads_error_unknown)
        stringResource(R.string.downloads_status_error, reason)
    }
    else -> download.status.value
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
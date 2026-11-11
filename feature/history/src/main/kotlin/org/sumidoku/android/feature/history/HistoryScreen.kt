package org.sumidoku.android.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.HistoryEntry
import org.sumidoku.android.core.common.relativeEpochMs

/** History tab — preview 11-history.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenManga: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last != null && last >= total - 4) viewModel.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.error != null && state.entries.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                        }
                    }
                !state.loading && state.entries.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有阅读记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(state.entries, key = { _, e -> "${e.chapter.id}:${e.lastReadAt}" }) { _, entry ->
                        HistoryRow(
                            entry = entry,
                            baseUrl = state.profile?.baseUrl.orEmpty(),
                            apiKey = state.profile?.apiKey,
                            onClick = { onOpenManga(entry.manga.id) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    baseUrl: String,
    apiKey: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val coverModel = entry.manga.cover?.let { cover ->
        val url = if (cover.startsWith("http")) cover else baseUrl.trimEnd('/') + cover
        ImageRequest.Builder(context).data(url).apply {
            apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
        }.build()
    }
    ListItem(
        leadingContent = {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.size(42.dp, 56.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        },
        headlineContent = {
            Text(entry.manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                buildString {
                    entry.chapter.chapterNumber?.let { append("第 $it 话  ·  ") }
                    append("读到第 ${entry.lastPageRead + 1} 页")
                    if (entry.isRead) append("  ·  已读完")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                relativeEpochMs(entry.lastReadAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

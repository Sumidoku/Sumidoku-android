package org.sumidoku.android.feature.updates

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
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
import java.net.URLEncoder
import org.sumidoku.android.core.api.model.RecentEntry
import org.sumidoku.android.core.common.relativeEpochMs
import org.sumidoku.android.core.model.ServerProfile

/** Updates tab — summary card + recent chapters feed.
 *  Preview 10-updates.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onOpenManga: (Long) -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
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
                title = { Text("更新") },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = "下载队列")
                    }
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                val s = state.summary
                                Text(
                                    if (s?.running == true) "正在更新书库…" else "书库更新",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    when {
                                        s == null -> "—"
                                        s.running -> "${s.complete}/${s.total} 完成"
                                        else -> "上次运行 ${s.lastRunAt?.let { relativeEpochMs(it) } ?: "—"} · 失败 ${s.failed} · 跳过 ${s.skipped}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.fetching) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            } else {
                                TextButton(onClick = { viewModel.fetchNow() }) { Text("立即检查") }
                            }
                        }
                    }
                }
                if (state.error != null && state.entries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                            }
                        }
                    }
                }
                if (!state.loading && state.entries.isEmpty() && state.error == null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("没有新章节", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(state.entries, key = { _, e -> "${e.chapter.id}:${e.fetchedAt}" }) { _, entry ->
                    ListItem(
                        leadingContent = { UpdateCover(entry, state.profile) },
                        headlineContent = {
                            Text(entry.manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                buildString {
                                    entry.chapter.chapterNumber?.let { append("第 $it 话  ·  ") }
                                    entry.chapter.title?.let { append(it) }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Text(
                                relativeEpochMs(entry.fetchedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { onOpenManga(entry.manga.id) },
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

/** Leading 2:3 cover of one update row — same proxied endpoint as webui
 *  UpdatesPage (`coverUrl(manga.id, manga.cover)`), so local-source manga
 *  get a cover too and the cache-busting `?v=` follows the source cover. */
@Composable
private fun UpdateCover(entry: RecentEntry, profile: ServerProfile?) {
    AsyncImage(
        model = coverRequest(LocalContext.current, entry.manga.id, entry.manga.cover, profile),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 48.dp, height = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun coverRequest(
    context: Context,
    mangaId: Long,
    cover: String?,
    profile: ServerProfile?,
): Any? {
    if (profile == null) return null
    val url = buildString {
        append(profile.baseUrl.trimEnd('/'))
        append("/api/v1/manga/").append(mangaId).append("/cover")
        cover?.let { append("?v=").append(URLEncoder.encode(it, "UTF-8")) }
    }
    return ImageRequest.Builder(context)
        .data(url)
        .apply {
            profile.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }
        .build()
}

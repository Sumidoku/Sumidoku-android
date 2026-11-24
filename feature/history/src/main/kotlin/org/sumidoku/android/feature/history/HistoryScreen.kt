package org.sumidoku.android.feature.history

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.HistoryEntry
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.clockEpochMs
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.common.dayBucket
import org.sumidoku.android.core.common.dayHeader
import org.sumidoku.android.core.common.normalizeEpochMs

/**
 * History tab — preview 11-history.html: day headers (`Today · Nov 11`),
 * rows `Manga title · Chapter N` + `Read 7 / 24 pages · 21:12` / `Finished · 20:47`,
 * a continue/finished tag, and a tap that resumes the chapter in the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenManga: (Long) -> Unit,
    onOpenChapter: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
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

    val groups = remember(state.entries) {
        state.entries.groupBy { dayBucket(it.lastReadAt) }
            .toSortedMap(compareByDescending { it })
            .map { (day, dayEntries) -> day to dedupeHistory(dayEntries).sortedByDescending { it.entry.lastReadAt } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.history_refresh),
                        )
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
                            TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.history_retry)) }
                        }
                    }
                !state.loading && state.entries.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.history_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    groups.forEach { (day, rows) ->
                        item(key = "header-$day") {
                            SectionHeader(dayHeader(rows.firstOrNull()?.entry?.lastReadAt ?: 0L))
                        }
                        items(rows, key = { "${it.entry.chapter.id}:${it.entry.lastReadAt}" }) { row ->
                            HistoryRow(
                                entry = row.entry,
                                extra = row.extra,
                                baseUrl = state.profile?.baseUrl.orEmpty(),
                                apiKey = state.profile?.apiKey,
                                onClick = {
                                    if (row.entry.isRead) {
                                        onOpenManga(row.entry.manga.id)
                                    } else {
                                        onOpenChapter(row.entry.manga.id, row.entry.chapter.id)
                                    }
                                },
                            )
                        }
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

private data class HistoryRowData(val entry: HistoryEntry, val extra: Int)

/** One manga per local calendar day: the newest read wins, the rest collapse
 *  into `N more` — same rule as webui HistoryPage. */
private fun dedupeHistory(entries: List<HistoryEntry>): List<HistoryRowData> {
    val groups = LinkedHashMap<Long, HistoryRowData>()
    for (e in entries) {
        val cur = groups[e.manga.id]
        groups[e.manga.id] = if (cur == null) {
            HistoryRowData(e, 0)
        } else {
            HistoryRowData(
                if (normalizeEpochMs(e.lastReadAt) > normalizeEpochMs(cur.entry.lastReadAt)) e else cur.entry,
                cur.extra + 1,
            )
        }
    }
    return groups.values.toList()
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    extra: Int,
    baseUrl: String,
    apiKey: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // Same as webui coverUrl(manga.id, manga.cover): always the manga cover
    // endpoint so local-source manga (cover == null) still resolve.
    val coverModel = if (baseUrl.isBlank()) null else ImageRequest.Builder(context)
        .data(coverUrl(baseUrl, entry.manga.id, entry.manga.cover))
        .apply {
            apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
        }.build()
    val read = entry.isRead || (entry.chapter.pageCount > 0 && entry.lastPageRead + 1 >= entry.chapter.pageCount)
    ListItem(
        leadingContent = {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.size(44.dp, 60.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        },
        headlineContent = {
            Text(
                entry.manga.title + " · " +
                    chapterLabel(entry.chapter.volumeNumber, entry.chapter.chapterNumber, entry.chapter.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            // Progress line: read pages (or finished) + clock, then the
            // collapsed-count suffix. Kept as whole formatted strings so the
            // separator and word order stay translatable per locale.
            val clock = clockEpochMs(entry.lastReadAt)
            val progress = when {
                read -> stringResource(R.string.history_progress_finished, clock)
                entry.chapter.pageCount > 0 -> stringResource(
                    R.string.history_progress_reading,
                    entry.lastPageRead + 1,
                    entry.chapter.pageCount,
                    clock,
                )
                else -> stringResource(
                    R.string.history_progress_reading_unknown,
                    entry.lastPageRead + 1,
                    clock,
                )
            }
            Text(
                if (extra > 0) {
                    progress + " · " + stringResource(R.string.history_more_entries, extra)
                } else {
                    progress
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            val tagColor = if (read) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
            val textColor = if (read) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            Text(
                if (read) {
                    stringResource(R.string.history_tag_finished)
                } else {
                    stringResource(R.string.history_tag_continue)
                },
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(tagColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        },
        modifier = Modifier.clickable { onClick() },
    )
}

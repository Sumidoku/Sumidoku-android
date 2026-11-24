package org.sumidoku.android.feature.updates

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.Chapter
import org.sumidoku.android.core.api.model.RecentEntry
import org.sumidoku.android.core.api.model.UpdateSummary
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.common.dayBucket
import org.sumidoku.android.core.common.dayHeader
import org.sumidoku.android.core.common.relativeEpochMs
import org.sumidoku.android.core.model.ServerProfile

/**
 * Updates tab — day-grouped new-chapter feed with a run summary card.
 * Preview 10-updates.html: section headers (`Today · 5 series with new chapters`),
 * rows of `Manga title / Chapter N · 2 hours ago`, trailing new-chapter badge, and
 * the update summary card at the top (skip rules live on the server).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onOpenManga: (Long) -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last != null && last >= total - 4) viewModel.loadMore()
            }
    }

    val groups = remember(state.entries) {
        state.entries.groupBy { dayBucket(it.fetchedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (day, dayEntries) -> day to buildRows(dayEntries) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = stringResource(R.string.updates_downloads),
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.updates_refresh),
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (state.summary?.running == true) {
                    item { RunningCard(state.summary) }
                }
                // Run recap leads the feed (top), not a footer after it.
                item { SummaryCard(state.summary) }
                if (state.error != null && state.entries.isEmpty()) {
                    item { FeedError(state.error!!) { viewModel.refresh() } }
                }
                if (!state.loading && state.entries.isEmpty() && state.error == null) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.updates_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                groups.forEach { (day, rows) ->
                    item(key = "header-$day") {
                        SectionHeader(
                            title = dayHeader(rows.firstOrNull()?.fetchedAt ?: System.currentTimeMillis()),
                            hint = stringResource(R.string.updates_section_hint, rows.size),
                        )
                    }
                    items(rows, key = { "row-${it.mangaId}-$day" }) { row ->
                        UpdateRowItem(row, state.profile) { onOpenManga(row.mangaId) }
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

/** One manga inside a day group: newest chapter plus how many landed that day. */
private data class UpdateRowItemData(
    val mangaId: Long,
    val title: String,
    val cover: String?,
    val chapter: Chapter,
    val fetchedAt: Long,
    val newCount: Int,
)

private fun buildRows(entries: List<RecentEntry>): List<UpdateRowItemData> =
    entries.groupBy { it.manga.id }
        .map { (mangaId, list) ->
            val newest = list.maxByOrNull { it.fetchedAt } ?: list.first()
            UpdateRowItemData(
                mangaId = mangaId,
                title = newest.manga.title,
                cover = newest.manga.cover,
                chapter = newest.chapter,
                fetchedAt = newest.fetchedAt,
                newCount = list.size,
            )
        }
        .sortedByDescending { it.fetchedAt }

@Composable
private fun SectionHeader(title: String, hint: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (hint != null) {
            Spacer(Modifier.weight(1f))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateRowItem(row: UpdateRowItemData, profile: ServerProfile?, onClick: () -> Unit) {
    ListItem(
        leadingContent = { UpdateCover(row, profile) },
        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                chapterLabel(row.chapter.volumeNumber, row.chapter.chapterNumber, row.chapter.title) +
                    " · " + relativeEpochMs(row.fetchedAt),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (row.newCount > 1) {
                Text(
                    stringResource(R.string.updates_chapter_count, row.newCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        },
        modifier = Modifier.clickable { onClick() },
    )
}

/** Live run progress — only shown while a library update is in flight. */
@Composable
private fun RunningCard(summary: UpdateSummary?) {
    val s = summary ?: return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.updates_running_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.updates_running_progress,
                        s.complete,
                        s.total,
                        s.failed,
                        s.skipped,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Update summary — the run recap card from preview 10-updates.html. */
@Composable
private fun SummaryCard(summary: UpdateSummary?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                stringResource(R.string.updates_summary_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (summary == null) {
                    stringResource(R.string.updates_summary_never_run)
                } else {
                    stringResource(
                        R.string.updates_summary_body,
                        summary.lastRunAt?.let { relativeEpochMs(it) } ?: "—",
                        summary.total,
                        summary.complete,
                        summary.failed,
                        summary.skipped,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.updates_retry)) }
        }
    }
}

/** Leading 2:3 cover of one update row — same proxied endpoint as webui
 *  UpdatesPage (`coverUrl(manga.id, manga.cover)`), so local-source manga
 *  get a cover too and the cache-busting `?v=` follows the source cover. */
@Composable
private fun UpdateCover(row: UpdateRowItemData, profile: ServerProfile?) {
    AsyncImage(
        model = coverRequest(LocalContext.current, row.mangaId, row.cover, profile),
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
    return ImageRequest.Builder(context)
        .data(coverUrl(profile.baseUrl, mangaId, cover))
        .apply {
            profile.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }
        .build()
}

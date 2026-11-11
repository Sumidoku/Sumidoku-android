package org.sumidoku.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.collectLatest
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.api.model.LibrarySort
import org.sumidoku.android.core.model.ServerProfile

/** Library grid over GET /api/v1/library — see design doc M1 and the
 *  preview screen 02-library.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenManga: (Long) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val categoryTitle = when {
        state.groupId != null -> state.filterGroups.firstOrNull { it.id == state.groupId }?.name ?: "过滤组"
        state.categoryId != null -> state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "书库"
        else -> "书库"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSheet = true },
                    ) {
                        Text(categoryTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择分类")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            sortLabels.forEach { (sort, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (sort == state.sort) "✓ $label" else label) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSort(sort)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.entries.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                        }
                    }
                state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("书库为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LibraryGrid(state.entries, state.profile, state.loadingMore, onOpenManga) {
                    viewModel.loadMore()
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Text(
                "分类",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("全部") },
                supportingContent = {
                    Text("${state.categories.sumOf { it.mangaCount }} 部", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingContent = { if (state.categoryId == null && state.groupId == null) Text("✓") },
                modifier = Modifier.clickable {
                    showSheet = false
                    viewModel.selectCategory(null)
                },
            )
            state.categories.forEach { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = {
                        Text("${category.mangaCount} 部", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { if (state.categoryId == category.id) Text("✓") },
                    modifier = Modifier.clickable {
                        showSheet = false
                        viewModel.selectCategory(category.id)
                    },
                )
            }
            if (state.filterGroups.isNotEmpty()) {
                Text(
                    "过滤组",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                state.filterGroups.forEach { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        trailingContent = { if (state.groupId == group.id) Text("✓") },
                        modifier = Modifier.clickable {
                            showSheet = false
                            viewModel.selectGroup(group.id)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val sortLabels: List<Pair<LibrarySort, String>> = listOf(
    LibrarySort.LATEST_CHAPTER to "最近更新",
    LibrarySort.LAST_READ to "最近阅读",
    LibrarySort.ADDED to "添加时间",
    LibrarySort.TITLE to "标题",
    LibrarySort.UNREAD to "未读数",
)

@Composable
private fun LibraryGrid(
    entries: List<LibraryEntry>,
    profile: ServerProfile?,
    loadingMore: Boolean,
    onOpenManga: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, entries.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collectLatest { last ->
                if (last != null && last >= entries.size - 6) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            MangaCell(entry, profile, onClick = { onOpenManga(entry.id) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun MangaCell(entry: LibraryEntry, profile: ServerProfile?, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = coverRequest(entry.cover, profile),
                contentDescription = entry.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            if (entry.unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                ) {
                    Text(
                        "${entry.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(
            entry.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun coverRequest(cover: String?, profile: ServerProfile?): Any? {
    if (cover == null || profile == null) return null
    val url = if (cover.startsWith("http")) cover else profile.baseUrl.trimEnd('/') + cover
    return ImageRequest.Builder(LocalContext.current)
        .data(url)
        .apply {
            profile.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }
        .build()
}

package org.sumidoku.android.feature.library

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest
import org.sumidoku.android.core.ui.AppModalDialog
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.api.model.LibraryFilter
import org.sumidoku.android.core.api.model.LibrarySort
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.data.FORM_COMPACT
import org.sumidoku.android.core.data.FORM_COMFORTABLE
import org.sumidoku.android.core.data.FORM_LIST
import org.sumidoku.android.core.data.LIBRARY_COLUMN_CHOICES
import org.sumidoku.android.core.data.LibraryDisplay
import org.sumidoku.android.core.model.ServerProfile

/** Library grid over GET /api/v1/library — see design doc M1 and the
 *  preview screen 02-library.html. Toolbar, filter panel and category sheet
 *  mirror webui LibraryPage.tsx + LibraryToolbar.tsx. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenManga: (Long) -> Unit = {},
    onBrowseSources: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }
    // Batch mode's own dialogs: the category picker and the remove confirmation.
    var showBatchCategorySheet by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // The filter sheet has two pages: the panel and one dimension's option list.
    var sheetDimension by remember { mutableStateOf<LibraryFilterDimension?>(null) }

    // Columns are remembered per orientation, so the sheet edits the value that
    // this configuration actually uses (design 34 note).
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val chips = activeFilterChips(state, viewModel)

    // One batch result at a time; the ViewModel clears the field once shown.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.clearNotice()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                // Batch mode (design 23): the bar only carries the count and the
                // select-all toggle; every other action would undercut the mode.
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_exit_batch_mode))
                        }
                    },
                    title = { Text(stringResource(R.string.library_selected_count, state.selected.size)) },
                    actions = {
                        val allSelected = state.entries.isNotEmpty() && state.selected.size >= state.entries.size
                        TextButton(onClick = { viewModel.selectAll(!allSelected) }) {
                            Text(stringResource(if (allSelected) R.string.library_deselect_all else R.string.library_select_all))
                        }
                    },
                )
            } else {
                TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showCategorySheet = true },
                    ) {
                        Text(stringResource(R.string.library_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.library_choose_category))
                    }
                },
                actions = {
                    IconButton(onClick = { showDisplaySheet = true }) {
                        Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.library_display))
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.library_sort))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            sortOrder.forEach { sort ->
                                val label = sortLabel(sort)
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
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (state.activeFilterCount > 0) {
                                    Badge { Text("${state.activeFilterCount}") }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.library_filter))
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.library_refresh))
                    }
                },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                BatchActionBar(
                    busy = state.batchBusy,
                    count = state.selected.size,
                    onCategories = { showBatchCategorySheet = true },
                    onMarkRead = { viewModel.batchMarkRead() },
                    onRemove = { showRemoveDialog = true },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (chips.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chips, key = { it.key }) { chip ->
                        InputChip(
                            selected = true,
                            onClick = chip.onRemove,
                            label = { Text(chip.label) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.library_remove_chip, chip.label),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize().weight(1f),
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
                                TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.library_retry)) }
                            }
                        }
                    state.entries.isEmpty() -> LibraryEmptyState(
                        filtered = state.hasAnyFilter,
                        filterLabels = chips.map { it.label },
                        onClear = { viewModel.clearAllFilters() },
                        onBrowseSources = onBrowseSources,
                    )
                    else -> LibraryContent(
                        state = state,
                        landscape = landscape,
                        onOpenManga = onOpenManga,
                        onSelect = { viewModel.toggleSelected(it) },
                        onBeginSelection = { viewModel.beginSelection(it) },
                        onLoadMore = { viewModel.loadMore() },
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        AppModalDialog(onDismissRequest = { showCategorySheet = false }) {
            CategorySheetContent(
                state = state,
                onCycleCategory = { viewModel.cycleCategory(it) },
                onReset = { viewModel.resetCategories() },
                onSelectGroup = { viewModel.setGroupId(it) },
                onDone = { showCategorySheet = false },
            )
        }
    }

    if (showFilterSheet) {
        AppModalDialog(
            onDismissRequest = {
                showFilterSheet = false
                sheetDimension = null
            },
        ) {
            val dimension = sheetDimension
            if (dimension == null) {
                FilterPanelContent(
                    state = state,
                    onCycleStatus = { viewModel.cycleStatusFilter(it) },
                    onOpenDimension = { sheetDimension = it },
                    onClear = { viewModel.clearFilters() },
                    onDone = {
                        showFilterSheet = false
                        sheetDimension = null
                    },
                )
            } else {
                DimensionSheetContent(
                    dimension = dimension,
                    state = state,
                    onCycle = { key -> viewModel.cycleDimension(dimension, key) },
                    onReset = { viewModel.resetDimension(dimension) },
                    onBack = { sheetDimension = null },
                    onDone = {
                        showFilterSheet = false
                        sheetDimension = null
                    },
                )
            }
        }
    }

    if (showDisplaySheet) {
        AppModalDialog(onDismissRequest = { showDisplaySheet = false }) {
            DisplaySheet(
                display = state.display,
                landscape = landscape,
                onForm = { viewModel.setForm(it) },
                onColumns = { viewModel.setColumns(landscape, it) },
                onBadge = { badge, enabled -> viewModel.setBadge(badge, enabled) },
            )
        }
    }

    if (showBatchCategorySheet) {
        AppModalDialog(onDismissRequest = { showBatchCategorySheet = false }) {
            BatchCategorySheet(
                categories = state.categoryOptions,
                onApply = { picked ->
                    showBatchCategorySheet = false
                    viewModel.batchSetCategories(picked.toList())
                },
                onDismiss = { showBatchCategorySheet = false },
            )
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.library_remove_from_library)) },
            text = { Text(stringResource(R.string.library_remove_confirm_message, state.selected.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        viewModel.batchRemove()
                    },
                ) { Text(stringResource(R.string.library_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

private val sortOrder: List<LibrarySort> = listOf(
    LibrarySort.ADDED,
    LibrarySort.TITLE,
    LibrarySort.LAST_READ,
    LibrarySort.LATEST_CHAPTER,
    LibrarySort.UNREAD,
)

@Composable
private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.ADDED -> stringResource(R.string.library_sort_added)
    LibrarySort.TITLE -> stringResource(R.string.library_sort_title)
    LibrarySort.LAST_READ -> stringResource(R.string.library_sort_last_read)
    LibrarySort.LATEST_CHAPTER -> stringResource(R.string.library_sort_latest_chapter)
    LibrarySort.UNREAD -> stringResource(R.string.library_sort_unread)
}

/** webui FILTER_OPTIONS order. */
private val statusFilterKeys: List<LibraryFilter> = listOf(
    LibraryFilter.UNREAD,
    LibraryFilter.COMPLETED,
    LibraryFilter.TRACKED,
    LibraryFilter.BOOKMARKED,
    LibraryFilter.DOWNLOADED,
)

@Composable
private fun statusLabel(key: String): String = when (key) {
    LibraryFilter.UNREAD.value -> stringResource(R.string.library_status_unread)
    LibraryFilter.COMPLETED.value -> stringResource(R.string.library_status_completed)
    LibraryFilter.TRACKED.value -> stringResource(R.string.library_status_tracked)
    LibraryFilter.BOOKMARKED.value -> stringResource(R.string.library_status_bookmarked)
    LibraryFilter.DOWNLOADED.value -> stringResource(R.string.library_status_downloaded)
    else -> key
}

/** Manga content rating levels (1/2/3) — not the source-level 0/1/2 scale. */
@Composable
private fun ratingLabel(key: String): String = when (key) {
    "1" -> stringResource(R.string.library_rating_safe)
    "2" -> stringResource(R.string.library_rating_suggestive)
    "3" -> stringResource(R.string.library_rating_nsfw)
    else -> key
}

@Composable
private fun dimensionTitle(dimension: LibraryFilterDimension): String = when (dimension) {
    LibraryFilterDimension.RATINGS -> stringResource(R.string.library_dimension_ratings)
    LibraryFilterDimension.SOURCES -> stringResource(R.string.library_dimension_sources)
    LibraryFilterDimension.AUTHORS -> stringResource(R.string.library_dimension_authors)
    LibraryFilterDimension.TAGS -> stringResource(R.string.library_dimension_tags)
}

private fun dimensionSelection(
    dimension: LibraryFilterDimension,
    state: LibraryUiState,
): Map<String, LibraryTri> = when (dimension) {
    LibraryFilterDimension.RATINGS -> state.ratings
    LibraryFilterDimension.SOURCES -> state.sources
    LibraryFilterDimension.AUTHORS -> state.authors
    LibraryFilterDimension.TAGS -> state.tags
}

private data class TriOption(val key: String, val label: String, val count: Long? = null)

@Composable
private fun dimensionOptions(
    dimension: LibraryFilterDimension,
    state: LibraryUiState,
): List<TriOption> = when (dimension) {
    LibraryFilterDimension.RATINGS -> listOf(
        TriOption("1", stringResource(R.string.library_rating_safe)),
        TriOption("2", stringResource(R.string.library_rating_suggestive)),
        TriOption("3", stringResource(R.string.library_rating_nsfw)),
    )
    LibraryFilterDimension.SOURCES -> {
        val counts = state.sourceCounts.associate { it.sourceId.toString() to it.count }
        state.sourceOptions.mapNotNull { source ->
            val key = source.id.toString()
            val count = counts[key]
            val hasEntries = (count ?: 0L) > 0
            // A source without entries could only ever narrow the library to
            // nothing, so it hides — unless it is already selected, which must
            // stay removable. Before the counts arrive every source is shown.
            if (state.sourceCountsLoaded && !hasEntries && !state.sources.containsKey(key)) {
                null
            } else {
                TriOption(key, source.name, count)
            }
        }
    }
    LibraryFilterDimension.AUTHORS -> state.authorCounts.map { TriOption(it.name, it.name, it.count) }
    LibraryFilterDimension.TAGS -> state.tagCounts.map { TriOption(it.name, it.name, it.count) }
}

private data class ActiveFilterChip(
    val key: String,
    val label: String,
    val onRemove: () -> Unit,
)

/** Removable summary of every active filter except categories,
 *  which own visible controls of their own (webui active count and chips). */
@Composable
private fun activeFilterChips(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
): List<ActiveFilterChip> {
    val sourceNames = remember(state.sourceOptions) {
        state.sourceOptions.associate { it.id.toString() to it.name }
    }
    val groupName = state.filterGroups.firstOrNull { it.id == state.groupId }?.name
    return buildList {
        state.filters.forEach { (key, _) ->
            add(ActiveFilterChip("status-$key", statusLabel(key)) {
                viewModel.removeStatusFilter(key)
            })
        }
        state.ratings.forEach { (key, _) ->
            add(ActiveFilterChip("rating-$key", stringResource(R.string.library_chip_rating, ratingLabel(key))) {
                viewModel.removeSelection(LibraryFilterDimension.RATINGS, key)
            })
        }
        state.sources.forEach { (key, _) ->
            add(ActiveFilterChip("source-$key", stringResource(R.string.library_chip_source, sourceNames[key] ?: key)) {
                viewModel.removeSelection(LibraryFilterDimension.SOURCES, key)
            })
        }
        state.authors.forEach { (key, _) ->
            add(ActiveFilterChip("author-$key", stringResource(R.string.library_chip_author, key)) {
                viewModel.removeSelection(LibraryFilterDimension.AUTHORS, key)
            })
        }
        state.tags.forEach { (key, _) ->
            add(ActiveFilterChip("tag-$key", stringResource(R.string.library_chip_tag, key)) {
                viewModel.removeSelection(LibraryFilterDimension.TAGS, key)
            })
        }
        groupName?.let { name ->
            add(ActiveFilterChip("group", stringResource(R.string.library_chip_group, name)) { viewModel.setGroupId(null) })
        }
    }
}

@Composable
private fun LibraryEmptyState(
    filtered: Boolean,
    filterLabels: List<String>,
    onClear: () -> Unit,
    onBrowseSources: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            // Design 35/36: a rounded art tile instead of a bare line of text, so
            // an empty library reads as "not set up yet" and a filtered miss reads
            // as "your filters are too narrow".
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (filtered) {
                        Icons.Outlined.FilterAlt
                    } else {
                        Icons.AutoMirrored.Outlined.MenuBook
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(
                    if (filtered) R.string.library_empty_filtered_title else R.string.library_empty_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (filtered) {
                    val labels = filterLabels.joinToString(" · ")
                    if (labels.isBlank()) {
                        stringResource(R.string.library_empty_filtered_hint)
                    } else {
                        stringResource(R.string.library_empty_active_filters, labels)
                    }
                } else {
                    stringResource(R.string.library_empty_hint)
                },
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (filtered) {
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.library_clear_filters))
                }
            } else {
                Button(onClick = onBrowseSources) { Text(stringResource(R.string.library_browse_sources)) }
            }
        }
    }
}

/** Shared header of the sheets: title, reset and done. */
@Composable
private fun SheetHeader(
    title: String,
    onReset: () -> Unit,
    onDone: () -> Unit,
    resetLabel: String? = null,
    resetEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.library_back))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = if (onBack == null) 12.dp else 0.dp)
                .weight(1f),
        )
        TextButton(onClick = onReset, enabled = resetEnabled) {
            Text(resetLabel ?: stringResource(R.string.library_reset))
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.library_done)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** One tri-state row: tap cycles none → include → exclude → none. */
@Composable
private fun TriRow(label: String, count: String?, state: LibraryTri, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (state) {
                LibraryTri.INCLUDE -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.library_tri_include),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                LibraryTri.EXCLUDE -> Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.library_tri_exclude),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                LibraryTri.NONE -> Icon(
                    Icons.Outlined.Circle,
                    contentDescription = stringResource(R.string.library_tri_none),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        count?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Status + "more filters" panel; a row opens its dimension page in the same sheet. */
@Composable
private fun FilterPanelContent(
    state: LibraryUiState,
    onCycleStatus: (String) -> Unit,
    onOpenDimension: (LibraryFilterDimension) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = stringResource(R.string.library_filter),
            onReset = onClear,
            onDone = onDone,
            resetLabel = stringResource(R.string.library_clear_filters),
            resetEnabled = state.activeFilterCount > 0,
        )
        HorizontalDivider()
        SectionLabel(stringResource(R.string.library_section_status))
        statusFilterKeys.forEach { filter ->
            val key = filter.value
            TriRow(statusLabel(key), null, state.filters[key] ?: LibraryTri.NONE) {
                onCycleStatus(key)
            }
        }
        SectionLabel(stringResource(R.string.library_more_filters))
        LibraryFilterDimension.entries.forEach { dimension ->
            val selected = dimensionSelection(dimension, state)
            DimensionRow(
                label = dimensionTitle(dimension),
                summary = if (selected.isEmpty()) {
                    stringResource(R.string.library_none_selected)
                } else {
                    stringResource(R.string.library_selected_count, selected.size)
                },
                active = selected.isNotEmpty(),
                onClick = { onOpenDimension(dimension) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DimensionRow(label: String, summary: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            summary,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

/** One dimension's option list (ratings / sources / authors / tags). */
@Composable
private fun DimensionSheetContent(
    dimension: LibraryFilterDimension,
    state: LibraryUiState,
    onCycle: (String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val selection = dimensionSelection(dimension, state)
    val options = dimensionOptions(dimension, state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = dimensionTitle(dimension),
            onReset = onReset,
            onDone = onDone,
            resetEnabled = selection.isNotEmpty(),
            onBack = onBack,
        )
        HorizontalDivider()
        if (options.isEmpty()) {
            Text(
                stringResource(R.string.library_no_options),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            options.forEach { option ->
                TriRow(
                    label = option.label,
                    count = option.count?.let { stringResource(R.string.library_manga_count, it) },
                    state = selection[option.key] ?: LibraryTri.NONE,
                ) { onCycle(option.key) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Category sheet: tri-state multi-select rows, unfiled included, plus the
 *  single-select filter groups (webui keeps groups separate from the tri set
 *  and ANDs them with it). */
@Composable
private fun CategorySheetContent(
    state: LibraryUiState,
    onCycleCategory: (String) -> Unit,
    onReset: () -> Unit,
    onSelectGroup: (Long?) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = stringResource(R.string.library_categories),
            onReset = onReset,
            onDone = onDone,
            resetEnabled = state.categories.isNotEmpty(),
        )
        HorizontalDivider()
        TriRow(
            label = stringResource(R.string.library_uncategorized),
            count = null,
            state = state.categories[UNCATEGORIZED_CATEGORY] ?: LibraryTri.NONE,
        ) { onCycleCategory(UNCATEGORIZED_CATEGORY) }
        state.categoryOptions.forEach { category ->
            val key = category.id.toString()
            TriRow(
                label = category.name,
                count = stringResource(R.string.library_manga_count, category.mangaCount),
                state = state.categories[key] ?: LibraryTri.NONE,
            ) { onCycleCategory(key) }
        }
        if (state.filterGroups.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel(stringResource(R.string.library_filter_groups))
            state.filterGroups.forEach { group ->
                val selected = state.groupId == group.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGroup(if (selected) null else group.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.library_selected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Everything a cell needs besides the entry itself (design 34): the display
 *  choice, the download set for the left-corner mark and the source names. */
private data class CellContext(
    val profile: ServerProfile?,
    val display: LibraryDisplay,
    val downloads: Set<Long>,
    val sources: List<Source>,
    val selected: Boolean = false,
    val selectionMode: Boolean = false,
    val onClick: () -> Unit = {},
    val onLongClick: () -> Unit = {},
)

/** One cell's wiring; keeps the three cell composables to (entry, context). */
private fun LibraryUiState.cellContext(
    id: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) = CellContext(
    profile = profile,
    display = display,
    downloads = downloads,
    sources = sourceOptions,
    selected = id in selected,
    selectionMode = selectionMode,
    onClick = onClick,
    onLongClick = onLongClick,
)

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    landscape: Boolean,
    onOpenManga: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onBeginSelection: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    when (state.display.form) {
        FORM_LIST -> LibraryList(state, onOpenManga, onSelect, onBeginSelection, onLoadMore)
        else -> LibraryGrid(state, landscape, onOpenManga, onSelect, onBeginSelection, onLoadMore)
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    landscape: Boolean,
    onOpenManga: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onBeginSelection: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val entries = state.entries
    LaunchedEffect(gridState, entries.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collectLatest { last ->
                if (last != null && last >= entries.size - 6) onLoadMore()
            }
    }

    // Auto = adaptive (design 34 note); a fixed choice wins. On tablets
    // (smallest width ≥ 600dp, the Material compact/medium breakpoint) the
    // minimum cell grows to 152dp — KMReader's tablet large grid size
    // (ui/library LibraryScreen: LargeGrid = Adaptive(152.dp)).
    val columns = state.display.columns(landscape)
    val compact = state.display.form == FORM_COMPACT
    val autoMinCell = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 152.dp else 100.dp

    LazyVerticalGrid(
        columns = if (columns == null) GridCells.Adaptive(minSize = autoMinCell) else GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val cell = state.cellContext(
                id = entry.id,
                onClick = { if (state.selectionMode) onSelect(entry.id) else onOpenManga(entry.id) },
                onLongClick = { onBeginSelection(entry.id) },
            )
            if (compact) CompactCell(entry, cell) else ComfortableCell(entry, cell)
        }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    onOpenManga: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onBeginSelection: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val entries = state.entries
    LaunchedEffect(listState, entries.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collectLatest { last ->
                if (last != null && last >= entries.size - 6) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            ListRow(
                entry = entry,
                ctx = state.cellContext(
                    id = entry.id,
                    onClick = { if (state.selectionMode) onSelect(entry.id) else onOpenManga(entry.id) },
                    onLongClick = { onBeginSelection(entry.id) },
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** Compact form (design 02/32, Komikku `MangaCompactGridItem`): the title sits
 *  inside the cover's bottom gradient with a 1dp shadow instead of below it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCell(entry: LibraryEntry, ctx: CellContext) {
    Box(
        Modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(if (ctx.selectionMode && !ctx.selected) 0.45f else 1f)
            .combinedClickable(onClick = ctx.onClick, onLongClick = ctx.onLongClick),
    ) {
        CoverImage(entry, ctx.profile, Modifier.fillMaxSize())
        // komikku CoverTextOverlay / KMReader CardTextOverlay parity: the
        // bottom third deepens to ~85% black and the title is explicit white
        // with a shadow — readable on any cover. The download mark trails
        // the title line (KMReader) instead of a corner chip.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 7.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), blurRadius = 3f),
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ctx.display.badgeDownload && entry.id in ctx.downloads) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(R.string.library_downloaded),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
        }
        if (ctx.selectionMode) SelectionMark(ctx.selected, Modifier.align(Alignment.TopStart)) else CoverMarks(entry, ctx, showDownload = false)
    }
}

/** Comfortable form (design 32): cover plus a two-line title underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComfortableCell(entry: LibraryEntry, ctx: CellContext) {
    Column(
        Modifier
            .alpha(if (ctx.selectionMode && !ctx.selected) 0.45f else 1f)
            .combinedClickable(onClick = ctx.onClick, onLongClick = ctx.onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(entry, ctx.profile, Modifier.fillMaxSize())
            if (ctx.selectionMode) SelectionMark(ctx.selected, Modifier.align(Alignment.TopStart)) else CoverMarks(entry, ctx)
        }
        Text(
            entry.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** List form (design 33): 52dp cover, title plus a one-line subtitle that only
 *  uses fields the library payload actually carries. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(entry: LibraryEntry, ctx: CellContext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ctx.selectionMode && !ctx.selected) 0.45f else 1f)
            .combinedClickable(onClick = ctx.onClick, onLongClick = ctx.onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(entry, ctx.profile, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                librarySubtitle(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ctx.selectionMode) {
                SelectionMark(ctx.selected, Modifier.padding(end = 4.dp))
            } else {
                if (ctx.display.badgeUnread && entry.unreadCount > 0) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(99.dp)) {
                        Text(
                            "${entry.unreadCount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                if (ctx.display.badgeDownload && entry.id in ctx.downloads) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(R.string.library_downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/** Batch-mode circle (design 23): outlined when free, filled with a tick when
 *  picked. */
@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
            )
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.library_selected),
                tint = Color(0xFF10131F),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Cover marks: unread count top-right, download/source top-left (design 02).
 *  Stacked so a manga that is both downloaded and from a foreign source never
 *  draws two marks on top of each other. showDownload=false when the cell
 *  draws the download mark inside the title overlay (compact form). */
@Composable
private fun BoxScope.CoverMarks(entry: LibraryEntry, ctx: CellContext, showDownload: Boolean = true) {
    if (ctx.display.badgeUnread && entry.unreadCount > 0) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Text(
                "${entry.unreadCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
    val sourceName = ctx.sources.firstOrNull { it.id == entry.sourceId }?.name
    Column(
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showDownload && ctx.display.badgeDownload && entry.id in ctx.downloads) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp)) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.library_downloaded),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(13.dp),
                )
            }
        }
        if (ctx.display.badgeSource && sourceName != null) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    sourceName.take(2),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Subtitle from the fields the library payload carries: latest chapter number
 *  plus the unread count (design 33's "Ch. 148 · 9 unread"). */
@Composable
private fun librarySubtitle(entry: LibraryEntry): String {
    val latest = entry.chapters?.mapNotNull { it.chapterNumber }?.maxOrNull()
    val chapter = latest?.let { stringResource(R.string.library_chapter_number, formatChapterNumber(it)) }
        ?: entry.chapters?.firstOrNull()?.title
        ?: stringResource(R.string.library_chapter_count, entry.chapterCount)
    val unread = if (entry.unreadCount > 0) {
        stringResource(R.string.library_unread_count, entry.unreadCount)
    } else {
        stringResource(R.string.library_all_read)
    }
    return "$chapter · $unread"
}

/** `148.0` reads as `148`, `148.5` stays as is. */
private fun formatChapterNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/** Batch bar (design 23): three actions, count in the title, disabled while one
 *  is running so two batches cannot race. */
@Composable
private fun BatchActionBar(
    busy: Boolean,
    count: Int,
    onCategories: () -> Unit,
    onMarkRead: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatchAction(Icons.Outlined.Category, stringResource(R.string.library_set_categories), busy, Modifier.weight(1f), onCategories)
            BatchAction(Icons.Outlined.Check, stringResource(R.string.library_mark_read), busy, Modifier.weight(1f), onMarkRead)
            BatchAction(Icons.Outlined.Delete, stringResource(R.string.library_remove_from_library), busy, Modifier.weight(1f), onRemove, danger = true)
        }
    }
}

@Composable
private fun BatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Category picker for a batch (design 23): a checkbox list; applying replaces
 *  each selected manga's category set, matching the server's PUT semantics. */
@Composable
private fun BatchCategorySheet(
    categories: List<Category>,
    onApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.library_set_categories), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.library_batch_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            picked = if (category.id in picked) picked - category.id else picked + category.id
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (category.id in picked) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (category.id in picked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
            TextButton(
                onClick = { onApply(picked) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (picked.isEmpty()) {
                        stringResource(R.string.library_clear_categories)
                    } else {
                        stringResource(R.string.library_apply_categories, picked.size)
                    },
                )
            }
        }
    }
}

@Composable
private fun DisplaySheet(
    display: LibraryDisplay,
    landscape: Boolean,
    onForm: (String) -> Unit,
    onColumns: (Int?) -> Unit,
    onBadge: (LibraryBadge, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Text(stringResource(R.string.library_display), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormOption(stringResource(R.string.library_form_compact), FORM_COMPACT, display.form, onForm) { CompactPreview() }
            FormOption(stringResource(R.string.library_form_comfortable), FORM_COMFORTABLE, display.form, onForm) { ComfortablePreview() }
            FormOption(stringResource(R.string.library_form_list), FORM_LIST, display.form, onForm) { ListPreview() }
        }
        SectionLabel(stringResource(R.string.library_columns))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LIBRARY_COLUMN_CHOICES.forEach { choice ->
                FilterChip(
                    selected = display.columns(landscape) == choice,
                    onClick = { onColumns(choice) },
                    label = { Text(choice?.let { stringResource(R.string.library_column_count, it) } ?: "Auto") },
                )
            }
        }
        Text(
            stringResource(R.string.library_columns_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        SectionLabel(stringResource(R.string.library_cover_badges))
        SwitchRow(stringResource(R.string.library_badge_unread), display.badgeUnread) { onBadge(LibraryBadge.UNREAD, it) }
        SwitchRow(stringResource(R.string.library_badge_download), display.badgeDownload) { onBadge(LibraryBadge.DOWNLOAD, it) }
        SwitchRow(stringResource(R.string.library_badge_source), display.badgeSource) { onBadge(LibraryBadge.SOURCE, it) }
    }
}

@Composable
private fun RowScope.FormOption(
    label: String,
    form: String,
    selectedForm: String,
    onSelect: (String) -> Unit,
    preview: @Composable () -> Unit,
) {
    val selected = form == selectedForm
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .clickable { onSelect(form) }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { preview() }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CompactPreview() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Box(Modifier.width(7.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(tint.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun ComfortablePreview() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(20.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(tint.copy(alpha = 0.5f)))
                Box(Modifier.padding(top = 3.dp).width(20.dp).height(2.5.dp).clip(RoundedCornerShape(1.dp)).background(tint.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun ListPreview() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(Modifier.width(38.dp).height(7.dp).clip(RoundedCornerShape(2.dp)).background(tint.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Cover with a coloured placeholder and a short fade-in (design 02/32: the
 *  sheet's covers must not flash grey while loading). The placeholder tint is
 *  derived from the entry, so it is stable across scrolls and rebinds — a real
 *  dominant-colour sample would need a decode pass per cell. */
@Composable
private fun CoverImage(entry: LibraryEntry, profile: ServerProfile?, modifier: Modifier = Modifier) {
    var loaded by remember(entry.id) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "coverFade",
    )
    Box(modifier.background(coverPlaceholder(entry))) {
        AsyncImage(
            model = coverRequest(entry.id, entry.cover, profile),
            contentDescription = entry.title,
            modifier = Modifier.fillMaxSize().alpha(alpha),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
        )
    }
}

private fun coverPlaceholder(entry: LibraryEntry): Brush {
    val seed = (entry.title.hashCode() * 31 + entry.id.toInt()) and 0x7fffffff
    val hue = (seed % 360).toFloat()
    return Brush.linearGradient(
        listOf(
            Color.hsv(hue, 0.30f, 0.32f),
            Color.hsv((hue + 26f) % 360f, 0.36f, 0.18f),
        ),
    )
}

@Composable
private fun coverRequest(mangaId: Long, cover: String?, profile: ServerProfile?): Any? {
    if (profile == null) return null
    // Same as webui coverUrl(m.id, m.cover): the server resolves the image
    // (cache / source fetch / local disk), so local-source manga with a null
    // `cover` field still get one.
    return ImageRequest.Builder(LocalContext.current)
        .data(coverUrl(profile.baseUrl, mangaId, cover))
        .apply {
            profile.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }
        .build()
}





package org.sumidoku.android.feature.source

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sumidoku.android.core.ui.AppModalDialog
import org.sumidoku.android.core.api.model.AidokuListing
import org.sumidoku.android.core.api.model.ContentRating
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.FilterValueOneOf1Sort
import org.sumidoku.android.core.api.model.FilterValueOneOf2Check
import org.sumidoku.android.core.api.model.FilterValueOneOf4MultiSelect
import org.sumidoku.android.core.api.model.FilterValueOneOf5Range
import org.sumidoku.android.core.api.model.FilterValueOneOfText
import org.sumidoku.android.core.api.MangaSeedStore
import org.sumidoku.android.core.api.model.HomeComponent
import org.sumidoku.android.core.api.model.HomeFilterItem
import org.sumidoku.android.core.api.model.Link
import org.sumidoku.android.core.api.model.MangaWithChapter
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceFilter
import org.sumidoku.android.core.api.model.SourceManga
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.entryCoverUrl
import org.sumidoku.android.core.common.normalizeEpochMs
import org.sumidoku.android.core.common.relativeEpochMs

/** Source home/listing/search screen — Aidoku SourceHomeContentView +
 *  webui SourceBrowsePage (filter header chips + panel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHomeScreen(
    onBack: () -> Unit,
    /** Aidoku open-first: registered entries pass their id, unregistered
     *  ones pass id=null + key and the detail page registers lazily. */
    onOpenManga: (id: Long?, key: String) -> Unit,
    onOpenSettings: () -> Unit,
    /** Query handed in by navigation (global search "view all"): the view
     *  model already starts in SearchMode for it — this only pre-fills the
     *  search field (webui /browse/{id}?q= parity). */
    initialQuery: String? = null,
    viewModel: SourceHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var searchOpen by remember { mutableStateOf(!initialQuery.isNullOrBlank()) }
    var searchQuery by remember { mutableStateOf(initialQuery.orEmpty()) }
    var filterPanelOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showSourceInfo by remember { mutableStateOf(false) }

    val isLocal = state.source?.kind == Source.Kind.LOCAL
    // AidokuRunner clears filters for a non-empty query when the source
    // declares hidesFiltersWhileSearching; the webui hides the panel then.
    val filtersHidden = state.source?.config?.get("hidesFiltersWhileSearching") == true &&
        searchQuery.isNotBlank()

    // Aidoku parity: activating the search bar switches the body to the
    // browse grid (empty box = unfiltered browse), so the filter button,
    // filter chips and results are actually visible; closing the bar
    // restores the tab that was showing before (home or a listing).
    // searchWasOpen guards the restore branch: LaunchedEffect also fires on
    // the FIRST composition (searchOpen=false), and a deep link lands in
    // SearchMode — restoring then would wrongly yank the grid back to home.
    var preSearchMode by remember { mutableStateOf<SourceMode?>(null) }
    var searchWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            searchWasOpen = true
            if (state.mode !is SourceMode.SearchMode) {
                preSearchMode = state.mode
                viewModel.search(searchQuery)
            }
        } else if (searchWasOpen) {
            searchWasOpen = false
            val prev = preSearchMode
            preSearchMode = null
            if (state.mode is SourceMode.SearchMode) {
                when (prev) {
                    is SourceMode.ListingMode -> viewModel.openListing(prev.listing)
                    else -> viewModel.backToHome()
                }
            }
        }
    }

    // Local-directory filtering is live (webui debounce parity); remote
    // sources wait for the explicit submit. Clearing the box always
    // resubmits, restoring the unsearched results.
    LaunchedEffect(searchQuery, isLocal, searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        val submitted = (state.mode as? SourceMode.SearchMode)?.query.orEmpty()
        if (!isLocal && searchQuery.isNotBlank()) return@LaunchedEffect
        if (searchQuery.trim() == submitted) return@LaunchedEffect
        delay(500)
        viewModel.search(searchQuery.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.source?.name ?: stringResource(R.string.source_title_default),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.source_back),
                        )
                    }
                },
                actions = {
                    // webui parity: the filter button lives inside the search
                    // form, which only renders in search mode (isSearch).
                    if (state.mode is SourceMode.SearchMode && state.filters.isNotEmpty() && !filtersHidden) {
                        IconButton(onClick = { filterPanelOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.source_filter))
                        }
                    }
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.source_search),
                        )
                    }
                    state.source?.baseUrl?.let { url ->
                        val context = LocalContext.current
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Icon(
                                Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.source_open_in_browser),
                            )
                        }
                    }
                    // Sources without a settings schema get no gear icon
                    // (browse list settingsAvailable parity).
                    if (state.hasSettings) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.source_settings))
                        }
                    }
                    // Design 04: source info / open in browser / settings / login
                    // collapse into one overflow once the action row gets crowded.
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.source_more))
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.source_info)) },
                                onClick = {
                                    overflowOpen = false
                                    showSourceInfo = true
                                },
                            )
                            if (state.source?.baseUrl != null) {
                                val context = LocalContext.current
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.source_open_in_browser)) },
                                    onClick = {
                                        overflowOpen = false
                                        val url = state.source?.baseUrl ?: return@DropdownMenuItem
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                )
                            }
                            if (state.hasSettings) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.source_settings_login)) },
                                    onClick = {
                                        overflowOpen = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = {
                        Text(
                            if (isLocal) stringResource(R.string.source_filter_local_placeholder)
                            else stringResource(R.string.source_search_placeholder),
                        )
                    },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) viewModel.search(searchQuery.trim())
                        }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.source_search))
                        }
                    },
                )
            }
            // Aidoku FilterHeaderView: horizontal chip bar, chip edits apply
            // immediately. webui renders it only in search mode (isSearch)
            // and hides it while searching when the source asks for it.
            if (state.mode is SourceMode.SearchMode && state.filters.isNotEmpty() && !filtersHidden) {
                FilterHeader(
                    filters = state.filters,
                    active = state.activeFilters,
                    onEdit = { id, value -> viewModel.setFilter(id, value) },
                    onApply = { viewModel.applyFilters() },
                )
            }
            // Active filters that the source's filter schema does not
            // describe (author/tag deep links, local sources) render as
            // removable chips (webui localFilterChipLabel parity) — keyed by
            // schema absence, not schema emptiness, so they survive the
            // schema arriving instead of vanishing after the refresh lands.
            val schemaIds = state.filters.mapTo(HashSet()) { it.id }
            val extraActive = state.activeFilters.filterKeys { it !in schemaIds }
            if (extraActive.isNotEmpty()) {
                LocalFilterChips(
                    active = extraActive,
                    onRemove = { id ->
                        viewModel.setFilter(id, null)
                        viewModel.applyFilters()
                    },
                )
            }
            // Listing switcher header: Home (when the source supports it) + each listing.
            // Aidoku parity: search takes over the whole page — the home/
            // listing switcher (and its highlight) is not part of the
            // search context, so it is hidden in SearchMode. Closing the
            // search box restores the previous tab (preSearchMode).
            if (state.mode !is SourceMode.SearchMode && (state.listings.isNotEmpty() || !state.home501)) {
                val showHomeTab = !state.home501 && state.source?.hasHome != false
                val listingBase = if (showHomeTab) 1 else 0
                val selected = when (val m = state.mode) {
                    SourceMode.Home -> 0
                    is SourceMode.ListingMode ->
                        listingBase + state.listings.indexOfFirst { it.id == m.listing.id }.coerceAtLeast(0)
                    is SourceMode.SearchMode -> -1
                }
                ScrollableTabRow(selectedTabIndex = selected.coerceAtLeast(0), edgePadding = 12.dp) {
                    if (showHomeTab) {
                        Tab(
                            selected = selected == 0,
                            onClick = { viewModel.backToHome() },
                            text = { Text(stringResource(R.string.source_home_tab)) },
                        )
                    }
                    state.listings.forEachIndexed { i, listing ->
                        Tab(
                            selected = selected == listingBase + i,
                            onClick = { viewModel.openListing(listing) },
                            text = { Text(listing.name) },
                        )
                    }
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.home == null && state.mode is SourceMode.Home ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.source_retry)) }
                        }
                    }
                else -> when (val mode = state.mode) {
                    SourceMode.Home -> HomeBody(state, viewModel, searchOpen, onOpenManga)
                    is SourceMode.ListingMode -> MangaGrid(
                        entries = mode.entries?.propertyEntries ?: emptyList(),
                        loading = mode.entries == null && state.modeError == null,
                        loadingMore = state.loadingMore,
                        error = state.modeError,
                        apiKey = state.profile?.apiKey,
                        baseUrl = state.profile?.baseUrl.orEmpty(),
                        sourceId = viewModel.sourceId,
                        isLocal = isLocal,
                        onOpen = {
                            // Aidoku instant render: hand the tapped entry to
                            // the detail page so the header shows before the
                            // first registration partial arrives.
                            if (it.id == null) MangaSeedStore.put(viewModel.sourceId, it)
                            onOpenManga(it.id, it.key)
                        },
                        onLoadMore = { viewModel.loadMore() },
                        onRetry = { viewModel.loadMore() },
                    )
                    is SourceMode.SearchMode -> MangaGrid(
                        entries = mode.entries?.propertyEntries ?: emptyList(),
                        loading = mode.entries == null && state.modeError == null,
                        loadingMore = state.loadingMore,
                        error = state.modeError,
                        apiKey = state.profile?.apiKey,
                        baseUrl = state.profile?.baseUrl.orEmpty(),
                        sourceId = viewModel.sourceId,
                        isLocal = isLocal,
                        onOpen = {
                            // Aidoku instant render: hand the tapped entry to
                            // the detail page so the header shows before the
                            // first registration partial arrives.
                            if (it.id == null) MangaSeedStore.put(viewModel.sourceId, it)
                            onOpenManga(it.id, it.key)
                        },
                        onLoadMore = { viewModel.loadMore() },
                        onRetry = { viewModel.loadMore() },
                    )
                }
            }
        }
    }

    if (filterPanelOpen) {
        FilterPanelSheet(
            filters = state.filters,
            active = state.activeFilters,
            savedGroups = state.savedGroups,
            onEdit = { id, value -> viewModel.setFilter(id, value) },
            onApply = {
                filterPanelOpen = false
                viewModel.applyFilters()
            },
            onClear = { viewModel.clearFilters() },
            onSaveGroup = { name -> viewModel.saveFilterGroup(name) },
            onApplyGroup = { group ->
                filterPanelOpen = false
                val query = viewModel.applyFilterGroup(group)
                if (query.isNotBlank()) {
                    searchQuery = query
                    searchOpen = true
                }
            },
            onDeleteGroup = { name -> viewModel.deleteFilterGroup(name) },
            onDismiss = { filterPanelOpen = false },
        )
    }

    if (showSourceInfo) {
        SourceInfoDialog(
            source = state.source,
            onDismiss = { showSourceInfo = false },
        )
    }
}

/** Design 04 overflow → Source info: read-only facts the server already exposes. */
@Composable
private fun SourceInfoDialog(source: Source?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_info)) },
        text = {
            Column {
                InfoLine(stringResource(R.string.source_name), source?.name ?: "—")
                InfoLine(stringResource(R.string.source_info_key), source?.key ?: "—")
                InfoLine(stringResource(R.string.source_info_type), source?.kind?.value ?: "—")
                InfoLine(stringResource(R.string.source_info_version), "v${source?.version ?: "—"}")
                InfoLine(
                    stringResource(R.string.source_info_language),
                    source?.languages?.joinToString().orEmpty().ifBlank { "—" },
                )
                InfoLine(
                    stringResource(R.string.source_info_base_url),
                    source?.baseUrl ?: stringResource(R.string.source_not_declared),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_close)) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeBody(
    state: SourceHomeUiState,
    viewModel: SourceHomeViewModel,
    searchOpen: Boolean,
    onOpenManga: (id: Long?, key: String) -> Unit,
) {
    val home = state.home
    if (home == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.homeError != null) {
                // A real failure (not 501): visible error + retry, like webui.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.homeError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.source_retry)) }
                }
            } else {
                Text(
                    if (state.listings.isEmpty()) {
                        stringResource(R.string.source_no_home_no_listings)
                    } else {
                        stringResource(R.string.source_no_home_use_listing)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    if (home.components.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.source_home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(home.components.size) { index ->
            val component = home.components[index]
            HomeComponentView(
                component = component,
                apiKey = state.profile?.apiKey,
                proxied = { viewModel.proxiedImage(it) },
                onOpenManga = { key -> onOpenManga(null, key) },
                onOpenListing = { id, name -> viewModel.openListingRef(id, name) },
                onOpenUrl = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                onOpenFilter = { item -> viewModel.openFilterItem(item) },
            )
        }
    }
}

@Composable
private fun HomeComponentView(
    component: HomeComponent,
    apiKey: String?,
    proxied: (String) -> String,
    onOpenManga: (String) -> Unit,
    onOpenListing: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenFilter: (HomeFilterItem) -> Unit,
) {
    val value = component.`value`
    // Aidoku attaches the backing listing to scroller/list components; the
    // header then carries a "view all" button (webui SourceHomePage parity).
    val listing: AidokuListing? = value.scroller?.listing
        ?: value.mangaList?.listing
        ?: value.mangaChapterList?.listing
    Column {
        // Aidoku TitleView: semibold section title with an optional subtitle
        // underneath (webui home-section__head parity).
        if (component.title != null || component.subtitle != null || listing != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        component.title ?: listing?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (listing != null) {
                        // MD3 tonal capsule (komikku section-header parity):
                        // pill-shaped button instead of a bare text link.
                        FilledTonalButton(
                            onClick = { onOpenListing(listing.id, listing.name) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.source_view_all),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
                component.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            value.imageScroller != null -> ImageScroller(
                links = value.imageScroller!!.links,
                autoScrollInterval = value.imageScroller!!.autoScrollInterval,
                width = value.imageScroller!!.width,
                height = value.imageScroller!!.height,
                apiKey = apiKey,
                proxied = proxied,
                onOpen = { linkAction(it, onOpenManga, { id -> onOpenListing(id, id) }, onOpenUrl) },
            )
            value.bigScroller != null -> BigScroller(
                entries = value.bigScroller!!.propertyEntries,
                autoScrollInterval = value.bigScroller!!.autoScrollInterval,
                apiKey = apiKey,
                proxied = proxied,
                onOpenManga = onOpenManga,
            )
            value.scroller != null -> LinkScroller(
                links = value.scroller!!.propertyEntries,
                apiKey = apiKey, proxied = proxied,
                onOpen = { linkAction(it, onOpenManga, { id -> onOpenListing(id, id) }, onOpenUrl) },
            )
            value.mangaList != null -> HomeRows(
                entries = value.mangaList!!.propertyEntries,
                pageSize = value.mangaList!!.pageSize,
            ) { index, link ->
                MangaListRow(
                    link = link,
                    // Aidoku MangaList ranking: numbered rows.
                    rank = if (value.mangaList!!.ranking) index + 1 else null,
                    apiKey = apiKey, proxied = proxied,
                ) { linkAction(link, onOpenManga, { id -> onOpenListing(id, id) }, onOpenUrl) }
            }
            value.mangaChapterList != null -> HomeRows(
                entries = value.mangaChapterList!!.propertyEntries,
                pageSize = value.mangaChapterList!!.pageSize,
            ) { _, entry ->
                MangaChapterRow(entry, apiKey, proxied) { onOpenManga(entry.manga.key) }
            }
            value.links != null -> Column(Modifier.fillMaxWidth()) {
                value.links!!.forEach { link ->
                    // Aidoku HomeLinksView row: primary-tinted title over a
                    // divider, no trailing chevron.
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { linkAction(link, onOpenManga, { id -> onOpenListing(id, id) }, onOpenUrl) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            link.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        link.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
            value.filters != null -> FilterButtons(
                items = value.filters!!,
                onOpen = onOpenFilter,
            )
        }
    }
}

/** Home "filters" component: a two-column button grid (Aidoku
 *  HomeFiltersView). Past six entries the grid keeps the first five plus a
 *  "View all" entry that lists every filter in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterButtons(items: List<HomeFilterItem>, onOpen: (HomeFilterItem) -> Unit) {
    if (items.isEmpty()) return
    var sheetOpen by remember { mutableStateOf(false) }
    val collapsed = items.size > 6
    // Slot 6 doubles as the "view all" entry, like Aidoku's index == 5.
    val slots: List<HomeFilterItem?> = if (collapsed) items.take(5) + listOf(null) else items
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    OutlinedButton(
                        onClick = {
                            if (item == null) sheetOpen = true else onOpen(item)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (item == null) {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.source_view_all))
                        } else {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (sheetOpen) {
        AppModalDialog(onDismissRequest = { sheetOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                sheetOpen = false
                                onOpen(item)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun linkAction(
    link: Link,
    onOpenManga: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val value = link.`value`
    when {
        value?.manga != null -> onOpenManga(value.manga!!.key)
        value?.listing != null -> onOpenListing(value.listing!!.id)
        value?.url != null -> onOpenUrl(value.url!!)
    }
}

// ---------------------------------------------------------------------------
// Filter header chips (webui FilterHeader / Aidoku FilterHeaderView parity)
// ---------------------------------------------------------------------------

private fun filterIdOf(value: FilterValue): String? =
    value.text?.id ?: value.sort?.id ?: value.check?.id
        ?: value.select?.id ?: value.multiSelect?.id ?: value.range?.id

/** Enabled = the draft differs from the source default (webui
 *  isFilterEnabled); enabled chips sort first in the header. */
private fun isFilterEnabled(filter: SourceFilter, active: Map<String, FilterValue>): Boolean {
    val value = active[filter.id] ?: return false
    return when (filter.type) {
        "sort" -> value.sort?.let { sort ->
            val def = runCatching { filter.default?.jsonObject }.getOrNull()
            sort.index != def?.get("index")?.jsonPrimitive?.intOrNull ||
                sort.ascending != (def?.get("ascending")?.jsonPrimitive?.booleanOrNull ?: true)
        } == true
        "check" -> (value.check?.value ?: 0) != 0
        "select" -> value.select?.let { it.value != (filter.default?.jsonPrimitive?.contentOrNull ?: "") } == true
        "multi-select" -> value.multiSelect?.let { it.included.isNotEmpty() || it.excluded.isNotEmpty() } == true
        "text" -> value.text?.value?.isNotBlank() == true
        "range" -> value.range?.let { it.from != null || it.to != null } == true
        else -> false
    }
}

/** The selected value takes ids (when present), otherwise the option name. */
private fun optionKey(filter: SourceFilter, index: Int): String =
    filter.ids?.getOrNull(index) ?: filter.options?.getOrNull(index) ?: ""

/** 0 → 1 → (-1 when exclusion is allowed) → 0. */
private fun nextTriState(current: Int, canExclude: Boolean): Int =
    if (current == 0) 1 else if (current == 1) (if (canExclude) -1 else 0) else 0

@Composable
private fun FilterHeader(
    filters: List<SourceFilter>,
    active: Map<String, FilterValue>,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
) {
    val sorted = filters.sortedByDescending { isFilterEnabled(it, active) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted, key = { it.id }) { filter ->
            // note/text/range never enter the header; a check filter with a
            // default stays out too (after a reset it would not wrongly show
            // as enabled) — Aidoku convention.
            if (filter.hideFromHeader == true) return@items
            when (filter.type) {
                "sort" -> SortChip(filter, active[filter.id]?.sort, onEdit, onApply)
                "check" -> if (filter.default == null) {
                    CheckChip(filter, active[filter.id]?.check?.value ?: 0, onEdit, onApply)
                }
                "select" -> SelectChip(filter, active[filter.id]?.select?.value, onEdit, onApply)
                "multi-select" -> MultiSelectChip(filter, active[filter.id]?.multiSelect, onEdit, onApply)
            }
        }
    }
}

@Composable
private fun SortChip(
    filter: SourceFilter,
    current: FilterValueOneOf1Sort?,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val options = filter.options ?: emptyList()
    val label = current?.let { c ->
        options.getOrNull(c.index)?.let { opt ->
            val arrow = if (filter.canAscend == true) (if (c.ascending) " ↑" else " ↓") else ""
            stringResource(
                R.string.source_label_value,
                filter.title ?: stringResource(R.string.source_sort),
                "$opt$arrow",
            )
        }
    } ?: (filter.title ?: stringResource(R.string.source_sort))
    Box {
        FilterChip(
            selected = current != null,
            onClick = { menuOpen = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option, modifier = Modifier.weight(1f))
                            if (current?.index == index) {
                                Text(if (filter.canAscend == true) (if (current.ascending) "↑" else "↓") else "✓")
                            }
                        }
                    },
                    onClick = {
                        // Aidoku: clicking the current option flips direction
                        // when ascending is allowed, else resets to descending.
                        val next = if (current?.index == index && filter.canAscend == true) {
                            FilterValueOneOf1Sort(filter.id, index, !current.ascending)
                        } else {
                            FilterValueOneOf1Sort(filter.id, index, false)
                        }
                        onEdit(filter.id, FilterValue(sort = next))
                        onApply()
                        menuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CheckChip(
    filter: SourceFilter,
    current: Int,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
) {
    FilterChip(
        selected = current != 0,
        onClick = {
            val next = nextTriState(current, filter.canExclude == true)
            onEdit(filter.id, if (next == 0) null else FilterValue(check = FilterValueOneOf2Check(filter.id, next)))
            onApply()
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (filter.canExclude == true && current != 0) {
                    Icon(
                        if (current == 1) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(filter.name ?: filter.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}

@Composable
private fun SelectChip(
    filter: SourceFilter,
    current: String?,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val options = filter.options ?: emptyList()
    val active = !current.isNullOrEmpty() && current != (filter.default?.jsonPrimitive?.contentOrNull ?: "")
    val currentLabel = options.getOrNull(
        options.indices.firstOrNull { optionKey(filter, it) == current } ?: -1,
    )
    Box {
        FilterChip(
            selected = active,
            onClick = { menuOpen = true },
            label = {
                Text(
                    if (currentLabel != null) {
                        stringResource(R.string.source_label_value, filter.title ?: "", currentLabel)
                    } else {
                        filter.title ?: ""
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!current.isNullOrEmpty()) {
                // webui ChipMenuItem reset: danger-tinted action row with a
                // RotateCcw-style icon instead of a checkmark.
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.source_reset), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            Icon(
                                Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    onClick = {
                        onEdit(filter.id, null)
                        onApply()
                        menuOpen = false
                    },
                )
            }
            options.forEachIndexed { index, option ->
                val key = optionKey(filter, index)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option, modifier = Modifier.weight(1f))
                            if (current == key) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        onEdit(filter.id, FilterValue(select = FilterValueOneOfText(filter.id, key)))
                        onApply()
                        menuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MultiSelectChip(
    filter: SourceFilter,
    current: FilterValueOneOf4MultiSelect?,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val options = filter.options ?: emptyList()
    val count = (current?.included?.size ?: 0) + (current?.excluded?.size ?: 0)
    Box {
        FilterChip(
            selected = count > 0,
            onClick = { menuOpen = true },
            label = {
                Text(
                    if (count > 0) stringResource(R.string.source_label_count, filter.title ?: "", count)
                    else (filter.title ?: ""),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        // Aidoku menuActionDismissDisabled: the menu stays open for
        // consecutive multi-selecting.
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (count > 0) {
                // webui TriStateDropdown: a reset row at the top clears every
                // selection in this dimension (applies immediately).
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.source_reset), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            Icon(
                                Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    onClick = {
                        onEdit(filter.id, null)
                        onApply()
                        menuOpen = false
                    },
                )
            }
            options.forEachIndexed { index, option ->
                val key = optionKey(filter, index)
                val state = when {
                    current?.included?.contains(key) == true -> 1
                    current?.excluded?.contains(key) == true -> -1
                    else -> 0
                }
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option, modifier = Modifier.weight(1f))
                            when (state) {
                                1 -> Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                -1 -> Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    onClick = {
                        val next = nextTriState(state, filter.canExclude == true)
                        val included = (current?.included ?: emptyList()).filter { it != key }
                        val excluded = (current?.excluded ?: emptyList()).filter { it != key }
                        val newIncluded = if (next == 1) included + key else included
                        val newExcluded = if (next == -1) excluded + key else excluded
                        onEdit(
                            filter.id,
                            if (newIncluded.isEmpty() && newExcluded.isEmpty()) null
                            else FilterValue(multiSelect = FilterValueOneOf4MultiSelect(filter.id, newIncluded, newExcluded)),
                        )
                        onApply()
                    },
                )
            }
        }
    }
}

/** Local-source deep-link filters (author/artist/genre) as removable chips
 *  — webui localFilterChipLabel parity. */
@Composable
private fun LocalFilterChips(active: Map<String, FilterValue>, onRemove: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(active.values.toList(), key = { filterIdOf(it) ?: it.toString() }) { value ->
            val label = valueLabelForChip(value) ?: return@items
            FilterChip(
                selected = true,
                onClick = { filterIdOf(value)?.let(onRemove) },
                label = { Text("$label ×", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun valueLabelForChip(value: FilterValue): String? {
    value.text?.let {
        return when (it.id) {
            "author" -> stringResource(R.string.source_chip_author, it.value)
            "artist" -> stringResource(R.string.source_chip_artist, it.value)
            else -> stringResource(R.string.source_label_value, it.id, it.value)
        }
    }
    value.select?.let {
        return if (it.id == "genre") stringResource(R.string.source_chip_genre, it.value)
        else stringResource(R.string.source_label_value, it.id, it.value)
    }
    value.multiSelect?.let {
        if (it.id == "genre" && it.included.isNotEmpty()) {
            return stringResource(R.string.source_chip_genre, it.included.joinToString())
        }
    }
    return value.sort?.let { stringResource(R.string.source_sort) }
        ?: value.check?.let { it.id } ?: value.range?.let { it.id }
}

// ---------------------------------------------------------------------------
// Filter panel sheet (webui filter-panel / Aidoku FilterListSheetView parity)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterPanelSheet(
    filters: List<SourceFilter>,
    active: Map<String, FilterValue>,
    savedGroups: List<SavedFilterGroup>,
    onEdit: (String, FilterValue?) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onSaveGroup: (String) -> Boolean,
    onApplyGroup: (SavedFilterGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var saveDialogOpen by remember { mutableStateOf(false) }
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedGroups.isNotEmpty()) {
                Text(stringResource(R.string.source_saved_filters), style = MaterialTheme.typography.titleSmall)
                savedGroups.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onApplyGroup(group) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            if (group.query.isNotBlank()) {
                                Text(group.query, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDeleteGroup(group.name) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.source_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            filters.forEach { filter ->
                FilterPanelItem(filter, active[filter.id], onEdit)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.source_clear)) }
                TextButton(onClick = { saveDialogOpen = true }) {
                    Text(stringResource(R.string.source_save_filter))
                }
                Button(onClick = onApply) { Text(stringResource(R.string.source_apply)) }
            }
        }
    }
    if (saveDialogOpen) {
        var name by remember { mutableStateOf("") }
        var duplicate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = { Text(stringResource(R.string.source_save_filter)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; duplicate = false },
                        placeholder = { Text(stringResource(R.string.source_name)) },
                        singleLine = true,
                        isError = duplicate,
                    )
                    if (duplicate) {
                        Text(
                            stringResource(R.string.source_duplicate_filter),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onSaveGroup(name)) saveDialogOpen = false else duplicate = true
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.source_save)) }
            },
            dismissButton = { TextButton(onClick = { saveDialogOpen = false }) { Text(stringResource(R.string.source_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanelItem(
    filter: SourceFilter,
    value: FilterValue?,
    onEdit: (String, FilterValue?) -> Unit,
) {
    when (filter.type) {
        "note" -> Column {
            Text(filter.title ?: "", style = MaterialTheme.typography.titleSmall)
            filter.text?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "text" -> Column {
            Text(filter.title ?: "", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = value?.text?.value ?: "",
                onValueChange = { text ->
                    onEdit(
                        filter.id,
                        if (text.isBlank()) null else FilterValue(text = FilterValueOneOfText(filter.id, text)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(filter.placeholder ?: "") },
                singleLine = true,
            )
        }
        "sort" -> Column {
            Text(filter.title ?: stringResource(R.string.source_sort), style = MaterialTheme.typography.titleSmall)
            var menuOpen by remember { mutableStateOf(false) }
            val options = filter.options ?: emptyList()
            val sort = value?.sort
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text(sort?.let { options.getOrNull(it.index) } ?: stringResource(R.string.source_default))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.source_default)) },
                            onClick = { onEdit(filter.id, null); menuOpen = false },
                        )
                        options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onEdit(filter.id, FilterValue(sort = FilterValueOneOf1Sort(filter.id, index, sort?.ascending ?: false)))
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                if (filter.canAscend == true && sort != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onEdit(filter.id, FilterValue(sort = sort.copy(ascending = !sort.ascending)))
                    }) {
                        Text(
                            if (sort.ascending) stringResource(R.string.source_ascending)
                            else stringResource(R.string.source_descending),
                        )
                    }
                }
            }
        }
        "check" -> {
            val current = value?.check?.value ?: 0
            if (filter.canExclude == true) {
                Column {
                    Text(
                        (filter.title ?: "") + (filter.name?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to stringResource(R.string.source_check_off),
                            1 to stringResource(R.string.source_check_include),
                            -1 to stringResource(R.string.source_check_exclude),
                        ).forEach { (state, label) ->
                            FilterChip(
                                selected = current == state,
                                onClick = {
                                    onEdit(filter.id, if (state == 0) null else FilterValue(check = FilterValueOneOf2Check(filter.id, state)))
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (filter.title ?: "") + (filter.name?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = current == 1,
                        onCheckedChange = { on ->
                            onEdit(filter.id, if (on) FilterValue(check = FilterValueOneOf2Check(filter.id, 1)) else null)
                        },
                    )
                }
            }
        }
        "select" -> Column {
            Text(filter.title ?: "", style = MaterialTheme.typography.titleSmall)
            var menuOpen by remember { mutableStateOf(false) }
            val options = filter.options ?: emptyList()
            val currentKey = value?.select?.value
            val currentLabel = options.getOrNull(options.indices.firstOrNull { optionKey(filter, it) == currentKey } ?: -1)
            Box {
                OutlinedButton(onClick = { menuOpen = true }) {
                    Text(currentLabel ?: stringResource(R.string.source_not_selected))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.source_not_selected)) },
                        onClick = { onEdit(filter.id, null); menuOpen = false },
                    )
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onEdit(filter.id, FilterValue(select = FilterValueOneOfText(filter.id, optionKey(filter, index))))
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
        "multi-select" -> Column {
            Text(filter.title ?: "", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filter.options?.forEachIndexed { index, option ->
                    val key = optionKey(filter, index)
                    val multi = value?.multiSelect
                    val chipState = when {
                        multi?.included?.contains(key) == true -> 1
                        multi?.excluded?.contains(key) == true -> -1
                        else -> 0
                    }
                    FilterChip(
                        selected = chipState != 0,
                        onClick = {
                            val next = nextTriState(chipState, filter.canExclude == true)
                            val included = (multi?.included ?: emptyList()).filter { it != key }
                            val excluded = (multi?.excluded ?: emptyList()).filter { it != key }
                            val newIncluded = if (next == 1) included + key else included
                            val newExcluded = if (next == -1) excluded + key else excluded
                            onEdit(
                                filter.id,
                                if (newIncluded.isEmpty() && newExcluded.isEmpty()) null
                                else FilterValue(multiSelect = FilterValueOneOf4MultiSelect(filter.id, newIncluded, newExcluded)),
                            )
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (chipState == 1) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                } else if (chipState == -1) {
                                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(option)
                            }
                        },
                    )
                }
            }
        }
        "range" -> Column {
            Text(filter.title ?: "", style = MaterialTheme.typography.titleSmall)
            val range = value?.range
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = range?.from?.toString() ?: "",
                    onValueChange = { text ->
                        val from = text.toFloatOrNull()
                        val to = range?.to
                        onEdit(
                            filter.id,
                            if (from == null && to == null) null
                            else FilterValue(range = FilterValueOneOf5Range(filter.id, from, to)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(filter.min?.toString() ?: stringResource(R.string.source_range_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (filter.decimal == true) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                )
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = range?.to?.toString() ?: "",
                    onValueChange = { text ->
                        val from = range?.from
                        val to = text.toFloatOrNull()
                        onEdit(
                            filter.id,
                            if (from == null && to == null) null
                            else FilterValue(range = FilterValueOneOf5Range(filter.id, from, to)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(filter.max?.toString() ?: stringResource(R.string.source_range_max)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (filter.decimal == true) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                )
            }
        }
    }
}

/**
 * Aidoku HomeBigScrollerView parity: one entry per page, cover on the left
 * (170dp tall, 2:3) and the details column on the right. The carousel is
 * driven by [autoScrollInterval] and a manual swipe restarts the timer with a
 * short grace period, mirroring Carousel.swift.
 */
@Composable
private fun BigScroller(
    entries: List<SourceManga>,
    autoScrollInterval: Float?,
    apiKey: String?,
    proxied: (String) -> String,
    onOpenManga: (String) -> Unit,
) {
    if (entries.isEmpty()) return
    // Aidoku's carousel loops. A huge starting page index gives endless
    // scrolling in both directions; single entry keeps a real page count so the
    // pager never auto advances.
    val pageCount = if (entries.size > 1) Int.MAX_VALUE else 1
    // Align the starting page on a multiple of the entry count so the first
    // visible page is the first entry again.
    val startPage = if (pageCount > 1) (Int.MAX_VALUE / 2 / entries.size) * entries.size else 0
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { pageCount },
    )
    // A touch bumps the key, which restarts the auto scroll timer.
    var restartKey by remember { mutableStateOf(0) }
    val intervalMs = autoScrollInterval?.takeIf { it > 0f }?.let { (it * 1000f).toLong() }
    LaunchedEffect(intervalMs, restartKey) {
        // Aidoku: single entry never auto scrolls.
        if (intervalMs == null || entries.size <= 1) return@LaunchedEffect
        // After a manual drag the timer restarts with a 3 s delay.
        delay(if (restartKey > 0) 3_000L else intervalMs)
        while (true) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
            delay(intervalMs)
        }
    }
    Box(Modifier.fillMaxWidth().restartAutoScrollOnTouch { restartKey++ }) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val manga = entries[page % entries.size]
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpenManga(manga.key) }
                    .padding(horizontal = 16.dp),
            ) {
                RemoteImage(
                    proxied(manga.cover ?: ""),
                    apiKey,
                    Modifier.height(170.dp).aspectRatio(2f / 3f),
                    ContentScale.Crop,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        manga.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val authors = manga.authors.orEmpty().filter { it.isNotBlank() }.joinToString(", ")
                    if (authors.isNotEmpty()) {
                        Text(
                            authors,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    manga.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp).clipToBounds(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ContentRatingBadge(manga.contentRating)
                        manga.tags.orEmpty().filter { it.isNotBlank() }.take(3).forEach { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        // Design 04: page counter + dots, both derived from the looping page
        // index so they stay in sync with the auto-advance timer.
        val current = pagerState.currentPage % entries.size
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "${current + 1}/${entries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.size in 2..8) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    repeat(entries.size) { i ->
                        val active = i == current
                        Box(
                            Modifier
                                .size(if (active) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                ),
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun ContentRatingBadge(rating: ContentRating) {
    if (rating == ContentRating.SAFE || rating == ContentRating.UNKNOWN) return
    val tint = if (rating == ContentRating.SUGGESTIVE) Color(0xFFFF9800) else Color(0xFFF44336)
    Text(
        rating.value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .background(tint.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Restarts a carousel timer whenever the user puts a finger on it. */
private fun Modifier.restartAutoScrollOnTouch(onTouchDown: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var wasPressed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.any { it.pressed }
            if (pressed && !wasPressed) onTouchDown()
            wasPressed = pressed
        }
    }
}

/**
 * Aidoku HomeImageScrollerView parity: a free scrolling row of plain covers
 * (no scrim, no overlaid text) that wraps around on a timer.
 */
@Composable
private fun ImageScroller(
    links: List<Link>,
    autoScrollInterval: Float?,
    width: Int?,
    height: Int?,
    apiKey: String?,
    proxied: (String) -> String,
    onOpen: (Link) -> Unit,
) {
    if (links.isEmpty()) return
    val itemHeight = height?.takeIf { it > 0 }?.dp ?: 140.dp
    val itemWidth = width?.takeIf { it > 0 }?.dp ?: (itemHeight * 1.5f)
    val listState = rememberLazyListState()
    val snapping = rememberSnapFlingBehavior(lazyListState = listState)
    var restartKey by remember { mutableStateOf(0) }
    val intervalMs = autoScrollInterval?.takeIf { it > 0f }?.let { (it * 1000f).toLong() }
    LaunchedEffect(intervalMs, restartKey) {
        if (intervalMs == null || links.size <= 1) return@LaunchedEffect
        delay(if (restartKey > 0) 3_000L else intervalMs)
        while (true) {
            // Wrap back to the first item after the last one.
            listState.animateScrollToItem((listState.firstVisibleItemIndex + 1) % links.size)
            delay(intervalMs)
        }
    }
    Box(Modifier.fillMaxWidth().restartAutoScrollOnTouch { restartKey++ }) {
        LazyRow(
            state = listState,
            flingBehavior = snapping,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(links) { link ->
                RemoteImage(
                    proxied(link.imageUrl ?: ""),
                    apiKey,
                    Modifier.size(itemWidth, itemHeight).clickable { onOpen(link) },
                    ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * Renders a list component either vertically or, when it declares a pageSize
 * and holds more entries than one page, as horizontally page-snapped groups of
 * pageSize stacked rows (Aidoku CollectionView mangaListLayout / webui
 * .home-pages).
 */
@Composable
private fun <T> HomeRows(entries: List<T>, pageSize: Int?, row: @Composable (Int, T) -> Unit) {
    val perPage = pageSize?.takeIf { it > 0 }
    if (perPage == null || entries.size <= perPage) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            entries.forEachIndexed { index, entry -> row(index, entry) }
        }
        return
    }
    val pages = entries.chunked(perPage)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(pages.size) { pageIndex ->
            Column(
                Modifier.fillParentMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pages[pageIndex].forEachIndexed { rowIndex, entry -> row(pageIndex * perPage + rowIndex, entry) }
            }
        }
    }
}

@Composable
private fun LinkScroller(
    links: List<Link>,
    apiKey: String?,
    proxied: (String) -> String,
    onOpen: (Link) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(links) { link ->
            Column(Modifier.width(120.dp).clickable { onOpen(link) }) {
                RemoteImage(
                    proxied(link.imageUrl ?: ""),
                    apiKey,
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    ContentScale.Crop,
                )
                // Fixed text block keeps cards with and without a subtitle aligned.
                Column(Modifier.fillMaxWidth().height(42.dp).padding(top = 4.dp)) {
                    Text(link.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    link.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaListRow(link: Link, rank: Int?, apiKey: String?, proxied: (String) -> String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(100.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        RemoteImage(proxied(link.imageUrl ?: ""), apiKey, Modifier.size(66.7.dp, 100.dp), ContentScale.Crop)
        // Ranking number sits after the cover, before the text (Aidoku order).
        if (rank != null) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(link.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            link.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            link.value?.manga?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MangaChapterRow(entry: MangaWithChapter, apiKey: String?, proxied: (String) -> String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(100.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        RemoteImage(proxied(entry.manga.cover ?: ""), apiKey, Modifier.size(66.7.dp, 100.dp), ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.manga.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                chapterLabel(entry.chapter.volumeNumber, entry.chapter.chapterNumber, entry.chapter.title),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            entry.chapter.dateUploaded?.takeIf { it > 0 }?.let { uploaded ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        relativeEpochMs(normalizeEpochMs(uploaded)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaGrid(
    entries: List<SearchManga>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    apiKey: String?,
    baseUrl: String,
    sourceId: Long,
    isLocal: Boolean,
    onOpen: (SearchManga) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entries.isEmpty() && error != null) {
        // First-page failure: visible error + retry instead of a forever spinner.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.source_retry)) }
            }
        }
        return
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.source_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last != null && last >= total - 6) onLoadMore()
            }
    }
    // Same rule as the library grid: tablets (smallest width ≥ 600dp) grow
    // the minimum cell to KMReader's large grid size (152dp).
    val minCell = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 152.dp else 110.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCell),
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Duplicate keys occur in real listings; the index keeps them stable.
        itemsIndexed(entries, key = { index, manga -> "${manga.key}#$index" }) { _, manga ->
            Column(Modifier.clickable { onOpen(manga) }) {
                // webui entryCover parity: /api/v1/manga/ paths pass through,
                // remote covers go through the source image proxy, local
                // sources fall back to the manga cover endpoint.
                RemoteImage(
                    entryCoverUrl(baseUrl, manga.sourceId, manga.cover, manga.id, isLocal),
                    apiKey,
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    ContentScale.Crop,
                )
                Text(manga.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                }
            }
        } else if (error != null) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.source_load_failed_retry)) }
                }
            }
        }
    }
}

@Composable
private fun RemoteImage(url: String?, apiKey: String?, modifier: Modifier, contentScale: ContentScale) {
    val context = LocalContext.current
    val model = ImageRequest.Builder(context).data(url?.ifEmpty { null }).apply {
        apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
    }.build()
    // Unified cover treatment: 5dp rounding plus a hairline outline.
    val shape = RoundedCornerShape(5.dp)
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentScale = contentScale,
    )
}

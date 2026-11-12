package org.sumidoku.android.feature.source

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.AidokuListing
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.HomeComponent
import org.sumidoku.android.core.api.model.HomeFilterItem
import org.sumidoku.android.core.api.model.Link
import org.sumidoku.android.core.api.model.Listing
import org.sumidoku.android.core.api.model.MangaWithChapter
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.SourceManga

/** Source home/listing/search screen — Aidoku SourceHomeContentView.
 *  Previews 04-source-home / 29-source-listing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHomeScreen(
    onBack: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    /** Filters handed in by navigation (manga detail author/tag/tag chip
     *  tap): the screen opens straight into the filtered grid. */
    initialFilters: List<FilterValue> = emptyList(),
    viewModel: SourceHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var initialFiltersApplied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.openManga.collect { onOpenManga(it) }
    }

    // The view model needs the profile before it can page a filtered search,
    // so wait for the home refresh to land.
    LaunchedEffect(initialFilters, state.profile) {
        if (!initialFiltersApplied && initialFilters.isNotEmpty() && state.profile != null) {
            initialFiltersApplied = true
            viewModel.openFilteredSearch(initialFilters)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.source?.name ?: "图源", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = "搜索")
                    }
                    state.source?.baseUrl?.let { url ->
                        IconButton(onClick = {
                            // Custom tab / browser — 与 Aidoku 的 safari 按钮一致（M5B 接 CustomTab）
                        }) {
                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = "浏览器打开")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "图源设置")
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
                    placeholder = { Text("搜索本图源…") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { if (searchQuery.isNotBlank()) viewModel.search(searchQuery.trim()) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                    },
                )
            }
            // listing 切换头：主页 + 各 listing
            if (state.listings.isNotEmpty() || !state.home501) {
                val selected = when (val m = state.mode) {
                    SourceMode.Home -> 0
                    is SourceMode.ListingMode -> 1 + state.listings.indexOfFirst { it.id == m.listing.id }.coerceAtLeast(0)
                    is SourceMode.SearchMode -> -1
                    is SourceMode.FilteredMode -> -1
                }
                ScrollableTabRow(selectedTabIndex = selected.coerceAtLeast(0), edgePadding = 12.dp) {
                    Tab(
                        selected = selected == 0,
                        onClick = { viewModel.backToHome() },
                        text = { Text("主页") },
                    )
                    state.listings.forEachIndexed { i, listing ->
                        Tab(
                            selected = selected == i + 1,
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
                            TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                        }
                    }
                else -> when (val mode = state.mode) {
                    SourceMode.Home -> HomeBody(state, viewModel, searchOpen)
                    is SourceMode.ListingMode -> MangaGrid(
                        entries = mode.entries?.propertyEntries ?: emptyList(),
                        loading = mode.entries == null,
                        loadingMore = state.loadingMore,
                        apiKey = state.profile?.apiKey,
                        proxied = { viewModel.proxiedImage(it) },
                        onOpen = { viewModel.openMangaKey(it.key) },
                        onLoadMore = { viewModel.loadMore() },
                    )
                    is SourceMode.SearchMode -> MangaGrid(
                        entries = mode.entries?.propertyEntries ?: emptyList(),
                        loading = mode.entries == null,
                        loadingMore = state.loadingMore,
                        apiKey = state.profile?.apiKey,
                        proxied = { viewModel.proxiedImage(it) },
                        onOpen = { viewModel.openMangaKey(it.key) },
                        onLoadMore = { viewModel.loadMore() },
                    )
                    is SourceMode.FilteredMode -> MangaGrid(
                        entries = mode.entries?.propertyEntries ?: emptyList(),
                        loading = mode.entries == null,
                        loadingMore = state.loadingMore,
                        apiKey = state.profile?.apiKey,
                        proxied = { viewModel.proxiedImage(it) },
                        onOpen = { viewModel.openMangaKey(it.key) },
                        onLoadMore = { viewModel.loadMore() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBody(state: SourceHomeUiState, viewModel: SourceHomeViewModel, searchOpen: Boolean) {
    val home = state.home
    if (home == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.listings.isEmpty()) "此图源没有主页与列表" else "此图源不提供主页，请切换到上方列表",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(home.components.size) { index ->
            val component = home.components[index]
            HomeComponentView(
                component = component,
                apiKey = state.profile?.apiKey,
                proxied = { viewModel.proxiedImage(it) },
                onOpenManga = { viewModel.openMangaKey(it) },
                onOpenListing = { listingId ->
                    state.listings.firstOrNull { it.id == listingId }?.let { viewModel.openListing(it) }
                },
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
    onOpenListing: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenFilter: (HomeFilterItem) -> Unit,
) {
    Column {
        component.title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        val value = component.`value`
        when {
            value?.imageScroller != null -> {
                val links = value.imageScroller!!.links
                val pagerState = rememberPagerState(pageCount = { links.size.coerceAtLeast(1) })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(180.dp)) { page ->
                    val link = links[page]
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp))
                            .clickable { linkAction(link, onOpenManga, onOpenListing, onOpenUrl) },
                    ) {
                        RemoteImage(proxied(link.imageUrl ?: ""), apiKey, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)))
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text(link.title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleSmall)
                            link.subtitle?.let { Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            value?.bigScroller != null -> MangaScroller(
                entries = value.bigScroller!!.propertyEntries,
                apiKey = apiKey, proxied = proxied, onOpenManga = onOpenManga, big = true,
            )
            value?.scroller != null -> LinkScroller(
                links = value.scroller!!.propertyEntries,
                apiKey = apiKey, proxied = proxied,
                onOpen = { linkAction(it, onOpenManga, onOpenListing, onOpenUrl) },
            )
            value?.mangaList != null -> Column {
                value.mangaList!!.propertyEntries.forEach { link ->
                    MangaListRow(link, apiKey, proxied) { linkAction(link, onOpenManga, onOpenListing, onOpenUrl) }
                }
            }
            value?.mangaChapterList != null -> Column {
                value.mangaChapterList!!.propertyEntries.forEach { entry ->
                    MangaChapterRow(entry, apiKey, proxied) { onOpenManga(entry.manga.key) }
                }
            }
            value?.links != null -> Column {
                value.links!!.forEach { link ->
                    Row(
                        Modifier.fillMaxWidth().clickable { linkAction(link, onOpenManga, onOpenListing, onOpenUrl) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(link.title, style = MaterialTheme.typography.bodyLarge)
                            link.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            value?.filters != null -> FilterButtons(
                items = value.filters!!,
                onOpen = onOpenFilter,
            )
        }
    }
}

/** Home "filters" component: a two-column button grid (Aidoku
 *  HomeFiltersView). Past six entries the grid keeps the first five plus a
 *  "查看全部" entry that lists every filter in a sheet. */
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
                            Text("查看全部")
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
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
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

@Composable
private fun MangaScroller(
    entries: List<SourceManga>,
    apiKey: String?,
    proxied: (String) -> String,
    onOpenManga: (String) -> Unit,
    big: Boolean = false,
) {
    val coverWidth = if (big) 120.dp else 92.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries) { manga ->
            Column(
                Modifier.width(coverWidth).clickable { onOpenManga(manga.key) },
            ) {
                RemoteImage(
                    proxied(manga.cover ?: ""),
                    apiKey,
                    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)),
                    ContentScale.Crop,
                )
                Text(
                    manga.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(links) { link ->
            Column(Modifier.width(92.dp).clickable { onOpen(link) }) {
                RemoteImage(
                    proxied(link.imageUrl ?: ""),
                    apiKey,
                    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)),
                    ContentScale.Crop,
                )
                Text(link.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MangaListRow(link: Link, apiKey: String?, proxied: (String) -> String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(proxied(link.imageUrl ?: ""), apiKey, Modifier.size(44.dp, 60.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(link.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            link.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
private fun MangaChapterRow(entry: MangaWithChapter, apiKey: String?, proxied: (String) -> String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(proxied(entry.manga.cover ?: ""), apiKey, Modifier.size(44.dp, 60.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(entry.manga.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val chapterLabel = buildString {
                entry.chapter.chapterNumber?.let { append("第 $it 话") }
                entry.chapter.title?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
            }
            if (chapterLabel.isNotEmpty()) {
                Text(chapterLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MangaGrid(
    entries: List<SearchManga>,
    loading: Boolean,
    loadingMore: Boolean,
    apiKey: String?,
    proxied: (String) -> String,
    onOpen: (SearchManga) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.key }) { manga ->
            Column(Modifier.clickable { onOpen(manga) }) {
                RemoteImage(
                    proxied(manga.cover ?: ""),
                    apiKey,
                    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)),
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
        }
    }
}

@Composable
private fun RemoteImage(url: String, apiKey: String?, modifier: Modifier, contentScale: ContentScale) {
    val context = LocalContext.current
    val model = ImageRequest.Builder(context).data(url.ifEmpty { null }).apply {
        apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
    }.build()
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = contentScale,
    )
}

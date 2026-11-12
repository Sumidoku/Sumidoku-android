package org.sumidoku.android.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.SearchManga

private enum class FilterGroup { RATINGS, LANGS, SOURCES }

/** Cross-source aggregate search (Aidoku search tab): tri-state filter
 *  groups pick which sources participate (webui SearchPanel.tsx /
 *  globalSearchPrefs), POST /search fans out, results render as
 *  per-source sections with independent pagination and per-source errors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenManga: (Long) -> Unit,
    showBack: Boolean = true,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<FilterGroup?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("在所有图源中搜索…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.search() }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
            }

            FilterHeader(
                state = state,
                onOpen = { sheet = it },
            )

            when {
                state.searching && state.sections.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.participatingSources.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("当前筛选没有可用图源", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                !state.searched ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "输入关键词，一次搜索 ${state.participatingSources.size} 个图源",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                state.sections.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = "header-${section.sourceId}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    section.sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${section.entries.size} 条",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (section.error != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "  ${section.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        if (section.entries.isNotEmpty()) {
                            item(key = "row-${section.sourceId}") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(section.entries.size) { i ->
                                        SearchResultCard(
                                            manga = section.entries[i],
                                            baseUrl = state.profile?.baseUrl.orEmpty(),
                                            apiKey = state.profile?.apiKey.orEmpty(),
                                            registering = state.registeringKey == section.entries[i].key,
                                            onClick = { viewModel.openResult(section.entries[i], onOpenManga) },
                                        )
                                    }
                                }
                            }
                        }
                        if (section.hasNextPage) {
                            item(key = "more-${section.sourceId}") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(
                                        enabled = !section.loading,
                                        onClick = { viewModel.loadMore(section.sourceId) },
                                    ) {
                                        Text(if (section.loading) "加载中…" else "加载更多")
                                    }
                                }
                            }
                        }
                        item(key = "divider-${section.sourceId}") {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }

    val current = sheet
    if (current != null) {
        TriStateSheet(
            title = when (current) {
                FilterGroup.RATINGS -> "分级"
                FilterGroup.LANGS -> "语言"
                FilterGroup.SOURCES -> "图源"
            },
            options = when (current) {
                FilterGroup.RATINGS -> listOf(0, 1, 2).map { it.toString() to contentRatingLabel(it) }
                FilterGroup.LANGS -> state.langOptions.map { it to languageLabel(it) }
                FilterGroup.SOURCES -> state.sources.mapNotNull { s -> s.id?.let { it.toString() to (s.name ?: s.key) } }
            },
            states = when (current) {
                FilterGroup.RATINGS -> state.ratingFilter.mapKeys { it.key.toString() }
                FilterGroup.LANGS -> state.langFilter
                FilterGroup.SOURCES -> state.sourceFilter.mapKeys { it.key.toString() }
            },
            onCycle = { key ->
                when (current) {
                    FilterGroup.RATINGS -> viewModel.cycleRating(key.toLong())
                    FilterGroup.LANGS -> viewModel.cycleLanguage(key)
                    FilterGroup.SOURCES -> viewModel.cycleSource(key.toLong())
                }
            },
            onReset = {
                when (current) {
                    FilterGroup.RATINGS -> viewModel.resetRatings()
                    FilterGroup.LANGS -> viewModel.resetLanguages()
                    FilterGroup.SOURCES -> viewModel.resetSources()
                }
            },
            onDismiss = { sheet = null },
        )
    }
}

/** Three filter-group buttons with tri-state counts (webui TriStateDropdown triggers). */
@Composable
private fun FilterHeader(state: SearchUiState, onOpen: (FilterGroup) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp),
    ) {
        FilterButton("分级", state.ratingFilter.size, onClick = { onOpen(FilterGroup.RATINGS) })
        FilterButton("语言", state.langFilter.size, onClick = { onOpen(FilterGroup.LANGS) })
        FilterButton("图源", state.sourceFilter.size, onClick = { onOpen(FilterGroup.SOURCES) })
        Text(
            "${state.participatingSources.size}/${state.sources.size} 个图源",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun FilterButton(label: String, active: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            Icons.Outlined.FilterList,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (active > 0) "$label · $active" else label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Tri-state option list in a bottom sheet: tap cycles none → include → exclude. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateSheet(
    title: String,
    options: List<Pair<String, String>>,
    states: Map<String, TriFilter>,
    onCycle: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("重置") }
            TextButton(onClick = onDismiss) { Text("完成") }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            options.forEach { (key, label) ->
                val tri = states[key] ?: TriFilter.NONE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCycle(key) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        when (tri) {
                            TriFilter.INCLUDE -> Icon(
                                Icons.Outlined.Check,
                                contentDescription = "包含",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            TriFilter.EXCLUDE -> Icon(
                                Icons.Outlined.Close,
                                contentDescription = "排除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                            TriFilter.NONE -> Unit
                        }
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                        color = if (tri == TriFilter.NONE) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    manga: SearchManga,
    baseUrl: String,
    apiKey: String,
    registering: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val coverUrl = manga.cover?.let { cover ->
        if (cover.startsWith("http")) {
            "${baseUrl.trimEnd('/')}/api/v1/sources/${manga.sourceId}/image?url=" +
                java.net.URLEncoder.encode(cover, "UTF-8")
        } else {
            "${baseUrl.trimEnd('/')}$cover"
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Authorization", "Bearer $apiKey")
                            .build(),
                    )
                    .build(),
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium),
            )
            if (registering) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
        Text(
            manga.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

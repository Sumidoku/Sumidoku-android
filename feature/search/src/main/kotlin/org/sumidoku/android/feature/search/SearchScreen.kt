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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.common.entryCoverUrl

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
    /** "View all" in a source section: browse that source with the current
     *  query (webui SearchPanel `navigate('/browse/{id}?q=')`). */
    onOpenSource: (Long, String) -> Unit,
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
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.search_back),
                            )
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
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.search() }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search_action),
                    )
                }
            }

            FilterHeader(
                state = state,
                onOpen = { sheet = it },
            )

            when {
                state.participatingSources.isEmpty() ->
                    CenteredMessage(stringResource(R.string.search_no_sources))
                !state.searched ->
                    CenteredMessage(
                        stringResource(R.string.search_prompt_source_count, state.participatingSources.size),
                    )
                state.visibleSections.isEmpty() && state.searching ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                // Every streaming source finished without a single entry —
                // webui shows the global empty state instead of empty shells.
                state.visibleSections.isEmpty() ->
                    CenteredMessage(stringResource(R.string.search_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    state.visibleSections.forEach { section ->
                        item(key = "header-${section.sourceId}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp),
                            ) {
                                Text(
                                    section.sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.search_entry_count, section.entries.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { onOpenSource(section.sourceId, state.query.trim()) },
                                    contentPadding = PaddingValues(start = 8.dp, end = 0.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.search_view_all),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
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
                                        val entry = section.entries[i]
                                        val entryIsLocal = state.sources.firstOrNull { it.id == entry.sourceId }?.kind == Source.Kind.LOCAL
                                        SearchResultCard(
                                            manga = entry,
                                            baseUrl = state.profile?.baseUrl.orEmpty(),
                                            apiKey = state.profile?.apiKey.orEmpty(),
                                            isLocal = entryIsLocal,
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
                                        Text(
                                            if (section.loading) {
                                                stringResource(R.string.search_loading)
                                            } else {
                                                stringResource(R.string.search_load_more)
                                            },
                                        )
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
                FilterGroup.RATINGS -> stringResource(R.string.search_filter_ratings)
                FilterGroup.LANGS -> stringResource(R.string.search_filter_languages)
                FilterGroup.SOURCES -> stringResource(R.string.search_filter_sources)
            },
            options = when (current) {
                FilterGroup.RATINGS -> listOf(
                    "0" to contentRatingLabel(0),
                    "1" to contentRatingLabel(1),
                    "2" to contentRatingLabel(2),
                )
                // Pair every language code with its localized label and sort by
                // the label, so the sheet reads alphabetically in the active
                // locale (the ViewModel only guarantees a stable code order).
                FilterGroup.LANGS -> {
                    val labeled = ArrayList<Pair<String, String>>(state.langOptions.size)
                    for (code in state.langOptions) labeled.add(code to languageLabel(code))
                    labeled.sortBy { it.second }
                    labeled
                }
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
            onDismiss = {
                sheet = null
                // Auto re-search only when the tri-state filters actually
                // changed while the sheet was open (webui refetches on the
                // sourceIds query key).
                viewModel.commitFilterChanges()
            },
        )
    }
}

/** Full-screen placeholder line (loading/idle/empty states). */
@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        FilterButton(
            stringResource(R.string.search_filter_ratings),
            state.ratingFilter.size,
            onClick = { onOpen(FilterGroup.RATINGS) },
        )
        FilterButton(
            stringResource(R.string.search_filter_languages),
            state.langFilter.size,
            onClick = { onOpen(FilterGroup.LANGS) },
        )
        FilterButton(
            stringResource(R.string.search_filter_sources),
            state.sourceFilter.size,
            onClick = { onOpen(FilterGroup.SOURCES) },
        )
        Text(
            stringResource(
                R.string.search_source_progress,
                state.participatingSources.size,
                state.sources.size,
            ),
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
            TextButton(onClick = onReset) { Text(stringResource(R.string.search_reset)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_done)) }
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
                                contentDescription = stringResource(R.string.search_include),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            TriFilter.EXCLUDE -> Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.search_exclude),
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
    isLocal: Boolean,
    registering: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // Mirrors webui SourceBrowsePage entryCover: server-relative manga covers
    // are used as-is, everything else goes through the source image proxy,
    // and local-source entries without a cover fall back to the manga cover
    // endpoint once the internal id is known.
    val coverUrl = entryCoverUrl(baseUrl, manga.sourceId, manga.cover, manga.id, isLocal)
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

/** Source-language display name (webui lib/source.ts languageLabel,
 *  Intl.DisplayNames equivalent with a fixed table for the common values). */
@Composable
private fun languageLabel(code: String): String {
    val normalized = code.trim().lowercase()
    val resId = when (normalized) {
        "multi", "all" -> R.string.search_lang_multi
        "en" -> R.string.search_lang_en
        "ja" -> R.string.search_lang_ja
        "zh" -> R.string.search_lang_zh
        "ko" -> R.string.search_lang_ko
        "fr" -> R.string.search_lang_fr
        "de" -> R.string.search_lang_de
        "es" -> R.string.search_lang_es
        "pt" -> R.string.search_lang_pt
        "it" -> R.string.search_lang_it
        "ru" -> R.string.search_lang_ru
        "ar" -> R.string.search_lang_ar
        "hi" -> R.string.search_lang_hi
        "th" -> R.string.search_lang_th
        "vi" -> R.string.search_lang_vi
        "id" -> R.string.search_lang_id
        "tr" -> R.string.search_lang_tr
        "pl" -> R.string.search_lang_pl
        "nl" -> R.string.search_lang_nl
        "sv" -> R.string.search_lang_sv
        "uk" -> R.string.search_lang_uk
        "ro" -> R.string.search_lang_ro
        "cs" -> R.string.search_lang_cs
        "el" -> R.string.search_lang_el
        "hu" -> R.string.search_lang_hu
        "fi" -> R.string.search_lang_fi
        "da" -> R.string.search_lang_da
        "no" -> R.string.search_lang_no
        "he" -> R.string.search_lang_he
        "fa" -> R.string.search_lang_fa
        "ms" -> R.string.search_lang_ms
        "tl" -> R.string.search_lang_tl
        "ca" -> R.string.search_lang_ca
        else -> null
    }
    return resId?.let { stringResource(it) } ?: code
}

/** Source-level content rating levels (webui CONTENT_RATING_LEVELS: 0 safe / 1 contains NSFW / 2 NSFW). */
@Composable
private fun contentRatingLabel(level: Int): String = when (level) {
    1 -> stringResource(R.string.search_rating_contains_nsfw)
    2 -> stringResource(R.string.search_rating_nsfw)
    else -> stringResource(R.string.search_rating_safe)
}

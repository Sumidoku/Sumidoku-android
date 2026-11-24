package org.sumidoku.android.feature.manga

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.toBitmap
import org.sumidoku.android.core.ui.AppModalDialog
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.Manga
import org.sumidoku.android.core.api.model.MangaStatus
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.api.model.TrackerLink
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.common.sourceImageUrl
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.entryCoverUrl
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.feature.trackers.TrackerLogo
import org.sumidoku.android.feature.trackers.trackerProviderLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manga detail: header, metadata, chapter list — preview 06-manga.html.
 *  Takes the id explicitly and builds the view model through its
 *  assisted factory so the screen can live inside a nav route or the
 *  expanded-width detail pane (design §17). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(
    mangaId: Long,
    onBack: () -> Unit,
    onOpenChapter: (mangaId: Long, chapterId: Long) -> Unit,
    /** Author/artist/tag tap: hand the browse page the single filter value
     *  it should search with, plus the manga's source (webui
     *  searchByFilters parity). */
    onFilterSearch: (sourceId: Long, filters: List<FilterValue>) -> Unit = { _, _ -> },
    /** Unregistered source entry (Aidoku open-first): mangaId is -1 and the
     *  view model registers it in the background while the page renders. */
    sourceId: Long = -1L,
    mangaKey: String? = null,
    viewModel: MangaViewModel = hiltViewModel<MangaViewModel, MangaViewModel.Factory>(
        key = "manga-$mangaId-$sourceId-${mangaKey.orEmpty()}",
    ) { factory -> factory.create(mangaId, sourceId, mangaKey) },
) {
    val state by viewModel.state.collectAsState()
    val meta by viewModel.meta.collectAsState()
    val searchMeta by viewModel.searchMeta.collectAsState()
    var activeSheet by remember { mutableStateOf<MangaSheet?>(null) }
    var showChapterFilters by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(meta.message, meta.error) {
        val text = meta.message ?: meta.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    // Average cover colour (KMReader computeCoverTint) accents the resume
    // FAB; reset when the manga (and thus its cover) changes.
    var coverTint by remember(state.manga?.id) { mutableStateOf<Color?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Floating resume-reading button (hidden in multi-select, where
            // the bottom action bar owns the corner).
            val manga = state.manga
            val resume = state.resumeChapter
            if (manga != null && resume != null && !selecting) {
                // The average cover colour (when resolved) accents the
                // resume FAB; falls back to the default FAB colours.
                val fabTint = coverTint
                ExtendedFloatingActionButton(
                    onClick = { if (manga.id > 0) onOpenChapter(manga.id, resume.id) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                    text = {
                        // "Start reading" until any chapter has progress;
                        // after that it is a resume (with the page number
                        // when the chapter was left mid-way).
                        val hasProgress = state.chapters.any { it.isRead || it.lastPageRead > 0 }
                        Text(
                            if (!hasProgress) {
                                stringResource(R.string.manga_start_reading)
                            } else if (resume.lastPageRead > 0 && !resume.isRead) {
                                stringResource(R.string.manga_resume_page, resume.lastPageRead + 1)
                            } else {
                                stringResource(R.string.manga_resume)
                            },
                        )
                    },
                    containerColor = fabTint ?: FloatingActionButtonDefaults.containerColor,
                    contentColor = fabTint
                        ?.let { if (it.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White }
                        ?: MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        bottomBar = {
            if (selecting && selectedIds.isNotEmpty()) {
                val finishedCount = state.chapters.count { it.id in selectedIds && it.hasFinishedDownload }
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        fun exitSelect() {
                            selecting = false
                            selectedIds = emptySet()
                        }
                        TextButton(onClick = {
                            viewModel.batchMarkRead(selectedIds.toList(), true)
                            exitSelect()
                        }) { Text(stringResource(R.string.manga_batch_mark_read)) }
                        TextButton(onClick = {
                            viewModel.batchMarkRead(selectedIds.toList(), false)
                            exitSelect()
                        }) { Text(stringResource(R.string.manga_batch_mark_unread)) }
                        TextButton(onClick = {
                            viewModel.batchBookmark(selectedIds.toList(), true)
                            exitSelect()
                        }) { Text(stringResource(R.string.manga_batch_bookmark)) }
                        TextButton(onClick = {
                            viewModel.batchBookmark(selectedIds.toList(), false)
                            exitSelect()
                        }) { Text(stringResource(R.string.manga_remove_bookmark)) }
                        TextButton(onClick = {
                            viewModel.downloadChapters(selectedIds.toList())
                            exitSelect()
                        }) { Text(stringResource(R.string.manga_download)) }
                        TextButton(
                            onClick = {
                                viewModel.deleteDownloadsFor(selectedIds.toList())
                                exitSelect()
                            },
                            enabled = finishedCount > 0,
                        ) { Text(stringResource(R.string.manga_delete_downloads_count, finishedCount)) }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {},
                // Transparent: the blurred-cover backdrop reaches behind the
                // bar (the list below skips the scaffold's top padding).
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.manga_back))
                    }
                },
                actions = {
                    val manga = state.manga
                    if (manga != null) {
                        IconButton(onClick = { viewModel.toggleIncognito() }) {
                            Icon(
                                Icons.Outlined.VisibilityOff,
                                contentDescription = stringResource(R.string.manga_incognito),
                                tint = if (manga.incognito) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.manga_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manga_refresh_details)) },
                                    enabled = !state.refreshingDetails,
                                    onClick = {
                                        showMenu = false
                                        viewModel.refreshDetailsFromSource()
                                    },
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.metadata_edit)) }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Edit
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.metadata_match)) }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Match
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.manga_change_cover)) }, onClick = {
                                    showMenu = false
                                    viewModel.loadAlternates()
                                    activeSheet = MangaSheet.Cover
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.metadata_restore)) }, onClick = {
                                    showMenu = false
                                    viewModel.restoreMetadata()
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.manga_categories)) }, onClick = {
                                    showMenu = false
                                    viewModel.loadCategories()
                                    activeSheet = MangaSheet.Categories
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.manga_migrate_menu)) }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Migrate
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.manga_mark_all_read)) }, onClick = {
                                    showMenu = false
                                    viewModel.markAllChapters(true)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.manga_mark_all_unread)) }, onClick = {
                                    showMenu = false
                                    viewModel.markAllChapters(false)
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.manga_retry)) }
                }
            }
            else -> {
                val manga = state.manga!!
                // The header backdrop reaches behind the transparent app
                // bar, so the list skips the scaffold's top padding and the
                // header item pads its content down instead.
                val topPadding = padding.calculateTopPadding()
                val bottomPadding = padding.calculateBottomPadding()
                // Info pane (blurred-cover header + tracker links) and
                // chapter pane (header row + selection bar + list) as
                // LazyListScope lambdas: the tablet layout below gives each
                // pane its own scroll container, the phone layout stacks
                // them in one list.
                val infoPane: LazyListScope.() -> Unit = {
                    item {
                        Box {
                            CoverBackdrop(manga, state.profile)
                            Column(Modifier.padding(top = topPadding)) {
                            MangaHeader(
                            state = state,
                            searchMeta = searchMeta,
                            onResume = {
                                if (manga.id > 0) {
                                    state.resumeChapter?.let { onOpenChapter(manga.id, it.id) }
                                }
                            },
                            onToggleLibrary = { viewModel.toggleLibrary() },
                            onFilterSearch = { values -> onFilterSearch(manga.sourceId, values) },
                            onCoverBitmap = { bitmap ->
                                // Purely decorative; a bitmap failure must not crash the page
                                runCatching { computeCoverTint(bitmap) }.getOrNull()?.let { coverTint = it }
                            },
                            )
                            // Tracker links live inside the backdrop too, so
                            // the gradient can fade out underneath them
                            // instead of cutting off at the header edge.
                            TrackerSection(
                                links = meta.links.orEmpty(),
                                remotes = meta.remoteStates,
                                onOpen = { link -> viewModel.openLinkEditor(link) },
                                onManage = {
                                    viewModel.loadLinks()
                                    activeSheet = MangaSheet.Links
                                },
                            )
                            }
                        }
                    }
                }
                val chapterPane: LazyListScope.() -> Unit = {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        // Unregistered entry still resolving:
                                        // no authoritative count yet.
                                        state.resolving && state.chapters.isEmpty() ->
                                            stringResource(R.string.manga_chapters)
                                        state.chapterPrefs.filtersActive -> stringResource(
                                            R.string.manga_chapters_filtered,
                                            state.visibleChapters.size,
                                            state.chapters.size,
                                        )
                                        else -> stringResource(
                                            R.string.manga_chapters_count,
                                            state.chapters.size,
                                        )
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                state.refreshNote?.let { note ->
                                    Text(
                                        note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (state.refreshingChapters) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(onClick = { viewModel.refreshChaptersFromSource() }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = stringResource(R.string.manga_refresh_chapters),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { showChapterFilters = true }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Outlined.FilterList,
                                    contentDescription = stringResource(R.string.manga_chapter_filters),
                                    tint = if (state.chapterPrefs.filtersActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (!selecting && state.chapters.isNotEmpty()) {
                                TextButton(onClick = { selecting = true }) { Text(stringResource(R.string.manga_select)) }
                            }
                        }
                    }
                    if (selecting) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                val visible = state.visibleChapters
                                val allSelected = visible.isNotEmpty() && visible.all { it.id in selectedIds }
                                TextButton(onClick = {
                                    selectedIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        visible.map { it.id }.toSet()
                                    }
                                }) { Text(if (allSelected) stringResource(R.string.manga_deselect_all) else stringResource(R.string.manga_select_all)) }
                                Text(
                                    stringResource(R.string.manga_selected_count, selectedIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    selecting = false
                                    selectedIds = emptySet()
                                }) { Text(stringResource(R.string.manga_done)) }
                            }
                        }
                    }
                    // Aidoku-style resolve in flight and no streamed chapters
                    // yet: inline loading row instead of a blank section.
                    if (state.resolving && state.visibleChapters.isEmpty()) {
                        item(key = "resolving") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    stringResource(R.string.manga_fetching_chapters),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(state.visibleChapters, key = { it.id }) { chapter ->
                        ChapterRow(
							chapter = chapter,
							localStatus = state.localStatus[chapter.id],
							selecting = selecting,
							selected = chapter.id in selectedIds,
							onClick = {
								if (selecting) {
									selectedIds = if (chapter.id in selectedIds) {
										selectedIds - chapter.id
									} else {
										selectedIds + chapter.id
									}
								} else if (manga.id > 0 && chapter.id > 0) {
									// Streaming placeholder chapters get real
									// server ids after registration lands.
									onOpenChapter(manga.id, chapter.id)
								}
							},
							onEnqueue = { viewModel.enqueueChapter(chapter.id) },
							onEnqueueLocal = { viewModel.enqueueLocal(chapter.id) },
							onMarkRead = { viewModel.batchMarkRead(listOf(chapter.id), true) },
							onMarkUnread = { viewModel.batchMarkRead(listOf(chapter.id), false) },
							onMarkPreviousRead = { viewModel.markPrevious(chapter, true) },
							onMarkPreviousUnread = { viewModel.markPrevious(chapter, false) },
							onToggleBookmark = { viewModel.batchBookmark(listOf(chapter.id), !chapter.isBookmarked) },
							onDeleteDownload = { viewModel.deleteDownloadsFor(listOf(chapter.id)) },
						)
                    }
                }
                // Two-panel only pays off with real horizontal room:
                // portrait tablets stay on the single-column phone layout.
                val configuration = LocalConfiguration.current
                val isTablet = configuration.smallestScreenWidthDp >= 600 &&
                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isTablet) {
                    // Two-panel (komikku TwoPanelBox parity): the info pane
                    // takes half the width capped at 450dp, the chapter list
                    // takes the rest; each pane scrolls on its own.
                    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
                        val leftWidth = (maxWidth / 2).coerceAtMost(450.dp)
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.width(leftWidth).fillMaxHeight(), content = infoPane)
                            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            LazyColumn(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(top = topPadding),
                                content = chapterPane,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(bottom = bottomPadding),
                    ) {
                        infoPane()
                        chapterPane()
                    }
                }
            }
        }
    }

    // M6 sheets
    val mangaForSheets = state.manga
    val profileForSheets = state.profile
    when (activeSheet) {
        MangaSheet.Edit -> if (mangaForSheets != null) {
            EditMetadataSheet(
                manga = mangaForSheets,
                busy = meta.editBusy,
                onDismiss = { activeSheet = null },
                onSave = { title, authors, artists, description, tags, status, contentRating, unlock ->
                    viewModel.saveMetadata(title, authors, artists, description, tags, status, contentRating, unlock)
                    activeSheet = null
                },
            )
        }
        MangaSheet.Cover -> if (mangaForSheets != null && profileForSheets != null) {
            CoverPickerSheet(
                profile = profileForSheets,
                sourceId = mangaForSheets.sourceId,
                alternates = meta.alternates,
                busy = meta.coverBusy,
                onDismiss = { activeSheet = null },
                onSelect = { url ->
                    viewModel.selectCover(url)
                    activeSheet = null
                },
            )
        }
        MangaSheet.Match -> if (mangaForSheets != null) {
            MatchMetadataSheet(
                meta = meta,
                defaultQuery = mangaForSheets.title,
                onProvider = { viewModel.setMatchProvider(it) },
                onQuery = { viewModel.setMatchQuery(it) },
                onSearch = { viewModel.matchSearch() },
                onPick = { viewModel.loadPreview(it) },
                onToggleField = { viewModel.togglePreviewField(it) },
                onBack = { viewModel.backToMatchResults() },
                onApply = { bind ->
                    viewModel.applyMatch(bind)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
            )
        }
        MangaSheet.Links -> if (mangaForSheets != null) {
            TrackerLinksSheet(
                meta = meta,
                defaultQuery = mangaForSheets.title,
                onProvider = { viewModel.setLinkAddProvider(it) },
                onQuery = { viewModel.setLinkAddQuery(it) },
                onSearch = { viewModel.linkAddSearch() },
                onAdd = { provider, item -> viewModel.addLink(provider, item) },
                onRemove = { viewModel.removeLink(it) },
                onSync = { viewModel.syncLink(it) },
                onEdit = { link ->
                    viewModel.openLinkEditor(link)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
            )
        }
        MangaSheet.Categories -> if (mangaForSheets != null) {
            CategoriesSheet(
                categories = meta.categories,
                selectedIds = mangaForSheets.categoryIds.toSet(),
                onSave = { ids ->
                    viewModel.saveCategories(ids)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
            )
        }
        MangaSheet.Migrate -> if (mangaForSheets != null) {
            MigrateSheet(
                meta = meta,
                sources = meta.migrationSources,
                profile = profileForSheets,
                onDismiss = { activeSheet = null },
                onStart = { viewModel.startMigration() },
                onSelectTarget = { viewModel.selectMigrationTarget(it) },
                onSelectCandidate = { viewModel.selectMigrationCandidate(it) },
                onRun = { mode ->
                    viewModel.runMigration(mode)
                    activeSheet = null
                },
            )
        }
        null -> Unit
    }

    // Chapter list filter/sort sheet (webui ChapterHeader dropdown parity).
    if (showChapterFilters) {
        ChapterFilterSheet(
            chapters = state.chapters,
            prefs = state.chapterPrefs,
            onChange = { viewModel.setChapterPrefs(it) },
            onDismiss = { showChapterFilters = false },
        )
    }

    // M6 sync-pull preview: show what would be applied, then confirm.
    meta.syncPreview?.let { (provider, result) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncPreview() },
            title = { Text(stringResource(R.string.tracker_sync_title)) },
            text = {
                val progressLine = stringResource(
                    R.string.tracker_sync_remote_progress,
                    result.progress.toString(),
                )
                val confirmLine = stringResource(R.string.tracker_sync_confirm, result.marked)
                Text(progressLine + confirmLine)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applySync(provider) }) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSyncPreview() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // M6 tracker-link editor: trackMode/chapterOffset + manual remote
    // status/score/progress edits, sync-pull and confirmed unbind
    // (webui TrackerEditForm).
    meta.editingLink?.let { link ->
        TrackerLinkEditSheet(
            meta = meta,
            link = link,
            onMode = { viewModel.setEditTrackMode(it) },
            onOffset = { viewModel.setEditChapterOffset(it) },
            onStatus = { viewModel.setEditStatus(it) },
            onScore = { viewModel.setEditScore(it) },
            onChapterProgress = { viewModel.setEditChapterProgress(it) },
            onVolumeProgress = { viewModel.setEditVolumeProgress(it) },
            onSave = { viewModel.saveLinkSettings() },
            onSync = { viewModel.syncLink(link.provider) },
            onRemove = { viewModel.removeLink(link.provider) },
            onDismiss = { viewModel.closeLinkEditor() },
        )
    }
}

/** M6 sheet targets on the manga detail screen. */
private enum class MangaSheet { Edit, Cover, Match, Links, Categories, Migrate }

/** Single-manga migration sheet (design §14 M7): pick a target
 *  source, auto-lookup matches (manual pick from candidates), choose
 *  copy/migrate/migrateWithHistory, execute. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MigrateSheet(
    meta: MetaUiState,
    sources: List<org.sumidoku.android.core.api.model.MigrationSource>?,
    profile: ServerProfile?,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onSelectTarget: (Long) -> Unit,
    onSelectCandidate: (SearchManga?) -> Unit,
    onRun: (MigrateMode) -> Unit,
) {
    var mode by remember { mutableStateOf(MigrateMode.MIGRATE) }
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.manga_migrate_title), style = MaterialTheme.typography.titleMedium)
            if (sources == null) {
                LaunchedEffect(Unit) { onStart() }
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(stringResource(R.string.manga_target_source), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources.size) { i ->
                        val source = sources[i]
                        FilterChip(
                            selected = meta.migrationTargetId == source.id,
                            onClick = { onSelectTarget(source.id) },
                            label = { Text(source.name ?: stringResource(R.string.manga_source_fallback, source.id), maxLines = 1) },
                        )
                    }
                }

                when {
                    meta.migrationBusy -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    meta.migrationCandidates != null -> {
                        HorizontalDivider()
                        Text(stringResource(R.string.manga_match_results), style = MaterialTheme.typography.titleSmall)
                        if (meta.migrationCandidates.isEmpty()) {
                            Text(
                                stringResource(R.string.manga_no_match),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            val isLocalTarget = sources.firstOrNull { it.id == meta.migrationTargetId }
                                ?.kind == org.sumidoku.android.core.api.model.MigrationSource.Kind.LOCAL
                            meta.migrationCandidates.take(6).forEach { candidate ->
                                val selected = meta.migrationSelected?.key == candidate.key
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectCandidate(if (selected) null else candidate)
                                        }
                                        .padding(vertical = 2.dp),
                                ) {
                                    SearchMangaCoverThumb(profile, candidate, isLocalTarget)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        candidate.authors?.takeIf { it.isNotEmpty() }?.let {
                                            Text(
                                                it.joinToString(stringResource(R.string.manga_list_separator)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            onSelectCandidate(if (selected) null else candidate)
                                        },
                                    )
                                }
                            }
                        }

                        HorizontalDivider()
                        Text(stringResource(R.string.manga_migrate_mode), style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(
                                MigrateMode.COPY to stringResource(R.string.manga_mode_copy),
                                MigrateMode.MIGRATE to stringResource(R.string.manga_mode_migrate),
                                MigrateMode.MIGRATE_WITH_HISTORY to stringResource(R.string.manga_mode_with_history),
                            ).forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = mode == value,
                                    onClick = { mode = value },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                                ) { Text(label) }
                            }
                        }
                        Button(
                            onClick = { onRun(mode) },
                            enabled = meta.migrationSelected != null && !meta.migrationBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.manga_start_migration)) }
                    }
                    else -> Text(
                        stringResource(R.string.manga_select_target_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Small cover for a search/migration candidate (SearchManga): mirrors webui
 *  entryCover — proxy through the source for remote covers, the manga id
 *  endpoint for local-source entries. */
@Composable
private fun SearchMangaCoverThumb(profile: ServerProfile?, candidate: SearchManga, isLocal: Boolean) {
    val context = LocalContext.current
    val model = profile?.let { p ->
        entryCoverUrl(p.baseUrl, candidate.sourceId, candidate.cover, candidate.id, isLocal)?.let { url ->
            ImageRequest.Builder(context).data(url).apply {
                p.apiKey?.let { key ->
                    httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
                }
            }.build()
        }
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .size(40.dp, 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
}

/** Komikku-style header backdrop (MangaInfoHeader parity): the cover itself,
 *  blurred and dimmed, fills the whole header area — the height follows the
 *  header content, not a fixed value. A surfaceTint veil keeps the text
 *  readable, and a gradient starting halfway down fades the backdrop into
 *  the page background. Loads the same image request as [CoverImage], so it
 *  is a coil cache hit. */
@Composable
private fun BoxScope.CoverBackdrop(manga: Manga, profile: ServerProfile?) {
    val context = LocalContext.current
    val url = profile?.baseUrl?.let { base ->
        if (manga.id <= 0) {
            manga.cover?.let { sourceImageUrl(base, manga.sourceId, it) }
        } else {
            coverUrl(base, manga.id, manga.cover)
        }
    }
    val model = profile?.let {
        ImageRequest.Builder(context).data(url).apply {
            it.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }.build()
    } ?: return
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .blur(7.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            // 0.35 (not komikku's 0.2): our surfaceTint is strongly hued,
            // so the cover needs more presence or every backdrop looks the
            // same colour.
            .alpha(0.35f),
        contentScale = ContentScale.Crop,
    )
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.2f)))
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, background),
                        startY = size.height / 2,
                        endY = size.height,
                    ),
                )
            },
    )
}

@Composable
private fun MangaHeader(
    state: MangaUiState,
    searchMeta: SourceSearchMeta,
    onResume: () -> Unit,
    onToggleLibrary: () -> Unit,
    onFilterSearch: (List<FilterValue>) -> Unit,
    onCoverBitmap: (Bitmap) -> Unit = {},
) {
    val manga = state.manga!!
    val profile = state.profile
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            CoverImage(manga.id, manga.cover, profile, Modifier.size(108.dp, 144.dp), manga.sourceId, onCoverBitmap)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(manga.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val creators = searchMeta.creators(manga.authors, manga.artists)
                if (creators.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        creators.forEachIndexed { index, (name, isAuthor) ->
                            if (index > 0) {
                                Text(
                                    " / ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val value = searchMeta.creatorFilterValue(name, isAuthor)
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (value != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = if (value != null) {
                                    Modifier.clickable { onFilterSearch(listOf(value)) }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.manga_status_chapters, mangaStatusLabel(manga.status), manga.chapterCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (manga.incognito) {
                        Spacer(Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(stringResource(R.string.manga_incognito_active), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onResume, enabled = state.resumeChapter != null, modifier = Modifier.weight(1f)) {
                Text(
                    state.resumeChapter?.let { ch ->
                        val hasProgress = state.chapters.any { it.isRead || it.lastPageRead > 0 }
                        if (!hasProgress) {
                            stringResource(R.string.manga_start_reading)
                        } else if (ch.lastPageRead > 0 && !ch.isRead) {
                            stringResource(R.string.manga_resume_page, ch.lastPageRead + 1)
                        } else {
                            stringResource(R.string.manga_resume)
                        }
                    } ?: stringResource(R.string.manga_no_chapters),
                )
            }
            FilledTonalButton(onClick = onToggleLibrary, modifier = Modifier.weight(1f)) {
                Text(if (manga.inLibrary) stringResource(R.string.manga_remove_from_library) else stringResource(R.string.manga_add_to_library))
            }
        }
        manga.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(12.dp))
            var expanded by remember { mutableStateOf(false) }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        manga.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tags.take(6).forEach { tag ->
                    val value = searchMeta.tagFilterValue(tag)
                    SuggestionChip(
                        onClick = { value?.let { onFilterSearch(listOf(it)) } },
                        label = { Text(tag) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterWithDownload,
    localStatus: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onEnqueue: () -> Unit,
    onEnqueueLocal: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPreviousRead: () -> Unit,
    onMarkPreviousUnread: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    val dimmed = chapter.isRead
    // Read the locale from the composition so a locale change recomposes properly
    // (Locale.getDefault() is not observable).
    val locale = LocalConfiguration.current.locales[0]
    var showActions by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                // webui chapterLabel parity: Vol. X · Ch. X · title → … → untitled chapter
                chapterLabel(chapter.volumeNumber, chapter.chapterNumber, chapter.title),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val readPageLabel = if (chapter.lastPageRead > 0 && !chapter.isRead) {
                stringResource(R.string.manga_read_to_page, chapter.lastPageRead + 1)
            } else {
                null
            }
            val subtitle = buildList {
                chapter.dateUploaded?.let { add(SimpleDateFormat("yyyy-MM-dd", locale).format(Date(org.sumidoku.android.core.common.normalizeEpochMs(it)))) }
                if (readPageLabel != null) add(readPageLabel)
                // Aidoku subtitle parity: every scanlator group, comma-joined.
                chapter.scanlators?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chapter.lastPageRead > 0 && !chapter.isRead) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (!selecting) {
            if (chapter.locked) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.manga_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (chapter.hasFinishedDownload) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = stringResource(R.string.manga_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else {
                IconButton(onClick = onEnqueue, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = stringResource(R.string.manga_download_chapter),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            when (localStatus) {
                "done" -> Icon(
                    Icons.Outlined.SaveAlt,
                    contentDescription = stringResource(R.string.manga_saved_locally),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(start = 2.dp),
                )
                "queued", "downloading" -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).padding(start = 2.dp),
                    strokeWidth = 2.dp,
                )
                "error" -> Icon(
                    Icons.Outlined.SaveAlt,
                    contentDescription = stringResource(R.string.manga_local_download_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                        .clickable(onClick = onEnqueueLocal),
                )
                else -> IconButton(onClick = onEnqueueLocal, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.SaveAlt,
                        contentDescription = stringResource(R.string.manga_save_local),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (chapter.isRead) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.manga_read),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box {
                IconButton(onClick = { showActions = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.manga_chapter_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_mark_read)) },
                        enabled = !chapter.isRead,
                        onClick = {
                            showActions = false
                            onMarkRead()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_mark_unread)) },
                        enabled = chapter.isRead,
                        onClick = {
                            showActions = false
                            onMarkUnread()
                        },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.manga_mark_previous_read)) }, onClick = {
                        showActions = false
                        onMarkPreviousRead()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.manga_mark_previous_unread)) }, onClick = {
                        showActions = false
                        onMarkPreviousUnread()
                    })
                    DropdownMenuItem(
                        text = { Text(if (chapter.isBookmarked) stringResource(R.string.manga_remove_bookmark) else stringResource(R.string.manga_bookmark_chapter)) },
                        onClick = {
                            showActions = false
                            onToggleBookmark()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_delete_download)) },
                        enabled = chapter.hasFinishedDownload,
                        onClick = {
                            showActions = false
                            onDeleteDownload()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSheet(
    categories: List<org.sumidoku.android.core.api.model.Category>?,
    selectedIds: Set<Long>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(selectedIds) { mutableStateOf(selectedIds) }
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.manga_categories_title), style = MaterialTheme.typography.titleMedium)
            when {
                categories == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                categories.isEmpty() -> Text(
                    stringResource(R.string.manga_no_categories),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (category.id in selected) selected - category.id else selected + category.id
                        },
                    ) {
                        Checkbox(
                            checked = category.id in selected,
                            onCheckedChange = {
                                selected = if (category.id in selected) selected - category.id else selected + category.id
                            },
                        )
                        Text(category.name, modifier = Modifier.weight(1f))
                        Text(
                            "${category.mangaCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
internal fun CoverImage(
    mangaId: Long,
    cover: String?,
    profile: ServerProfile?,
    modifier: Modifier = Modifier,
    /** Needed only while mangaId is the -1 unregistered placeholder: the
     *  cover then comes from the source-image proxy, not /manga/{id}/cover. */
    sourceId: Long = -1L,
    /** Decoded cover pixels (used to derive the header tint). */
    onBitmap: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Same as webui coverUrl(manga.id, manga.cover): the server resolves the
    // image itself, so local-source manga with cover == null still load.
    val url = profile?.baseUrl?.let { base ->
        if (mangaId <= 0) {
            cover?.let { sourceImageUrl(base, sourceId, it) }
        } else {
            coverUrl(base, mangaId, cover)
        }
    }
    val model = profile?.let {
        ImageRequest.Builder(context).data(url).apply {
            it.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }.build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
        onSuccess = { result ->
            onBitmap?.let { callback -> runCatching { callback(result.result.image.toBitmap()) } }
        },
    )
}

/** KMReader-style cover tint (ui/components computeCoverTint): the 8×8
 *  average colour of the cover with HSV saturation capped at 0.8 and value
 *  clamped to 0.2–0.45, so the header backdrop stays readable in both
 *  light and dark themes. */
private fun computeCoverTint(bitmap: Bitmap): Color? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val scaled = Bitmap.createScaledBitmap(software, 8, 8, true)
    var r = 0L
    var g = 0L
    var b = 0L
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val pixel = scaled.getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
    }
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (r / 64f).toInt().coerceIn(0, 255),
        (g / 64f).toInt().coerceIn(0, 255),
        (b / 64f).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = hsv[1].coerceAtMost(0.8f)
    hsv[2] = hsv[2].coerceIn(0.2f, 0.45f)
    return Color.hsv(hsv[0], hsv[1], hsv[2])
}

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/** Inline tracker bindings, mirroring webui's TrackerSection: a section
 *  header with the bound provider pills (brand logo + name + remote status)
 *  and one entry point that opens the full link manager (add/sync/unbind). */
@Composable
private fun TrackerSection(
    links: List<TrackerLink>,
    remotes: Map<TrackerProvider, TrackerRemote>,
    onOpen: (TrackerLink) -> Unit,
    onManage: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tracker_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManage) {
                Text(if (links.isEmpty()) stringResource(R.string.tracker_add_link) else stringResource(R.string.tracker_manage))
            }
        }
        if (links.isEmpty()) {
            Text(
                stringResource(R.string.tracker_no_links),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                links.forEach { link ->
                    TrackerPill(
                        link = link,
                        remote = remotes[link.provider],
                        onClick = { onOpen(link) },
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/** Pill = brand logo + provider name + remote status; a red dot marks a
 *  provider whose remote state could not be read. Tapping opens the editor. */
@Composable
private fun TrackerPill(link: TrackerLink, remote: TrackerRemote?, onClick: () -> Unit) {
    val status = remote?.state?.status?.let { trackStatusLabel(it) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrackerLogo(link.provider, size = 16.dp)
        Text(trackerProviderLabel(link.provider), style = MaterialTheme.typography.labelLarge)
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (remote?.failed == true) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

@Composable
private fun mangaStatusLabel(status: MangaStatus): String = when (status) {
    MangaStatus.UNKNOWN -> stringResource(R.string.manga_status_unknown)
    MangaStatus.ONGOING -> stringResource(R.string.manga_status_ongoing)
    MangaStatus.COMPLETED -> stringResource(R.string.manga_status_completed)
    MangaStatus.CANCELLED -> stringResource(R.string.manga_status_cancelled)
    MangaStatus.HIATUS -> stringResource(R.string.manga_status_hiatus)
}

/** Chapter list filter/sort sheet (webui chapterPrefs + Aidoku chapter
 *  filters parity): sort key + direction, four tri-state filters, and
 *  language / scanlator pickers that only appear when more than one
 *  distinct value exists. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChapterFilterSheet(
    chapters: List<ChapterWithDownload>,
    prefs: ChapterPrefs,
    onChange: (ChapterPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val languages = chapters.mapNotNull { it.language?.takeIf(String::isNotBlank) }.distinct().sorted()
    val allScanlators = chapters.flatMap { it.scanlators.orEmpty() }.distinct().sorted()
    val hasUngrouped = chapters.any { it.scanlators.orEmpty().isEmpty() }
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.chapter_sort), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val sorts = listOf(
                    ChapterListSort.SOURCE to stringResource(R.string.chapter_sort_default),
                    ChapterListSort.CHAPTER to stringResource(R.string.chapter_sort_number),
                    ChapterListSort.DATE to stringResource(R.string.chapter_sort_date),
                )
                sorts.forEachIndexed { index, (sort, label) ->
                    SegmentedButton(
                        selected = prefs.sort == sort,
                        onClick = { onChange(prefs.copy(sort = sort)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = sorts.size),
                    ) { Text(label) }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val dirs = listOf(
                    false to stringResource(R.string.chapter_sort_desc),
                    true to stringResource(R.string.chapter_sort_asc),
                )
                dirs.forEachIndexed { index, (asc, label) ->
                    SegmentedButton(
                        selected = prefs.asc == asc,
                        onClick = { onChange(prefs.copy(asc = asc)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = dirs.size),
                    ) { Text(label) }
                }
            }
            Text(stringResource(R.string.chapter_filter), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriChip(stringResource(R.string.chapter_filter_unread), prefs.unread) { onChange(prefs.copy(unread = it)) }
                TriChip(stringResource(R.string.chapter_filter_downloaded), prefs.downloaded) { onChange(prefs.copy(downloaded = it)) }
                TriChip(stringResource(R.string.chapter_filter_bookmarked), prefs.bookmarked) { onChange(prefs.copy(bookmarked = it)) }
                TriChip(stringResource(R.string.chapter_filter_locked), prefs.locked) { onChange(prefs.copy(locked = it)) }
            }
            if (languages.size > 1) {
                Text(stringResource(R.string.chapter_language), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.language == null,
                        onClick = { onChange(prefs.copy(language = null)) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    languages.forEach { lang ->
                        FilterChip(
                            selected = prefs.language == lang,
                            onClick = { onChange(prefs.copy(language = lang)) },
                            label = { Text(lang) },
                        )
                    }
                }
            }
            if (allScanlators.size > 1) {
                Text(stringResource(R.string.chapter_scanlators), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selected = prefs.scanlators
                    val options = if (hasUngrouped) listOf("") + allScanlators else allScanlators
                    FilterChip(
                        selected = selected == null,
                        onClick = { onChange(prefs.copy(scanlators = null)) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    options.forEach { group ->
                        FilterChip(
                            selected = selected != null && group in selected,
                            onClick = {
                                val next = (selected ?: emptySet()).toMutableSet().apply {
                                    if (!add(group)) remove(group)
                                }
                                onChange(prefs.copy(scanlators = next.ifEmpty { null }))
                            },
                            label = { Text(if (group.isEmpty()) stringResource(R.string.chapter_no_scanlator) else group) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onChange(ChapterPrefs()) }) { Text(stringResource(R.string.chapter_reset_all)) }
                Button(onClick = onDismiss) { Text(stringResource(R.string.manga_done)) }
            }
        }
    }
}

@Composable
private fun TriChip(label: String, value: Tri, onChange: (Tri) -> Unit) {
    val stateLabel = when (value) {
        Tri.OFF -> stringResource(R.string.tri_off)
        Tri.INCLUDE -> stringResource(R.string.tri_include)
        Tri.EXCLUDE -> stringResource(R.string.tri_exclude)
    }
    FilterChip(
        selected = value != Tri.OFF,
        onClick = { onChange(value.next()) },
        label = { Text(stringResource(R.string.tri_chip_label, label, stateLabel)) },
    )
}

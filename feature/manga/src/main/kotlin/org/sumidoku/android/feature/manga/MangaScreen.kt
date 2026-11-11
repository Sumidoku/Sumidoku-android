package org.sumidoku.android.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.North
import androidx.compose.material.icons.outlined.South
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
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
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.model.ServerProfile
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
    viewModel: MangaViewModel = hiltViewModel<MangaViewModel, MangaViewModel.Factory>(
        key = "manga-$mangaId",
    ) { factory -> factory.create(mangaId) },
) {
    val state by viewModel.state.collectAsState()
    val meta by viewModel.meta.collectAsState()
    var activeSheet by remember { mutableStateOf<MangaSheet?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(meta.message, meta.error) {
        val text = meta.message ?: meta.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val manga = state.manga
                    if (manga != null) {
                        IconButton(onClick = { viewModel.toggleIncognito() }) {
                            Icon(
                                Icons.Outlined.VisibilityOff,
                                contentDescription = "无痕阅读",
                                tint = if (manga.incognito) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("编辑元数据") }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Edit
                                })
                                DropdownMenuItem(text = { Text("匹配元数据") }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Match
                                })
                                DropdownMenuItem(text = { Text("更换封面") }, onClick = {
                                    showMenu = false
                                    viewModel.loadAlternates()
                                    activeSheet = MangaSheet.Cover
                                })
                                DropdownMenuItem(text = { Text("恢复图源元数据") }, onClick = {
                                    showMenu = false
                                    viewModel.restoreMetadata()
                                })
                                DropdownMenuItem(text = { Text("追踪链接") }, onClick = {
                                    showMenu = false
                                    viewModel.loadLinks()
                                    activeSheet = MangaSheet.Links
                                })
                                DropdownMenuItem(text = { Text("分类…") }, onClick = {
                                    showMenu = false
                                    viewModel.loadCategories()
                                    activeSheet = MangaSheet.Categories
                                })
                                DropdownMenuItem(text = { Text("迁移到其他图源…") }, onClick = {
                                    showMenu = false
                                    activeSheet = MangaSheet.Migrate
                                })
                                DropdownMenuItem(text = { Text("全部标为已读") }, onClick = {
                                    showMenu = false
                                    viewModel.markAllChapters(true)
                                })
                                DropdownMenuItem(text = { Text("全部标为未读") }, onClick = {
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
                    TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                }
            }
            else -> {
                val manga = state.manga!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    item {
                        MangaHeader(
                            state = state,
                            onResume = {
                                state.resumeChapter?.let { onOpenChapter(manga.id, it.id) }
                            },
                            onToggleLibrary = { viewModel.toggleLibrary() },
                        )
                    }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "章节（${state.chapters.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.toggleChapterOrder() }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    if (viewModel.chapterOrder == org.sumidoku.android.core.api.model.SortOrder.DESC) {
                                        Icons.Outlined.South
                                    } else {
                                        Icons.Outlined.North
                                    },
                                    contentDescription = if (viewModel.chapterOrder == org.sumidoku.android.core.api.model.SortOrder.DESC) {
                                        "当前：最新在前，点击切换"
                                    } else {
                                        "当前：最旧在前，点击切换"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(state.chapters, key = { it.id }) { chapter ->
                        ChapterRow(
							chapter = chapter,
							localStatus = state.localStatus[chapter.id],
							onClick = { onOpenChapter(manga.id, chapter.id) },
							onEnqueue = { viewModel.enqueueChapter(chapter.id) },
							onEnqueueLocal = { viewModel.enqueueLocal(chapter.id) },
						)
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
                onProvider = { viewModel.setMatchProvider(it) },
                onQuery = { viewModel.setMatchQuery(it) },
                onSearch = { viewModel.matchSearch() },
                onAdd = { provider, item -> viewModel.addLink(provider, item) },
                onRemove = { viewModel.removeLink(it) },
                onSync = { viewModel.syncLink(it) },
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

    // M6 sync-pull preview: show what would be applied, then confirm.
    meta.syncPreview?.let { (provider, result) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncPreview() },
            title = { Text("从追踪器同步进度") },
            text = {
                Text(
                    buildString {
                        result.progress?.let { append("远端读到第 $it 话\n") }
                        append("将标记 ${result.marked} 章为已读。\n确定应用吗？")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applySync(provider) }) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSyncPreview() }) { Text("取消") }
            },
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
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onSelectTarget: (Long) -> Unit,
    onSelectCandidate: (SearchManga?) -> Unit,
    onRun: (MigrateMode) -> Unit,
) {
    var mode by remember { mutableStateOf(MigrateMode.MIGRATE) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("迁移到其他图源", style = MaterialTheme.typography.titleMedium)
            if (sources == null) {
                LaunchedEffect(Unit) { onStart() }
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text("目标图源", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources.size) { i ->
                        val source = sources[i]
                        FilterChip(
                            selected = meta.migrationTargetId == source.id,
                            onClick = { onSelectTarget(source.id) },
                            label = { Text(source.name ?: "图源 ${source.id}", maxLines = 1) },
                        )
                    }
                }

                when {
                    meta.migrationBusy -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    meta.migrationCandidates != null -> {
                        HorizontalDivider()
                        Text("匹配结果", style = MaterialTheme.typography.titleSmall)
                        if (meta.migrationCandidates.isEmpty()) {
                            Text(
                                "没有匹配到同名漫画",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            meta.migrationCandidates.take(6).forEach { candidate ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectCandidate(
                                                if (meta.migrationSelected?.key == candidate.key) null else candidate,
                                            )
                                        },
                                ) {
                                    RadioButton(
                                        selected = meta.migrationSelected?.key == candidate.key,
                                        onClick = {
                                            onSelectCandidate(
                                                if (meta.migrationSelected?.key == candidate.key) null else candidate,
                                            )
                                        },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        candidate.authors?.takeIf { it.isNotEmpty() }?.let {
                                            Text(
                                                it.joinToString("、"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("迁移模式", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(
                                MigrateMode.COPY to "复制",
                                MigrateMode.MIGRATE to "迁移",
                                MigrateMode.MIGRATE_WITH_HISTORY to "带历史",
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
                        ) { Text("开始迁移") }
                    }
                    else -> Text(
                        "选择一个目标图源以查找匹配",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaHeader(
    state: MangaUiState,
    onResume: () -> Unit,
    onToggleLibrary: () -> Unit,
) {
    val manga = state.manga!!
    val profile = state.profile
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            CoverImage(manga.cover, profile, Modifier.size(108.dp, 144.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(manga.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                manga.authors?.firstOrNull()?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${manga.status.value} · ${manga.chapterCount} 话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (manga.incognito) {
                        Spacer(Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text("无痕中", style = MaterialTheme.typography.labelSmall)
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
                        if (ch.lastPageRead > 0 && !ch.isRead) "继续阅读 · 第 ${ch.lastPageRead + 1} 页" else "继续阅读"
                    } ?: "无章节",
                )
            }
            OutlinedButton(onClick = onToggleLibrary, modifier = Modifier.weight(1f)) {
                Text(if (manga.inLibrary) "移出书库" else "加入书库")
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
                    SuggestionChip(onClick = { /* tap-to-search: M6 对齐 webui */ }, label = { Text(tag) })
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
    onClick: () -> Unit,
    onEnqueue: () -> Unit,
    onEnqueueLocal: () -> Unit,
) {
    val dimmed = chapter.isRead
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    chapter.chapterNumber?.let { append("第 ${formatNumber(it)} 话") }
                    chapter.title?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (isEmpty()) append(chapter.key)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildList {
                chapter.dateUploaded?.let { add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))) }
                if (chapter.lastPageRead > 0 && !chapter.isRead) add("读到第 ${chapter.lastPageRead + 1} 页")
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
        if (chapter.hasFinishedDownload) {
            Icon(
                Icons.Outlined.CloudDownload,
                contentDescription = "已下载",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            IconButton(onClick = onEnqueue, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = "下载本章",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        when (localStatus) {
            "done" -> Icon(
                Icons.Outlined.SaveAlt,
                contentDescription = "已存到本机",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(start = 2.dp),
            )
            "queued", "downloading" -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp).padding(start = 2.dp),
                strokeWidth = 2.dp,
            )
            "error" -> Icon(
                Icons.Outlined.SaveAlt,
                contentDescription = "本机下载失败，点击重试",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(start = 2.dp)
                    .clickable(onClick = onEnqueueLocal),
            )
            else -> IconButton(onClick = onEnqueueLocal, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.SaveAlt,
                    contentDescription = "存到本机（离线阅读）",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (chapter.isRead) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "已读",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("分类", style = MaterialTheme.typography.titleMedium)
            when {
                categories == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                categories.isEmpty() -> Text(
                    "还没有分类（在设置里创建）",
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
            Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}

@Composable
internal fun CoverImage(cover: String?, profile: ServerProfile?, modifier: Modifier = Modifier) {    val context = LocalContext.current
    val model = cover?.let { c ->
        val url = if (c.startsWith("http")) c else profile?.baseUrl?.trimEnd('/') + c
        ImageRequest.Builder(context).data(url).apply {
            profile?.apiKey?.let { key ->
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
    )
}

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

package org.sumidoku.android.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import org.sumidoku.android.core.model.ServerProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manga detail: header, metadata, chapter list — preview 06-manga.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(
    onBack: () -> Unit,
    onOpenChapter: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: MangaViewModel = hiltViewModel(),
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
                        Text(
                            "章节（${state.chapters.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
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
        null -> Unit
    }
}

/** M6 sheet targets on the manga detail screen. */
private enum class MangaSheet { Edit, Cover, Match, Links }

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
                Text(
                    "${manga.status.value} · ${manga.chapterCount} 话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
internal fun CoverImage(cover: String?, profile: ServerProfile?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
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

package org.sumidoku.android.feature.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.RepoSource
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.model.ServerProfile

/** Browse tab: Aidoku-style grouped installed-source list
 *  (updates / pinned / installed). See preview 03-browse.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenSource: (Long) -> Unit = {},
    onOpenSourceSettings: (Long) -> Unit = {},
    onAddSource: () -> Unit = {},
    onMigrate: () -> Unit = {},
    onSearch: () -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPinOrder by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.updateAllResult) {
        state.updateAllResult?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("浏览") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "跨源搜索")
                    }
                    IconButton(onClick = onAddSource) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加图源")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("迁移…") },
                                onClick = {
                                    showMenu = false
                                    onMigrate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.updateAllRunning) "更新中…" else "全部更新") },
                                enabled = !state.updateAllRunning,
                                onClick = {
                                    showMenu = false
                                    viewModel.updateAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑置顶顺序") },
                                enabled = state.pinned.size > 1,
                                onClick = {
                                    showMenu = false
                                    showPinOrder = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("添加内部源 / 导入 .aix") },
                                onClick = {
                                    showMenu = false
                                    onAddSource()
                                },
                            )
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
            state.error != null && state.sources.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                    }
                }
            state.sources.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有安装图源", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddSource) { Text("去添加") }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.updatable.isNotEmpty()) {
                    item { SectionHeader("可更新（${state.updatable.size}）") }
                    items(state.updatable, key = { "u${it.id}" }) { source ->
                        SourceRow(source, state.profile, badge = "v${source.updateVersion}", onClick = { onOpenSource(source.id) }, onSettings = { onOpenSourceSettings(source.id) }, onTogglePin = { viewModel.togglePin(source) }, onToggleEnabled = { viewModel.toggleEnabled(source) })
                    }
                }
                if (state.pinned.isNotEmpty()) {
                    item { SectionHeader("置顶") }
                    items(state.pinned, key = { "p${it.id}" }) { source ->
                        SourceRow(source, state.profile, onClick = { onOpenSource(source.id) }, onSettings = { onOpenSourceSettings(source.id) }, onTogglePin = { viewModel.togglePin(source) }, onToggleEnabled = { viewModel.toggleEnabled(source) })
                    }
                }
                if (state.installed.isNotEmpty()) {
                    item { SectionHeader("已安装") }
                    items(state.installed, key = { "i${it.id}" }) { source ->
                        SourceRow(source, state.profile, onClick = { onOpenSource(source.id) }, onSettings = { onOpenSourceSettings(source.id) }, onTogglePin = { viewModel.togglePin(source) }, onToggleEnabled = { viewModel.toggleEnabled(source) })
                    }
                }
            }
        }
    }

    if (showPinOrder) {
        PinOrderDialog(
            pinned = state.pinned,
            onDismiss = { showPinOrder = false },
            onSave = { ids ->
                showPinOrder = false
                viewModel.savePinOrder(ids)
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    source: Source,
    profile: ServerProfile?,
    badge: String? = null,
    onClick: () -> Unit,
    onSettings: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = { SourceIcon(source, profile) },
            headlineContent = {
                Text(
                    buildString {
                        append(source.name)
                        append("  v${badge?.removePrefix("v") ?: source.version}")
                        if (source.contentRating >= 2) append("  18+")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (source.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = {
                Text(
                    buildString {
                        append(source.languages.joinToString(" · ").ifEmpty { "multi" })
                        if (!source.enabled) append(" · 已停用")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (source.missingFromRepos) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "已不在任何来源列表中",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    // 齿轮进图源设置（webui：OPDS 源无齿轮）
                    if (source.kind != Source.Kind.OPDS) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "图源设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
            ),
        )
        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (source.pinned) "取消置顶" else "置顶") },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text(if (source.enabled) "停用" else "启用") },
                onClick = {
                    showContextMenu = false
                    onToggleEnabled()
                },
            )
        }
    }
}

/** Pinned-order editor: up/down arrows, save sends the full ordered id
 *  list (server semantics: complete replacement). */
@Composable
private fun PinOrderDialog(
    pinned: List<Source>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    var order by remember { mutableStateOf(pinned.map { it.id }) }
    val byId = remember(pinned) { pinned.associateBy { it.id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑置顶顺序") },
        text = {
            LazyColumn {
                items(order.size) { i ->
                    val source = byId[order[i]] ?: return@items
                    ListItem(
                        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        if (i > 0) {
                                            order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                                        }
                                    },
                                    enabled = i > 0,
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移") }
                                IconButton(
                                    onClick = {
                                        if (i < order.size - 1) {
                                            order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                        }
                                    },
                                    enabled = i < order.size - 1,
                                ) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移") }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(order) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SourceIcon(source: Source, profile: ServerProfile?) {
    val context = LocalContext.current
    val model = profile?.let {
        ImageRequest.Builder(context)
            .data("${it.baseUrl.trimEnd('/')}/api/v1/sources/${source.id}/icon")
            .apply {
                it.apiKey?.let { key ->
                    httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
                }
            }
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
    )
}

/** Add-source screen: flat aggregated list of every repo's not-yet-
 *  installed sources with inline GET buttons (Aidoku AddSourceView).
 *  See preview 17-add-source.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.addState.collectAsState()
    val context = LocalContext.current
    var showInternalDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.loadRepoSources() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    // 导入 .aix：系统文件选择器拿到字节后直接上传 sources/import
    val aixLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            if (bytes != null) {
                val name = it.lastPathSegment?.substringAfterLast('/') ?: "source.aix"
                viewModel.importAix(name, bytes) {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("添加图源") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { aixLauncher.launch("*/*") }) { Text("导入 .aix") }
                    TextButton(onClick = { showInternalDialog = true }) { Text("内部源") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setAddQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索名称或语言…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.repoSources.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.loadRepoSources() }) { Text("重试") }
                        }
                    }
                state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.repoSources.isEmpty()) "没有可安装的图源（先在设置里添加仓库）" else "没有匹配的图源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(state.visible, key = { "${it.repoId}:${it.key}" }) { repoSource ->
                        RepoSourceRow(
                            repoSource = repoSource,
                            installing = state.installingKeys.contains(repoSource.key),
                            installed = state.installedKeys.contains(repoSource.key) || repoSource.installedId != null,
                            onInstall = { viewModel.install(repoSource) },
                        )
                    }
                }
            }
        }
    }

    if (showInternalDialog) {
        InternalSourceDialog(
            onDismiss = { showInternalDialog = false },
            onCreateLocal = { name, dirs, oneshots ->
                viewModel.createInternalLocal(name, dirs, oneshots) { showInternalDialog = false }
            },
            onCreateOpds = { name, url ->
                viewModel.createInternalOpds(name, url) { showInternalDialog = false }
            },
        )
    }
}

/** 添加内部源：本地文件夹或 OPDS 目录（服务器侧路径/URL）。
 *  对齐 webui InternalSourceModal 的核心字段。 */
@Composable
private fun InternalSourceDialog(
    onDismiss: () -> Unit,
    onCreateLocal: (name: String, dirs: List<String>, oneshotsDir: String?) -> Unit,
    onCreateOpds: (name: String, url: String) -> Unit,
) {
    var isLocal by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var dirsText by remember { mutableStateOf("") }
    var oneshotsDir by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加内部源") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { isLocal = true }) {
                        Text("本地文件夹", color = if (isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { isLocal = false }) {
                        Text("OPDS 目录", color = if (!isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (isLocal) {
                    OutlinedTextField(
                        value = dirsText,
                        onValueChange = { dirsText = it },
                        label = { Text("目录（每行一个，服务器路径）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = oneshotsDir,
                        onValueChange = { oneshotsDir = it },
                        label = { Text("单话目录（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("OPDS 目录 URL") },
                        placeholder = { Text("https://…/opds") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            val valid = name.isNotBlank() && if (isLocal) dirsText.isNotBlank() else url.isNotBlank()
            TextButton(
                onClick = {
                    if (isLocal) {
                        onCreateLocal(
                            name.trim(),
                            dirsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            oneshotsDir.trim().ifEmpty { null },
                        )
                    } else {
                        onCreateOpds(name.trim(), url.trim())
                    }
                },
                enabled = valid,
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RepoSourceRow(
    repoSource: RepoSource,
    installing: Boolean,
    installed: Boolean,
    onInstall: () -> Unit,
) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = repoSource.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            )
        },
        headlineContent = {
            Text(
                buildString {
                    append(repoSource.name)
                    append("  v${repoSource.version}")
                    if (repoSource.contentRating >= 2) append("  18+")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                buildString {
                    append(repoSource.languages.joinToString(" · ").ifEmpty { "multi" })
                    repoSource.repoName?.let { append("  ·  $it") }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        trailingContent = {
            when {
                installed -> Text("已安装", color = MaterialTheme.colorScheme.onSurfaceVariant)
                installing -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                else -> Button(onClick = onInstall) { Text("获取") }
            }
        },
    )
}

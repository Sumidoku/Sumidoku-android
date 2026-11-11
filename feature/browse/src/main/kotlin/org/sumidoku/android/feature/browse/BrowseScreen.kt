package org.sumidoku.android.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
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
    onAddSource: () -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { /* M2: 跨源搜索 */ }) {
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
                                text = { Text(if (state.updateAllRunning) "更新中…" else "全部更新") },
                                enabled = !state.updateAllRunning,
                                onClick = {
                                    showMenu = false
                                    viewModel.updateAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("添加内部源（M5）") },
                                enabled = false,
                                onClick = {},
                            )
                            DropdownMenuItem(
                                text = { Text("导入 .aix（M5）") },
                                enabled = false,
                                onClick = {},
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
                        SourceRow(source, state.profile, badge = "v${source.updateVersion}", onClick = { onOpenSource(source.id) })
                    }
                }
                if (state.pinned.isNotEmpty()) {
                    item { SectionHeader("置顶") }
                    items(state.pinned, key = { "p${it.id}" }) { source ->
                        SourceRow(source, state.profile, onClick = { onOpenSource(source.id) })
                    }
                }
                if (state.installed.isNotEmpty()) {
                    item { SectionHeader("已安装") }
                    items(state.installed, key = { "i${it.id}" }) { source ->
                        SourceRow(source, state.profile, onClick = { onOpenSource(source.id) })
                    }
                }
            }
        }
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

@Composable
private fun SourceRow(
    source: Source,
    profile: ServerProfile?,
    badge: String? = null,
    onClick: () -> Unit,
) {
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
            )
        },
        supportingContent = {
            Text(
                source.languages.joinToString(" · ").ifEmpty { "multi" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        trailingContent = {
            if (source.missingFromRepos) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = "已不在任何来源列表中",
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
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
    LaunchedEffect(Unit) { viewModel.loadRepoSources() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加图源") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
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

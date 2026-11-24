package org.sumidoku.android.feature.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
    // Design 03: the pinned group is reordered in place (⠿ handle + arrows)
    // instead of a modal dialog, so reordering never leaves the list.
    var editPinned by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val pinned = state.pinned
    var editOrder by remember(pinned) { mutableStateOf(pinned.map { it.id }) }
    val orderedPinned = remember(pinned, editOrder) {
        val byId = pinned.associateBy { it.id }
        editOrder.mapNotNull { byId[it] }
    }
    LaunchedEffect(state.pinned.size) {
        if (state.pinned.size < 2) editPinned = false
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.updateAllResult) {
        state.updateAllResult?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.browse_cross_search))
                    }
                    IconButton(onClick = onAddSource) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_source_title))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.browse_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_migrate)) },
                                onClick = {
                                    showMenu = false
                                    onMigrate()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.updateAllRunning) stringResource(R.string.browse_updating_all)
                                        else stringResource(R.string.browse_update_all_count, state.updatable.size),
                                    )
                                },
                                enabled = state.updatable.isNotEmpty() && !state.updateAllRunning,
                                onClick = {
                                    showMenu = false
                                    viewModel.updateAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (editPinned) stringResource(R.string.browse_edit_done) else stringResource(R.string.browse_edit_pin_order)) },
                                enabled = state.pinned.size > 1,
                                onClick = {
                                    showMenu = false
                                    editPinned = !editPinned
                                    if (!editPinned) viewModel.savePinOrder(editOrder)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_add_import_source)) },
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
                        TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.browse_retry)) }
                    }
                }
            state.sources.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.browse_no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddSource) { Text(stringResource(R.string.browse_go_add)) }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // Design 03: a tappable search field at the top of the list; the
                // real search lives in SearchScreen, so this only forwards the tap.
                item(key = "searchbar") {
                    BrowseSearchBar(onClick = onSearch)
                }
                if (state.updatable.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.browse_updatable_section, state.updatable.size)) }
                    items(state.updatable, key = { "u${it.id}" }) { source ->
                        SourceRow(source, state.profile, hasSettings = state.settingsAvailable[source.id] == true, updating = state.updatingIds.contains(source.id), onUpdate = { viewModel.updateSource(source) }, onClick = { onOpenSource(source.id) }, onSettings = { onOpenSourceSettings(source.id) }, onTogglePin = { viewModel.togglePin(source) }, onToggleEnabled = { viewModel.toggleEnabled(source) }, onUninstall = { viewModel.uninstall(source) })
                    }
                }
                if (orderedPinned.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.browse_pinned),
                            hint = if (editPinned) stringResource(R.string.browse_pin_hint_editing) else stringResource(R.string.browse_pin_hint_idle),
                        )
                    }
                    itemsIndexed(orderedPinned, key = { _, source -> "p${source.id}" }) { index, source ->
                        if (editPinned) {
                            PinnedEditRow(
                                source = source,
                                profile = state.profile,
                                canUp = index > 0,
                                canDown = index < orderedPinned.lastIndex,
                                onMove = { up ->
                                    val target = if (up) index - 1 else index + 1
                                    if (target in editOrder.indices) {
                                        editOrder = editOrder.toMutableList().apply {
                                            add(target, removeAt(index))
                                        }
                                    }
                                },
                            )
                        } else {
                            SourceRow(
                                source,
                                state.profile,
                                hasSettings = state.settingsAvailable[source.id] == true,
                                updating = state.updatingIds.contains(source.id),
                                onUpdate = { viewModel.updateSource(source) },
                                onClick = { onOpenSource(source.id) },
                                onSettings = { onOpenSourceSettings(source.id) },
                                onTogglePin = { viewModel.togglePin(source) },
                                onToggleEnabled = { viewModel.toggleEnabled(source) },
                                onUninstall = { viewModel.uninstall(source) },
                            )
                        }
                    }
                }
                if (state.installed.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.browse_installed)) }
                    items(state.installed, key = { "i${it.id}" }) { source ->
                        SourceRow(source, state.profile, hasSettings = state.settingsAvailable[source.id] == true, updating = state.updatingIds.contains(source.id), onUpdate = { viewModel.updateSource(source) }, onClick = { onOpenSource(source.id) }, onSettings = { onOpenSourceSettings(source.id) }, onTogglePin = { viewModel.togglePin(source) }, onToggleEnabled = { viewModel.toggleEnabled(source) }, onUninstall = { viewModel.uninstall(source) })
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
private fun BrowseSearchBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.browse_search_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        hint?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Design 03: a pinned row in edit mode — ⠿ handle plus arrow buttons, since
 *  we have no drag-reorder dependency. Tapping an arrow swaps the row. */
@Composable
private fun PinnedEditRow(
    source: Source,
    profile: ServerProfile?,
    canUp: Boolean,
    canDown: Boolean,
    onMove: (up: Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { SourceIcon(source, profile) },
        headlineContent = {
            Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text("${source.key} · v${source.version}") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⠿",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = { onMove(true) }, enabled = canUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                }
                IconButton(onClick = { onMove(false) }, enabled = canDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    source: Source,
    profile: ServerProfile?,
    hasSettings: Boolean,
    updating: Boolean = false,
    onUpdate: (() -> Unit)? = null,
    onClick: () -> Unit,
    onSettings: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = { SourceIcon(source, profile) },
            headlineContent = {
                // webui: name followed by a rating chip (only when rated)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (source.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    RatingChip(source.contentRating)
                }
            },
            supportingContent = {
                Column {
                    // webui meta line: key · version. A pending update shows both
                    // versions (design 03: "1.4.2 → 1.4.3").
                    val versionLine = buildString {
                        append(source.key)
                        append(" · v${source.version}")
                        if (source.updateVersion != null && source.updateVersion != source.version) {
                            append(" → v${source.updateVersion}")
                        }
                        if (!source.enabled) append(" · ").append(stringResource(R.string.browse_meta_disabled))
                    }
                    Text(
                        versionLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (source.updateVersion != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LanguageChips(source.languages)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (source.missingFromRepos) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = stringResource(R.string.browse_missing_from_repos),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    // Row pill for a pending update (design 03); the whole
                    // "update all" path stays in the overflow menu.
                    if (source.updateVersion != null && onUpdate != null) {
                        if (updating) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = onUpdate) { Text(stringResource(R.string.browse_update)) }
                        }
                    }
                    // Show the gear only when the source actually declares settings
                    // (OPDS sources have no settings schema).
                    if (hasSettings && source.kind != Source.Kind.OPDS) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.browse_source_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                // Disabled sources fade out as a whole (design 03), so the row
                // still occupies its slot but stops competing for attention.
                .alpha(if (source.enabled) 1f else 0.45f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true },
                ),
        )
        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (source.pinned) stringResource(R.string.browse_unpin) else stringResource(R.string.browse_pin)) },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text(if (source.enabled) stringResource(R.string.browse_disable) else stringResource(R.string.browse_enable)) },
                onClick = {
                    showContextMenu = false
                    onToggleEnabled()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browse_uninstall), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showContextMenu = false
                    confirmUninstall = true
                },
            )
        }
        if (confirmUninstall) {
            AlertDialog(
                onDismissRequest = { confirmUninstall = false },
                title = { Text(stringResource(R.string.browse_uninstall_confirm_title, source.name)) },
                text = { Text(stringResource(R.string.browse_uninstall_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmUninstall = false
                        onUninstall()
                    }) { Text(stringResource(R.string.browse_uninstall), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** webui languageLabel: multi/all maps to a "multi-language" label; the rest
 *  map to their localized display names. */
@Composable
private fun languageLabel(code: String): String = when (code.lowercase()) {
    "multi", "all" -> stringResource(R.string.lang_multi)
    "en" -> stringResource(R.string.lang_en)
    "ja" -> stringResource(R.string.lang_ja)
    "ko" -> stringResource(R.string.lang_ko)
    "zh", "zh-hans" -> stringResource(R.string.lang_zh_hans)
    "zh-hant" -> stringResource(R.string.lang_zh_hant)
    "fr" -> stringResource(R.string.lang_fr)
    "de" -> stringResource(R.string.lang_de)
    "es" -> stringResource(R.string.lang_es)
    "it" -> stringResource(R.string.lang_it)
    "pt", "pt-br" -> stringResource(R.string.lang_pt)
    "ru" -> stringResource(R.string.lang_ru)
    "vi" -> stringResource(R.string.lang_vi)
    "th" -> stringResource(R.string.lang_th)
    "id" -> stringResource(R.string.lang_id)
    else -> code.uppercase()
}

/** webui language chips: the first 3 display names plus a "+N" overflow chip. */
@Composable
private fun LanguageChips(languages: List<String>) {
    if (languages.isEmpty()) return
    Row(modifier = Modifier.padding(top = 2.dp)) {
        languages.take(3).forEach { lang ->
            MiniChip(languageLabel(lang))
        }
        if (languages.size > 3) {
            MiniChip("+${languages.size - 3}")
        }
    }
}

@Composable
private fun MiniChip(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** webui rating chip: 0 shows nothing, 1 is "contains NSFW" (warning colour),
 *  2 is NSFW (danger colour). */
@Composable
private fun RatingChip(rating: Long) {
    when {
        rating >= 2 -> MiniChip("NSFW", MaterialTheme.colorScheme.error)
        rating == 1L -> MiniChip(stringResource(R.string.rating_contains_nsfw), MaterialTheme.colorScheme.tertiary)
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
        title = { Text(stringResource(R.string.browse_edit_pin_order)) },
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
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up)) }
                                IconButton(
                                    onClick = {
                                        if (i < order.size - 1) {
                                            order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                        }
                                    },
                                    enabled = i < order.size - 1,
                                ) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down)) }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(order) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Source icon. Local-folder sources have no server icon (webui falls back to
 *  a hashed-colour initial), so draw the icon when there is one and a letter
 *  badge otherwise; a failed load falls back the same way. */
@Composable
private fun SourceIcon(source: Source, profile: ServerProfile?) {
    var failed by remember(source.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val model = profile
        ?.takeIf { !failed && source.kind != Source.Kind.LOCAL }
        ?.let { p ->
            ImageRequest.Builder(context)
                .data("${p.baseUrl.trimEnd('/')}/api/v1/sources/${source.id}/icon")
                .apply {
                    p.apiKey?.let { key ->
                        httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
                    }
                }
                .build()
        }
    if (model == null) {
        SourceIconFallback(source)
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            onError = { failed = true },
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
private fun SourceIconFallback(source: Source) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(sourceIconColor(source.key)),
    ) {
        Text(
            source.name.take(1),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** Mirrors webui browse/iconColor.ts so a source keeps the same badge tint. */
private val sourceIconColors = listOf(
    Color(0xFF7C6CF0),
    Color(0xFF4F8EF7),
    Color(0xFF3FB68B),
    Color(0xFFD9A03F),
    Color(0xFFE5534B),
    Color(0xFFC86DD7),
    Color(0xFF5FB8A6),
    Color(0xFF8B9CF9),
)

private fun sourceIconColor(key: String): Color {
    var hash = 0
    key.forEach { hash = hash * 31 + it.code }
    val index = ((hash % sourceIconColors.size) + sourceIconColors.size) % sourceIconColors.size
    return sourceIconColors[index]
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
    var internalStartLocal by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.loadRepoSources() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    // Import .aix: the system file picker hands us the bytes, which we upload
    // straight to sources/import.
    val aixImportedMessage = stringResource(R.string.add_source_imported_aix)
    val aixLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            if (bytes != null) {
                val name = it.lastPathSegment?.substringAfterLast('/') ?: "source.aix"
                viewModel.importAix(name, bytes) {
                    scope.launch { snackbar.showSnackbar(aixImportedMessage) }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_source_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.add_source_filter_lang))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setAddQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.add_source_search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            // webui/Aidoku-style language filter: multi-select chips aggregated
            // from every language present in the repo listing.
            if (showFilters && state.availableLangs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    state.availableLangs.forEach { lang ->
                        val selected = lang in state.selectedLangs
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleLang(lang) },
                            label = { Text(languageLabel(lang)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.repoSources.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.loadRepoSources() }) { Text(stringResource(R.string.browse_retry)) }
                        }
                    }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                    // Design 17: the import action is a full-width card, not a
                    // toolbar text button — it is the primary way to sideload.
                    item(key = "import") {
                        ImportAixCard(onClick = { aixLauncher.launch("*/*") })
                    }
                    item(key = "internal-header") {
                        SectionHeaderWithNote(stringResource(R.string.add_source_internal_header), stringResource(R.string.add_source_internal_note))
                    }
                    item(key = "internal-local") {
                        InternalSourceCard(
                            glyph = stringResource(R.string.add_source_local_glyph),
                            title = stringResource(R.string.add_source_local_title),
                            description = stringResource(R.string.add_source_local_desc),
                            onCreate = {
                                internalStartLocal = true
                                showInternalDialog = true
                            },
                        )
                    }
                    item(key = "internal-opds") {
                        InternalSourceCard(
                            glyph = "O",
                            title = stringResource(R.string.add_source_opds_title),
                            description = stringResource(R.string.add_source_opds_desc),
                            onCreate = {
                                internalStartLocal = false
                                showInternalDialog = true
                            },
                        )
                    }
                    item(key = "installable-header") {
                        SectionHeaderWithNote(
                            stringResource(R.string.add_source_installable_header),
                            if (state.repoCount > 0) stringResource(R.string.add_source_from_repos, state.repoCount) else stringResource(R.string.add_source_add_repo_first),
                        )
                    }
                    if (state.visible.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.repoSources.isEmpty()) stringResource(R.string.add_source_empty_no_repo) else stringResource(R.string.add_source_empty_no_match),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(state.visible, key = { "${it.repoId}:${it.key}" }) { repoSource ->
                            RepoSourceRow(
                                repoSource = repoSource,
                                installing = state.installingKeys.contains(repoSource.key),
                                installed = state.installedKeys.contains(repoSource.key) || repoSource.installedId != null,
                                onInstall = { viewModel.install(repoSource) },
                            )
                        }
                    }
                    item(key = "note") {
                        Text(
                            stringResource(R.string.add_source_footnote),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showInternalDialog) {
        InternalSourceDialog(
            initialLocal = internalStartLocal,
            onDismiss = { showInternalDialog = false },
            onCreateLocal = { draft ->
                viewModel.createInternalLocal(draft) { showInternalDialog = false }
            },
            onCreateOpds = { name, url, username, password ->
                viewModel.createInternalOpds(name, url, username, password) { showInternalDialog = false }
            },
        )
    }
}

/** Design 17: dashed-free full-width card for the .aix sideload path. */
@Composable
private fun ImportAixCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.FileUpload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.add_source_import_aix), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One of the two internal-source entry cards (design 17). */
@Composable
private fun InternalSourceCard(
    glyph: String,
    title: String,
    description: String,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onCreate) { Text(stringResource(R.string.add_source_create)) }
    }
}

/** Section title with a right-aligned note (design 17 headers). */
@Composable
private fun SectionHeaderWithNote(title: String, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Add an internal source: a local folder or an OPDS catalog (server-side
 *  path/URL). Fields mirror the webui InternalSourceModal: multiple
 *  directories, a oneshots directory, exclusion substrings, and the three
 *  scan switches (defaults match the server config layer and can still be
 *  changed in the source settings after creation). */
@Composable
private fun InternalSourceDialog(
    initialLocal: Boolean = true,
    onDismiss: () -> Unit,
    onCreateLocal: (LocalSourceDraft) -> Unit,
    onCreateOpds: (name: String, url: String, username: String?, password: String?) -> Unit,
) {
    var isLocal by remember { mutableStateOf(initialLocal) }
    var name by remember { mutableStateOf("") }
    var dirsText by remember { mutableStateOf("") }
    var oneshotsDir by remember { mutableStateOf("") }
    var exclusionsText by remember { mutableStateOf("") }
    var forceModifiedTime by remember { mutableStateOf(false) }
    var analyzePages by remember { mutableStateOf(true) }
    var analyzeMetadata by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    var opdsUsername by remember { mutableStateOf("") }
    var opdsPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_source_internal_dialog_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row {
                    TextButton(onClick = { isLocal = true }) {
                        Text(stringResource(R.string.add_source_tab_local), color = if (isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { isLocal = false }) {
                        Text(stringResource(R.string.add_source_opds_title), color = if (!isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_source_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (isLocal) {
                    Text(
                        stringResource(R.string.add_source_local_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dirsText,
                        onValueChange = { dirsText = it },
                        label = { Text(stringResource(R.string.add_source_dirs_label)) },
                        placeholder = { Text("/mnt/manga") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = oneshotsDir,
                        onValueChange = { oneshotsDir = it },
                        label = { Text(stringResource(R.string.add_source_oneshots_label)) },
                        placeholder = { Text("oneshots") },
                        supportingText = { Text(stringResource(R.string.add_source_oneshots_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exclusionsText,
                        onValueChange = { exclusionsText = it },
                        label = { Text(stringResource(R.string.add_source_exclusions_label)) },
                        placeholder = { Text(stringResource(R.string.add_source_exclusions_placeholder)) },
                        supportingText = { Text(stringResource(R.string.add_source_exclusions_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DialogSwitchRow(stringResource(R.string.add_source_switch_force_mtime), forceModifiedTime) { forceModifiedTime = it }
                    DialogSwitchRow(stringResource(R.string.add_source_switch_analyze_pages), analyzePages) { analyzePages = it }
                    DialogSwitchRow(stringResource(R.string.add_source_switch_analyze_metadata), analyzeMetadata) { analyzeMetadata = it }
                    Text(
                        stringResource(R.string.add_source_switches_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.add_source_opds_url_label)) },
                        placeholder = { Text("https://…/opds") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = opdsUsername,
                        onValueChange = { opdsUsername = it },
                        label = { Text(stringResource(R.string.add_source_username_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = opdsPassword,
                        onValueChange = { opdsPassword = it },
                        label = { Text(stringResource(R.string.add_source_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
                            LocalSourceDraft(
                                name = name.trim(),
                                paths = linesOf(dirsText),
                                oneshotsDir = oneshotsDir.trim().ifEmpty { null },
                                exclusions = linesOf(exclusionsText),
                                forceModifiedTime = forceModifiedTime,
                                analyzePages = analyzePages,
                                analyzeMetadata = analyzeMetadata,
                            ),
                        )
                    } else {
                        onCreateOpds(
                            name.trim(),
                            url.trim(),
                            opdsUsername.trim().ifEmpty { null },
                            opdsPassword.ifEmpty { null },
                        )
                    }
                },
                enabled = valid,
            ) { Text(stringResource(R.string.add_source_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun linesOf(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun DialogSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repoSource.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RatingChip(repoSource.contentRating)
            }
        },
        supportingContent = {
            Column {
                Text(
                    buildString {
                        append(repoSource.key)
                        append(" · v${repoSource.version}")
                        repoSource.repoName?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LanguageChips(repoSource.languages)
            }
        },
        trailingContent = {
            when {
                installed -> Text(stringResource(R.string.browse_installed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                installing -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                else -> Button(onClick = onInstall) { Text(stringResource(R.string.add_source_get)) }
            }
        },
    )
}

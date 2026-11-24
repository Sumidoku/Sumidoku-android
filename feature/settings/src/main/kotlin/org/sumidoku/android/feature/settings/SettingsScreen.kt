package org.sumidoku.android.feature.settings

import android.os.Build

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Settings pages a hub row can open. On a phone a row pushes the page as
 *  a full-screen route; on a wide tablet the row selects the page in the
 *  detail pane beside the hub (KMReader settings split parity). */
enum class SettingsPage {
    ReaderDefaults,
    Repos,
    Servers,
    Trackers,
    Backups,
    ApiKeys,
    Cache,
    Tasks,
    Categories,
    FilterGroups,
    ServerSettings,
    Trash,
}

/** Smallest screen width that marks a device as a tablet (sw600dp split point). */
private const val TabletMinWidthDp = 600

/** Narrowest window that fits the hub beside a readable detail pane. */
private const val SplitMinWidthDp = 840

/** Width of the hub column in the split layout. */
private val SidebarWidth = 360.dp

/** Settings hub — static entry rows. Server-side setting groups
 *  (updates/library/download/…) land with M8; this screen only hosts
 *  entries that already have screens. Preview 12-settings.
 *
 *  @param splitDetail detail pane of the tablet split layout; receives the
 *  page, the callback that clears the selection, and the callback that
 *  switches to another page without going back through the hub. When null
 *  (or the window is too small) rows navigate full-screen instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRepos: () -> Unit,
    onManageServers: () -> Unit,
    onOpenTrackers: () -> Unit = {},
    onOpenBackups: () -> Unit = {},
    onOpenApiKeys: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenFilterGroups: () -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
    onOpenReaderDefaults: () -> Unit = {},
    splitDetail: (@Composable (page: SettingsPage, onDismiss: () -> Unit, onNavigate: (SettingsPage) -> Unit) -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val detail = splitDetail
    val split = detail != null &&
        configuration.smallestScreenWidthDp >= TabletMinWidthDp &&
        configuration.screenWidthDp >= SplitMinWidthDp
    // The chosen pane survives a configuration change; enums are saveable.
    var selectedPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // In the split layout a row selects the pane beside the hub; otherwise the
    // host navigates to the page.
    val open: (SettingsPage, () -> Unit) -> Unit = { page, fallback ->
        if (split) selectedPage = page else fallback()
    }

    val hub: @Composable (Modifier) -> Unit = { modifier ->
        SettingsHub(
            open = open,
            selectedPage = selectedPage.takeIf { split },
            onManageServers = onManageServers,
            onOpenRepos = onOpenRepos,
            onOpenTrackers = onOpenTrackers,
            onOpenBackups = onOpenBackups,
            onOpenApiKeys = onOpenApiKeys,
            onOpenCache = onOpenCache,
            onOpenTasks = onOpenTasks,
            onOpenTrash = onOpenTrash,
            onOpenCategories = onOpenCategories,
            onOpenFilterGroups = onOpenFilterGroups,
            onOpenServerSettings = onOpenServerSettings,
            onOpenReaderDefaults = onOpenReaderDefaults,
            modifier = modifier,
        )
    }

    if (detail != null && split) {
        // System back leaves the open page before it leaves the settings hub.
        BackHandler(enabled = selectedPage != null) { selectedPage = null }
        // Paint the whole split on the page surface so the empty pane cannot
        // show (or leak taps to) whatever sits behind this route.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(Modifier.fillMaxSize()) {
                hub(Modifier.width(SidebarWidth))
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val page = selectedPage
                    if (page == null) {
                        SettingsDetailPlaceholder()
                    } else {
                        detail(page, { selectedPage = null }, { selectedPage = it })
                    }
                }
            }
        }
    } else {
        hub(Modifier.fillMaxSize())
    }
}

/** The hub itself: a titled, scrollable list of section rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHub(
    open: (SettingsPage, () -> Unit) -> Unit,
    selectedPage: SettingsPage?,
    onManageServers: () -> Unit,
    onOpenRepos: () -> Unit,
    onOpenTrackers: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFilterGroups: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenReaderDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        // komikku settings hub parity: the whole list lives inside one
        // rounded card, rows carry no dividers, and the open pane's row is a
        // rounded filled highlight inset from the card edge.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SectionHeader(stringResource(R.string.settings_section_appearance))
                    AppearanceRow()
                    IncognitoRow()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = stringResource(R.string.settings_reader_defaults),
                        subtitle = stringResource(R.string.settings_reader_defaults_subtitle),
                        selected = selectedPage == SettingsPage.ReaderDefaults,
                        onClick = { open(SettingsPage.ReaderDefaults, onOpenReaderDefaults) },
                    )
                    SectionHeader(stringResource(R.string.settings_section_sources))
                    SettingsRow(
                        icon = Icons.Outlined.Extension,
                        title = stringResource(R.string.settings_source_repos),
                        subtitle = stringResource(R.string.settings_source_repos_subtitle),
                        selected = selectedPage == SettingsPage.Repos,
                        onClick = { open(SettingsPage.Repos, onOpenRepos) },
                    )
                    SectionHeader(stringResource(R.string.settings_section_server))
                    SettingsRow(
                        icon = Icons.Outlined.Dns,
                        title = stringResource(R.string.settings_manage_servers),
                        subtitle = stringResource(R.string.settings_manage_servers_subtitle),
                        selected = selectedPage == SettingsPage.Servers,
                        onClick = { open(SettingsPage.Servers, onManageServers) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Sync,
                        title = stringResource(R.string.settings_instance_sync),
                        subtitle = stringResource(R.string.settings_instance_sync_subtitle),
                        enabled = false,
                        onClick = {},
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Public,
                        title = stringResource(R.string.settings_trackers),
                        subtitle = stringResource(R.string.settings_trackers_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Trackers,
                        onClick = { open(SettingsPage.Trackers, onOpenTrackers) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.CloudQueue,
                        title = stringResource(R.string.settings_backup_restore),
                        subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Backups,
                        onClick = { open(SettingsPage.Backups, onOpenBackups) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Key,
                        title = stringResource(R.string.settings_api_keys),
                        subtitle = stringResource(R.string.settings_api_keys_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.ApiKeys,
                        onClick = { open(SettingsPage.ApiKeys, onOpenApiKeys) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.settings_cache),
                        subtitle = stringResource(R.string.settings_cache_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Cache,
                        onClick = { open(SettingsPage.Cache, onOpenCache) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.PlaylistPlay,
                        title = stringResource(R.string.settings_tasks),
                        subtitle = stringResource(R.string.settings_tasks_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Tasks,
                        onClick = { open(SettingsPage.Tasks, onOpenTasks) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Label,
                        title = stringResource(R.string.settings_categories),
                        subtitle = stringResource(R.string.settings_categories_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Categories,
                        onClick = { open(SettingsPage.Categories, onOpenCategories) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.FilterList,
                        title = stringResource(R.string.settings_filter_groups),
                        subtitle = stringResource(R.string.settings_filter_groups_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.FilterGroups,
                        onClick = { open(SettingsPage.FilterGroups, onOpenFilterGroups) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Settings,
                        title = stringResource(R.string.settings_server_settings),
                        subtitle = stringResource(R.string.settings_server_settings_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.ServerSettings,
                        onClick = { open(SettingsPage.ServerSettings, onOpenServerSettings) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = stringResource(R.string.settings_trash),
                        subtitle = stringResource(R.string.settings_trash_subtitle),
                        enabled = true,
                        selected = selectedPage == SettingsPage.Trash,
                        onClick = { open(SettingsPage.Trash, onOpenTrash) },
                    )
                    SectionHeader(stringResource(R.string.settings_section_about))
                    VersionRow()
                }
            }
        }
    }
}

/** Right pane of the split layout before a row is chosen. */
@Composable
private fun SettingsDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_split_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Version — preview 12-settings.html bottom group: `app 0.1.0 · server …`. */
@Composable
private fun VersionRow() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "—" }
    }
    ListItem(
        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.settings_version_title)) },
        supportingContent = {
            Text(
                stringResource(R.string.settings_version_detail, version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AppearanceRow(viewModel: AppearanceViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    // Design 37: the three chips pick the palette family, the switch lifts the
    // dark palette's background to true black. Turning the switch off always
    // lands on Dark so the user never sits on an empty state.
    val amoled = theme == "amoled"
    ListItem(
        leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // komikku parity: a full-width segmented control instead of
                // loose filter chips (follow-system / light / dark). Android
                // 12+ also offers Monet (wallpaper-derived colours).
                val options = buildList {
                    add("system" to stringResource(R.string.settings_theme_system))
                    add("light" to stringResource(R.string.settings_theme_light))
                    add("dark" to stringResource(R.string.settings_theme_dark))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add("dynamic" to stringResource(R.string.settings_theme_dynamic))
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        val active = theme == value || (value == "dark" && amoled)
                        SegmentedButton(
                            selected = active,
                            onClick = { viewModel.setTheme(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            icon = { SegmentedButtonDefaults.Icon(active = active) },
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
                // AMOLED true black only applies to the static dark palette;
                // Monet derives its own surfaces from the wallpaper.
                if (theme != "dynamic") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_theme_amoled), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_theme_amoled_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = amoled,
                        onCheckedChange = { on -> viewModel.setTheme(if (on) "amoled" else "dark") },
                    )
                }
                }
            }
        },
    )
}

/** Incognito reading switch: belongs to the same General group as Appearance
 *  in the webui (SettingsPage.tsx:2532-2537), so it lives under the Appearance
 *  section here rather than in server settings; toggling immediately PUTs the
 *  server setting (detail pages can still override per manga). */
@Composable
private fun IncognitoRow(viewModel: IncognitoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    ListItem(
        leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.settings_incognito)) },
        supportingContent = {
            Text(
                state.error ?: stringResource(R.string.settings_incognito_subtitle),
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Switch(
                    checked = state.enabled,
                    enabled = !state.busy,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    /** Highlights the row as the open pane of the split layout. */
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    // komikku hub parity: no dividers; the selected row is a rounded filled
    // highlight inset from the card edge, everything else is transparent.
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        ),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
    )
}


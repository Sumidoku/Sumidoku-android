package org.sumidoku.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Settings hub — static entry rows. Server-side setting groups
 *  (updates/library/download/…) land with M8; this screen only hosts
 *  entries that already have screens. Preview 12-settings. */
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
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item { AppearanceRow() }
            item { IncognitoRow() }
            item {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = stringResource(R.string.settings_reader_defaults),
                    subtitle = stringResource(R.string.settings_reader_defaults_subtitle),
                    onClick = onOpenReaderDefaults,
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_sources)) }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.settings_source_repos),
                    subtitle = stringResource(R.string.settings_source_repos_subtitle),
                    onClick = onOpenRepos,
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_server)) }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.settings_manage_servers),
                    subtitle = stringResource(R.string.settings_manage_servers_subtitle),
                    onClick = onManageServers,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.settings_instance_sync),
                    subtitle = stringResource(R.string.settings_instance_sync_subtitle),
                    enabled = false,
                    onClick = {},
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Public,
                    title = stringResource(R.string.settings_trackers),
                    subtitle = stringResource(R.string.settings_trackers_subtitle),
                    enabled = true,
                    onClick = onOpenTrackers,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.CloudQueue,
                    title = stringResource(R.string.settings_backup_restore),
                    subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                    enabled = true,
                    onClick = onOpenBackups,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.settings_api_keys),
                    subtitle = stringResource(R.string.settings_api_keys_subtitle),
                    enabled = true,
                    onClick = onOpenApiKeys,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Storage,
                    title = stringResource(R.string.settings_cache),
                    subtitle = stringResource(R.string.settings_cache_subtitle),
                    enabled = true,
                    onClick = onOpenCache,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = stringResource(R.string.settings_tasks),
                    subtitle = stringResource(R.string.settings_tasks_subtitle),
                    enabled = true,
                    onClick = onOpenTasks,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Label,
                    title = stringResource(R.string.settings_categories),
                    subtitle = stringResource(R.string.settings_categories_subtitle),
                    enabled = true,
                    onClick = onOpenCategories,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.FilterList,
                    title = stringResource(R.string.settings_filter_groups),
                    subtitle = stringResource(R.string.settings_filter_groups_subtitle),
                    enabled = true,
                    onClick = onOpenFilterGroups,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.settings_server_settings),
                    subtitle = stringResource(R.string.settings_server_settings_subtitle),
                    enabled = true,
                    onClick = onOpenServerSettings,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.settings_trash),
                    subtitle = stringResource(R.string.settings_trash_subtitle),
                    enabled = true,
                    onClick = onOpenTrash,
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item { VersionRow() }
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
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "system" to stringResource(R.string.settings_theme_system),
                        "dark" to stringResource(R.string.settings_theme_dark),
                        "light" to stringResource(R.string.settings_theme_light),
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = theme == value || (value == "dark" && amoled),
                            onClick = { viewModel.setTheme(value) },
                            label = { Text(label) },
                        )
                    }
                }
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
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}


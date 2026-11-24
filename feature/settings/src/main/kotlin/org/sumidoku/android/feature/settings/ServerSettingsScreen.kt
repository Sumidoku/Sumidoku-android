package org.sumidoku.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.ChapterCacheSettings
import org.sumidoku.android.core.api.model.DownloadSettings
import org.sumidoku.android.core.common.formatEpochMs
import org.sumidoku.android.core.common.relativeEpochMs
import org.sumidoku.android.core.ui.SettingsGroupCard

/** Structured server-settings editor (design §14 M7): sections map
 *  1:1 onto GET /api/v1/settings; Save PUTs the complete structured
 *  object. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    onOpenBackups: () -> Unit = {},
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.server_settings_saved)

    LaunchedEffect(state.saved, state.error) {
        when {
            state.saved -> {
                snackbar.showSnackbar(savedMessage)
                viewModel.consumeTransient()
            }
            state.error != null -> {
                snackbar.showSnackbar(state.error!!)
                viewModel.consumeTransient()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state.settings
        val categories = state.categories
        when {
            state.loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            s == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(state.error ?: stringResource(R.string.server_settings_load_failed)) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { SectionHeader(stringResource(R.string.server_settings_section_library)) }
                item {
                    SettingsGroupCard {
                    SettingChipsRow(
                        label = stringResource(R.string.server_settings_default_category),
                        hint = stringResource(R.string.server_settings_default_category_hint),
                        options = buildList<Pair<String, String>> {
                            add("all" to stringResource(R.string.server_settings_category_all))
                            add("uncategorized" to stringResource(R.string.server_settings_category_uncategorized))
                            categories.forEach { add(it.id.toString() to it.name) }
                        },
                        selected = s.library.defaultCategory,
                        onSelect = { v ->
                            viewModel.update { it.copy(library = it.library.copy(defaultCategory = v)) }
                        },
                    )
                    ToggleRow(stringResource(R.string.server_settings_show_uncategorized), stringResource(R.string.server_settings_show_uncategorized_hint), s.library.showUncategorized) { v ->
                        viewModel.update { it.copy(library = it.library.copy(showUncategorized = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_refresh_metadata), stringResource(R.string.server_settings_refresh_metadata_hint), s.library.refreshMetadata) { v ->
                        viewModel.update { it.copy(library = it.library.copy(refreshMetadata = v)) }
                    }
                    UpdateIntervalRow(
                        valueHours = s.library.updateIntervalHours,
                        presets = libraryUpdateIntervalPresets(),
                        onSelect = { hours ->
                            viewModel.update { it.copy(library = it.library.copy(updateIntervalHours = hours)) }
                        },
                    )
                    Text(
                        stringResource(R.string.server_settings_skip_titles),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
                    )
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            "hasUnread" to stringResource(R.string.server_settings_skip_has_unread),
                            "completed" to stringResource(R.string.server_settings_skip_completed),
                            "notStarted" to stringResource(R.string.server_settings_skip_not_started),
                        ).forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = key in s.library.skipTitles,
                                    onCheckedChange = { checked ->
                                        viewModel.update {
                                            val next = if (checked) it.library.skipTitles + key else it.library.skipTitles - key
                                            it.copy(library = it.library.copy(skipTitles = next))
                                        }
                                    },
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ExcludedCategoriesRow(
                        title = stringResource(R.string.server_settings_excluded_update_title),
                        subtitle = stringResource(R.string.server_settings_excluded_update_subtitle),
                        categories = categories,
                        excluded = s.library.excludedUpdateCategories,
                        onChange = { next ->
                            viewModel.update { it.copy(library = it.library.copy(excludedUpdateCategories = next)) }
                        },
                    )
                    }
                }

                item { SectionHeader(stringResource(R.string.server_settings_section_download)) }
                item {
                    SettingsGroupCard {
                    ToggleRow(stringResource(R.string.server_settings_auto_download_new), stringResource(R.string.server_settings_auto_download_new_hint), s.download.autoDownloadNewChapters) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoDownloadNewChapters = v)) }
                    }
                    NumberRow(stringResource(R.string.server_settings_auto_download_limit), s.download.autoDownloadLimit.toString()) { v ->
                        v.toLongOrNull()?.let { limit ->
                            viewModel.update { it.copy(download = it.download.copy(autoDownloadLimit = limit)) }
                        }
                    }
                    NumberRow(stringResource(R.string.server_settings_download_ahead_count), s.download.downloadAheadCount.toString()) { v ->
                        v.toLongOrNull()?.let { count ->
                            viewModel.update { it.copy(download = it.download.copy(downloadAheadCount = count)) }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.server_settings_download_format), style = MaterialTheme.typography.bodyMedium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            DownloadSettings.DownloadFormat.entries.forEachIndexed { index, format ->
                                SegmentedButton(
                                    selected = s.download.downloadFormat == format,
                                    onClick = {
                                        viewModel.update { it.copy(download = it.download.copy(downloadFormat = format)) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DownloadSettings.DownloadFormat.entries.size),
                                ) { Text(format.value) }
                            }
                        }
                    }
                    NumberRow(stringResource(R.string.server_settings_download_concurrency), s.download.downloadConcurrency.toString()) { v ->
                        v.toLongOrNull()?.let { c ->
                            viewModel.update { it.copy(download = it.download.copy(downloadConcurrency = c)) }
                        }
                    }
                    ToggleRow(stringResource(R.string.server_settings_auto_skip_unread), stringResource(R.string.server_settings_auto_skip_unread_hint), s.download.autoSkipMangaWithUnread) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoSkipMangaWithUnread = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_auto_ignore_reuploads), stringResource(R.string.server_settings_auto_ignore_reuploads_hint), s.download.autoIgnoreReuploads) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoIgnoreReuploads = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_delete_after_manual_read), stringResource(R.string.server_settings_delete_after_manual_read_hint), s.download.deleteAfterManualRead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(deleteAfterManualRead = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_delete_after_read), stringResource(R.string.server_settings_delete_after_read_hint), s.download.deleteAfterRead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(deleteAfterRead = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_allow_delete_bookmarked), stringResource(R.string.server_settings_allow_delete_bookmarked_hint), s.download.allowDeleteBookmarked) { v ->
                        viewModel.update { it.copy(download = it.download.copy(allowDeleteBookmarked = v)) }
                    }
                    ToggleRow(stringResource(R.string.server_settings_download_ahead), stringResource(R.string.server_settings_download_ahead_hint), s.download.downloadAhead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(downloadAhead = v)) }
                    }
                    ExcludedCategoriesRow(
                        title = stringResource(R.string.server_settings_excluded_download_title),
                        subtitle = stringResource(R.string.server_settings_excluded_download_subtitle),
                        categories = categories,
                        excluded = s.library.excludedDownloadCategories,
                        onChange = { next ->
                            viewModel.update { it.copy(library = it.library.copy(excludedDownloadCategories = next)) }
                        },
                    )
                    TextRow(
                        label = stringResource(R.string.server_settings_move_path_label),
                        value = s.download.movePath ?: "",
                        placeholder = "/mnt/library/{author}/{manga}/{filename}",
                        supporting = stringResource(R.string.server_settings_move_path_supporting),
                        onValueChange = { v ->
                            viewModel.update { it.copy(download = it.download.copy(movePath = v.ifBlank { null })) }
                        },
                    )
                    }
                }

                item { SectionHeader(stringResource(R.string.server_settings_section_backup)) }
                item {
                    SettingsGroupCard {
                    ToggleRow(stringResource(R.string.server_settings_backup_enabled), stringResource(R.string.server_settings_backup_enabled_hint), s.backup.enabled) { v ->
                        viewModel.update { it.copy(backup = it.backup.copy(enabled = v)) }
                    }
                    NumberRow(stringResource(R.string.server_settings_backup_interval), s.backup.intervalHours.toString()) { v ->
                        v.toLongOrNull()?.let { h ->
                            viewModel.update { it.copy(backup = it.backup.copy(intervalHours = h)) }
                        }
                    }
                    NumberRow(stringResource(R.string.server_settings_backup_retention), s.backup.retentionCount.toString()) { v ->
                        v.toLongOrNull()?.let { r ->
                            viewModel.update { it.copy(backup = it.backup.copy(retentionCount = r)) }
                        }
                    }
                    TextRow(
                        label = stringResource(R.string.server_settings_backup_dir),
                        value = s.backup.dir ?: "",
                        placeholder = stringResource(R.string.server_settings_backup_dir_placeholder),
                        supporting = stringResource(R.string.server_settings_backup_dir_supporting),
                        onValueChange = { v ->
                            viewModel.update { it.copy(backup = it.backup.copy(dir = v.ifBlank { null })) }
                        },
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.server_settings_backup_last), style = MaterialTheme.typography.bodyLarge)
                        // lastRun is maintained by the server (0/empty means it has never run)
                        Text(
                            s.backup.lastRun?.takeIf { it > 0L }
                                ?.let { stringResource(R.string.server_settings_backup_last_run, relativeEpochMs(it), formatEpochMs(it)) }
                                ?: stringResource(R.string.server_settings_backup_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.server_settings_view_backups), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.server_settings_view_backups_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onOpenBackups) { Text(stringResource(R.string.server_settings_open)) }
                    }
                    }
                }

                item { SectionHeader(stringResource(R.string.server_settings_section_cache)) }
                item {
                    SettingsGroupCard {
                    NumberRow(stringResource(R.string.server_settings_chapter_cache_max), ((s.cache?.chapterMaxBytes ?: 0L) / 1048576L).toString()) { v ->
                        v.toLongOrNull()?.let { mb ->
                            viewModel.update {
                                it.copy(cache = (it.cache ?: ChapterCacheSettings(chapterMaxBytes = 0L)).copy(chapterMaxBytes = mb * 1048576L))
                            }
                        }
                    }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text(stringResource(if (state.busy) R.string.server_settings_saving else R.string.server_settings_save)) }
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
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberRow(label: String, value: String, onValid: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() }) onValid(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Single-choice chips: used for category-like values (default category) and
 *  preset intervals alike. A current value outside the options gets its own
 *  extra chip so a custom value stored on the server is not silently lost. */
@Composable
private fun SettingChipsRow(
    label: String,
    hint: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val all = if (options.any { it.first == selected }) options else options + (selected to selected)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            all.forEach { (value, name) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(name) },
                )
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Preset update intervals, matching webui LIBRARY_UPDATE_INTERVALS
 *  (SettingsPage.tsx:1086-1092). */
@Composable
private fun libraryUpdateIntervalPresets(): List<Pair<String, String>> = listOf(
    "0" to stringResource(R.string.server_settings_interval_never),
    "12" to stringResource(R.string.server_settings_interval_12h),
    "24" to stringResource(R.string.server_settings_interval_24h),
    "48" to stringResource(R.string.server_settings_interval_2d),
    "168" to stringResource(R.string.server_settings_interval_weekly),
)

/** Update interval: preset steps plus a "Custom" dialog (any number of hours);
 *  both write back to library.updateIntervalHours (the extra Select option of
 *  webui LibraryUpdateSection, SettingsPage.tsx:1259-1278). A non-preset value
 *  stored on the server gets its own selected chip so it is not silently
 *  dropped. */
@Composable
private fun UpdateIntervalRow(
    valueHours: Long,
    presets: List<Pair<String, String>>,
    onSelect: (Long) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val current = valueHours.toString()
    val isPreset = presets.any { it.first == current }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.server_settings_update_interval), style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { (value, name) ->
                FilterChip(
                    selected = current == value,
                    onClick = { value.toLongOrNull()?.let(onSelect) },
                    label = { Text(name) },
                )
            }
            FilterChip(
                selected = !isPreset,
                onClick = {
                    draft = if (isPreset) "" else valueHours.toString()
                    showDialog = true
                },
                label = {
                    Text(
                        if (isPreset) {
                            stringResource(R.string.server_settings_interval_custom)
                        } else {
                            stringResource(R.string.server_settings_interval_custom_value, valueHours.toString())
                        },
                    )
                },
            )
        }
        Text(
            stringResource(R.string.server_settings_update_interval_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showDialog) {
        val parsed = draft.toLongOrNull()
        val valid = parsed != null && parsed > 0
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.server_settings_custom_interval_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { v -> if (v.all { it.isDigit() }) draft = v },
                    label = { Text(stringResource(R.string.server_settings_hours)) },
                    placeholder = { Text(stringResource(R.string.server_settings_hours_placeholder)) },
                    supportingText = { Text(stringResource(R.string.server_settings_hours_supporting)) },
                    isError = draft.isNotEmpty() && !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let(onSelect)
                        showDialog = false
                    },
                    enabled = valid,
                ) { Text(stringResource(R.string.server_settings_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.server_settings_cancel)) } },
        )
    }
}

/** Category exclusion rule: one-line summary plus a multi-select dialog
 *  (webui ExcludedCategoriesSetting). */
@Composable
private fun ExcludedCategoriesRow(
    title: String,
    subtitle: String,
    categories: List<Category>,
    excluded: List<Long>,
    onChange: (List<Long>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (excluded.isEmpty()) subtitle else stringResource(R.string.server_settings_excluded_count, excluded.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { showDialog = true }) { Text(stringResource(R.string.server_settings_select)) }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                if (categories.isEmpty()) {
                    Text(stringResource(R.string.server_settings_no_categories))
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        categories.forEach { category ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onChange(toggleId(excluded, category.id)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = category.id in excluded,
                                    onCheckedChange = null,
                                )
                                Text(category.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.server_settings_done)) } },
        )
    }
}

private fun toggleId(ids: List<Long>, id: Long): List<Long> =
    if (id in ids) ids - id else ids + id

@Composable
private fun TextRow(
    label: String,
    value: String,
    placeholder: String? = null,
    supporting: String? = null,
    onValueChange: (String) -> Unit,
) {
    val placeholderContent: (@Composable () -> Unit)? =
        if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        }
    val supportingContent: (@Composable () -> Unit)? =
        if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholderContent,
        supportingText = supportingContent,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

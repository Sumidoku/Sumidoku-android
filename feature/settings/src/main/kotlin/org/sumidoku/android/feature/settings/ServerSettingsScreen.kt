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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.ChapterCacheSettings
import org.sumidoku.android.core.api.model.DownloadSettings

/** Structured server-settings editor (design §14 M7): sections map
 *  1:1 onto GET /api/v1/settings; Save PUTs the complete structured
 *  object. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved, state.error) {
        when {
            state.saved -> {
                snackbar.showSnackbar("设置已保存")
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
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
            ) { Text(state.error ?: "无法加载设置") }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { SectionHeader("书库与更新") }
                item {
                    SettingChipsRow(
                        label = "默认分类",
                        hint = "你从未手动选过分类时书库打开的定位",
                        options = buildList<Pair<String, String>> {
                            add("all" to "全部")
                            add("uncategorized" to "未分类")
                            categories.forEach { add(it.id.toString() to it.name) }
                        },
                        selected = s.library.defaultCategory,
                        onSelect = { v ->
                            viewModel.update { it.copy(library = it.library.copy(defaultCategory = v)) }
                        },
                    )
                }
                item {
                    ToggleRow("显示未分类", "书库里保留「未分类」分组", s.library.showUncategorized) { v ->
                        viewModel.update { it.copy(library = it.library.copy(showUncategorized = v)) }
                    }
                }
                item {
                    ToggleRow("更新时刷新元数据", "书库更新时重新拉取图源元数据", s.library.refreshMetadata) { v ->
                        viewModel.update { it.copy(library = it.library.copy(refreshMetadata = v)) }
                    }
                }
                item {
                    SettingChipsRow(
                        label = "更新周期",
                        hint = "全局书库更新的运行间隔",
                        options = listOf(
                            "0" to "从不",
                            "12" to "每 12 小时",
                            "24" to "每 24 小时",
                            "48" to "每 2 天",
                            "168" to "每周",
                        ),
                        selected = s.library.updateIntervalHours.toString(),
                        onSelect = { v ->
                            v.toLongOrNull()?.let { hours ->
                                viewModel.update { it.copy(library = it.library.copy(updateIntervalHours = hours)) }
                            }
                        },
                    )
                }
                item {
                    Text(
                        "更新时跳过（skipTitles）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("hasUnread" to "有未读", "completed" to "已完结", "notStarted" to "未开始").forEach { (key, label) ->
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
                }

                item {
                    ExcludedCategoriesRow(
                        title = "更新时排除的分类",
                        subtitle = "属于这些分类的漫画在全局更新时跳过",
                        categories = categories,
                        excluded = s.library.excludedUpdateCategories,
                        onChange = { next ->
                            viewModel.update { it.copy(library = it.library.copy(excludedUpdateCategories = next)) }
                        },
                    )
                }

                item { SectionHeader("下载") }
                item {
                    ToggleRow("自动下载新章节", "更新发现新章节时自动入队", s.download.autoDownloadNewChapters) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoDownloadNewChapters = v)) }
                    }
                }
                item {
                    NumberRow("自动下载上限（话/部）", s.download.autoDownloadLimit.toString()) { v ->
                        v.toLongOrNull()?.let { limit ->
                            viewModel.update { it.copy(download = it.download.copy(autoDownloadLimit = limit)) }
                        }
                    }
                }
                item {
                    NumberRow("提前下载章节数", s.download.downloadAheadCount.toString()) { v ->
                        v.toLongOrNull()?.let { count ->
                            viewModel.update { it.copy(download = it.download.copy(downloadAheadCount = count)) }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("下载格式", style = MaterialTheme.typography.bodyMedium)
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
                }
                item {
                    NumberRow("并发下载数", s.download.downloadConcurrency.toString()) { v ->
                        v.toLongOrNull()?.let { c ->
                            viewModel.update { it.copy(download = it.download.copy(downloadConcurrency = c)) }
                        }
                    }
                }
                item {
                    ToggleRow("跳过有未读章节的漫画", "更新前已有未读章节的漫画不自动下载", s.download.autoSkipMangaWithUnread) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoSkipMangaWithUnread = v)) }
                    }
                }
                item {
                    ToggleRow("忽略重新上传的章节", "自动下载时跳过重新上传的章节", s.download.autoIgnoreReuploads) { v ->
                        viewModel.update { it.copy(download = it.download.copy(autoIgnoreReuploads = v)) }
                    }
                }
                item {
                    ToggleRow("手动标记已读后删除", "手动标记已读时删除该章的下载", s.download.deleteAfterManualRead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(deleteAfterManualRead = v)) }
                    }
                }
                item {
                    ToggleRow("阅后删除", "读完已下载章节后删除成品", s.download.deleteAfterRead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(deleteAfterRead = v)) }
                    }
                }
                item {
                    ToggleRow("允许删除带书签的章节", "带书签的章节也按阅后删除规则处理", s.download.allowDeleteBookmarked) { v ->
                        viewModel.update { it.copy(download = it.download.copy(allowDeleteBookmarked = v)) }
                    }
                }
                item {
                    ToggleRow("预读下一章", "阅读时提前下载后续章节", s.download.downloadAhead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(downloadAhead = v)) }
                    }
                }
                item {
                    ExcludedCategoriesRow(
                        title = "自动下载排除的分类",
                        subtitle = "这些分类里的漫画只跳过自动下载，仍可手动下载",
                        categories = categories,
                        excluded = s.library.excludedDownloadCategories,
                        onChange = { next ->
                            viewModel.update { it.copy(library = it.library.copy(excludedDownloadCategories = next)) }
                        },
                    )
                }
                item {
                    TextRow(
                        label = "下载后移动（模板）",
                        value = s.download.movePath ?: "",
                        placeholder = "/mnt/library/{author}/{manga}/{filename}",
                        supporting = "变量：{source} 源名 · {title}/{manga} 漫画标题 · {author} 作者 · {artist} 画师 · {chapter} 章节名 · {filename} 默认文件名；留空表示不移动",
                        onValueChange = { v ->
                            viewModel.update { it.copy(download = it.download.copy(movePath = v.ifBlank { null })) }
                        },
                    )
                }

                item { SectionHeader("计划备份") }
                item {
                    ToggleRow("启用计划备份", "按间隔自动写出备份文件", s.backup.enabled) { v ->
                        viewModel.update { it.copy(backup = it.backup.copy(enabled = v)) }
                    }
                }
                item {
                    NumberRow("备份间隔（小时）", s.backup.intervalHours.toString()) { v ->
                        v.toLongOrNull()?.let { h ->
                            viewModel.update { it.copy(backup = it.backup.copy(intervalHours = h)) }
                        }
                    }
                }
                item {
                    NumberRow("保留份数", s.backup.retentionCount.toString()) { v ->
                        v.toLongOrNull()?.let { r ->
                            viewModel.update { it.copy(backup = it.backup.copy(retentionCount = r)) }
                        }
                    }
                }
                item {
                    TextRow(
                        label = "备份目录",
                        value = s.backup.dir ?: "",
                        placeholder = "留空使用服务器默认备份目录",
                        supporting = "也可用环境变量 SUMIDOKU_BACKUP_DIR 指定",
                        onValueChange = { v ->
                            viewModel.update { it.copy(backup = it.backup.copy(dir = v.ifBlank { null })) }
                        },
                    )
                }

                item { SectionHeader("缓存") }
                item {
                    NumberRow("章节页缓存上限（MB）", ((s.cache?.chapterMaxBytes ?: 0L) / 1048576L).toString()) { v ->
                        v.toLongOrNull()?.let { mb ->
                            viewModel.update {
                                it.copy(cache = (it.cache ?: ChapterCacheSettings(chapterMaxBytes = 0L)).copy(chapterMaxBytes = mb * 1048576L))
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text(if (state.busy) "保存中…" else "保存设置") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** 单选 chips：分类类取值（默认分类）与预置间隔都用它，选项外的当前值
 *  单独补一个 chip，避免服务器上的自定义值被静默丢掉。 */
@Composable
private fun SettingChipsRow(
    label: String,
    hint: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val all = if (options.any { it.first == selected }) options else options + (selected to selected)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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

/** 分类排除规则：一行摘要 + 弹窗多选（webui ExcludedCategoriesSetting）。 */
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
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (excluded.isEmpty()) subtitle else "已排除 ${excluded.size} 个分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { showDialog = true }) { Text("选择") }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                if (categories.isEmpty()) {
                    Text("还没有分类")
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
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("完成") } },
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

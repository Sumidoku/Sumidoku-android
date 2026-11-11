package org.sumidoku.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                item { SectionHeader("阅读") }
                item {
                    ToggleRow("无痕阅读", "全局暂停记录阅读历史（详情页可按漫画覆盖）", s.incognito.enabled) { v ->
                        viewModel.update { it.copy(incognito = it.incognito.copy(enabled = v)) }
                    }
                }

                item { SectionHeader("书库与更新") }
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
                    NumberRow("更新间隔（小时）", s.library.updateIntervalHours.toString()) { v ->
                        v.toLongOrNull()?.let { hours ->
                            viewModel.update { it.copy(library = it.library.copy(updateIntervalHours = hours)) }
                        }
                    }
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
                    ToggleRow("阅后删除", "读完已下载章节后删除成品", s.download.deleteAfterRead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(deleteAfterRead = v)) }
                    }
                }
                item {
                    ToggleRow("预读下一章", "阅读时提前下载后续章节", s.download.downloadAhead) { v ->
                        viewModel.update { it.copy(download = it.download.copy(downloadAhead = v)) }
                    }
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

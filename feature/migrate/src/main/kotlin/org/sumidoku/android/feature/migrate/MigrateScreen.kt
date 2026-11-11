package org.sumidoku.android.feature.migrate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.MigrateResult

/** Batch migration wizard: source → manga → targets/mode → auto-match →
 *  confirm → results. Mirrors webui MigratePage.tsx. Preview 15-migrate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    onBack: () -> Unit = {},
    viewModel: MigrateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stepTitle(state.step)) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state.step) {
                            MigrateStep.MANGA -> viewModel.backTo(MigrateStep.SOURCE)
                            MigrateStep.TARGETS -> viewModel.backTo(MigrateStep.MANGA)
                            MigrateStep.CONFIRM -> viewModel.backTo(MigrateStep.TARGETS)
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.step == MigrateStep.MANGA) {
                        TextButton(onClick = { viewModel.selectAll() }) { Text("全选") }
                        TextButton(onClick = { viewModel.clearSelection() }) { Text("清除") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.step) {
                    MigrateStep.SOURCE -> SourceStep(state, viewModel)
                    MigrateStep.MANGA -> MangaStep(state, viewModel)
                    MigrateStep.TARGETS -> TargetsStep(state, viewModel)
                    MigrateStep.MATCHING -> MatchingStep(state)
                    MigrateStep.CONFIRM -> ConfirmStep(state, viewModel)
                    MigrateStep.RUNNING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("正在迁移…", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    MigrateStep.RESULTS -> ResultsStep(state, onDone = onBack)
                }
            }
        }
    }
}

private fun stepTitle(step: MigrateStep): String = when (step) {
    MigrateStep.SOURCE -> "迁移 · 选择来源源"
    MigrateStep.MANGA -> "迁移 · 选择漫画"
    MigrateStep.TARGETS -> "迁移 · 目标与模式"
    MigrateStep.MATCHING -> "迁移 · 自动匹配中"
    MigrateStep.CONFIRM -> "迁移 · 确认匹配"
    MigrateStep.RUNNING -> "迁移 · 执行中"
    MigrateStep.RESULTS -> "迁移 · 结果"
}

@Composable
private fun SourceStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    if (state.sources.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有可迁移的图源（书库为空）", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.sources.size) { i ->
            val source = state.sources[i]
            Card(Modifier.fillMaxWidth().clickable { viewModel.selectFromSource(source) }) {
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = {
                        Text("${source.mangaCount} 本在库 · v${source.version}${source.languages.joinToString(" ").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}")
                    },
                )
            }
        }
    }
}

@Composable
private fun MangaStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (state.entriesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.entries.size) { i ->
                val entry = state.entries[i]
                val id = entry.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = id != null) {
                        id?.let { viewModel.toggleManga(it) }
                    },
                ) {
                    Checkbox(
                        checked = id != null && id in state.selectedIds,
                        onCheckedChange = { id?.let { viewModel.toggleManga(it) } },
                    )
                    Column {
                        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        val unread = entry.unreadCount
                        if (unread != null && unread > 0) {
                            Text("$unread 未读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (i == state.entries.lastIndex - 4 && state.hasNextPage) {
                    LaunchedEffect(state.entries.size) { viewModel.loadMoreEntries() }
                }
            }
        }
        FilledTonalButton(
            onClick = { viewModel.toTargetsStep() },
            enabled = state.selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("下一步（已选 ${state.selectedIds.size}）") }
    }
}

@Composable
private fun TargetsStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "目标源（可多选）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.sources.size) { i ->
                val source = state.sources[i]
                if (source.id == state.fromSource?.id) return@items
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleTarget(source.id) },
                ) {
                    Checkbox(
                        checked = source.id in state.targetIds,
                        onCheckedChange = { viewModel.toggleTarget(source.id) },
                    )
                    Text(source.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    "迁移模式",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MigrateMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MigrateMode.entries.size),
                        ) {
                            Text(
                                when (mode) {
                                    MigrateMode.COPY -> "复制"
                                    MigrateMode.MIGRATE -> "迁移"
                                    MigrateMode.MIGRATE_WITH_HISTORY -> "迁移+历史"
                                },
                            )
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = { viewModel.startMatching() },
            enabled = state.targetIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("自动匹配（${state.selectedIds.size} × ${state.targetIds.size}）") }
    }
}

@Composable
private fun MatchingStep(state: MigrateUiState) {
    val done = state.matchRows.count { it.status != MatchStatus.PENDING }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("正在匹配 $done / ${state.matchRows.size}", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ConfirmStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.matchRows.size) { i ->
                val row = state.matchRows[i]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            imageVector = when (row.status) {
                                MatchStatus.MATCHED -> Icons.Outlined.CheckCircle
                                MatchStatus.NOT_FOUND -> Icons.Outlined.ErrorOutline
                                MatchStatus.PENDING -> Icons.Outlined.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = when (row.status) {
                                MatchStatus.MATCHED -> MaterialTheme.colorScheme.primary
                                MatchStatus.NOT_FOUND -> MaterialTheme.colorScheme.error
                                MatchStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(row.mangaTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                when (row.status) {
                                    MatchStatus.MATCHED -> "→ ${row.target.name}：${row.candidate?.title ?: ""}"
                                    MatchStatus.NOT_FOUND -> "→ ${row.target.name}：未找到匹配"
                                    MatchStatus.PENDING -> "→ ${row.target.name}：匹配中"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = { viewModel.runMigration() },
            enabled = state.matchable.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("开始迁移（${state.matchable.size} 条可迁移）") }
    }
}

@Composable
private fun ResultsStep(state: MigrateUiState, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val okCount = state.results.count { it.ok }
        Text(
            "完成：$okCount / ${state.results.size} 成功",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.results.size) { i ->
                val result = state.results[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.ok) Icons.Outlined.Check else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = when {
                            result.ok -> "#${result.fromMangaId} 迁移成功"
                            else -> "#${result.fromMangaId} 失败：${result.message ?: result.reason?.value ?: "未知原因"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                result.warnings?.forEach { warning ->
                    Text(
                        "⚠ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 26.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("完成")
        }
    }
}

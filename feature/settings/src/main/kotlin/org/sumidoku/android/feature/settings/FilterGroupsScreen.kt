package org.sumidoku.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.FilterGroup
import org.sumidoku.android.core.api.model.FilterRule
import org.sumidoku.android.core.api.model.FilterRuleType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Filter-group management screen (design §14 M7): list with
 *  up/down reorder, rename/delete dialogs, FAB create, and a bottom
 *  sheet rule editor per group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterGroupsScreen(
    onBack: () -> Unit,
    viewModel: FilterGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var createOpen by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FilterGroup?>(null) }
    var deleteTarget by remember { mutableStateOf<FilterGroup?>(null) }
    var rulesTarget by remember { mutableStateOf<FilterGroup?>(null) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeTransient()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("过滤组") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建过滤组")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.groups.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "还没有过滤组。\n过滤组把保存的筛选规则应用在书库上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.groups, key = { _, g -> g.id }) { index, group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${group.rules.size} 条规则",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.move(group, -1) }, enabled = index > 0) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                                }
                                IconButton(onClick = { viewModel.move(group, 1) }, enabled = index < state.groups.lastIndex) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                                }
                                IconButton(onClick = { renameTarget = group }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "重命名")
                                }
                                IconButton(onClick = { deleteTarget = group }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (group.rules.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                group.rules.forEach { rule ->
                                    Text(
                                        "• ${describeRule(rule)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { rulesTarget = group },
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text("编辑规则") }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        NameDialog(
            title = "新建过滤组",
            confirmLabel = "创建",
            onConfirm = { name ->
                createOpen = false
                viewModel.create(name)
            },
            onDismiss = { createOpen = false },
        )
    }

    renameTarget?.let { group ->
        NameDialog(
            title = "重命名「${group.name}」",
            initial = group.name,
            confirmLabel = "保存",
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(group, name)
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除过滤组") },
            text = { Text("确定删除「${group.name}」？书库条目不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(group)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    rulesTarget?.let { group ->
        FilterRulesSheet(
            group = group,
            state = state,
            onSave = { drafts ->
                rulesTarget = null
                viewModel.saveRules(group, drafts.map { it.toInput() })
            },
            onDismiss = { rulesTarget = null },
        )
    }
}

private fun describeRule(rule: FilterRule): String {
    val base = when (rule.type) {
        FilterRuleType.DOWNLOADED -> "已下载"
        FilterRuleType.HAS_UNREAD -> "有未读"
        FilterRuleType.STARTED -> "已开始阅读"
        FilterRuleType.COMPLETED -> "已完结"
        FilterRuleType.SOURCE -> "图源 = ${valueText(rule)}"
        FilterRuleType.CONTENT_RATING -> "分级 = ${valueText(rule)}"
        FilterRuleType.CATEGORY -> "分类 = ${valueText(rule)}"
        FilterRuleType.AUTHOR -> "作者 = ${valueText(rule)}"
        FilterRuleType.TAG -> "标签 = ${valueText(rule)}"
    }
    return if (rule.exclude) "排除：$base" else base
}

private fun valueText(rule: FilterRule): String =
    (rule.value as? JsonPrimitive)?.contentOrNull ?: rule.value?.toString() ?: "—"

@Composable
private fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRulesSheet(
    group: FilterGroup,
    state: FilterGroupsUiState,
    onSave: (List<RuleDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    var drafts by remember(group.id) {
        mutableStateOf(
            group.rules.map { rule ->
                RuleDraft(
                    type = rule.type,
                    valueText = (rule.value as? JsonPrimitive)?.contentOrNull ?: "",
                    exclude = rule.exclude,
                )
            },
        )
    }
    var typeMenuOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("规则 · ${group.name}", style = MaterialTheme.typography.titleMedium)

            drafts.forEachIndexed { index, draft ->
                Card {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                typeLabel(draft.type),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text("排除", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = draft.exclude,
                                onCheckedChange = { checked ->
                                    drafts = drafts.toMutableList().also { it[index] = draft.copy(exclude = checked) }
                                },
                            )
                            IconButton(onClick = {
                                drafts = drafts.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除规则")
                            }
                        }
                        when (draft.type) {
                            FilterRuleType.DOWNLOADED,
                            FilterRuleType.HAS_UNREAD,
                            FilterRuleType.STARTED,
                            FilterRuleType.COMPLETED,
                            -> Unit // flag rules carry no value
                            FilterRuleType.SOURCE -> {
                                ValuePicker(
                                    options = state.sources.map { it.id.toString() to it.name },
                                    selected = draft.valueText,
                                    onSelect = { v ->
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(valueText = v) }
                                    },
                                )
                            }
                            FilterRuleType.CATEGORY -> {
                                ValuePicker(
                                    options = state.categories.map { it.id.toString() to it.name },
                                    selected = draft.valueText,
                                    onSelect = { v ->
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(valueText = v) }
                                    },
                                )
                            }
                            FilterRuleType.CONTENT_RATING -> {
                                ValuePicker(
                                    options = listOf("0" to "安全", "1" to "NSFW", "2" to "18+"),
                                    selected = draft.valueText,
                                    onSelect = { v ->
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(valueText = v) }
                                    },
                                )
                            }
                            FilterRuleType.AUTHOR, FilterRuleType.TAG -> {
                                OutlinedTextField(
                                    value = draft.valueText,
                                    onValueChange = { v ->
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(valueText = v) }
                                    },
                                    label = { Text(if (draft.type == FilterRuleType.AUTHOR) "作者" else "标签") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            Box {
                OutlinedButton(onClick = { typeMenuOpen = true }) { Text("＋ 添加规则") }
                androidx.compose.material3.DropdownMenu(
                    expanded = typeMenuOpen,
                    onDismissRequest = { typeMenuOpen = false },
                ) {
                    FilterRuleType.entries.forEach { type ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(typeLabel(type)) },
                            onClick = {
                                typeMenuOpen = false
                                drafts = drafts + RuleDraft(type = type, valueText = "", exclude = false)
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                TextButton(onClick = { onSave(drafts) }, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}

private fun typeLabel(type: FilterRuleType): String = when (type) {
    FilterRuleType.DOWNLOADED -> "已下载"
    FilterRuleType.HAS_UNREAD -> "有未读"
    FilterRuleType.STARTED -> "已开始阅读"
    FilterRuleType.COMPLETED -> "已完结"
    FilterRuleType.SOURCE -> "图源"
    FilterRuleType.CONTENT_RATING -> "分级"
    FilterRuleType.CATEGORY -> "分类"
    FilterRuleType.AUTHOR -> "作者"
    FilterRuleType.TAG -> "标签"
}

@Composable
private fun ValuePicker(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "选择…")
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

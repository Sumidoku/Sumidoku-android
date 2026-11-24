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
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.filter_groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.misc_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.filter_groups_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.groups.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.filter_groups_empty),
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
                                        stringResource(R.string.filter_groups_rule_count, group.rules.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.move(group, -1) }, enabled = index > 0) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.misc_move_up))
                                }
                                IconButton(onClick = { viewModel.move(group, 1) }, enabled = index < state.groups.lastIndex) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.misc_move_down))
                                }
                                IconButton(onClick = { renameTarget = group }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.misc_rename))
                                }
                                IconButton(onClick = { deleteTarget = group }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.misc_delete), tint = MaterialTheme.colorScheme.error)
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
                            ) { Text(stringResource(R.string.filter_groups_edit_rules)) }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        NameDialog(
            title = stringResource(R.string.filter_groups_new),
            confirmLabel = stringResource(R.string.misc_create),
            onConfirm = { name ->
                createOpen = false
                viewModel.create(name)
            },
            onDismiss = { createOpen = false },
        )
    }

    renameTarget?.let { group ->
        NameDialog(
            title = stringResource(R.string.filter_groups_rename_title, group.name),
            initial = group.name,
            confirmLabel = stringResource(R.string.misc_save),
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
            title = { Text(stringResource(R.string.filter_groups_delete_title)) },
            text = { Text(stringResource(R.string.filter_groups_delete_text, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(group)
                }) { Text(stringResource(R.string.misc_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.misc_cancel)) } },
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

@Composable
private fun describeRule(rule: FilterRule): String {
    val base = when (rule.type) {
        FilterRuleType.DOWNLOADED -> stringResource(R.string.filter_rule_type_downloaded)
        FilterRuleType.HAS_UNREAD -> stringResource(R.string.filter_rule_type_has_unread)
        FilterRuleType.STARTED -> stringResource(R.string.filter_rule_type_started)
        FilterRuleType.COMPLETED -> stringResource(R.string.filter_rule_type_completed)
        FilterRuleType.SOURCE -> stringResource(R.string.filter_rule_value_source, valueText(rule))
        FilterRuleType.CONTENT_RATING -> stringResource(R.string.filter_rule_value_content_rating, valueText(rule))
        FilterRuleType.CATEGORY -> stringResource(R.string.filter_rule_value_category, valueText(rule))
        FilterRuleType.AUTHOR -> stringResource(R.string.filter_rule_value_author, valueText(rule))
        FilterRuleType.TAG -> stringResource(R.string.filter_rule_value_tag, valueText(rule))
    }
    return if (rule.exclude) stringResource(R.string.filter_rule_exclude_prefix, base) else base
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
                label = { Text(stringResource(R.string.misc_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.misc_cancel)) } },
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
            Text(stringResource(R.string.filter_groups_rules_title, group.name), style = MaterialTheme.typography.titleMedium)

            drafts.forEachIndexed { index, draft ->
                Card {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                typeLabel(draft.type),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.filter_groups_exclude), style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = draft.exclude,
                                onCheckedChange = { checked ->
                                    drafts = drafts.toMutableList().also { it[index] = draft.copy(exclude = checked) }
                                },
                            )
                            IconButton(onClick = {
                                drafts = drafts.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.filter_groups_remove_rule))
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
                                    options = listOf(
                                        "0" to stringResource(R.string.filter_groups_rating_safe),
                                        "1" to "NSFW",
                                        "2" to "18+",
                                    ),
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
                                    label = {
                                        Text(
                                            if (draft.type == FilterRuleType.AUTHOR) {
                                                stringResource(R.string.filter_rule_type_author)
                                            } else {
                                                stringResource(R.string.filter_rule_type_tag)
                                            },
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            Box {
                OutlinedButton(onClick = { typeMenuOpen = true }) { Text(stringResource(R.string.filter_groups_add_rule)) }
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
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.misc_cancel)) }
                TextButton(onClick = { onSave(drafts) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.misc_save)) }
            }
        }
    }
}

@Composable
private fun typeLabel(type: FilterRuleType): String = when (type) {
    FilterRuleType.DOWNLOADED -> stringResource(R.string.filter_rule_type_downloaded)
    FilterRuleType.HAS_UNREAD -> stringResource(R.string.filter_rule_type_has_unread)
    FilterRuleType.STARTED -> stringResource(R.string.filter_rule_type_started)
    FilterRuleType.COMPLETED -> stringResource(R.string.filter_rule_type_completed)
    FilterRuleType.SOURCE -> stringResource(R.string.filter_rule_type_source)
    FilterRuleType.CONTENT_RATING -> stringResource(R.string.filter_rule_type_content_rating)
    FilterRuleType.CATEGORY -> stringResource(R.string.filter_rule_type_category)
    FilterRuleType.AUTHOR -> stringResource(R.string.filter_rule_type_author)
    FilterRuleType.TAG -> stringResource(R.string.filter_rule_type_tag)
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
            Text(options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.filter_groups_select_value))
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

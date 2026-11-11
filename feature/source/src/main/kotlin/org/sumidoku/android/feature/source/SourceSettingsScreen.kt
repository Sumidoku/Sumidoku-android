package org.sumidoku.android.feature.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.json.JsonElement
import org.sumidoku.android.core.api.model.SourceSetting
import kotlin.math.roundToInt

/** Schema-driven source settings screen — renders the declarative
 *  SourceSetting tree natively (Aidoku settings model).
 *  Preview 13-source-settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SourceSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) {
        if (state.saved) snackbar.showSnackbar("已保存")
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.source?.name?.let { "$it 设置" } ?: "图源设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "重置为默认")
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.dirty && !state.saving,
                    ) { Text(if (state.saving) "保存中…" else "保存") }
                },
            )
        },
    ) { padding ->
        val settings = state.settings
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            settings == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "无法加载设置", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                }
            }
            settings.settings.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("此图源没有设置项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(settings.settings, key = { it.key }) { setting ->
                    if (setting.visibleIn(state.values)) {
                        SettingRow(setting, state.values, viewModel::updateValue)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    setting: SourceSetting,
    values: Map<String, JsonElement>,
    onChange: (String, JsonElement) -> Unit,
) {
    val value = values[setting.key]
    val settingValue = setting.`value`
    Column {
        setting.notification?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        when {
            settingValue?.group != null -> {
                Text(
                    setting.title ?: setting.key,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            settingValue?.toggle != null -> {
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        settingValue.toggle!!.subtitle?.let { Text(it) }
                    },
                    trailingContent = {
                        Switch(
                            checked = boolValue(value, settingValue.toggle!!.`default` ?: false),
                            onCheckedChange = { onChange(setting.key, jsonBool(it)) },
                        )
                    },
                )
            }
            settingValue?.select != null -> {
                val select = settingValue.select!!
                var open by remember { mutableStateOf(false) }
                val current = stringValue(value).ifEmpty { select.`default` ?: "" }
                val titles = select.titles ?: emptyList()
                val options = select.propertyValues
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        val idx = options.indexOf(current)
                        Text(if (idx >= 0 && idx < titles.size) titles[idx] else current.ifEmpty { "未选择" })
                    },
                    modifier = Modifier.clickable { open = true },
                )
                if (open) {
                    AlertDialog(
                        onDismissRequest = { open = false },
                        title = { Text(setting.title ?: setting.key) },
                        text = {
                            LazyColumn {
                                items(options.size) { i ->
                                    ListItem(
                                        headlineContent = { Text(titles.getOrElse(i) { options[i] }) },
                                        modifier = Modifier.clickable {
                                            onChange(setting.key, jsonString(options[i]))
                                            open = false
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
                    )
                }
            }
            settingValue?.multiSelect != null -> {
                val multi = settingValue.multiSelect!!
                var open by remember { mutableStateOf(false) }
                val selected = listValue(value)
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        Text(if (selected.isEmpty()) "未选择" else selected.joinToString("、"))
                    },
                    modifier = Modifier.clickable { open = true },
                )
                if (open) {
                    var draft by remember { mutableStateOf(selected.toSet()) }
                    AlertDialog(
                        onDismissRequest = { open = false },
                        title = { Text(setting.title ?: setting.key) },
                        text = {
                            LazyColumn {
                                items(multi.propertyValues.size) { i ->
                                    val option = multi.propertyValues[i]
                                    val label = multi.titles?.getOrElse(i) { option } ?: option
                                    ListItem(
                                        headlineContent = { Text(label) },
                                        trailingContent = {
                                            Checkbox(
                                                checked = draft.contains(option),
                                                onCheckedChange = {
                                                    draft = if (it) draft + option else draft - option
                                                },
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            draft = if (draft.contains(option)) draft - option else draft + option
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onChange(setting.key, jsonList(draft.toList()))
                                open = false
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
                    )
                }
            }
            settingValue?.stepper != null -> {
                val stepper = settingValue.stepper!!
                val min = stepper.minimumValue ?: 0.0
                val max = stepper.maximumValue ?: 100.0
                val step = stepper.stepValue ?: 1.0
                val current = doubleValue(value, stepper.`default` ?: min)
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        Column {
                            Slider(
                                value = current.toFloat(),
                                onValueChange = { raw ->
                                    val snapped = (min + ((raw - min.toFloat()) / step.toFloat()).roundToInt() * step).coerceIn(min, max)
                                    onChange(setting.key, jsonDouble(snapped))
                                },
                                valueRange = min.toFloat()..max.toFloat(),
                            )
                            Text(
                                if (step == step.toLong().toDouble()) {
                                    current.toLong().toString()
                                } else {
                                    current.toString()
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
            settingValue?.segment != null -> {
                val segment = settingValue.segment!!
                val options = segment.options
                val current = intValue(value, segment.`default` ?: 0)
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            options.forEachIndexed { i, option ->
                                SegmentedButton(
                                    selected = current == i,
                                    onClick = { onChange(setting.key, jsonInt(i)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                                ) { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    },
                )
            }
            settingValue?.text != null -> {
                val textDef = settingValue.text!!
                var draft by remember(setting.key) { mutableStateOf(stringValue(value).ifEmpty { textDef.`default` ?: "" }) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            onChange(setting.key, jsonString(it))
                        },
                        label = { Text(setting.title ?: setting.key) },
                        placeholder = { textDef.placeholder?.let { p -> Text(p) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            settingValue?.editableList != null -> {
                val list = listValue(value)
                var open by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        Text(if (list.isEmpty()) "空列表" else "${list.size} 项")
                    },
                    modifier = Modifier.clickable { open = true },
                )
                if (open) {
                    var draft by remember { mutableStateOf(list.joinToString("\n")) }
                    AlertDialog(
                        onDismissRequest = { open = false },
                        title = { Text(setting.title ?: setting.key) },
                        text = {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { settingValue.editableList!!.placeholder?.let { Text(it) } },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onChange(setting.key, jsonList(draft.lines().map { it.trim() }.filter { it.isNotEmpty() }))
                                open = false
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
                    )
                }
            }
            settingValue?.login != null -> {
                // 登录三法（basic/web/oauth）在 M5B 用 WebView 辅助实现
                ListItem(
                    headlineContent = { Text(setting.title ?: "登录") },
                    supportingContent = { Text("登录支持即将上线（M5B）") },
                )
            }
            settingValue?.link != null -> {
                ListItem(
                    headlineContent = { Text(setting.title ?: "链接") },
                    supportingContent = { Text(settingValue.link!!.url) },
                    modifier = Modifier.clickable {
                        // 浏览器打开（M5B 接 CustomTab）
                    },
                )
            }
            settingValue?.page != null -> {
                // 嵌套设置页（M5B 递归渲染）
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = { Text("嵌套设置页即将支持（M5B）") },
                )
            }
            else -> {
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = { Text("暂不支持的设置类型") },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

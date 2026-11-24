package org.sumidoku.android.feature.source

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.sumidoku.android.core.api.model.SettingValueOneOf8Login
import org.sumidoku.android.core.api.model.Source
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
    val savedMessage = stringResource(R.string.source_saved)

    LaunchedEffect(state.saved) {
        if (state.saved) snackbar.showSnackbar(savedMessage)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.actionMessage, state.actionError) {
        (state.actionError ?: state.actionMessage)?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.source?.name?.let {
                            stringResource(R.string.source_settings_title_named, it)
                        } ?: stringResource(R.string.source_settings),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.source_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.source_reset_to_default),
                        )
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.dirty && !state.saving,
                    ) {
                        Text(
                            if (state.saving) stringResource(R.string.source_saving)
                            else stringResource(R.string.source_save),
                        )
                    }
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
                    Text(
                        state.error ?: stringResource(R.string.source_settings_load_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.source_retry))
                    }
                }
            }
            settings.settings.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.source_no_settings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                // Local sources get the webui LocalSourcePanel actions:
                // re-scan the configured directories (queued as a task).
                if (state.source?.kind == Source.Kind.LOCAL) {
                    item(key = "__scan_local__") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.source_scan_local)) },
                            supportingContent = {
                                Text(stringResource(R.string.source_scan_local_subtitle))
                            },
                            trailingContent = {
                                TextButton(onClick = { viewModel.scanLocal() }) {
                                    Text(stringResource(R.string.source_scan))
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                items(settings.settings, key = { it.key }) { setting ->
                    if (setting.visibleIn(state.values)) {
                        SettingRow(
                            setting,
                            state.values,
                            SettingActions(
                                onChange = viewModel::updateValue,
                                basicLogin = viewModel::basicLogin,
                                webLogin = viewModel::webLogin,
                                startOauth = viewModel::startOauth,
                                completeOauth = viewModel::completeOauth,
                                logout = viewModel::logout,
                                pressButton = viewModel::pressButton,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Actions the rows can trigger on the ViewModel. */
class SettingActions(
    val onChange: (String, JsonElement) -> Unit,
    val basicLogin: (key: String, username: String, password: String) -> Unit,
    val webLogin: (key: String, cookies: Map<String, String>, localStorage: Map<String, String>?) -> Unit,
    val startOauth: suspend (key: String) -> String?,
    val completeOauth: (key: String, callbackUrl: String) -> Unit,
    val logout: (key: String, clearCookies: Boolean) -> Unit,
    val pressButton: (SourceSetting) -> Unit,
)

/** Single-choice dialog shared by select/picker rows: radio indicators,
 *  rounded rows with the selected option highlighted (M3 spec style instead
 *  of a bare list). */
@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    titles: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    val selected = options[i] == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable {
                                onPick(options[i])
                                onDismiss()
                            }
                            .padding(end = 12.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                onPick(options[i])
                                onDismiss()
                            },
                        )
                        Text(
                            titles.getOrElse(i) { options[i] },
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_cancel)) } },
    )
}

@Composable
private fun SettingRow(
    setting: SourceSetting,
    values: Map<String, JsonElement>,
    actions: SettingActions,
) {
    val onChange = actions.onChange
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
                val group = settingValue.group!!
                val loginTag = groupLoginTag(group, values)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        setting.title ?: setting.key,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    loginTag?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                // Group children are real settings — render them
                // recursively instead of dropping them.
                group.items.forEach { child ->
                    if (child.visibleIn(values)) {
                        SettingRow(child, values, actions)
                    }
                }
                group.footer?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            settingValue?.rawString != null -> {
                // Bare-string kinds: "list" (local source paths/exclusions),
                // "Button", and kinds unknown to the server normalizer.
                when (setting.type.lowercase()) {
                    "list" -> EditableStringListRow(setting, values, onChange, placeholder = null)
                    "button" -> ListItem(
                        headlineContent = { Text(setting.title ?: setting.key) },
                        trailingContent = {
                            TextButton(onClick = { actions.pressButton(setting) }) {
                                Text(stringResource(R.string.source_run))
                            }
                        },
                    )
                    else -> ListItem(
                        headlineContent = { Text(setting.title ?: setting.key) },
                        supportingContent = {
                            Text(stringValue(value).ifEmpty { stringResource(R.string.source_not_set) })
                        },
                    )
                }
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
                        Text(
                            if (idx >= 0 && idx < titles.size) titles[idx]
                            else current.ifEmpty { stringResource(R.string.source_not_selected) },
                        )
                    },
                    modifier = Modifier.clickable { open = true },
                )
                if (open) {
                    SingleChoiceDialog(
                        title = setting.title ?: setting.key,
                        options = options,
                        titles = titles,
                        current = current,
                        onPick = { onChange(setting.key, jsonString(it)) },
                        onDismiss = { open = false },
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
                        Text(
                            if (selected.isEmpty()) stringResource(R.string.source_not_selected)
                            else selected.joinToString(stringResource(R.string.source_list_separator)),
                        )
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
                                    val checked = draft.contains(option)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (checked) {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                } else {
                                                    Color.Transparent
                                                },
                                            )
                                            .clickable {
                                                draft = if (checked) draft - option else draft + option
                                            }
                                            .padding(end = 12.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                draft = if (it) draft + option else draft - option
                                            },
                                        )
                                        Text(
                                            label,
                                            color = if (checked) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onChange(setting.key, jsonList(draft.toList()))
                                open = false
                            }) { Text(stringResource(R.string.source_confirm_count, draft.size)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { open = false }) {
                                Text(stringResource(R.string.source_cancel))
                            }
                        },
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
                        visualTransformation = if (textDef.secure == true) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            settingValue?.editableList != null -> {
                EditableStringListRow(setting, values, onChange, placeholder = settingValue.editableList!!.placeholder)
            }
            settingValue?.picker != null -> {
                // Picker is a value/title single choice, same interaction as
                // select (webui renders both as a choice list).
                val picker = settingValue.picker!!
                var open by remember { mutableStateOf(false) }
                val current = stringValue(value).ifEmpty { picker.`default` ?: "" }
                val titles = picker.titles ?: emptyList()
                val options = picker.propertyValues
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = {
                        val idx = options.indexOf(current)
                        Text(
                            if (idx >= 0 && idx < titles.size) titles[idx]
                            else current.ifEmpty { stringResource(R.string.source_not_selected) },
                        )
                    },
                    modifier = Modifier.clickable { open = true },
                )
                if (open) {
                    SingleChoiceDialog(
                        title = setting.title ?: setting.key,
                        options = options,
                        titles = titles,
                        current = current,
                        onPick = { onChange(setting.key, jsonString(it)) },
                        onDismiss = { open = false },
                    )
                }
            }
            settingValue?.login != null -> {
                val login = settingValue.login!!
                val marker = stringValue(value)
                val loggedIn = marker.isNotEmpty()
                LoginRow(setting, login, loggedIn, values, actions)
            }
            settingValue?.link != null -> {
                val context = LocalContext.current
                ListItem(
                    headlineContent = { Text(setting.title ?: stringResource(R.string.source_link)) },
                    supportingContent = { Text(settingValue.link!!.url) },
                    trailingContent = {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.source_open_in_browser),
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settingValue.link!!.url)))
                    },
                )
            }
            settingValue?.page != null -> {
                val page = settingValue.page!!
                var expanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = { page.info?.let { Text(it) } },
                    trailingContent = {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                if (expanded) {
                    Column(Modifier.padding(start = 12.dp)) {
                        page.items.forEach { child ->
                            if (child.visibleIn(values)) {
                                SettingRow(child, values, actions)
                            }
                        }
                    }
                }
            }
            else -> {
                ListItem(
                    headlineContent = { Text(setting.title ?: setting.key) },
                    supportingContent = { Text(stringResource(R.string.source_unsupported_setting)) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** String-list editor shared by the EditableList object kind and the
 *  bare-string "list" kind (local source paths/exclusions). One entry per
 *  line in a dialog, mirroring webui's textarea editor. */
@Composable
private fun EditableStringListRow(
    setting: SourceSetting,
    values: Map<String, JsonElement>,
    onChange: (String, JsonElement) -> Unit,
    placeholder: String?,
) {
    val list = listValue(values[setting.key])
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(setting.title ?: setting.key) },
        supportingContent = {
            Text(
                if (list.isEmpty()) stringResource(R.string.source_empty_list)
                else stringResource(R.string.source_items_count, list.size),
            )
        },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        var draft by remember { mutableStateOf(list.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title ?: setting.key) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.source_list_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = {
                            Text(placeholder ?: stringResource(R.string.source_list_edit_placeholder))
                        },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.source_items_total,
                                    draft.lines().count { it.isNotBlank() },
                                ),
                            )
                        },
                        minLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(setting.key, jsonList(draft.lines().map { it.trim() }.filter { it.isNotEmpty() }))
                    open = false
                }) { Text(stringResource(R.string.source_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.source_cancel)) }
            },
        )
    }
}

/** Login setting row — Aidoku basic/web/oauth methods. Logged-in state is a
 *  non-empty marker string at propertyValues[setting.key] (webui parity). */
@Composable
private fun LoginRow(
    setting: SourceSetting,
    login: SettingValueOneOf8Login,
    loggedIn: Boolean,
    values: Map<String, JsonElement>,
    actions: SettingActions,
) {
    val scope = rememberCoroutineScope()
    when (login.method) {
        SettingValueOneOf8Login.Method.BASIC -> {
            var username by remember(setting.key) {
                mutableStateOf(stringValue(values["${setting.key}.username"]))
            }
            var password by remember(setting.key) { mutableStateOf("") }
            var showFields by remember(setting.key, loggedIn) { mutableStateOf(!loggedIn) }
            Column {
                ListItem(
                    headlineContent = { Text(setting.title ?: stringResource(R.string.source_login)) },
                    supportingContent = {
                        if (loggedIn) {
                            Text(
                                if (username.isNotEmpty()) {
                                    stringResource(R.string.source_logged_in_as, username)
                                } else {
                                    stringResource(R.string.source_logged_in)
                                },
                            )
                        }
                    },
                    trailingContent = {
                        if (loggedIn) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showFields = !showFields }) {
                                    Text(
                                        if (showFields) stringResource(R.string.source_collapse)
                                        else stringResource(R.string.source_relogin),
                                    )
                                }
                                // Design 13: every login method can log out,
                                // not just web/oauth — parity with them.
                                TextButton(
                                    onClick = {
                                        actions.logout(setting.key, login.clearCookiesOnLogOut == true)
                                    },
                                ) {
                                    Text(
                                        login.logoutTitle ?: stringResource(R.string.source_logout),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    },
                )
                if (showFields) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = {
                                Text(
                                    if (login.useEmail == true) stringResource(R.string.source_email)
                                    else stringResource(R.string.source_username),
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.source_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { actions.basicLogin(setting.key, username, password) },
                            enabled = username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.source_login)) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        SettingValueOneOf8Login.Method.WEB -> {
            var showWebView by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(setting.title ?: stringResource(R.string.source_login)) },
                supportingContent = {
                    Text(
                        if (loggedIn) stringResource(R.string.source_logged_in)
                        else stringResource(R.string.source_web_login_subtitle),
                    )
                },
                trailingContent = {
                    if (loggedIn) {
                        TextButton(onClick = {
                            actions.logout(setting.key, login.clearCookiesOnLogOut == true)
                        }) { Text(login.logoutTitle ?: stringResource(R.string.source_logout)) }
                    } else {
                        TextButton(
                            onClick = { showWebView = true },
                            enabled = !login.url.isNullOrBlank(),
                        ) { Text(stringResource(R.string.source_open_login_page)) }
                    }
                },
            )
            if (showWebView && !login.url.isNullOrBlank()) {
                WebLoginDialog(
                    url = login.url!!,
                    localStorageKeys = login.localStorageKeys.orEmpty(),
                    onDismiss = { showWebView = false },
                    onCaptured = { cookies, ls ->
                        showWebView = false
                        actions.webLogin(setting.key, cookies, ls)
                    },
                )
            }
        }
        SettingValueOneOf8Login.Method.OAUTH -> {
            var authUrl by remember { mutableStateOf<String?>(null) }
            ListItem(
                headlineContent = { Text(setting.title ?: stringResource(R.string.source_login)) },
                supportingContent = {
                    Text(
                        if (loggedIn) stringResource(R.string.source_logged_in)
                        else stringResource(R.string.source_oauth_login),
                    )
                },
                trailingContent = {
                    if (loggedIn) {
                        TextButton(onClick = {
                            actions.logout(setting.key, login.clearCookiesOnLogOut == true)
                        }) { Text(login.logoutTitle ?: stringResource(R.string.source_logout)) }
                    } else {
                        TextButton(onClick = {
                            scope.launch { authUrl = actions.startOauth(setting.key) }
                        }) { Text(stringResource(R.string.source_oauth_start)) }
                    }
                },
            )
            authUrl?.let { url ->
                OAuthDialog(
                    url = url,
                    callbackScheme = login.callbackScheme,
                    onDismiss = { authUrl = null },
                    onCallback = { callback ->
                        authUrl = null
                        actions.completeOauth(setting.key, callback)
                    },
                )
            }
        }
    }
}

/** WebView that loads the login page; "Finish login" captures cookies for the
 *  current host (plus requested localStorage keys) and returns them —
 *  the Android-native advantage over webui's paste-cookies flow. */
@Composable
private fun WebLoginDialog(
    url: String,
    localStorageKeys: List<String>,
    onDismiss: () -> Unit,
    onCaptured: (cookies: Map<String, String>, localStorage: Map<String, String>?) -> Unit,
) {
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_web_login_title)) },
        text = {
            Column {
                Text(currentUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, u: String?) {
                                    if (u != null) currentUrl = u
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val wv = webView ?: return@TextButton
                val cookies = android.webkit.CookieManager.getInstance()
                    .getCookie(currentUrl)?.split(";")
                    ?.mapNotNull { part ->
                        val kv = part.trim().split("=", limit = 2)
                        if (kv.size == 2 && kv[0].isNotEmpty()) kv[0] to kv[1] else null
                    }?.toMap().orEmpty()
                if (localStorageKeys.isEmpty()) {
                    onCaptured(cookies, null)
                } else {
                    val ls = mutableMapOf<String, String>()
                    var remaining = localStorageKeys.size
                    localStorageKeys.forEach { key ->
                        wv.evaluateJavascript("localStorage.getItem('$key')") { raw ->
                            val v = raw?.removeSurrounding("\"")?.takeIf { it != "null" }
                            if (v != null) ls[key] = v
                            remaining -= 1
                            if (remaining == 0) onCaptured(cookies, ls.ifEmpty { null })
                        }
                    }
                }
            }) { Text(stringResource(R.string.source_finish_login)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_cancel)) }
        },
    )
}

/** WebView for OAuth: intercepts the callback navigation (custom scheme or
 *  the configured callback prefix) and returns the full callback URL. */
@Composable
private fun OAuthDialog(
    url: String,
    callbackScheme: String?,
    onDismiss: () -> Unit,
    onCallback: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_oauth_title)) },
        text = {
            AndroidView(
                factory = { context ->
                    android.webkit.WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                            ): Boolean {
                                val u = request?.url?.toString() ?: return false
                                val scheme = request.url.scheme ?: ""
                                val isCallback =
                                    (!callbackScheme.isNullOrBlank() && u.startsWith(callbackScheme)) ||
                                        (scheme != "http" && scheme != "https")
                                if (isCallback) {
                                    onCallback(u)
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(420.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_cancel)) } },
    )
}

/** Design 13: a settings group that owns a login item advertises the state in
 *  its header ("Logged in · web"), so you do not have to scroll to the row. */
@Composable
private fun groupLoginTag(
    group: org.sumidoku.android.core.api.model.SettingValueOneOfGroup,
    values: Map<String, kotlinx.serialization.json.JsonElement>,
): String? {
    var loggedInMethod: String? = null
    group.items.forEach { child ->
        val login = child.value?.login ?: return@forEach
        if (stringValue(values[child.key]).isNotEmpty()) {
            loggedInMethod = login.method?.value ?: stringResource(R.string.source_login)
        }
    }
    return loggedInMethod?.let { stringResource(R.string.source_logged_in_tag, it) }
}
package org.sumidoku.android.feature.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.sumidoku.android.core.api.model.ContentRating
import org.sumidoku.android.core.api.model.Manga
import org.sumidoku.android.core.api.model.MangaStatus
import org.sumidoku.android.core.api.model.MetadataField
import org.sumidoku.android.core.api.model.MetadataPreviewField
import org.sumidoku.android.core.api.model.TrackSearchItem
import org.sumidoku.android.core.api.model.TrackerLink
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.model.ServerProfile
import java.net.URLEncoder

fun providerLabel(provider: TrackerProvider): String = when (provider) {
    TrackerProvider.ANILIST -> "AniList"
    TrackerProvider.MAL -> "MyAnimeList"
    TrackerProvider.BANGUMI -> "Bangumi"
    TrackerProvider.MANGABAKA -> "MangaBaka"
}

private fun fieldLabel(field: MetadataField): String = when (field) {
    MetadataField.TITLE -> "标题"
    MetadataField.AUTHORS -> "作者"
    MetadataField.ARTISTS -> "画师"
    MetadataField.DESCRIPTION -> "简介"
    MetadataField.TAGS -> "标签"
    MetadataField.STATUS -> "连载状态"
    MetadataField.CONTENT_RATING -> "内容分级"
    MetadataField.COVER -> "封面"
    else -> field.value
}

/** Render the raw JSON value one line: strings bare, arrays joined. */
private fun prettyValue(value: JsonElement?): String = when (value) {
    null -> "—"
    is JsonPrimitive -> value.content
    is JsonArray -> value.joinToString("、") { (it as? JsonPrimitive)?.content ?: it.toString() }
    else -> value.toString()
}

private fun splitList(text: String): List<String> =
    text.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }

/** Edit metadata dialog. Saved fields become manual locks; the lock list
 *  lets the user release fields back to the provider (metadataUnlock). */
@Composable
fun EditMetadataSheet(
    manga: Manga,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String?, authors: List<String>?, artists: List<String>?,
        description: String?, tags: List<String>?,
        status: MangaStatus?, contentRating: ContentRating?,
        unlock: List<MetadataField>,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(manga.title) }
    var authors by remember { mutableStateOf(manga.authors.orEmpty().joinToString("，")) }
    var artists by remember { mutableStateOf(manga.artists.orEmpty().joinToString("，")) }
    var description by remember { mutableStateOf(manga.description.orEmpty()) }
    var tags by remember { mutableStateOf(manga.tags.orEmpty().joinToString("，")) }
    var status by remember { mutableStateOf(manga.status) }
    var contentRating by remember { mutableStateOf(manga.contentRating) }
    var unlock by remember { mutableStateOf(setOf<MetadataField>()) }
    val locks = manga.metadataLocks.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑元数据") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = authors, onValueChange = { authors = it }, label = { Text("作者（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = artists, onValueChange = { artists = it }, label = { Text("画师（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("简介") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("标签（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                EnumField("连载状态", status, MangaStatus.entries) { status = it }
                EnumField("内容分级", contentRating, ContentRating.entries) { contentRating = it }

                if (locks.isNotEmpty()) {
                    HorizontalDivider()
                    Text("字段锁（点选解锁，交还给提供方）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    locks.forEach { (name, lock) ->
                        val field = runCatching { MetadataField.valueOf(name.uppercase()) }.getOrNull() ?: return@forEach
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                unlock = if (field in unlock) unlock - field else unlock + field
                            },
                        ) {
                            Checkbox(checked = field in unlock, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(fieldLabel(field), style = MaterialTheme.typography.bodyMedium)
                                Text("锁定：${lock.provider}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    onSave(
                        title.ifBlank { null },
                        splitList(authors).ifEmpty { null },
                        splitList(artists).ifEmpty { null },
                        description.ifBlank { null },
                        splitList(tags).ifEmpty { null },
                        status,
                        contentRating,
                        unlock.toList(),
                    )
                },
            ) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumField(label: String, value: T, options: List<T>, onSelect: (T) -> Unit) where T : Enum<T> {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

/** Alternate-cover picker. Images load through the server-side source
 *  image proxy (7-day cache + ETag), same as the webui cover picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPickerSheet(
    profile: ServerProfile,
    sourceId: Long,
    alternates: List<String>?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("更换封面", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when {
                alternates == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("加载备选封面…")
                }
                alternates.isEmpty() -> Text("没有备选封面", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(alternates) { url ->
                        val proxy = "${profile.baseUrl.trimEnd('/')}/api/v1/sources/$sourceId/image?url=" +
                            URLEncoder.encode(url, "UTF-8")
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(proxy)
                                .httpHeaders(
                                    NetworkHeaders.Builder()
                                        .set("Authorization", "Bearer ${profile.apiKey}")
                                        .build(),
                                )
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable(enabled = !busy) { onSelect(url) },
                        )
                    }
                }
            }
        }
    }
}

/** Match-metadata flow: provider chips → search → per-field preview with
 *  checkboxes → apply (optionally binding the tracker link). Mirrors the
 *  webui MatchMetadataDialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchMetadataSheet(
    meta: MetaUiState,
    defaultQuery: String,
    onProvider: (TrackerProvider) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (TrackSearchItem) -> Unit,
    onToggleField: (MetadataField) -> Unit,
    onBack: () -> Unit,
    onApply: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var bind by remember { mutableStateOf(true) }
    LaunchedEffectQuery(defaultQuery, meta.matchQuery, onQuery)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("匹配元数据", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrackerProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = meta.matchProvider == provider,
                        onClick = { onProvider(provider) },
                        label = { Text(providerLabel(provider)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val preview = meta.preview
            if (preview == null) {
                OutlinedTextField(
                    value = meta.matchQuery,
                    onValueChange = onQuery,
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = onSearch) { Text("搜索") }
                    },
                )
                if (meta.matchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                val results = meta.matchResults.orEmpty()
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    item.info ?: item.description.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                if (item.tracked) AssistChip(onClick = {}, label = { Text("已追踪") })
                            },
                            modifier = Modifier.clickable { onPick(item) },
                        )
                    }
                }
            } else {
                Text(
                    preview.candidate.title ?: preview.candidate.remoteId,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.linked) {
                    Text("已绑定此条目", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(preview.fields) { field: MetadataPreviewField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = field.selectable) {
                                onToggleField(field.`field`)
                            },
                        ) {
                            Checkbox(
                                checked = field.`field` in meta.previewSelected,
                                onCheckedChange = null,
                                enabled = field.selectable,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(fieldLabel(field.`field`), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${prettyValue(field.current)} → ${prettyValue(field.incoming)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val note = field.reason ?: field.lock?.let { "已锁定：${it.provider}" }
                                if (note != null) {
                                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = bind, onCheckedChange = { bind = it })
                    Text("同时绑定追踪链接", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    OutlinedButton(onClick = onBack) { Text("返回") }
                    Button(
                        enabled = !meta.applyBusy && meta.previewSelected.isNotEmpty(),
                        onClick = { onApply(bind) },
                    ) { Text(if (meta.applyBusy) "应用中…" else "应用 ${meta.previewSelected.size} 项") }
                }
            }
        }
    }
}

@Composable
private fun LaunchedEffectQuery(defaultQuery: String, current: String, onQuery: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(defaultQuery) {
        if (current.isEmpty()) onQuery(defaultQuery)
    }
}

/** Tracker links sheet: bound links with sync/unbind, plus a search to
 *  add a new binding (same provider chips + search as the match flow). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerLinksSheet(
    meta: MetaUiState,
    defaultQuery: String,
    onProvider: (TrackerProvider) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (TrackerProvider, TrackSearchItem) -> Unit,
    onRemove: (TrackerProvider) -> Unit,
    onSync: (TrackerProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    LaunchedEffectQuery(defaultQuery, meta.matchQuery, onQuery)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (!adding) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("追踪链接", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text("添加绑定") }
                }
                val links = meta.links
                when {
                    links == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("加载中…")
                    }
                    links.isEmpty() -> Text("尚未绑定任何追踪器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(links, key = { it.provider.value }) { link: TrackerLink ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(providerLabel(link.provider), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(link.title ?: link.trackId, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        TextButton(enabled = !meta.linkBusy, onClick = { onSync(link.provider) }) { Text("同步进度") }
                                        TextButton(enabled = !meta.linkBusy, onClick = { onRemove(link.provider) }) { Text("解绑") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("添加绑定", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = false }) { Text("返回") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackerProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = meta.matchProvider == provider,
                            onClick = { onProvider(provider) },
                            label = { Text(providerLabel(provider)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = meta.matchQuery,
                    onValueChange = onQuery,
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = onSearch) { Text("搜索") } },
                )
                if (meta.matchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(meta.matchResults.orEmpty(), key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(item.info ?: item.description.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                TextButton(enabled = !meta.linkBusy, onClick = {
                                    onAdd(meta.matchProvider, item)
                                    adding = false
                                }) { Text("绑定") }
                            },
                        )
                    }
                }
            }
        }
    }
}

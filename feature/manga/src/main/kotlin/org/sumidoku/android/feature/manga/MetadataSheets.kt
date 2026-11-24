package org.sumidoku.android.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import org.sumidoku.android.core.api.model.TrackStatus
import org.sumidoku.android.core.api.model.TrackerLink
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.common.sourceImageUrl
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.feature.trackers.TrackerLogo

fun providerLabel(provider: TrackerProvider): String = when (provider) {
    TrackerProvider.ANILIST -> "AniList"
    TrackerProvider.MAL -> "MyAnimeList"
    TrackerProvider.BANGUMI -> "Bangumi"
    TrackerProvider.MANGABAKA -> "MangaBaka"
}

@Composable
private fun fieldLabel(field: MetadataField): String = when (field) {
    MetadataField.TITLE -> stringResource(R.string.metadata_field_title)
    MetadataField.AUTHORS -> stringResource(R.string.metadata_field_authors)
    MetadataField.ARTISTS -> stringResource(R.string.metadata_field_artists)
    MetadataField.DESCRIPTION -> stringResource(R.string.metadata_field_description)
    MetadataField.TAGS -> stringResource(R.string.metadata_field_tags)
    MetadataField.STATUS -> stringResource(R.string.metadata_field_status)
    MetadataField.CONTENT_RATING -> stringResource(R.string.metadata_field_content_rating)
    MetadataField.COVER -> stringResource(R.string.metadata_field_cover)
    else -> field.value
}

/** Render the raw JSON value one line: strings bare, arrays joined. */
@Composable
private fun prettyValue(value: JsonElement?): String = when (value) {
    null -> "—"
    is JsonPrimitive -> value.content
    is JsonArray -> value.joinToString(stringResource(R.string.manga_list_separator)) {
        (it as? JsonPrimitive)?.content ?: it.toString()
    }
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
    var authors by remember { mutableStateOf(manga.authors.orEmpty().joinToString(", ")) }
    var artists by remember { mutableStateOf(manga.artists.orEmpty().joinToString(", ")) }
    var description by remember { mutableStateOf(manga.description.orEmpty()) }
    var tags by remember { mutableStateOf(manga.tags.orEmpty().joinToString(", ")) }
    var status by remember { mutableStateOf(manga.status) }
    var contentRating by remember { mutableStateOf(manga.contentRating) }
    var unlock by remember { mutableStateOf(setOf<MetadataField>()) }
    val locks = manga.metadataLocks.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metadata_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.metadata_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = authors, onValueChange = { authors = it }, label = { Text(stringResource(R.string.metadata_authors_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = artists, onValueChange = { artists = it }, label = { Text(stringResource(R.string.metadata_artists_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.metadata_field_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text(stringResource(R.string.metadata_tags_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                EnumField(stringResource(R.string.metadata_field_status), status, MangaStatus.entries) { status = it }
                EnumField(stringResource(R.string.metadata_field_content_rating), contentRating, ContentRating.entries) { contentRating = it }

                if (locks.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.metadata_locks_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.metadata_locked_by, lock.provider), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            ) { Text(if (busy) stringResource(R.string.action_saving) else stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
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
            Text(stringResource(R.string.manga_change_cover), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when {
                alternates == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.cover_loading))
                }
                alternates.isEmpty() -> Text(stringResource(R.string.cover_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(alternates) { url ->
                        val proxy = sourceImageUrl(profile.baseUrl, sourceId, url)
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
            Text(stringResource(R.string.metadata_match), style = MaterialTheme.typography.titleMedium)
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
                    label = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = onSearch) { Text(stringResource(R.string.action_search)) }
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
                                if (item.tracked) AssistChip(onClick = {}, label = { Text(stringResource(R.string.tracker_tracked)) })
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
                    Text(stringResource(R.string.metadata_already_linked), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                                val note = field.reason ?: field.lock?.let {
                                    stringResource(R.string.metadata_locked_by_long, it.provider)
                                }
                                if (note != null) {
                                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = bind, onCheckedChange = { bind = it })
                    Text(stringResource(R.string.metadata_bind_link), style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    Button(
                        enabled = !meta.applyBusy && meta.previewSelected.isNotEmpty(),
                        onClick = { onApply(bind) },
                    ) {
                        Text(
                            if (meta.applyBusy) {
                                stringResource(R.string.action_applying)
                            } else {
                                stringResource(R.string.metadata_apply_count, meta.previewSelected.size)
                            },
                        )
                    }
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

/** Tracker links sheet: bound links with edit/sync/unbind, plus a search to
 *  add a new binding. The add flow uses its own `linkAdd*` state (not the
 *  metadata-match fields) so the two dialogs cannot overwrite each other. */
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
    onEdit: (TrackerLink) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<TrackerProvider?>(null) }
    LaunchedEffectQuery(defaultQuery, meta.linkAddQuery, onQuery)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (!adding) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tracker_links_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text(stringResource(R.string.tracker_add_link)) }
                }
                val links = meta.links
                when {
                    links == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.loading))
                    }
                    links.isEmpty() -> Text(stringResource(R.string.tracker_none_bound), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(links, key = { it.provider.value }) { link: TrackerLink ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TrackerLogo(link.provider, size = 28.dp)
                                        Spacer(Modifier.size(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(providerLabel(link.provider), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(link.title ?: link.trackId, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        TextButton(enabled = !meta.linkBusy, onClick = { onEdit(link) }) { Text(stringResource(R.string.action_edit)) }
                                        TextButton(enabled = !meta.linkBusy, onClick = { onSync(link.provider) }) { Text(stringResource(R.string.tracker_sync_progress)) }
                                        TextButton(
                                            enabled = !meta.linkBusy,
                                            onClick = { pendingRemove = link.provider },
                                        ) { Text(stringResource(R.string.tracker_unbind)) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tracker_add_link), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_back)) }
                }
                // Only providers without an existing binding (webui
                // availableTrackerProviders): the API keeps one link/provider.
                val bound = meta.links.orEmpty().map { it.provider }.toSet()
                val available = TrackerProvider.entries.filter { it !in bound }
                // Keep the selected provider inside the still-bindable set.
                androidx.compose.runtime.LaunchedEffect(available, meta.links) {
                    if (available.isNotEmpty() && meta.linkAddProvider !in available) {
                        onProvider(available.first())
                    }
                }
                if (available.isEmpty()) {
                    Text(
                        stringResource(R.string.tracker_all_bound),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        available.forEach { provider ->
                            FilterChip(
                                selected = meta.linkAddProvider == provider,
                                onClick = { onProvider(provider) },
                                label = { Text(providerLabel(provider)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = meta.linkAddQuery,
                    onValueChange = onQuery,
                    label = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = onSearch) { Text(stringResource(R.string.action_search)) } },
                )
                if (meta.linkAddLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(meta.linkAddResults.orEmpty(), key = { it.id }) { item ->
                        ListItem(
                            leadingContent = {
                                // Tracker covers are absolute external URLs
                                // (AniList/MAL/...), no auth needed.
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp, 56.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                )
                            },
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(item.info ?: item.description.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                TextButton(enabled = !meta.linkBusy, onClick = {
                                    onAdd(meta.linkAddProvider, item)
                                    adding = false
                                }) { Text(stringResource(R.string.tracker_bind)) }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingRemove?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.tracker_unbind_title, providerLabel(provider))) },
            text = { Text(stringResource(R.string.tracker_unbind_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    onRemove(provider)
                }) { Text(stringResource(R.string.tracker_unbind)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Edit one tracker link (webui TrackerEditForm): trackMode tri-state +
 *  chapter offset saved through PUT /trackers/links/{id}, plus manual remote
 *  status/score/progress edits pushed to /trackers/{provider}/update
 *  (diff-based, webui buildTrackUpdate), sync-pull, and confirmed unbind. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackerLinkEditSheet(
    meta: MetaUiState,
    link: TrackerLink,
    onMode: (TrackerLink.TrackMode) -> Unit,
    onOffset: (String) -> Unit,
    onStatus: (TrackStatus) -> Unit,
    onScore: (String) -> Unit,
    onChapterProgress: (String) -> Unit,
    onVolumeProgress: (String) -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmUnbind by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerLogo(link.provider, size = 28.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tracker_edit_title, providerLabel(link.provider)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        link.title ?: link.trackId,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TrackerRemoteCard(meta.remoteStates[link.provider])

            // Manual remote edits (webui TrackerEditForm remote-fields block,
            // only rendered once the remote state is known).
            if (meta.remoteStates[link.provider]?.state != null) {
                Text(stringResource(R.string.tracker_remote_progress), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackStatus.entries.forEach { status ->
                        FilterChip(
                            selected = meta.editStatus == status,
                            onClick = { onStatus(status) },
                            label = { Text(trackStatusLabel(status)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = meta.editScore,
                        onValueChange = onScore,
                        label = { Text(stringResource(R.string.tracker_score)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = meta.editChapterProgress,
                        onValueChange = onChapterProgress,
                        label = { Text(stringResource(R.string.tracker_progress_chapters)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = meta.editVolumeProgress,
                        onValueChange = onVolumeProgress,
                        label = { Text(stringResource(R.string.tracker_progress_volumes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(stringResource(R.string.tracker_track_mode), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(
                    TrackerLink.TrackMode.AUTO to stringResource(R.string.tracker_mode_auto),
                    TrackerLink.TrackMode.CHAPTER to stringResource(R.string.tracker_mode_chapter),
                    TrackerLink.TrackMode.VOLUME to stringResource(R.string.tracker_mode_volume),
                ).forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = meta.editTrackMode == mode,
                        onClick = { onMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = meta.editChapterOffset,
                onValueChange = onOffset,
                label = { Text(stringResource(R.string.tracker_chapter_offset)) },
                supportingText = { Text(stringResource(R.string.tracker_offset_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !meta.linkBusy,
                    onClick = onSync,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tracker_sync_progress)) }
                Button(
                    enabled = !meta.linkBusy,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                ) { Text(if (meta.linkBusy) stringResource(R.string.action_saving) else stringResource(R.string.action_save)) }
            }
            TextButton(
                enabled = !meta.linkBusy,
                onClick = { confirmUnbind = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tracker_unbind), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmUnbind) {
        AlertDialog(
            onDismissRequest = { confirmUnbind = false },
            title = { Text(stringResource(R.string.tracker_unbind_title, providerLabel(link.provider))) },
            text = { Text(stringResource(R.string.tracker_unbind_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnbind = false
                    onRemove()
                }) { Text(stringResource(R.string.tracker_unbind)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnbind = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Read-only remote state block inside the link editor. */
@Composable
private fun TrackerRemoteCard(remote: TrackerRemote?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.tracker_remote_status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                remote == null || remote.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.tracker_state_loading), style = MaterialTheme.typography.bodySmall)
                }
                remote.failed -> Text(
                    stringResource(R.string.tracker_state_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val state = remote.state
                    if (state == null) {
                        Text(stringResource(R.string.tracker_no_progress), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            stringResource(R.string.tracker_status_line, trackStatusLabel(state.status)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            state.totalChapters?.let { total ->
                                stringResource(
                                    R.string.tracker_progress_line_total,
                                    formatDecimal(state.lastReadChapter) ?: "?",
                                    total,
                                )
                            } ?: stringResource(
                                R.string.tracker_progress_line,
                                formatDecimal(state.lastReadChapter) ?: "?",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        state.lastReadVolume?.let { volume ->
                            Text(
                                state.totalVolumes?.let { total ->
                                    stringResource(R.string.tracker_volume_line_total, volume, total)
                                } ?: stringResource(R.string.tracker_volume_line, volume),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        state.score?.let {
                            Text(stringResource(R.string.tracker_score_line, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** Remote tracking status in the same words the webui uses. */
@Composable
internal fun trackStatusLabel(status: TrackStatus?): String = when (status) {
    TrackStatus.READING -> stringResource(R.string.tracker_status_reading)
    TrackStatus.PLANNING -> stringResource(R.string.tracker_status_planning)
    TrackStatus.COMPLETED -> stringResource(R.string.tracker_status_completed)
    TrackStatus.PAUSED -> stringResource(R.string.tracker_status_paused)
    TrackStatus.DROPPED -> stringResource(R.string.tracker_status_dropped)
    TrackStatus.REREADING -> stringResource(R.string.tracker_status_rereading)
    null -> stringResource(R.string.tracker_status_unknown)
}

private fun formatDecimal(value: Double?): String? = value?.let {
    if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
}

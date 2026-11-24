package org.sumidoku.android.feature.migrate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.MigrateResult
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.common.entryCoverUrl
import org.sumidoku.android.core.model.ServerProfile

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
        // A failed source-list load is shown inline in the SOURCE step
        // (with a retry button) instead of a transient snackbar.
        val inlineError = state.step == MigrateStep.SOURCE && state.sources.isEmpty()
        if (!inlineError) {
            state.error?.let {
                snackbar.showSnackbar(it)
                viewModel.consumeError()
            }
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.migrate_back))
                    }
                },
                actions = {
                    if (state.step == MigrateStep.MANGA) {
                        TextButton(onClick = { viewModel.selectAll() }) { Text(stringResource(R.string.migrate_select_all)) }
                        TextButton(onClick = { viewModel.clearSelection() }) { Text(stringResource(R.string.migrate_clear)) }
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
                            Text(stringResource(R.string.migrate_running), modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    MigrateStep.RESULTS -> ResultsStep(state, onDone = onBack)
                }
            }
        }
    }
}

@Composable
private fun stepTitle(step: MigrateStep): String = stringResource(
    when (step) {
        MigrateStep.SOURCE -> R.string.migrate_step_source
        MigrateStep.MANGA -> R.string.migrate_step_manga
        MigrateStep.TARGETS -> R.string.migrate_step_targets
        MigrateStep.MATCHING -> R.string.migrate_step_matching
        MigrateStep.CONFIRM -> R.string.migrate_step_confirm
        MigrateStep.RUNNING -> R.string.migrate_step_running
        MigrateStep.RESULTS -> R.string.migrate_step_results
    },
)

@Composable
private fun SourceStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    if (state.sources.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.migrate_load_sources_failed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                    TextButton(onClick = { viewModel.loadSources() }) { Text(stringResource(R.string.migrate_retry)) }
                }
            } else {
                Text(stringResource(R.string.migrate_no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.sources.size) { i ->
            val source = state.sources[i]
            Card(Modifier.fillMaxWidth().clickable { viewModel.selectFromSource(source) }) {
                ListItem(
                    headlineContent = { Text(source.name ?: source.key ?: stringResource(R.string.migrate_source_fallback, source.id)) },
                    supportingContent = {
                        Text(buildString {
                            append(stringResource(R.string.migrate_source_meta, source.mangaCount ?: 0, source.version ?: 0))
                            val langs = source.languages?.joinToString(" ").orEmpty()
                            if (langs.isNotEmpty()) append(" · $langs")
                        })
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
                    val ctx = LocalContext.current
                    val coverModel = state.profile?.let { profile ->
                        // Same as webui coverUrl: the server resolves local-source
                        // covers even when the `cover` field is null.
                        ImageRequest.Builder(ctx).data(coverUrl(profile.baseUrl, entry.id, entry.cover)).apply {
                            profile.apiKey?.let {
                                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build())
                            }
                        }.build()
                    }
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp, 48.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        val unread = entry.unreadCount
                        if (unread != null && unread > 0) {
                            Text(stringResource(R.string.migrate_unread_count, unread), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
        ) { Text(stringResource(R.string.migrate_next_selected, state.selectedIds.size)) }
    }
}

@Composable
private fun TargetsStep(state: MigrateUiState, viewModel: MigrateViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.migrate_targets_title),
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
                    Text(source.name ?: source.key ?: stringResource(R.string.migrate_source_fallback, source.id), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    stringResource(R.string.migrate_mode_title),
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
                                stringResource(
                                    when (mode) {
                                        MigrateMode.COPY -> R.string.migrate_mode_copy
                                        MigrateMode.MIGRATE -> R.string.migrate_mode_migrate
                                        MigrateMode.MIGRATE_WITH_HISTORY -> R.string.migrate_mode_migrate_with_history
                                    },
                                ),
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
        ) { Text(stringResource(R.string.migrate_start_matching, state.selectedIds.size, state.targetIds.size)) }
    }
}

@Composable
private fun MatchingStep(state: MigrateUiState) {
    val done = state.matchRows.count { it.status != MatchStatus.PENDING }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.migrate_matching_progress, done, state.matchRows.size), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Small 36×50 cover thumb for the confirm step (source entry and matched
 *  candidate both go through the same authenticated image request). */
@Composable
private fun CoverThumb(profile: ServerProfile?, url: String?) {
    val context = LocalContext.current
    val model = if (profile != null && url != null) {
        ImageRequest.Builder(context).data(url).apply {
            profile.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }.build()
    } else {
        null
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .size(36.dp, 50.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
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
                        // Source side: the library entry being migrated.
                        val sourceCoverUrl = state.profile?.let { coverUrl(it.baseUrl, row.manga.id, row.manga.cover) }
                        CoverThumb(state.profile, sourceCoverUrl)
                        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                            Text(row.mangaTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    when (row.status) {
                                        MatchStatus.MATCHED -> stringResource(
                                            R.string.migrate_matched_to,
                                            row.target.name.orEmpty(),
                                            row.candidate?.title.orEmpty(),
                                        )
                                        MatchStatus.NOT_FOUND -> stringResource(
                                            R.string.migrate_not_found,
                                            row.target.name.orEmpty(),
                                        )
                                        MatchStatus.PENDING -> stringResource(
                                            R.string.migrate_matching_row,
                                            row.target.name.orEmpty(),
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                        // Target side: the matched candidate on the target source.
                        row.candidate?.let { candidate ->
                            val isLocal = row.target.kind == org.sumidoku.android.core.api.model.MigrationSource.Kind.LOCAL
                            val url = state.profile?.let { p ->
                                entryCoverUrl(p.baseUrl, candidate.sourceId, candidate.cover, candidate.id, isLocal)
                            }
                            CoverThumb(state.profile, url)
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = { viewModel.runMigration() },
            enabled = state.matchable.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(stringResource(R.string.migrate_start_button, state.matchable.size)) }
    }
}

@Composable
private fun ResultsStep(state: MigrateUiState, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val okCount = state.results.count { it.ok }
        Text(
            stringResource(R.string.migrate_results_summary, okCount, state.results.size),
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
                            result.ok -> stringResource(R.string.migrate_result_success, result.fromMangaId)
                            else -> stringResource(
                                R.string.migrate_result_failure,
                                result.fromMangaId,
                                result.message ?: result.reason?.value ?: stringResource(R.string.migrate_unknown_reason),
                            )
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
            Text(stringResource(R.string.migrate_done))
        }
    }
}

package org.sumidoku.android.feature.migrate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.MigrationApi
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.api.model.MigrateEntry
import org.sumidoku.android.core.api.model.MigrateMode
import org.sumidoku.android.core.api.model.MigrateRequest
import org.sumidoku.android.core.api.model.MigrateResult
import org.sumidoku.android.core.api.model.MigrationSource
import org.sumidoku.android.core.api.model.SearchManga
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** Wizard state machine mirroring webui MigratePage.tsx:
 *  pick source → pick manga → pick target sources + mode → auto-match
 *  per manga/target via /migrate/lookup → confirm → POST /migrate → results. */
enum class MigrateStep { SOURCE, MANGA, TARGETS, MATCHING, CONFIRM, RUNNING, RESULTS }

enum class MatchStatus { PENDING, MATCHED, NOT_FOUND }

/** One manga × one target source pairing. */
data class MatchRow(
    val manga: LibraryEntry,
    val target: MigrationSource,
    val status: MatchStatus = MatchStatus.PENDING,
    val candidate: SearchManga? = null,
) {
    val fromMangaId: Long? get() = manga.id
    val mangaTitle: String get() = manga.title
}

data class MigrateUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val error: String? = null,
    val step: MigrateStep = MigrateStep.SOURCE,
    // Step 1
    val sources: List<MigrationSource> = emptyList(),
    val fromSource: MigrationSource? = null,
    // Step 2
    val entries: List<LibraryEntry> = emptyList(),
    val entriesLoading: Boolean = false,
    val hasNextPage: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    // Step 3
    val targetIds: Set<Long> = emptySet(),
    val mode: MigrateMode = MigrateMode.MIGRATE,
    // Step 4/5
    val matchRows: List<MatchRow> = emptyList(),
    val results: List<MigrateResult> = emptyList(),
) {
    val matchable: List<MatchRow>
        get() = matchRows.filter { it.status == MatchStatus.MATCHED }
    val targets: List<MigrationSource>
        get() = sources.filter { it.id in targetIds }
}

@HiltViewModel
class MigrateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(MigrateUiState())
    val state: StateFlow<MigrateUiState> = _state

    private var entryPage = 1

    init {
        loadSources()
    }

    /** Re-run the source list load (the SOURCE step's retry button). */
    fun loadSources() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = MigrateUiState(loading = false, error = appContext.getString(R.string.migrate_error_no_server))
                return@launch
            }
            try {
                val sources = clientFactory.create<MigrationApi>(profile)
                    .getMigrationSources().await()
                    .filter { (it.mangaCount ?: 0L) > 0L }
                _state.value = _state.value.copy(loading = false, profile = profile, sources = sources)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, profile = profile, error = e.message ?: e.toString())
            }
        }
    }

    private fun migrationApi(): MigrationApi? =
        _state.value.profile?.let { clientFactory.create<MigrationApi>(it) }

    fun selectFromSource(source: MigrationSource) {
        val profile = _state.value.profile ?: return
        _state.value = _state.value.copy(
            step = MigrateStep.MANGA,
            fromSource = source,
            entries = emptyList(),
            selectedIds = emptySet(),
            entriesLoading = true,
            hasNextPage = false,
        )
        entryPage = 1
        loadEntries(profile, source.id, replace = true)
    }

    fun loadMoreEntries() {
        val s = _state.value
        val source = s.fromSource ?: return
        if (s.entriesLoading || !s.hasNextPage) return
        entryPage += 1
        loadEntries(s.profile ?: return, source.id, replace = false)
    }

    private var entriesJob: Job? = null
    private var matchingJob: Job? = null
    private var runJob: Job? = null

    private fun loadEntries(profile: ServerProfile, sourceId: Long, replace: Boolean) {
        // Cancel the in-flight page load: picking another source (or backing
        // out) must not let the stale response overwrite the new listing.
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            _state.value = _state.value.copy(entriesLoading = true)
            try {
                val page = clientFactory.create<LibraryApi>(profile)
                    .listLibrary(sourceId = sourceId, page = entryPage)
                    .await()
                _state.value = _state.value.copy(
                    entriesLoading = false,
                    entries = if (replace) page.propertyEntries else _state.value.entries + page.propertyEntries,
                    hasNextPage = page.hasNextPage ?: false,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                entryPage = (entryPage - 1).coerceAtLeast(1)
                _state.value = _state.value.copy(entriesLoading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun toggleManga(id: Long) {
        val sel = _state.value.selectedIds
        _state.value = _state.value.copy(selectedIds = if (id in sel) sel - id else sel + id)
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedIds = _state.value.entries.mapNotNull { it.id }.toSet(),
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    fun toggleTarget(id: Long) {
        val sel = _state.value.targetIds
        _state.value = _state.value.copy(targetIds = if (id in sel) sel - id else sel + id)
    }

    fun setMode(mode: MigrateMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun toTargetsStep() {
        _state.value = _state.value.copy(step = MigrateStep.TARGETS)
    }

    fun backTo(step: MigrateStep) {
        // Navigating away abandons the in-flight async work of the step left
        // behind; their late completions are guarded as well.
        if (step != MigrateStep.MATCHING) matchingJob?.cancel()
        if (step != MigrateStep.RUNNING) runJob?.cancel()
        entriesJob?.cancel()
        _state.value = _state.value.copy(step = step, entriesLoading = false)
    }

    /** Auto-match: one lookup per manga × target, first candidate wins —
     *  the same heuristic the webui wizard uses. */
    fun startMatching() {
        val s = _state.value
        val api = migrationApi() ?: return
        val selected = s.entries.filter { it.id in s.selectedIds }
        val targets = s.targets
        if (selected.isEmpty() || targets.isEmpty()) return
        val rows = selected.flatMap { manga ->
            targets.map { target -> MatchRow(manga = manga, target = target) }
        }
        _state.value = s.copy(step = MigrateStep.MATCHING, matchRows = rows)
        matchingJob?.cancel()
        matchingJob = viewModelScope.launch {
            val resolved = coroutineScope {
                rows.map { row ->
                    async {
                        try {
                            val id = row.fromMangaId ?: return@async row.copy(status = MatchStatus.NOT_FOUND)
                            val resp = api.lookupMigrationCandidates(
                                mangaId = id,
                                targetSourceId = row.target.id,
                            ).await()
                            val first = resp.candidates.firstOrNull()
                            if (first != null) {
                                row.copy(status = MatchStatus.MATCHED, candidate = first)
                            } else {
                                row.copy(status = MatchStatus.NOT_FOUND)
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            row.copy(status = MatchStatus.NOT_FOUND)
                        }
                    }
                }.awaitAll()
            }
            // Stale-completion guard: backing out of the matching step must
            // not be overridden by the late awaitAll result.
            if (_state.value.step == MigrateStep.MATCHING) {
                _state.value = _state.value.copy(step = MigrateStep.CONFIRM, matchRows = resolved)
            }
        }
    }

    /** Let the user re-pick a candidate for one row (manual override). */
    fun overrideCandidate(rowIndex: Int, candidate: SearchManga) {
        val rows = _state.value.matchRows.toMutableList()
        val row = rows.getOrNull(rowIndex) ?: return
        rows[rowIndex] = row.copy(status = MatchStatus.MATCHED, candidate = candidate)
        _state.value = _state.value.copy(matchRows = rows)
    }

    fun runMigration() {
        val s = _state.value
        val api = migrationApi() ?: return
        val rows = s.matchable
        if (rows.isEmpty()) return
        _state.value = s.copy(step = MigrateStep.RUNNING)
        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                val entries = rows.map { row ->
                    MigrateEntry(
                        fromMangaId = row.fromMangaId ?: -1,
                        toSourceId = row.target.id,
                        toMangaKey = row.candidate?.key ?: "",
                    )
                }
                val resp = api.migrateManga(
                    MigrateRequest(mode = s.mode, propertyEntries = entries),
                ).await()
                // The user may have backed out of the running step; only the
                // still-running wizard consumes the result.
                if (_state.value.step == MigrateStep.RUNNING) {
                    _state.value = _state.value.copy(
                        step = MigrateStep.RESULTS,
                        results = resp.results,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_state.value.step == MigrateStep.RUNNING) {
                    _state.value = _state.value.copy(step = MigrateStep.CONFIRM, error = e.message ?: e.toString())
                }
            }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}

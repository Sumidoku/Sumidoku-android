package org.sumidoku.android.feature.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.SettingsApi
import org.sumidoku.android.core.api.model.ChapterSort
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.api.model.ProgressPatch
import org.sumidoku.android.core.api.model.SortOrder
import org.sumidoku.android.core.api.model.Viewer
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.data.OfflineProgressStore
import org.sumidoku.android.core.data.PendingProgress
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class ReaderUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val pages: List<Page> = emptyList(),
    /** Chapters in source order (ascending) — drives prev/next transitions
     *  and the chapter drawer. */
    val chapters: List<ChapterWithDownload> = emptyList(),
    val chapterId: Long = 0,
    val index: Int = 0,
    /** Resolved reading mode: paged | continuous | webtoon. */
    val mode: String = ReadingMode.PAGED,
    /** Preference layer for the mode chips ("paged"/"continuous"/"webtoon"). */
    val modePreference: String = ReaderField.MODE.fallback,
    /** Resolved reading direction: ltr | rtl (never "default"). */
    val dir: String = "ltr",
    /** Preference layer for the direction chips: default | ltr | rtl. */
    val dirPreference: String = ReaderField.DIR.fallback,
    val layout: PageLayout = PageLayout.AUTO,
    val coverFirst: Boolean = false,
    val scale: String = ReaderField.SCALE.fallback,
    val splitWide: Boolean = false,
    val tapZones: String = ReaderField.TAP.fallback,
    val background: String = ReaderField.BG.fallback,
    val brightness: Float = 1f,
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    /** manga.viewer metadata driving dir/mode until the manga is overridden. */
    val viewer: Viewer? = null,
    /** Fields carrying a per-manga override (the chain block on screens 24/41). */
    val overridden: Set<ReaderField> = emptySet(),
    /** Server incognito: progress writes are skipped entirely. */
    val incognito: Boolean = false,
    val overlay: Boolean = false,
    val chapterSheet: Boolean = false,
    /** Bumped on an explicit slider seek so the pager/list follows the
     *  requested page instead of the other way round. */
    val seekRequest: Int = 0,
    val error: String? = null,
    val chaptersError: String? = null,
) {
    val currentChapter: ChapterWithDownload? get() = chapters.firstOrNull { it.id == chapterId }
    /** Chapter title for the reader chrome. Empty when no chapter has
     *  resolved — the reader substitutes the localized fallback. */
    val chapterTitle: String
        get() = currentChapter?.let { ch ->
            chapterLabel(ch.volumeNumber, ch.chapterNumber, ch.title)
        }.orEmpty()
    val chapterNumberLabel: String
        get() = currentChapter?.let { chapterLabel(it.volumeNumber, it.chapterNumber, null) } ?: ""
    val nextChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }
            ?.let { chapters.getOrNull(it + 1) }
    val prevChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it > 0 }
            ?.let { chapters.getOrNull(it - 1) }
    /** Vertical continuous rendering (webtoon / continuous). */
    val vertical: Boolean get() = ReadingMode.isVertical(mode)
    /** Spread of two pages is displayed for this layout/orientation. */
    fun doubleSpread(landscape: Boolean): Boolean =
        !vertical && !splitWide && when (layout) {
            PageLayout.SINGLE -> false
            PageLayout.DOUBLE -> true
            PageLayout.AUTO -> landscape
        }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val localDownloads: org.sumidoku.android.core.download.LocalDownloadEngine,
    private val offlineProgressStore: OfflineProgressStore,
    private val settingsStore: ReaderSettingsStore,
) : ViewModel() {

    val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val initialChapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state

    private var progressJob: Job? = null
    private var brightnessJob: Job? = null
    /** Tier B: extracted local page files for the open chapter, when a
     *  finished on-device copy exists — the reader serves these instead
     *  of network URLs, which is what makes offline reading work. */
    private var localPages: List<java.io.File>? = null

    // ---- preference layers (global default + per-manga overrides) ----

    private var globalRaw: Map<ReaderField, String> = emptyMap()
    private var overrideRaw: Map<ReaderField, String> = emptyMap()
    private var viewer: Viewer? = null

    private val globalPrefs: ReaderPrefs get() = ReaderPrefs.from(globalRaw)

    init {
        // Preference layers stream straight from DataStore so an edit in
        // the quick panel (or another surface) re-resolves immediately.
        viewModelScope.launch {
            settingsStore.global.collect { raw ->
                globalRaw = raw
                refreshPreferences()
            }
        }
        viewModelScope.launch {
            settingsStore.overrides(mangaId).collect { raw ->
                overrideRaw = raw
                refreshPreferences()
            }
        }
        viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = ReaderUiState(loading = false, error = appContext.getString(R.string.reader_no_server))
                return@launch
            }
            _state.value = _state.value.copy(profile = profile)
            loadIncognito(profile)
            loadMangaViewer(profile)
            loadChapters(profile)
            openChapter(initialChapterId)
        }
    }

    /** Recompute the resolved view of the two preference layers.
     *  Resolution order (webui readerPrefs.resolveDir parity):
     *  per-manga override › manga.viewer metadata › global default. */
    private fun refreshPreferences() {
        val global = globalPrefs
        val merged = global.overlay(overrideRaw)
        val mode = overrideRaw[ReaderField.MODE]
            ?: ViewerReading.modeOf(viewer)
            ?: global.mode
        val dirPreference = overrideRaw[ReaderField.DIR] ?: global.dir
        val dir = when (dirPreference) {
            "ltr", "rtl" -> dirPreference
            else -> ViewerReading.dirOf(viewer) ?: "ltr"
        }
        _state.value = _state.value.copy(
            mode = mode,
            modePreference = overrideRaw[ReaderField.MODE] ?: global.mode,
            dir = dir,
            dirPreference = dirPreference,
            layout = PageLayout.of(overrideRaw[ReaderField.LAYOUT] ?: global.layout),
            coverFirst = (overrideRaw[ReaderField.COVER] ?: global.raw(ReaderField.COVER)).toBoolean(),
            scale = overrideRaw[ReaderField.SCALE] ?: global.scale,
            splitWide = (overrideRaw[ReaderField.SPLIT] ?: global.raw(ReaderField.SPLIT)).toBoolean(),
            tapZones = (overrideRaw[ReaderField.TAP] ?: global.tapZones),
            background = (overrideRaw[ReaderField.BG] ?: global.background),
            brightness = merged.brightness,
            grayscale = (overrideRaw[ReaderField.GRAY] ?: global.raw(ReaderField.GRAY)).toBoolean(),
            sepia = (overrideRaw[ReaderField.SEPIA] ?: global.raw(ReaderField.SEPIA)).toBoolean(),
            viewer = viewer,
            overridden = overrideRaw.keys,
        )
    }

    private suspend fun loadMangaViewer(profile: ServerProfile) {
        try {
            val manga = clientFactory.create<MangaApi>(profile).getManga(mangaId).await()
            viewer = manga.viewer.takeIf { it != Viewer.UNKNOWN }
            refreshPreferences()
        } catch (_: Exception) {
            // Metadata is advisory: a failure leaves the global default.
        }
    }

    private suspend fun loadIncognito(profile: ServerProfile) {
        try {
            val settings = clientFactory.create<SettingsApi>(profile).getSettings().await()
            _state.value = _state.value.copy(incognito = settings.incognito.enabled)
        } catch (_: Exception) {
        }
    }

    private suspend fun loadChapters(profile: ServerProfile) {
        try {
            val api = clientFactory.create<MangaApi>(profile)
            val chapters = api.getMangaChapters(
                mangaId,
                sort = ChapterSort.SOURCE,
                order = SortOrder.ASC,
            ).await()
            _state.value = _state.value.copy(chapters = chapters, chaptersError = null)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The drawer must explain the empty list instead of silently
            // showing nothing (the old build swallowed this).
            _state.value = _state.value.copy(
                chaptersError = appContext.getString(
                    R.string.reader_chapters_load_failed,
                    e.message ?: e.toString(),
                ),
            )
        }
    }

    fun retryChapters() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch { loadChapters(profile) }
    }

    private var chapterJob: Job? = null

    fun openChapter(chapterId: Long) {
        // Cancel the in-flight chapter load: jumping chapters quickly must
        // not let the stale response overwrite the newer chapter's pages.
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null, chapterId = chapterId, index = 0)
            localPages = try {
                localDownloads.localPages(chapterId)
            } catch (_: Exception) {
                null
            }
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val pages = api.getChapterPages(chapterId).await()
                val resumeIndex = _state.value.chapters
                    .firstOrNull { it.id == chapterId }
                    ?.let { if (it.isRead) 0 else it.lastPageRead.toInt().coerceAtLeast(0) }
                    ?: 0
                _state.value = _state.value.copy(
                    loading = false,
                    pages = pages,
                    index = resumeIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun setIndex(index: Int) {
        val s = _state.value
        val clamped = index.coerceIn(0, (s.pages.size - 1).coerceAtLeast(0))
        if (clamped == s.index) return
        _state.value = s.copy(index = clamped)
        writeProgress(clamped)
    }

    /** Slider seek: unlike [setIndex] this must move the pager/list, so it
     *  bumps [ReaderUiState.seekRequest] for the body to observe. */
    fun seekTo(index: Int) {
        val s = _state.value
        val clamped = index.coerceIn(0, (s.pages.size - 1).coerceAtLeast(0))
        _state.value = s.copy(index = clamped, seekRequest = s.seekRequest + 1)
        writeProgress(clamped)
    }

    /** Debounced progress PATCH; in incognito mode nothing is written at
     *  all — not even the offline queue (no local trace of the reading). */
    private fun writeProgress(index: Int) {
        if (_state.value.incognito) return
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(700)
            val cur = _state.value
            if (cur.incognito) return@launch
            val profile = cur.profile ?: return@launch
            val finished = index >= cur.pages.size - 1
            try {
                clientFactory.create<MangaApi>(profile).updateChapterProgress(
                    cur.chapterId,
                    ProgressPatch(
                        lastPageRead = index.toLong(),
                        isRead = if (finished) true else null,
                    ),
                ).await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // M2 offline progress queue: a failed write is kept
                // locally and flushed on the next app start.
                offlineProgressStore.add(
                    PendingProgress(
                        chapterId = cur.chapterId,
                        lastPageRead = index,
                        isRead = finished,
                    ),
                )
            }
        }
    }

    // ---- quick panel / settings setters ----

    /** Per-manga field: keeps an override only when it differs from the
     *  value this manga would resolve to without it (webui self-cleaning). */
    private fun setMangaField(field: ReaderField, value: String, withoutOverride: String) {
        val keep = value != withoutOverride
        viewModelScope.launch {
            settingsStore.setOverride(mangaId, field, value, keep)
        }
    }

    fun setMode(mode: String) {
        val base = ViewerReading.modeOf(viewer) ?: globalPrefs.mode
        setMangaField(ReaderField.MODE, mode, base)
    }

    fun setDir(dirPreference: String) {
        // "default" always clears the override: the manga then follows
        // its own viewer metadata, then the global default.
        if (dirPreference == "default") {
            viewModelScope.launch { settingsStore.setOverride(mangaId, ReaderField.DIR, null, keep = false) }
            return
        }
        val base = ViewerReading.dirOf(viewer) ?: globalPrefs.dir.takeIf { it != "default" } ?: "ltr"
        setMangaField(ReaderField.DIR, dirPreference, base)
    }

    fun setLayout(layout: PageLayout) =
        setMangaField(ReaderField.LAYOUT, layout.value, globalPrefs.layout)

    fun setCoverFirst(enabled: Boolean) =
        setMangaField(ReaderField.COVER, enabled.toString(), globalPrefs.coverFirst.toString())

    fun setScale(scale: String) =
        setMangaField(ReaderField.SCALE, scale, globalPrefs.scale)

    fun setSplitWide(enabled: Boolean) =
        setMangaField(ReaderField.SPLIT, enabled.toString(), globalPrefs.splitWide.toString())

    /** Reset to defaults: clears every override for this manga. */
    fun resetMangaOverrides() {
        viewModelScope.launch { settingsStore.clearOverrides(mangaId) }
    }

    // Global-only fields (webui GLOBALIZED_KEYS: background, tap zones,
    // plus the visual comfort filters).
    fun setTapZones(value: String) {
        viewModelScope.launch { settingsStore.setGlobal(ReaderField.TAP, value) }
    }

    fun setBackground(value: String) {
        viewModelScope.launch { settingsStore.setGlobal(ReaderField.BG, value) }
    }

    /** Slider feedback must be immediate; the DataStore write is debounced
     *  so dragging does not emit hundreds of preference writes. */
    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.5f)
        _state.value = _state.value.copy(brightness = clamped)
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            delay(250)
            settingsStore.setGlobal(ReaderField.BRIGHT, clamped.toString())
        }
    }

    fun setGrayscale(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setGlobal(ReaderField.GRAY, enabled.toString()) }
    }

    fun setSepia(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setGlobal(ReaderField.SEPIA, enabled.toString()) }
    }

    fun toggleOverlay() {
        _state.value = _state.value.copy(overlay = !_state.value.overlay)
    }

    fun openChapterSheet(open: Boolean) {
        _state.value = _state.value.copy(chapterSheet = open)
    }

    fun pageImageUrl(page: Page): String {
        val local = localPages
        if (local != null && page.index.toInt() < local.size) {
            return android.net.Uri.fromFile(local[page.index.toInt()]).toString()
        }
        val profile = _state.value.profile
        val base = profile?.baseUrl?.trimEnd('/').orEmpty()
        return "$base/api/v1/chapter/${_state.value.chapterId}/page/${page.index}?v=${page.cacheRevision}"
    }
}

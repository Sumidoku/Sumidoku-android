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
import kotlin.math.roundToInt
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.model.ChapterSort
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.api.model.ProgressPatch
import org.sumidoku.android.core.api.model.SortOrder
import org.sumidoku.android.core.api.model.Viewer
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.chapterNumberLabel
import org.sumidoku.android.core.common.coverUrl
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.ChapterPrefs
import org.sumidoku.android.core.data.ChapterPrefsStore
import org.sumidoku.android.core.data.IncognitoStore
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
    /** Cover URL used to seed the reader's dynamic colour scheme. */
    val coverUrl: String? = null,
    val pages: List<Page> = emptyList(),
    /** Chapters newest-first in the current sort key (webui navChapters
     *  parity) — drives prev/next transitions and the chapter drawer.
     *  Derived from the shared per-manga chapter prefs (sort + filters);
     *  trashed entries are dropped: the API refuses them (webui behaviour). */
    val chapters: List<ChapterWithDownload> = emptyList(),
    /** Shared per-manga chapter list prefs (sort/filters/asc), same entry
     *  the detail page edits (webui chapterPrefs parity). */
    val chapterPrefs: ChapterPrefs = ChapterPrefs(),
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
    /** Double-tap zoom multiplier (1–8x), global-only like the filters. */
    val doubleTapZoom: Float = 3f,
    /** Text page typeface, size and line-height multiplier (webui
     *  typography.ts parity). Editable per manga through the same override
     *  chain as the viewer fields. */
    val font: String = ReaderField.FONT.fallback,
    val fontSize: Float = 1f,
    val lineHeight: Float = 1.8f,
    /** Prose chapters: paged multi-column layout on wide screens (the
     *  webui TextColumns presentation) instead of one scrolling column. */
    val textColumns: Boolean = true,
    /** Column pager position for a prose chapter, as a spread index plus
     *  the spread count measured on the current viewport. */
    val textSpread: Int = 0,
    val textSpreads: Int = 0,
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    /** manga.viewer metadata driving dir/mode until the manga is overridden. */
    val viewer: Viewer? = null,
    /** Fields carrying a per-manga override (the chain block on screens 24/41). */
    val overridden: Set<ReaderField> = emptySet(),
    /** App-local incognito: progress writes are skipped entirely. */
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
    /** Number-only label (`第 X 话` / `Ch. X`) for the reader chrome subtitle.
     *  Empty when the chapter has no numbers — the UI hides it instead of
     *  showing an "untitled chapter" placeholder (detail-page parity). */
    val chapterNumberLabel: String
        get() = currentChapter?.let { chapterNumberLabel(it.volumeNumber, it.chapterNumber) } ?: ""
    /** Next in reading order = the newer chapter. The list is newest-first
     *  (webui `nextCh = navChapters[pos - 1]` parity), so that is index - 1. */
    val nextChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it > 0 }
            ?.let { chapters.getOrNull(it - 1) }
    /** Previous in reading order = the older chapter (index + 1). */
    val prevChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }
            ?.let { chapters.getOrNull(it + 1) }
    /** Vertical continuous rendering (webtoon / continuous). */
    val vertical: Boolean get() = ReadingMode.isVertical(mode)
    /** Text chapter (EPUB book or local text file): every page carries
     *  text, so the reader leaves the image pager behind and reads the
     *  chapter as continuous prose. */
    val textChapter: Boolean
        get() = pages.isNotEmpty() && pages.all { it.pageText() != null }
    /** Prose chapter that arrives as a single server page — the shape the
     *  web client paginates by column rather than by page. */
    val pagedText: Boolean get() = textChapter && pages.size == 1
    /** Reading progress in percent; text chapters have no meaningful page
     *  count, so the chrome reports percent instead of "3 / 40". When the
     *  column pager owns the screen its measured track wins, because one
     *  server page says nothing about how much of the book is left. */
    val textProgress: Int
        get() = when {
            textSpreads > 0 -> (((textSpread + 1) * 100) / textSpreads).coerceIn(1, 100)
            pages.isEmpty() -> 0
            else -> (((index + 1) * 100) / pages.size).coerceIn(1, 100)
        }
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
    private val incognitoStore: IncognitoStore,
    private val settingsStore: ReaderSettingsStore,
    private val chapterPrefsStore: ChapterPrefsStore,
) : ViewModel() {

    val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val initialChapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state

    private var progressJob: Job? = null
    private var brightnessJob: Job? = null
    private var doubleTapZoomJob: Job? = null
    /** Debounced write for the typography sliders (size / line-height). */
    private var typographyJob: Job? = null
    /** Tier B: extracted local page files for the open chapter, when a
     *  finished on-device copy exists — the reader serves these instead
     *  of network URLs, which is what makes offline reading work. */
    private var localPages: List<java.io.File>? = null

    // ---- preference layers (global default + per-manga overrides) ----

    private var globalRaw: Map<ReaderField, String> = emptyMap()
    private var overrideRaw: Map<ReaderField, String> = emptyMap()
    private var viewer: Viewer? = null

    private val globalPrefs: ReaderPrefs get() = ReaderPrefs.from(globalRaw)

    /** Shared per-manga chapter list prefs (sort/filters/asc). */
    private var chapterPrefs = ChapterPrefs()
    /** Raw API chapter list (source_order ASC = newest-first); navChapters()
     *  derives the navigation/drawer list from it. */
    private var rawChapters: List<ChapterWithDownload> = emptyList()

    /** Navigation list (webui navChapters parity): the shared prefs' sort
     *  key + filters applied, always descending in that key (asc only
     *  affects the detail page's display order). Trashed chapters are
     *  unreadable (the API refuses them), so they are dropped. */
    private fun navChapters(): List<ChapterWithDownload> =
        chapterPrefs.copy(asc = false).applyTo(rawChapters).filter { !it.trashed }

    init {
        // Preference layers stream straight from DataStore so an edit in
        // the quick panel (or another surface) re-resolves immediately.
        viewModelScope.launchGuarded {
            settingsStore.global.collect { raw ->
                globalRaw = raw
                refreshPreferences()
            }
        }
        // App-local incognito streams straight from DataStore, so toggling
        // the switch mid-reading takes effect immediately (and there is no
        // server round-trip at chapter open).
        viewModelScope.launchGuarded {
            incognitoStore.enabled.collect { enabled ->
                _state.value = _state.value.copy(incognito = enabled)
            }
        }
        viewModelScope.launchGuarded {
            settingsStore.overrides(mangaId).collect { raw ->
                overrideRaw = raw
                refreshPreferences()
            }
        }
        viewModelScope.launchGuarded {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = ReaderUiState(loading = false, error = appContext.getString(R.string.reader_no_server))
                return@launchGuarded
            }
            _state.value = _state.value.copy(profile = profile)
            // Shared per-manga chapter prefs (webui chapterPrefs parity):
            // the drawer/navigation honour the detail page's sort + filters.
            chapterPrefs = chapterPrefsStore.load(mangaId) ?: ChapterPrefs()
            _state.value = _state.value.copy(chapterPrefs = chapterPrefs)
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
            doubleTapZoom = merged.doubleTapZoom,
            font = merged.font,
            fontSize = merged.fontSize,
            lineHeight = merged.lineHeight,
            textColumns = merged.textColumns,
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
            _state.value = _state.value.copy(
                coverUrl = coverUrl(profile.baseUrl, mangaId, manga.cover),
            )
            refreshPreferences()
        } catch (_: Exception) {
            // Metadata is advisory: a failure leaves the global default.
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
            rawChapters = chapters
            _state.value = _state.value.copy(chapters = navChapters(), chaptersError = null)
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
        viewModelScope.launchGuarded { loadChapters(profile) }
    }

    /** Flip the shared chapter-list direction and persist it (webui
     *  toggleAsc parity): the navigation list is always descending in the
     *  sort key, so only the persisted preference (and the detail page's
     *  display order) changes. */
    fun toggleChapterAsc() {
        val next = chapterPrefs.copy(asc = !chapterPrefs.asc)
        chapterPrefs = next
        _state.value = _state.value.copy(chapterPrefs = next)
        viewModelScope.launchGuarded { chapterPrefsStore.save(mangaId, next) }
    }

    private var chapterJob: Job? = null

    fun openChapter(chapterId: Long) {
        // Cancel the in-flight chapter load: jumping chapters quickly must
        // not let the stale response overwrite the newer chapter's pages.
        chapterJob?.cancel()
        chapterJob = viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
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
                    textSpread = 0,
                    textSpreads = 0,
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
    private fun writeProgress(index: Int, finishedOverride: Boolean? = null) {
        if (_state.value.incognito) return
        progressJob?.cancel()
        progressJob = viewModelScope.launchGuarded {
            delay(700)
            val cur = _state.value
            if (cur.incognito) return@launchGuarded
            val profile = cur.profile ?: return@launchGuarded
            // A prose chapter is a single server page, so "page 1 of 1" is
            // not the end of the book: only the reader's own end-of-text
            // signal may finish it, or opening an EPUB would mark it read.
            val finished = finishedOverride ?: if (cur.textChapter) {
                cur.textSpreads > 0 && cur.textSpread >= cur.textSpreads - 1
            } else {
                index >= cur.pages.size - 1
            }
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
        viewModelScope.launchGuarded {
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
            viewModelScope.launchGuarded { settingsStore.setOverride(mangaId, ReaderField.DIR, null, keep = false) }
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
        viewModelScope.launchGuarded { settingsStore.clearOverrides(mangaId) }
    }

    // Global-only fields (webui GLOBALIZED_KEYS: background, tap zones,
    // plus the visual comfort filters).
    fun setTapZones(value: String) {
        viewModelScope.launchGuarded { settingsStore.setGlobal(ReaderField.TAP, value) }
    }

    fun setBackground(value: String) {
        viewModelScope.launchGuarded { settingsStore.setGlobal(ReaderField.BG, value) }
    }

    /** Slider feedback must be immediate; the DataStore write is debounced
     *  so dragging does not emit hundreds of preference writes. */
    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.5f)
        _state.value = _state.value.copy(brightness = clamped)
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launchGuarded {
            delay(250)
            settingsStore.setGlobal(ReaderField.BRIGHT, clamped.toString())
        }
    }

    /** KMReader parity: double-tap zoom multiplier, 1–8x in 0.5 steps. Same
     *  immediate-state + debounced-write pattern as the brightness slider. */
    fun setDoubleTapZoom(value: Float) {
        val snapped = ((value * 2f).roundToInt() / 2f).coerceIn(1f, 8f)
        _state.value = _state.value.copy(doubleTapZoom = snapped)
        doubleTapZoomJob?.cancel()
        doubleTapZoomJob = viewModelScope.launchGuarded {
            delay(250)
            settingsStore.setGlobal(ReaderField.DTZOOM, snapped.toString())
        }
    }

    /** Typography rides the same per-manga override chain as the viewer
     *  fields: a change made while reading pins the value to this manga, and
     *  an exact match with the global default drops the override again.
     *  Typeface chips write immediately; the two sliders debounce like
     *  brightness so a drag does not emit hundreds of writes. */
    fun setFont(value: String) {
        val font = value.takeIf { it in ReaderPrefs.FONTS } ?: return
        _state.value = _state.value.copy(font = font)
        setMangaField(ReaderField.FONT, font, globalPrefs.font)
    }

    fun setFontSize(value: Float) {
        val snapped = ((value * 20f).roundToInt() / 20f).coerceIn(ReaderPrefs.FONT_SIZE_RANGE)
        _state.value = _state.value.copy(fontSize = snapped)
        typographyJob?.cancel()
        typographyJob = viewModelScope.launchGuarded {
            delay(250)
            settingsStore.setOverride(
                mangaId,
                ReaderField.FSIZE,
                snapped.toString(),
                keep = snapped != globalPrefs.fontSize,
            )
        }
    }

    fun setLineHeight(value: Float) {
        val snapped = ((value * 10f).roundToInt() / 10f).coerceIn(ReaderPrefs.LINE_HEIGHT_RANGE)
        _state.value = _state.value.copy(lineHeight = snapped)
        typographyJob?.cancel()
        typographyJob = viewModelScope.launchGuarded {
            delay(250)
            settingsStore.setOverride(
                mangaId,
                ReaderField.FLINE,
                snapped.toString(),
                keep = snapped != globalPrefs.lineHeight,
            )
        }
    }

    /** Text page column switch, per-manga like the typography it refines. */
    fun setTextColumns(enabled: Boolean) =
        setMangaField(ReaderField.TCOLS, enabled.toString(), globalPrefs.textColumns.toString())

    /** Column pager position for a prose chapter. Reaching the last spread
     *  is what marks the chapter read, since it never pages out. */
    fun setTextSpread(position: Int, total: Int) {
        if (total <= 0) return
        val clamped = position.coerceIn(0, total - 1)
        val s = _state.value
        if (s.textSpread == clamped && s.textSpreads == total) return
        _state.value = s.copy(textSpread = clamped, textSpreads = total)
        if (clamped >= total - 1) {
            writeProgress(s.pages.lastIndex.coerceAtLeast(0), finishedOverride = true)
        }
    }

    /** The scrolling prose column reached its end: the only completion signal
     *  a text chapter can offer when it is not paginated by column. */
    fun markTextChapterRead() {
        val s = _state.value
        if (!s.textChapter) return
        writeProgress(s.pages.lastIndex.coerceAtLeast(0), finishedOverride = true)
    }

    fun setGrayscale(enabled: Boolean) {
        viewModelScope.launchGuarded { settingsStore.setGlobal(ReaderField.GRAY, enabled.toString()) }
    }

    fun setSepia(enabled: Boolean) {
        viewModelScope.launchGuarded { settingsStore.setGlobal(ReaderField.SEPIA, enabled.toString()) }
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


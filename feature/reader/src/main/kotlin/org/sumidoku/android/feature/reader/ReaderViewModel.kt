package org.sumidoku.android.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.model.ChapterSort
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.api.model.ProgressPatch
import org.sumidoku.android.core.api.model.SortOrder
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class ReaderUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val pages: List<Page> = emptyList(),
    /** Chapters in source order (ascending) — drives prev/next transitions. */
    val chapters: List<ChapterWithDownload> = emptyList(),
    val chapterId: Long = 0,
    val index: Int = 0,
    val webtoon: Boolean = false,
    val overlay: Boolean = false,
    /** Reading direction: "ltr" | "rtl" — mirrors tap zones and the
     *  pager layout direction (design §12). */
    val dir: String = "ltr",
    /** Paged mode only: split landscape pages into halves read in
     *  direction order; progress stays in real page units. */
    val splitWide: Boolean = false,
    /** Paged mode only: 0 = off, 1 = double, 2 = double without cover
     *  (design §12: landscape pages always get their own spread;
     *  mutually exclusive with split-wide). */
    val doublePage: Int = 0,
    val error: String? = null,
) {
    val currentChapter: ChapterWithDownload? get() = chapters.firstOrNull { it.id == chapterId }
    val chapterTitle: String
        get() = currentChapter?.let { ch ->
            buildString {
                ch.chapterNumber?.let { n ->
                    append("第 ")
                    append(if (n % 1f == 0f) n.toInt().toString() else n.toString())
                    append(" 话")
                }
                ch.title?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
            }.ifEmpty { "章节" }
        } ?: "章节"
    val nextChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }
            ?.let { chapters.getOrNull(it + 1) }
    val prevChapter: ChapterWithDownload?
        get() = chapters.indexOfFirst { it.id == chapterId }.takeIf { it > 0 }
            ?.let { chapters.getOrNull(it - 1) }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val localDownloads: org.sumidoku.android.core.download.LocalDownloadEngine,
) : ViewModel() {

    val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val initialChapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state

    private var progressJob: Job? = null
    /** Tier B: extracted local page files for the open chapter, when a
     *  finished on-device copy exists — the reader serves these instead
     *  of network URLs, which is what makes offline reading work. */
    private var localPages: List<java.io.File>? = null

    init {
        viewModelScope.launch {
            val profile = repository.activeProfile.first()
            if (profile == null) {
                _state.value = ReaderUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            _state.value = _state.value.copy(profile = profile)
            loadChapters(profile)
            openChapter(initialChapterId)
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
            _state.value = _state.value.copy(chapters = chapters)
        } catch (_: Exception) {
        }
    }

    fun openChapter(chapterId: Long) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null, chapterId = chapterId)
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
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun setIndex(index: Int) {
        val s = _state.value
        val clamped = index.coerceIn(0, (s.pages.size - 1).coerceAtLeast(0))
        if (clamped == s.index) return
        _state.value = s.copy(index = clamped)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            // Debounced progress write — mirrors the webui reader.
            delay(700)
            val cur = _state.value
            val profile = cur.profile ?: return@launch
            try {
                val api = clientFactory.create<MangaApi>(profile)
                api.updateChapterProgress(
                    cur.chapterId,
                    ProgressPatch(
                        lastPageRead = clamped.toLong(),
                        isRead = if (clamped >= cur.pages.size - 1) true else null,
                    ),
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    fun setWebtoon(webtoon: Boolean) {
        _state.value = _state.value.copy(webtoon = webtoon)
    }

    fun setDir(dir: String) {
        _state.value = _state.value.copy(dir = dir)
    }

    fun setSplitWide(splitWide: Boolean) {
        _state.value = _state.value.copy(splitWide = splitWide)
    }

    fun setDoublePage(doublePage: Int) {
        _state.value = _state.value.copy(doublePage = doublePage)
    }

    fun toggleOverlay() {
        _state.value = _state.value.copy(overlay = !_state.value.overlay)
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

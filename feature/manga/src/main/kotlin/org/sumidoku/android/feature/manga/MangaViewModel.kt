package org.sumidoku.android.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.DownloadsApi
import org.sumidoku.android.core.api.MetadataApi
import org.sumidoku.android.core.api.TrackersApi
import org.sumidoku.android.core.api.model.AddToLibraryRequest
import org.sumidoku.android.core.api.model.ChapterSort
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.ContentRating
import org.sumidoku.android.core.api.model.EnqueueDownloadsRequest
import org.sumidoku.android.core.api.model.Manga
import org.sumidoku.android.core.api.model.MangaStatus
import org.sumidoku.android.core.api.model.MetadataApplyRequest
import org.sumidoku.android.core.api.model.MetadataField
import org.sumidoku.android.core.api.model.MetadataPreview
import org.sumidoku.android.core.api.model.MetadataRestoreRequest
import org.sumidoku.android.core.api.model.SelectCoverRequest
import org.sumidoku.android.core.api.model.SortOrder
import org.sumidoku.android.core.api.model.TrackSearchItem
import org.sumidoku.android.core.api.model.TrackerLink
import org.sumidoku.android.core.api.model.TrackerLinkUpsert
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.api.model.TrackerSyncRequest
import org.sumidoku.android.core.api.model.UpdateMangaRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.download.LocalDownloadEngine
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await

data class MangaUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val manga: Manga? = null,
    val chapters: List<ChapterWithDownload> = emptyList(),
    /** Tier B local offline status per chapter: queued/downloading/done/error. */
    val localStatus: Map<Long, String> = emptyMap(),
    val error: String? = null,
) {
    /** First unread chapter, else the latest one — the "继续阅读" target. */
    val resumeChapter: ChapterWithDownload?
        get() = chapters.firstOrNull { !it.isRead } ?: chapters.lastOrNull()
}

@HiltViewModel(assistedFactory = MangaViewModel.Factory::class)
class MangaViewModel @AssistedInject constructor(
    @Assisted val mangaId: Long,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
    private val localDownloads: LocalDownloadEngine,
) : ViewModel() {

    /** Assisted factory so the screen works both inside a nav route and
     *  inside the expanded-width list-detail pane (design §17). */
    @AssistedFactory
    interface Factory {
        fun create(mangaId: Long): MangaViewModel
    }

    private val _state = MutableStateFlow(MangaUiState())
    val state: StateFlow<MangaUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            localDownloads.init()
            localDownloads.observeForManga(mangaId).collect { rows ->
                _state.value = _state.value.copy(
                    localStatus = rows.associate { it.chapterId to it.status },
                )
            }
        }
    }

    /** Tier B enqueue: download the chapter's CBZ to device storage for
     *  offline reading (design §6 Tier B). */
    fun enqueueLocal(chapterId: Long) {
        val profile = _state.value.profile ?: return
        val manga = _state.value.manga ?: return
        val chapter = _state.value.chapters.firstOrNull { it.id == chapterId } ?: return
        val label = chapter.title?.takeIf { it.isNotBlank() }
            ?: chapter.chapterNumber?.let { "第 $it 话" }
            ?: "第 ${chapterId} 章"
        viewModelScope.launch {
            localDownloads.enqueue(profile, chapterId, mangaId, manga.title, label)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = MangaUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val manga = api.getManga(mangaId).await()
                val chapters = api.getMangaChapters(
                    mangaId,
                    sort = ChapterSort.CHAPTER,
                    order = SortOrder.DESC,
                ).await()
                _state.value = MangaUiState(
                    loading = false,
                    profile = profile,
                    manga = manga,
                    chapters = chapters,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, profile = profile, error = e.message ?: e.toString())
            }
        }
    }

    fun toggleLibrary() {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                if (manga.inLibrary) {
                    api.removeFromLibrary(mangaId).await()
                    _state.value = _state.value.copy(manga = manga.copy(inLibrary = false))
                } else {
                    val updated = api.addToLibrary(AddToLibraryRequest(mangaId = mangaId)).await()
                    _state.value = _state.value.copy(manga = updated)
                }
            } catch (_: Exception) {
                // 保持原状，下一次 refresh 纠偏
            }
        }
    }

    fun toggleIncognito() {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val updated = api.updateManga(mangaId, UpdateMangaRequest(incognito = !manga.incognito)).await()
                _state.value = _state.value.copy(manga = updated)
            } catch (_: Exception) {
            }
        }
    }

    /** M2: whole-manga read/unread via the batch progress endpoint. */
    fun markAllChapters(read: Boolean) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val api = clientFactory.create<MangaApi>(profile)
                api.batchUpdateChapters(
                    org.sumidoku.android.core.api.model.BatchProgressRequest(
                        isRead = read,
                        mangaIds = listOf(mangaId),
                    ),
                ).await()
                refresh()
            } catch (_: Exception) {
            }
        }
    }

    /** M2/M7: categories editor data and save. */
    fun loadCategories() {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val categories = clientFactory.create<org.sumidoku.android.core.api.CategoriesApi>(profile)
                    .listCategories()
                    .await()
                _meta.value = _meta.value.copy(categories = categories)
            } catch (_: Exception) {
            }
        }
    }

    fun saveCategories(ids: Set<Long>) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val updated = clientFactory.create<org.sumidoku.android.core.api.CategoriesApi>(profile)
                    .setMangaCategories(
                        mangaId,
                        org.sumidoku.android.core.api.model.SetCategoriesRequest(categoryIds = ids.toList()),
                    )
                    .await()
                _state.value = _state.value.copy(manga = updated)
            } catch (_: Exception) {
            }
        }
    }

    /** Tier A enqueue: server downloads the chapter — POST /downloads.
     *  The row's cloud icon flips to the downloaded state on next refresh. */
    fun enqueueChapter(chapterId: Long) {
        viewModelScope.launch {
            val profile = _state.value.profile ?: return@launch
            try {
                val api = clientFactory.create<DownloadsApi>(profile)
                api.enqueueDownloads(EnqueueDownloadsRequest(chapterIds = listOf(chapterId))).await()
            } catch (_: Exception) {
            }
        }
    }

    // --- M6: metadata editing / matching / cover / tracker links ---

    private val _meta = MutableStateFlow(MetaUiState())
    val meta: StateFlow<MetaUiState> = _meta

    fun consumeMessage() {
        _meta.value = _meta.value.copy(message = null, error = null)
    }

    private fun fail(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        _meta.value = _meta.value.copy(
            matchLoading = false, applyBusy = false, coverBusy = false,
            editBusy = false, linkBusy = false,
            error = e.message ?: e.toString(),
        )
    }

    /** Manual metadata edit; every touched field becomes a manual lock
     *  (server-side semantics), unlock carries fields the user released. */
    fun saveMetadata(
        title: String?, authors: List<String>?, artists: List<String>?,
        description: String?, tags: List<String>?,
        status: MangaStatus?, contentRating: ContentRating?,
        unlock: List<MetadataField>,
    ) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(editBusy = true)
            try {
                val api = clientFactory.create<MangaApi>(profile)
                val updated = api.updateManga(
                    mangaId,
                    UpdateMangaRequest(
                        title = title, authors = authors, artists = artists,
                        description = description, tags = tags,
                        status = status, contentRating = contentRating,
                        metadataUnlock = unlock.ifEmpty { null },
                    ),
                ).await()
                _state.value = _state.value.copy(manga = updated)
                _meta.value = _meta.value.copy(editBusy = false, message = "已保存")
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Restore source metadata and drop locks (webui "恢复图源元数据"). */
    fun restoreMetadata() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(editBusy = true)
            try {
                val result = clientFactory.create<MetadataApi>(profile)
                    .restoreMangaMetadata(mangaId, MetadataRestoreRequest(fields = null, refresh = null))
                    .await()
                _state.value = _state.value.copy(manga = result.manga)
                _meta.value = _meta.value.copy(
                    editBusy = false,
                    message = "已恢复图源元数据，解锁 ${result.unlockedFields.size} 个字段",
                )
            } catch (e: Exception) { fail(e) }
        }
    }

    fun loadAlternates() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(coverBusy = true)
            try {
                val urls = clientFactory.create<MangaApi>(profile)
                    .getAlternateCovers(mangaId).await()
                    .map { it.toString() }
                _meta.value = _meta.value.copy(coverBusy = false, alternates = urls)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun selectCover(url: String) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(coverBusy = true)
            try {
                val updated = clientFactory.create<MangaApi>(profile)
                    .selectMangaCover(mangaId, SelectCoverRequest(url = java.net.URI(url)))
                    .await()
                _state.value = _state.value.copy(manga = updated)
                _meta.value = _meta.value.copy(coverBusy = false, message = "已更换封面")
            } catch (e: Exception) { fail(e) }
        }
    }

    // --- metadata match (preview → apply) ---

    fun setMatchProvider(provider: TrackerProvider) {
        _meta.value = _meta.value.copy(
            matchProvider = provider, matchResults = null, preview = null,
        )
    }

    fun setMatchQuery(query: String) {
        _meta.value = _meta.value.copy(matchQuery = query)
    }

    fun matchSearch() {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val query = meta.matchQuery.ifBlank { _state.value.manga?.title ?: return }
        viewModelScope.launch {
            _meta.value = _meta.value.copy(matchLoading = true, preview = null)
            try {
                val results = clientFactory.create<TrackersApi>(profile)
                    .trackerSearch(meta.matchProvider, query)
                    .await()
                _meta.value = _meta.value.copy(matchLoading = false, matchResults = results)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun loadPreview(item: TrackSearchItem) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(matchLoading = true)
            try {
                val preview = clientFactory.create<MetadataApi>(profile)
                    .previewMangaMetadata(mangaId, _meta.value.matchProvider.value, item.id)
                    .await()
                val selected = preview.fields
                    .filter { it.selectable && it.incoming != null }
                    .map { it.`field` }
                    .toSet()
                _meta.value = _meta.value.copy(
                    matchLoading = false, preview = preview, previewSelected = selected,
                )
            } catch (e: Exception) { fail(e) }
        }
    }

    fun togglePreviewField(field: MetadataField) {
        val current = _meta.value.previewSelected
        _meta.value = _meta.value.copy(
            previewSelected = if (field in current) current - field else current + field,
        )
    }

    fun backToMatchResults() {
        _meta.value = _meta.value.copy(preview = null)
    }

    /** Apply selected fields; bind also creates/updates the tracker link
     *  (server-side upsert semantics from the webui match dialog). */
    fun applyMatch(bind: Boolean) {
        val profile = _state.value.profile ?: return
        val meta = _meta.value
        val preview = meta.preview ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(applyBusy = true)
            try {
                val result = clientFactory.create<MetadataApi>(profile)
                    .applyMangaMetadata(
                        mangaId,
                        MetadataApplyRequest(
                            provider = meta.matchProvider.value,
                            remoteId = preview.candidate.remoteId,
                            fields = meta.previewSelected.toList(),
                            propertyValues = null,
                            bind = bind,
                        ),
                    )
                    .await()
                _state.value = _state.value.copy(manga = result.manga)
                _meta.value = _meta.value.copy(
                    applyBusy = false, preview = null, matchResults = null,
                    message = "已应用 ${result.appliedFields.size} 个字段",
                )
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    // --- tracker links ---

    fun loadLinks() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                val links = clientFactory.create<TrackersApi>(profile)
                    .listTrackerLinks(mangaId)
                    .await()
                _meta.value = _meta.value.copy(linkBusy = false, links = links)
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Bind the manga to a tracker entry (PUT upsert semantics). */
    fun addLink(provider: TrackerProvider, item: TrackSearchItem) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                clientFactory.create<TrackersApi>(profile)
                    .putTrackerLink(
                        mangaId,
                        TrackerLinkUpsert(provider = provider, trackId = item.id, title = item.title),
                    )
                    .await()
                _meta.value = _meta.value.copy(message = "已绑定 ${item.title}")
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun removeLink(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                clientFactory.create<TrackersApi>(profile)
                    .deleteTrackerLink(mangaId, provider)
                    .await()
                _meta.value = _meta.value.copy(message = "已解绑")
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Pull remote progress into local chapters (webui "从追踪器同步进度"). */
    fun syncLink(provider: TrackerProvider) {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            _meta.value = _meta.value.copy(linkBusy = true)
            try {
                val result = clientFactory.create<TrackersApi>(profile)
                    .syncTrackerLink(mangaId, provider, TrackerSyncRequest(preview = false))
                    .await()
                _meta.value = _meta.value.copy(message = "已按远端进度标记 ${result.marked} 章")
                refresh()
                loadLinks()
            } catch (e: Exception) { fail(e) }
        }
    }
}

/** Sub-state for the metadata/tracker sheets (M6). */
data class MetaUiState(
    val alternates: List<String>? = null,
    val coverBusy: Boolean = false,
    val editBusy: Boolean = false,
    val matchProvider: TrackerProvider = TrackerProvider.ANILIST,
    val matchQuery: String = "",
    val matchResults: List<TrackSearchItem>? = null,
    val matchLoading: Boolean = false,
    val preview: MetadataPreview? = null,
    val previewSelected: Set<MetadataField> = emptySet(),
    val applyBusy: Boolean = false,
    val links: List<TrackerLink>? = null,
    val linkBusy: Boolean = false,
    /** Server categories for the categories editor sheet (M2/M7). */
    val categories: List<org.sumidoku.android.core.api.model.Category>? = null,
    val message: String? = null,
    val error: String? = null,
)

package org.sumidoku.android.feature.manga

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.MangaApi
import org.sumidoku.android.core.api.model.AddToLibraryRequest
import org.sumidoku.android.core.api.model.ChapterSort
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Manga
import org.sumidoku.android.core.api.model.SortOrder
import org.sumidoku.android.core.api.model.UpdateMangaRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class MangaUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val manga: Manga? = null,
    val chapters: List<ChapterWithDownload> = emptyList(),
    val error: String? = null,
) {
    /** First unread chapter, else the latest one — the "继续阅读" target. */
    val resumeChapter: ChapterWithDownload?
        get() = chapters.firstOrNull { !it.isRead } ?: chapters.lastOrNull()
}

@HiltViewModel
class MangaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])

    private val _state = MutableStateFlow(MangaUiState())
    val state: StateFlow<MangaUiState> = _state

    init {
        refresh()
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
}

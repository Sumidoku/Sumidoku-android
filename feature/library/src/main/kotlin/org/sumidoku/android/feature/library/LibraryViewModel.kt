package org.sumidoku.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.CategoriesApi
import org.sumidoku.android.core.api.FilterGroupsApi
import org.sumidoku.android.core.api.LibraryApi
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.FilterGroup
import org.sumidoku.android.core.api.model.LibraryEntry
import org.sumidoku.android.core.api.model.LibrarySort
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val profile: ServerProfile? = null,
    val entries: List<LibraryEntry> = emptyList(),
    val hasNextPage: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val filterGroups: List<FilterGroup> = emptyList(),
    val categoryId: Long? = null,
    val groupId: Long? = null,
    val sort: LibrarySort = LibrarySort.LATEST_CHAPTER,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private var page = 1
    private var loadJob: Job? = null

    init {
        refresh()
        loadFilters()
    }

    fun refresh() {
        page = 1
        loadPage(replace = true, refreshing = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasNextPage || s.error != null) return
        loadPage(replace = false, refreshing = false)
    }

    fun selectCategory(id: Long?) {
        if (_state.value.categoryId == id && _state.value.groupId == null) return
        _state.update { it.copy(categoryId = id, groupId = null) }
        refresh()
    }

    fun selectGroup(id: Long?) {
        if (_state.value.groupId == id) return
        _state.update { it.copy(groupId = id, categoryId = null) }
        refresh()
    }

    fun setSort(sort: LibrarySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    private fun loadPage(replace: Boolean, refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = replace && it.entries.isEmpty(),
                    refreshing = refreshing,
                    loadingMore = !replace,
                    error = null,
                )
            }
            val profile = _state.value.profile ?: repository.activeProfile.first()
            if (profile == null) {
                _state.update { it.copy(loading = false, refreshing = false, error = "未选择服务器") }
                return@launch
            }
            try {
                val api = clientFactory.create<LibraryApi>(profile)
                val s = _state.value
                val result = api.listLibrary(
                    categoryId = s.categoryId,
                    groupId = s.groupId,
                    sort = s.sort,
                    page = page,
                ).await()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        profile = profile,
                        entries = if (replace) result.propertyEntries else it.entries + result.propertyEntries,
                        hasNextPage = result.hasNextPage,
                    )
                }
                page += 1
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val profile = repository.activeProfile.first() ?: return@launch
            runCatching {
                val categories = clientFactory.create<CategoriesApi>(profile).listCategories().await()
                val groups = clientFactory.create<FilterGroupsApi>(profile).listFilterGroups().await()
                _state.update { it.copy(categories = categories, filterGroups = groups) }
            }
        }
    }
}

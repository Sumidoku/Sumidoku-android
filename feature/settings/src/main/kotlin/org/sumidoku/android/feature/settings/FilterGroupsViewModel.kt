package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.CategoriesApi
import org.sumidoku.android.core.api.FilterGroupsApi
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.Category
import org.sumidoku.android.core.api.model.CreateFilterGroupRequest
import org.sumidoku.android.core.api.model.FilterGroup
import org.sumidoku.android.core.api.model.FilterRuleInput
import org.sumidoku.android.core.api.model.FilterRuleType
import org.sumidoku.android.core.api.model.ReorderRequest
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.UpdateFilterGroupRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await

data class FilterGroupsUiState(
    val loading: Boolean = true,
    val groups: List<FilterGroup> = emptyList(),
    val sources: List<Source> = emptyList(),
    val categories: List<Category> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Library filter-group management (design §14 M7): create, rename,
 *  delete, reorder, and a rule editor per group. Rules carry a type,
 *  an optional free-form value (flag rules have none; source /
 *  content-rating / category take an integer; author / tag a string)
 *  and an exclude flag. */
@HiltViewModel
class FilterGroupsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerProfileRepository,
    private val factory: SumidokuClientFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(FilterGroupsUiState())
    val state: StateFlow<FilterGroupsUiState> = _state.asStateFlow()

    private suspend fun profile(): ServerProfile? = repository.activeProfile.first()

    init {
        refresh()
        viewModelScope.launch {
            val p = profile() ?: return@launch
            runCatching { factory.create<SourcesApi>(p).listSources().await() }
                .onSuccess { _state.value = _state.value.copy(sources = it) }
            runCatching { factory.create<CategoriesApi>(p).listCategories().await() }
                .onSuccess { _state.value = _state.value.copy(categories = it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val p = profile() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val groups = factory.create<FilterGroupsApi>(p).listFilterGroups().await()
                _state.value = _state.value.copy(loading = false, groups = groups)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    private fun mutate(message: String, block: suspend (FilterGroupsApi) -> Unit) {
        viewModelScope.launch {
            val p = profile() ?: return@launch
            _state.value = _state.value.copy(busy = true)
            try {
                block(factory.create(p))
                _state.value = _state.value.copy(busy = false, message = message)
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(busy = false, error = e.message ?: e.toString())
            }
        }
    }

    fun create(name: String) = mutate(appContext.getString(R.string.filter_groups_created)) { api ->
        api.createFilterGroup(CreateFilterGroupRequest(name = name, rules = emptyList())).await()
    }

    fun rename(group: FilterGroup, name: String) = mutate(appContext.getString(R.string.filter_groups_renamed)) { api ->
        api.updateFilterGroup(group.id, UpdateFilterGroupRequest(name = name)).await()
    }

    fun delete(group: FilterGroup) = mutate(appContext.getString(R.string.filter_groups_deleted)) { api ->
        api.deleteFilterGroup(group.id).await()
    }

    fun saveRules(group: FilterGroup, rules: List<FilterRuleInput>) = mutate(appContext.getString(R.string.filter_groups_rules_saved)) { api ->
        api.updateFilterGroup(group.id, UpdateFilterGroupRequest(rules = rules)).await()
    }

    fun move(group: FilterGroup, delta: Int) {
        val groups = _state.value.groups.toMutableList()
        val from = groups.indexOfFirst { it.id == group.id }
        val to = from + delta
        if (from < 0 || to !in groups.indices) return
        val item = groups.removeAt(from)
        groups.add(to, item)
        mutate(appContext.getString(R.string.filter_groups_order_saved)) { api ->
            api.reorderFilterGroups(ReorderRequest(ids = groups.map { it.id })).await()
        }
    }

    fun consumeTransient() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}

/** Stable draft of one rule while the sheet editor is open. */
data class RuleDraft(
    val type: FilterRuleType,
    val valueText: String,
    val exclude: Boolean,
)

fun RuleDraft.toInput(): FilterRuleInput = FilterRuleInput(
    type = type,
    value = when {
        valueText.isBlank() -> null
        type == FilterRuleType.AUTHOR || type == FilterRuleType.TAG ->
            kotlinx.serialization.json.JsonPrimitive(valueText)
        else -> valueText.toLongOrNull()?.let { kotlinx.serialization.json.JsonPrimitive(it) }
    },
    exclude = exclude,
)

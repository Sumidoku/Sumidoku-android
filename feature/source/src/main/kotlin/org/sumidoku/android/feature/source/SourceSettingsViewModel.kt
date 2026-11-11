package org.sumidoku.android.feature.source

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceSetting
import org.sumidoku.android.core.api.model.SourceSettings
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

/** Schema-driven source settings editor — declarative setting tree rendered
 *  natively (Aidoku model). Values live in SourceSettings.propertyValues
 *  (key → JsonElement); PUT sends the whole map back.
 *  Preview 13-source-settings. */
data class SourceSettingsUiState(
    val loading: Boolean = true,
    val profile: ServerProfile? = null,
    val source: Source? = null,
    val settings: SourceSettings? = null,
    val values: Map<String, JsonElement> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    val sourceId: Long = checkNotNull(savedStateHandle["sourceId"])

    private val _state = MutableStateFlow(SourceSettingsUiState())
    val state: StateFlow<SourceSettingsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, saved = false)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = SourceSettingsUiState(loading = false, error = "未选择服务器")
                return@launch
            }
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                val source = api.listSources().await().firstOrNull { it.id == sourceId }
                val settings = api.getSourceSettings(sourceId).await()
                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    source = source,
                    settings = settings,
                    values = settings.propertyValues,
                    dirty = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: e.toString())
            }
        }
    }

    fun updateValue(key: String, value: JsonElement) {
        _state.value = _state.value.copy(
            values = _state.value.values + (key to value),
            dirty = true,
            saved = false,
        )
    }

    fun save() {
        val current = _state.value
        val profile = current.profile ?: return
        viewModelScope.launch {
            _state.value = current.copy(saving = true)
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                // Map<String, JsonElement> satisfies the Map<String, Any> body;
                // the kotlinx converter writes raw JSON values.
                api.putSourceSettings(sourceId, current.values.mapValues { it.value as Any }).await()
                _state.value = _state.value.copy(saving = false, dirty = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun reset() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                api.resetSourceSettings(sourceId).await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
}

/** Visibility rule: `requires` must be truthy, `requires_false` falsy. */
fun SourceSetting.visibleIn(values: Map<String, JsonElement>): Boolean {
    requires?.let { key ->
        if (values[key]?.jsonPrimitive?.booleanOrNull != true) return false
    }
    requiresFalse?.let { key ->
        if (values[key]?.jsonPrimitive?.booleanOrNull == true) return false
    }
    return true
}

fun stringValue(v: JsonElement?): String = v?.jsonPrimitive?.content ?: ""
fun boolValue(v: JsonElement?, fallback: Boolean = false): Boolean =
    v?.jsonPrimitive?.booleanOrNull ?: fallback
fun doubleValue(v: JsonElement?, fallback: Double): Double =
    v?.jsonPrimitive?.content?.toDoubleOrNull() ?: fallback
fun intValue(v: JsonElement?, fallback: Int = 0): Int =
    v?.jsonPrimitive?.content?.toIntOrNull() ?: fallback
fun listValue(v: JsonElement?): List<String> =
    (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

fun jsonString(s: String): JsonElement = JsonPrimitive(s)
fun jsonBool(b: Boolean): JsonElement = JsonPrimitive(b)
fun jsonDouble(d: Double): JsonElement = JsonPrimitive(d)
fun jsonInt(i: Int): JsonElement = JsonPrimitive(i)
fun jsonList(l: List<String>): JsonElement = JsonArray(l.map { JsonPrimitive(it) })

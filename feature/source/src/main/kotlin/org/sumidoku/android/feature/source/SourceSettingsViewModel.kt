package org.sumidoku.android.feature.source

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.sumidoku.android.core.api.SourcesApi
import org.sumidoku.android.core.api.TasksApi
import org.sumidoku.android.core.api.model.BasicLoginRequest
import org.sumidoku.android.core.api.model.NotifyRequest
import org.sumidoku.android.core.api.model.OAuthCompleteRequest
import org.sumidoku.android.core.api.model.OAuthStartRequest
import org.sumidoku.android.core.api.model.ScanLocalSourcesRequest
import org.sumidoku.android.core.api.model.Source
import org.sumidoku.android.core.api.model.SourceLogoutRequest
import org.sumidoku.android.core.api.model.SourceSetting
import org.sumidoku.android.core.api.model.SourceSettings
import org.sumidoku.android.core.api.model.WebLoginRequest
import org.sumidoku.android.core.common.launchGuarded
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
    /** Keys edited since load — only these are sent on save (webui parity). */
    val editedKeys: Set<String> = emptySet(),
    /** One-shot transient status for login/logout/button actions. */
    val actionMessage: String? = null,
    val actionError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
        viewModelScope.launchGuarded {
            _state.value = _state.value.copy(loading = true, error = null, saved = false)
            val profile = repository.activeProfile.first() ?: run {
                _state.value = SourceSettingsUiState(
                    loading = false,
                    error = appContext.getString(R.string.source_no_server),
                )
                return@launchGuarded
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
            editedKeys = _state.value.editedKeys + key,
            dirty = true,
            saved = false,
        )
    }

    fun save() {
        val current = _state.value
        val profile = current.profile ?: return
        viewModelScope.launchGuarded {
            _state.value = current.copy(saving = true)
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                // Only the edited keys go back (webui parity); each changed
                // setting's declared notification is forwarded via ?notify=.
                // The body is a raw JsonObject — the generated Map<String,Any>
                // body type cannot serialize (patched in core:api).
                val touched = flattenSettings(current.settings?.settings.orEmpty())
                    .filter { it.key in current.editedKeys }
                val edits = kotlinx.serialization.json.buildJsonObject {
                    current.editedKeys.forEach { k ->
                        current.values[k]?.let { v -> put(k, v) }
                    }
                }
                val notify = touched.mapNotNull { s -> s.notification?.takeIf { it.isNotEmpty() } }
                api.putSourceSettings(sourceId, edits, notify.ifEmpty { null }).await()
                _state.value = _state.value.copy(
                    saving = false, dirty = false, saved = true, editedKeys = emptySet(),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun reset() {
        val profile = _state.value.profile ?: return
        viewModelScope.launchGuarded {
            try {
                val api = clientFactory.create<SourcesApi>(profile)
                api.resetSourceSettings(sourceId).await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // --- login flows (Aidoku basic/web/oauth) ---

    fun basicLogin(key: String, username: String, password: String) {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<SourcesApi>(profile)
                    .sourceBasicLogin(sourceId, BasicLoginRequest(key, username, password))
                    .await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    fun webLogin(key: String, cookies: Map<String, String>, localStorage: Map<String, String>?) {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<SourcesApi>(profile)
                    .sourceWebLogin(sourceId, WebLoginRequest(key, cookies, localStorage))
                    .await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    /** Build the authorization URL; the UI opens it in a WebView. */
    suspend fun startOauth(key: String): String? {
        val profile = _state.value.profile ?: repository.activeProfile.first() ?: return null
        return try {
            clientFactory.create<SourcesApi>(profile)
                .startSourceOauth(sourceId, OAuthStartRequest(key))
                .await().url.toString()
        } catch (e: Exception) {
            _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            null
        }
    }

    fun completeOauth(key: String, callbackUrl: String) {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<SourcesApi>(profile)
                    .completeSourceOauth(sourceId, OAuthCompleteRequest(key, java.net.URI(callbackUrl)))
                    .await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    fun logout(key: String, clearCookies: Boolean) {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<SourcesApi>(profile)
                    .sourceLogout(sourceId, SourceLogoutRequest(key, clearCookies))
                    .await()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    /** Pressing a Button setting sends its notification to the source. */
    fun pressButton(setting: SourceSetting) {
        val notification = setting.notification?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<SourcesApi>(profile)
                    .notifySource(sourceId, NotifyRequest(notification))
                    .await()
                _state.value = _state.value.copy(
                    actionMessage = appContext.getString(R.string.source_notify_sent),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    /** Local sources only: kick off a filesystem scan (webui LocalSourcePanel
     *  "Scan"). The server queues a task; progress shows on the tasks page. */
    fun scanLocal() {
        viewModelScope.launchGuarded {
            val profile = _state.value.profile ?: return@launchGuarded
            try {
                clientFactory.create<TasksApi>(profile)
                    .scanLocalSources(ScanLocalSourcesRequest(sourceId))
                    .await()
                _state.value = _state.value.copy(
                    actionMessage = appContext.getString(R.string.source_scan_started),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionError = e.message ?: e.toString())
            }
        }
    }

    fun consumeActionMessage() {
        _state.value = _state.value.copy(actionMessage = null, actionError = null)
    }
}

/** Flatten the declarative setting tree (groups and nested pages). */
fun flattenSettings(settings: List<SourceSetting>): List<SourceSetting> {
    val out = ArrayList<SourceSetting>()
    fun walk(list: List<SourceSetting>) {
        for (s in list) {
            out += s
            s.value.group?.items?.let(::walk)
            s.value.page?.items?.let(::walk)
        }
    }
    walk(settings)
    return out
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

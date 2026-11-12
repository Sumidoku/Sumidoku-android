package org.sumidoku.android.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.model.LoginRequest
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import javax.inject.Inject

data class ServersUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeId: Long? = null,
    /** Profile id → probed server version (from GET /api/v1/version). */
    val versions: Map<Long, String> = emptyMap(),
    val addInFlight: Boolean = false,
    val addError: String? = null,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val clientFactory: SumidokuClientFactory,
) : ViewModel() {

    private val versions = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val addInFlight = MutableStateFlow(false)
    private val addError = MutableStateFlow<String?>(null)

    val state: StateFlow<ServersUiState> = combine(
        repository.profiles,
        repository.activeId,
        versions,
        addInFlight,
        addError,
    ) { profiles, activeId, versions, adding, error ->
        ServersUiState(
            profiles = profiles,
            activeId = activeId,
            versions = versions,
            addInFlight = adding,
            addError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServersUiState())

    init {
        viewModelScope.launch {
            repository.profiles.collect { list ->
                for (profile in list) {
                    if (versions.value.containsKey(profile.id)) continue
                    launch {
                        clientFactory.probeVersion(profile)?.let { version ->
                            versions.value = versions.value + (profile.id to version)
                        }
                    }
                }
            }
        }
    }

    /** Adds a server after a successful /api/v1/version probe, so typos and
     *  non-Sumidoku URLs are rejected before they ever become profiles.
     *  Credentials: either a static API key, or username/password which is
     *  exchanged for the rolling 30-day session token (POST /auth/login)
     *  and stored as the bearer credential — the same token the webui
     *  keeps in its cookie (docs/android-client.md §5). */
    fun addServer(
        name: String,
        baseUrl: String,
        credentialMode: String,
        credential: String?,
        username: String,
        password: String,
        mirrorsText: String,
    ) {
        if (addInFlight.value) return
        viewModelScope.launch {
            addInFlight.value = true
            addError.value = null
            val candidate = ServerProfile(
                id = 0,
                name = name.trim().ifBlank { baseUrl.trim().removePrefix("http://").removePrefix("https://") },
                baseUrl = normalizeUrl(baseUrl),
                mirrors = mirrorsText.lines().map { normalizeUrl(it.trim()) }.filter { it.isNotEmpty() },
                apiKey = credential?.trim()?.takeIf { it.isNotEmpty() },
            )
            val probe = clientFactory.probeVersionDetailed(candidate)
            val probedVersion = probe.version
            if (probedVersion == null) {
                addError.value = "无法连接或非 Sumidoku 服务器（/api/v1/version：${probe.error}）"
                addInFlight.value = false
                return@launch
            }
            val storedCredential = if (credentialMode == "password") {
                val token = try {
                    val api = clientFactory.create<org.sumidoku.android.core.api.AuthApi>(
                        candidate.copy(apiKey = null),
                    )
                    api.login(LoginRequest(username.trim(), password)).await().token
                } catch (e: retrofit2.HttpException) {
                    addError.value = when (e.code()) {
                        401, 403 -> "用户名或密码错误"
                        else -> "登录失败（HTTP ${e.code()}）"
                    }
                    addInFlight.value = false
                    return@launch
                } catch (e: Exception) {
                    addError.value = "登录失败：${e.message ?: e.javaClass.simpleName}"
                    addInFlight.value = false
                    return@launch
                }
                token
            } else {
                candidate.apiKey
            }
            val id = repository.add(candidate.copy(apiKey = storedCredential))
            versions.value = versions.value + (id to probedVersion)
            addInFlight.value = false
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActive(id) }
    }

    fun delete(profile: ServerProfile) {
        viewModelScope.launch {
            repository.delete(profile)
            versions.value = versions.value - profile.id
        }
    }

    fun dismissAddError() {
        addError.value = null
    }

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url
    }
}

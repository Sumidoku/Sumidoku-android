package org.sumidoku.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sumidoku.android.core.common.AppVersion
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.UpdateSettingsStore
import org.sumidoku.android.core.network.update.UpdateInfo
import org.sumidoku.android.core.network.update.UpdateRepository
import javax.inject.Inject

/** Everything the About section and the release dialog render. */
data class AppUpdateState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    /** The newer release, once one has been found. */
    val update: UpdateInfo? = null,
    val dialogVisible: Boolean = false,
    /** A completed check that found nothing newer. */
    val upToDate: Boolean = false,
    val error: String? = null,
    /** 0 until the first successful lookup. */
    val lastCheckedAt: Long = 0L,
    val autoCheckEnabled: Boolean = true,
)

/**
 * The update check, shared between the launch hook, the dialog and the About
 * rows.
 *
 * The root of the app owns the one instance and passes it down (KMReader's
 * `AppUpdateViewModel` pattern), so a release announced at launch, one found by
 * "Check now" and one re-opened from the About row are all the same state.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: UpdateSettingsStore,
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppUpdateState(currentVersion = installedVersion(context)),
    )
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launchGuarded {
            val autoCheck = settings.autoCheck.first()
            val lastCheckedAt = settings.lastCheckedAt.first()
            _state.value = _state.value.copy(
                autoCheckEnabled = autoCheck,
                lastCheckedAt = lastCheckedAt,
            )
            if (autoCheck) checkAutomatically()
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(autoCheckEnabled = enabled)
        viewModelScope.launchGuarded {
            settings.setAutoCheck(enabled)
            // Turning the switch back on should report the current release
            // without waiting for the next launch.
            if (enabled) checkAutomatically()
        }
    }

    /** The "Check now" row: bypasses the daily throttle and always announces. */
    fun checkNow() {
        check(manual = true)
    }

    /** Re-opens the dialog for a release already found (About row tap). */
    fun showAvailableUpdate() {
        if (_state.value.update != null) {
            _state.value = _state.value.copy(dialogVisible = true)
        }
    }

    fun dismissDialog() {
        val version = _state.value.update?.version
        _state.value = _state.value.copy(dialogVisible = false)
        if (version != null) {
            viewModelScope.launchGuarded { settings.setDismissedVersion(version) }
        }
    }

    /** Launch check: at most one lookup per [AUTO_CHECK_INTERVAL_MILLIS]. */
    private fun checkAutomatically() {
        val now = System.currentTimeMillis()
        val last = _state.value.lastCheckedAt
        if (last != 0L && now - last < AUTO_CHECK_INTERVAL_MILLIS) return
        check(manual = false)
    }

    private fun check(manual: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launchGuarded {
            _state.value = _state.value.copy(checking = true, error = null, upToDate = false)
            val result = repository.latestRelease()
            result.fold(
                onSuccess = { info ->
                    val newer = AppVersion.isNewer(info.version, _state.value.currentVersion)
                    // A release the user already said "Later" to stays quiet on
                    // launch; a manual check re-opens it because the tap was the
                    // request.
                    val dismissed = settings.dismissedVersion.first()
                    _state.value = _state.value.copy(
                        checking = false,
                        update = if (newer) info else null,
                        upToDate = !newer,
                        dialogVisible = newer && (manual || info.version != dismissed),
                    )
                },
                onFailure = { failure ->
                    // No timestamp: the next launch retries instead of waiting
                    // a day on a flaky connection.
                    _state.value = _state.value.copy(
                        checking = false,
                        error = failure.message ?: failure::class.java.simpleName,
                        // A failed lookup proves nothing about the latest
                        // release, so it must not keep an "up to date" badge.
                        upToDate = false,
                    )
                },
            )
            if (result.isSuccess) recordSuccess()
        }
    }

    private suspend fun recordSuccess() {
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(lastCheckedAt = now)
        settings.setLastCheckedAt(now)
    }

    private fun installedVersion(context: Context): String =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "0.0.0" }

    companion object {
        /** GitHub is polled at most daily — no user-visible knob is worth more. */
        const val AUTO_CHECK_INTERVAL_MILLIS: Long = 24L * 60 * 60 * 1000
    }
}

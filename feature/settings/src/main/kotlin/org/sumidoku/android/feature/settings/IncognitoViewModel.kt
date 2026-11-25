package org.sumidoku.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.sumidoku.android.core.common.launchGuarded
import org.sumidoku.android.core.data.IncognitoStore

data class IncognitoUiState(
    val enabled: Boolean = false,
)

/** The "incognito reading" toggle on the settings home page and the More
 *  tab. App-local by design: the flag lives in [IncognitoStore] on device,
 *  applies instantly with no server round-trip, and is enforced client-side
 *  (the reader simply skips progress writes). Manga detail pages can still
 *  override per manga via the server-side per-title flag. */
@HiltViewModel
class IncognitoViewModel @Inject constructor(
    private val store: IncognitoStore,
) : ViewModel() {

    val state: StateFlow<IncognitoUiState> = store.enabled
        .map { IncognitoUiState(enabled = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncognitoUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launchGuarded { store.setEnabled(enabled) }
    }
}

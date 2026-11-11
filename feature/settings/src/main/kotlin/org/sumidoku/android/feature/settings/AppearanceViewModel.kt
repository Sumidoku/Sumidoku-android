package org.sumidoku.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sumidoku.android.core.data.AppearanceStore
import javax.inject.Inject

/** Appearance preference for the settings hub (design §14 M9). */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val appearanceStore: AppearanceStore,
) : ViewModel() {

    val theme: StateFlow<String> = appearanceStore.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setTheme(theme: String) {
        viewModelScope.launch { appearanceStore.setTheme(theme) }
    }
}

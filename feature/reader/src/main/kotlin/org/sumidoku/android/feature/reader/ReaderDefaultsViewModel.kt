package org.sumidoku.android.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt
import org.sumidoku.android.core.common.launchGuarded

/**
 * Global reader defaults (preview 24-reader-settings.html): the same fields the
 * in-reader sheet writes, but with no manga override chain — a manga's own
 * `Manga.viewer` metadata still wins at read time, so these are the fallback
 * the reader uses when neither an override nor metadata decides.
 */
data class ReaderDefaultsState(
    val loading: Boolean = true,
    val mode: String = ReadingMode.PAGED,
    val dir: String = "default",
    val layout: String = "auto",
    val coverFirst: Boolean = true,
    val scale: String = "fit",
    val tapZones: String = "lr",
    val background: String = "dark",
    val brightness: Float = 1f,
    val doubleTapZoom: Float = 3f,
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
)

@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val store: ReaderSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderDefaultsState())
    val state: StateFlow<ReaderDefaultsState> = _state

    init {
        viewModelScope.launchGuarded {
            val prefs = ReaderPrefs.from(store.global.first())
            _state.value = ReaderDefaultsState(
                loading = false,
                mode = prefs.mode,
                dir = prefs.dir,
                layout = prefs.layout,
                coverFirst = prefs.coverFirst,
                scale = prefs.scale,
                tapZones = prefs.tapZones,
                background = prefs.background,
                brightness = prefs.brightness,
                doubleTapZoom = prefs.doubleTapZoom,
                grayscale = prefs.grayscale,
                sepia = prefs.sepia,
            )
        }
    }

    fun setMode(value: String) = write(ReaderField.MODE, value)
    fun setDir(value: String) = write(ReaderField.DIR, value)
    fun setLayout(value: String) = write(ReaderField.LAYOUT, value)
    fun setScale(value: String) = write(ReaderField.SCALE, value)
    fun setTapZones(value: String) = write(ReaderField.TAP, value)
    fun setBackground(value: String) = write(ReaderField.BG, value)
    fun setBrightness(value: Float) {
        _state.value = _state.value.copy(brightness = value)
        write(ReaderField.BRIGHT, value.toString())
    }
    fun setDoubleTapZoom(value: Float) {
        val snapped = ((value * 2f).roundToInt() / 2f).coerceIn(1f, 8f)
        _state.value = _state.value.copy(doubleTapZoom = snapped)
        write(ReaderField.DTZOOM, snapped.toString())
    }
    fun setGrayscale(value: Boolean) = write(ReaderField.GRAY, value.toString())
    fun setSepia(value: Boolean) = write(ReaderField.SEPIA, value.toString())

    fun setCoverFirst(value: Boolean) {
        _state.value = _state.value.copy(coverFirst = value)
        viewModelScope.launchGuarded { store.setGlobal(ReaderField.COVER, value.toString()) }
    }

    private fun write(field: ReaderField, raw: String) {
        _state.value = _state.value.applied(field, raw)
        viewModelScope.launchGuarded { store.setGlobal(field, raw) }
    }

    fun reset() {
        viewModelScope.launchGuarded {
            ReaderField.entries.forEach { store.setGlobal(it, null) }
            val prefs = ReaderPrefs.from(emptyMap())
            _state.value = ReaderDefaultsState(
                loading = false,
                mode = prefs.mode,
                dir = prefs.dir,
                layout = prefs.layout,
                coverFirst = prefs.coverFirst,
                scale = prefs.scale,
                tapZones = prefs.tapZones,
                background = prefs.background,
                brightness = prefs.brightness,
                grayscale = prefs.grayscale,
                sepia = prefs.sepia,
            )
        }
    }
}

private fun ReaderDefaultsState.applied(field: ReaderField, raw: String): ReaderDefaultsState = when (field) {
    ReaderField.MODE -> copy(mode = raw)
    ReaderField.DIR -> copy(dir = raw)
    ReaderField.LAYOUT -> copy(layout = raw)
    ReaderField.COVER -> copy(coverFirst = raw.toBooleanStrictOrNull() ?: coverFirst)
    ReaderField.SCALE -> copy(scale = raw)
    ReaderField.SPLIT -> this
    ReaderField.TAP -> copy(tapZones = raw)
    ReaderField.BG -> copy(background = raw)
    ReaderField.BRIGHT -> copy(brightness = raw.toFloatOrNull() ?: brightness)
    ReaderField.DTZOOM -> copy(doubleTapZoom = raw.toFloatOrNull() ?: doubleTapZoom)
    ReaderField.GRAY -> copy(grayscale = raw.toBooleanStrictOrNull() ?: grayscale)
    ReaderField.SEPIA -> copy(sepia = raw.toBooleanStrictOrNull() ?: sepia)
}

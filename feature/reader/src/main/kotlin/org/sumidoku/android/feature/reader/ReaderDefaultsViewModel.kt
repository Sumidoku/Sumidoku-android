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
import org.sumidoku.android.core.image.upscale.DenoiseLevel
import org.sumidoku.android.core.image.upscale.UpscaleMode
import org.sumidoku.android.core.image.upscale.UpscalePrecision
import org.sumidoku.android.core.image.upscale.UpscaleSettings

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
    val font: String = ReaderField.FONT.fallback,
    val fontSize: Float = 1f,
    val lineHeight: Float = 1.8f,
    val textColumns: Boolean = true,
    val drawUnderCutout: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val volumeKeyPaging: Boolean = false,
    /** Waifu2x upscaling defaults (device-only, so they only exist on the global layer). */
    val upscale: UpscaleSettings = UpscaleSettings(),
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
                font = prefs.font,
                fontSize = prefs.fontSize,
                lineHeight = prefs.lineHeight,
                textColumns = prefs.textColumns,
                drawUnderCutout = prefs.drawUnderCutout,
                keepScreenAwake = prefs.keepScreenAwake,
                volumeKeyPaging = prefs.volumeKeyPaging,
                upscale = prefs.upscaleSettings(),
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
    fun setFont(value: String) {
        val font = value.takeIf { it in ReaderPrefs.FONTS } ?: return
        _state.value = _state.value.copy(font = font)
        write(ReaderField.FONT, font)
    }

    fun setFontSize(value: Float) {
        val snapped = ((value * 20f).roundToInt() / 20f).coerceIn(ReaderPrefs.FONT_SIZE_RANGE)
        _state.value = _state.value.copy(fontSize = snapped)
        write(ReaderField.FSIZE, snapped.toString())
    }

    fun setLineHeight(value: Float) {
        val snapped = ((value * 10f).roundToInt() / 10f).coerceIn(ReaderPrefs.LINE_HEIGHT_RANGE)
        _state.value = _state.value.copy(lineHeight = snapped)
        write(ReaderField.FLINE, snapped.toString())
    }

    fun setTextColumns(value: Boolean) {
        _state.value = _state.value.copy(textColumns = value)
        write(ReaderField.TCOLS, value.toString())
    }

    fun setGrayscale(value: Boolean) = write(ReaderField.GRAY, value.toString())
    fun setSepia(value: Boolean) = write(ReaderField.SEPIA, value.toString())
    fun setDrawUnderCutout(value: Boolean) = write(ReaderField.CUTOUT, value.toString())
    fun setKeepScreenAwake(value: Boolean) = write(ReaderField.KEEP, value.toString())

    fun setVolumeKeyPaging(value: Boolean) {
        _state.value = _state.value.copy(volumeKeyPaging = value)
        write(ReaderField.VOLUME, value.toString())
    }

    // ---- waifu2x upscaling (device-only, written to the global layer) ----

    fun setUpscaleEnabled(value: Boolean) {
        val mode = if (value) UpscaleMode.AUTO else UpscaleMode.OFF
        write(ReaderField.UPSCALE, mode.raw)
    }

    fun setUpscaleDenoise(value: DenoiseLevel) = write(ReaderField.UPSCALE_DENOISE, value.raw)

    fun setUpscaleThreshold(value: Float) =
        write(ReaderField.UPSCALE_THRESHOLD, UpscaleSettings.clampAutoTriggerScale(value).toString())

    fun setUpscalePrecision(value: UpscalePrecision) = write(ReaderField.UPSCALE_PRECISION, value.raw)

    fun setUpscalePreload(value: Int) =
        write(ReaderField.UPSCALE_PRELOAD, UpscaleSettings.clampPreloadAhead(value).toString())

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
                font = prefs.font,
                fontSize = prefs.fontSize,
                lineHeight = prefs.lineHeight,
                textColumns = prefs.textColumns,
                drawUnderCutout = prefs.drawUnderCutout,
                keepScreenAwake = prefs.keepScreenAwake,
                volumeKeyPaging = prefs.volumeKeyPaging,
                upscale = prefs.upscaleSettings(),
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
    ReaderField.FONT -> copy(font = raw)
    ReaderField.FSIZE -> copy(fontSize = raw.toFloatOrNull() ?: fontSize)
    ReaderField.FLINE -> copy(lineHeight = raw.toFloatOrNull() ?: lineHeight)
    ReaderField.TCOLS -> copy(textColumns = raw.toBooleanStrictOrNull() ?: textColumns)
    ReaderField.CUTOUT -> copy(drawUnderCutout = raw.toBooleanStrictOrNull() ?: drawUnderCutout)
    ReaderField.KEEP -> copy(keepScreenAwake = raw.toBooleanStrictOrNull() ?: keepScreenAwake)
    ReaderField.VOLUME -> copy(volumeKeyPaging = raw.toBooleanStrictOrNull() ?: volumeKeyPaging)
    ReaderField.UPSCALE -> copy(upscale = upscale.copy(mode = UpscaleMode.fromRaw(raw)))
    ReaderField.UPSCALE_DENOISE -> copy(upscale = upscale.copy(denoise = DenoiseLevel.fromRaw(raw)))
    ReaderField.UPSCALE_THRESHOLD -> copy(
        upscale = upscale.copy(
            autoTriggerScale = UpscaleSettings.clampAutoTriggerScale(
                raw.toFloatOrNull() ?: upscale.autoTriggerScale,
            ),
        ),
    )
    ReaderField.UPSCALE_PRECISION -> copy(upscale = upscale.copy(precision = UpscalePrecision.fromRaw(raw)))
    ReaderField.UPSCALE_PRELOAD -> copy(
        upscale = upscale.copy(
            preloadAhead = UpscaleSettings.clampPreloadAhead(
                raw.toIntOrNull() ?: upscale.preloadAhead,
            ),
        ),
    )
}


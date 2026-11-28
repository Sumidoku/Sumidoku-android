package org.sumidoku.android.feature.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sumidoku.android.core.api.model.Viewer
import org.sumidoku.android.core.image.upscale.DenoiseLevel
import org.sumidoku.android.core.image.upscale.UpscaleConfig
import org.sumidoku.android.core.image.upscale.UpscaleMode
import org.sumidoku.android.core.image.upscale.UpscalePrecision
import org.sumidoku.android.core.image.upscale.UpscaleSettings

/**
 * Persistable reader preference fields. Every field is stored as a string so
 * the "global default" and "per-manga override" layers share a single
 * read/write path (matching the three-tier resolveDir / resolveMode
 * resolution in webui readerPrefs.ts).
 */
enum class ReaderField(val key: String, val fallback: String, val deviceOnly: Boolean = false) {
    MODE("mode", "paged"),        // paged | continuous | webtoon
    DIR("dir", "default"),        // default | ltr | rtl
    LAYOUT("layout", "auto"),     // single | double | auto
    COVER("cover", "false"),      // true = first page stands alone (webui doubleNoCover)
    SCALE("scale", "fit"),        // fit | width | height | original
    SPLIT("split", "false"),      // split wide pages
    TAP("tap", "lr"),             // tap zones
    BG("bg", "dark"),             // canvas background
    BRIGHT("bright", "1"),        // 0.5–1.5
    GRAY("gray", "false"),
    SEPIA("sepia", "false"),
    DTZOOM("dtzoom", "3"),      // double-tap zoom multiplier, 1–8x
    FONT("font", "default"),    // text page typeface: default | sans | serif | mono
    FSIZE("fsize", "1"),        // text page size multiplier, 0.7–2.0x
    FLINE("fline", "1.8"),      // text page line-height multiplier, 1.0–2.5x
    TCOLS("tcols", "true"),     // text pages: paged multi-column layout on wide screens
    CUTOUT("cutout", "false"),  // true = content draws under the notch band (Koharia drawUnderCutout)
    KEEP("keep", "false"),      // keep the screen awake while reading
    VOLUME("volume", "false", deviceOnly = true),  // volume keys turn pages (device input, global only)
    // Waifu2x upscaling. A device capability, not a book preference: GPU cost, driver quirks and
    // the APK's model set are properties of the phone, so these never get a per-manga override.
    UPSCALE("upscale", "off", deviceOnly = true),                       // off | auto
    UPSCALE_DENOISE("upscaleDenoise", "low", deviceOnly = true),        // none | low | medium | high | max
    UPSCALE_THRESHOLD("upscaleThreshold", "1.05", deviceOnly = true),   // 1.0–1.5
    UPSCALE_PRECISION("upscalePrecision", "auto", deviceOnly = true),   // auto | fp16 | fp32
    UPSCALE_PRELOAD("upscalePreload", "3", deviceOnly = true),          // 0–5 ahead
}

/** Strongly typed view of the reader preferences; always built via ReaderPrefs.from(map). */
data class ReaderPrefs(
    val mode: String = ReaderField.MODE.fallback,
    val dir: String = ReaderField.DIR.fallback,
    val layout: String = ReaderField.LAYOUT.fallback,
    val coverFirst: Boolean = false,
    val scale: String = ReaderField.SCALE.fallback,
    val splitWide: Boolean = false,
    val tapZones: String = ReaderField.TAP.fallback,
    val background: String = ReaderField.BG.fallback,
    val brightness: Float = 1f,
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val doubleTapZoom: Float = 3f,
    val font: String = ReaderField.FONT.fallback,
    val fontSize: Float = 1f,
    val lineHeight: Float = 1.8f,
    val textColumns: Boolean = true,
    /** Let page content run into the display cutout (notch / punch-hole) band.
     *  Off (the default) keeps pages and prose clear of the cutout with a band
     *  of the reader's own background; on gives the page the whole screen
     *  (KMReader / Koharia drawUnderCutout semantics). */
    val drawUnderCutout: Boolean = false,
    /** Keep the screen awake while the reader is the top page. */
    val keepScreenAwake: Boolean = false,
    val volumeKeyPaging: Boolean = false,
    /** Waifu2x upscaling, device-only (no per-manga override). See ReaderField.UPSCALE*. */
    val upscale: String = ReaderField.UPSCALE.fallback,
    val upscaleDenoise: String = ReaderField.UPSCALE_DENOISE.fallback,
    val upscaleThreshold: Float = UpscaleSettings.DEFAULT_AUTO_TRIGGER_SCALE,
    val upscalePrecision: String = ReaderField.UPSCALE_PRECISION.fallback,
    val upscalePreload: Int = UpscaleSettings.DEFAULT_PRELOAD_AHEAD,
) {
    /** Resolved upscaling settings; the single mapping used by the reader and the settings UI. */
    fun upscaleSettings(): UpscaleSettings = UpscaleSettings(
        mode = UpscaleMode.fromRaw(upscale),
        denoise = DenoiseLevel.fromRaw(upscaleDenoise),
        autoTriggerScale = UpscaleSettings.clampAutoTriggerScale(upscaleThreshold),
        precision = UpscalePrecision.fromRaw(upscalePrecision),
        preloadAhead = UpscaleSettings.clampPreloadAhead(upscalePreload),
    )

    /** Decoder-side configuration (same values, without the preload window). */
    fun upscaleConfig(): UpscaleConfig = upscaleSettings().toConfig()
    /** Exports the current values as strings (used both for DataStore writes
     *  and for comparing override values). */
    fun raw(field: ReaderField): String = when (field) {
        ReaderField.MODE -> mode
        ReaderField.DIR -> dir
        ReaderField.LAYOUT -> layout
        ReaderField.COVER -> coverFirst.toString()
        ReaderField.SCALE -> scale
        ReaderField.SPLIT -> splitWide.toString()
        ReaderField.TAP -> tapZones
        ReaderField.BG -> background
        ReaderField.BRIGHT -> brightness.toString()
        ReaderField.GRAY -> grayscale.toString()
        ReaderField.SEPIA -> sepia.toString()
        ReaderField.DTZOOM -> doubleTapZoom.toString()
        ReaderField.FONT -> font
        ReaderField.FSIZE -> fontSize.toString()
        ReaderField.FLINE -> lineHeight.toString()
        ReaderField.TCOLS -> textColumns.toString()
        ReaderField.CUTOUT -> drawUnderCutout.toString()
        ReaderField.KEEP -> keepScreenAwake.toString()
        ReaderField.VOLUME -> volumeKeyPaging.toString()
        ReaderField.UPSCALE -> upscale
        ReaderField.UPSCALE_DENOISE -> upscaleDenoise
        ReaderField.UPSCALE_THRESHOLD -> upscaleThreshold.toString()
        ReaderField.UPSCALE_PRECISION -> upscalePrecision
        ReaderField.UPSCALE_PRELOAD -> upscalePreload.toString()
    }

    /** Replaces one field with a string value; invalid values fall back to the current one. */
    fun set(field: ReaderField, raw: String?): ReaderPrefs {
        if (raw == null) return this
        return when (field) {
            ReaderField.MODE -> copy(mode = raw.takeIf { it in MODES } ?: mode)
            ReaderField.DIR -> copy(dir = raw.takeIf { it in DIRS } ?: dir)
            ReaderField.LAYOUT -> copy(layout = raw.takeIf { it in LAYOUTS } ?: layout)
            ReaderField.COVER -> copy(coverFirst = raw.toBoolean())
            ReaderField.SCALE -> copy(scale = raw.takeIf { it in SCALES } ?: scale)
            ReaderField.SPLIT -> copy(splitWide = raw.toBoolean())
            ReaderField.TAP -> copy(tapZones = raw.takeIf { it in TAPS } ?: tapZones)
            ReaderField.BG -> copy(background = raw.takeIf { it in BACKGROUNDS } ?: background)
            ReaderField.BRIGHT -> copy(brightness = raw.toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: brightness)
            ReaderField.GRAY -> copy(grayscale = raw.toBoolean())
            ReaderField.SEPIA -> copy(sepia = raw.toBoolean())
            ReaderField.DTZOOM -> copy(doubleTapZoom = raw.toFloatOrNull()?.coerceIn(1f, 8f) ?: doubleTapZoom)
            ReaderField.FONT -> copy(font = raw.takeIf { it in FONTS } ?: font)
            ReaderField.FSIZE -> copy(fontSize = raw.toFloatOrNull()?.coerceIn(FONT_SIZE_RANGE) ?: fontSize)
            ReaderField.FLINE -> copy(lineHeight = raw.toFloatOrNull()?.coerceIn(LINE_HEIGHT_RANGE) ?: lineHeight)
            ReaderField.TCOLS -> copy(textColumns = raw.toBoolean())
            ReaderField.CUTOUT -> copy(drawUnderCutout = raw.toBoolean())
            ReaderField.KEEP -> copy(keepScreenAwake = raw.toBoolean())
            ReaderField.VOLUME -> copy(volumeKeyPaging = raw.toBoolean())
            ReaderField.UPSCALE -> copy(upscale = raw.takeIf { it in UPSCALE_MODES } ?: upscale)
            ReaderField.UPSCALE_DENOISE -> copy(upscaleDenoise = raw.takeIf { it in DENOISE_LEVELS } ?: upscaleDenoise)
            ReaderField.UPSCALE_THRESHOLD -> copy(
                upscaleThreshold = UpscaleSettings.clampAutoTriggerScale(
                    raw.toFloatOrNull() ?: upscaleThreshold,
                ),
            )
            ReaderField.UPSCALE_PRECISION -> copy(upscalePrecision = raw.takeIf { it in UPSCALE_PRECISIONS } ?: upscalePrecision)
            ReaderField.UPSCALE_PRELOAD -> copy(
                upscalePreload = UpscaleSettings.clampPreloadAhead(
                    raw.toIntOrNull() ?: upscalePreload,
                ),
            )
        }
    }

    /** Lays one layer of override values on top (fields absent from the
     *  override map keep their current value). */
    fun overlay(overrides: Map<ReaderField, String>): ReaderPrefs =
        overrides.entries.fold(this) { acc, (field, value) -> acc.set(field, value) }

    companion object {
        val MODES = setOf("paged", "continuous", "webtoon")
        val DIRS = setOf("default", "ltr", "rtl")
        val LAYOUTS = setOf("single", "double", "auto")
        val SCALES = setOf("fit", "width", "height", "original")
        val TAPS = setOf("lr", "rl", "edge", "kindle", "off")
        val BACKGROUNDS = setOf("dark", "gray", "light", "auto")
        /** Text page typefaces, matching the webui ReaderFont union. */
        val FONTS = setOf("default", "sans", "serif", "mono")
        val FONT_SIZE_RANGE = 0.7f..2f
        val LINE_HEIGHT_RANGE = 1f..2.5f

        /** Upscaling enums as the strings stored in DataStore. */
        val UPSCALE_MODES: Set<String> = UpscaleMode.entries.mapTo(LinkedHashSet()) { it.raw }
        val DENOISE_LEVELS: Set<String> = DenoiseLevel.entries.mapTo(LinkedHashSet()) { it.raw }
        val UPSCALE_PRECISIONS: Set<String> = UpscalePrecision.entries.mapTo(LinkedHashSet()) { it.raw }

        fun from(raw: Map<ReaderField, String>): ReaderPrefs =
            ReaderPrefs().overlay(raw)
    }
}

/**
 * Reader settings store: global defaults plus field-level overrides per manga.
 *
 * Key names mirror the webui's `sumidoku.reader.*`: `reader.g.<field>` is
 * global, `reader.m.<mangaId>.<field>` is the per-manga override. An override
 * only exists while it differs from the resolved value — reading back an
 * option equal to the resolved value deletes the override key automatically
 * (the self-cleaning semantics of webui overrideStore.writeOverride).
 */
@Singleton
class ReaderSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private fun globalKey(field: ReaderField) = stringPreferencesKey("reader.g.${field.key}")
    private fun mangaKey(mangaId: Long, field: ReaderField) =
        stringPreferencesKey("reader.m.$mangaId.${field.key}")

    /** Global defaults (absent fields are omitted). */
    val global: Flow<Map<ReaderField, String>> = dataStore.data.map { prefs ->
        ReaderField.entries.mapNotNull { field ->
            prefs[globalKey(field)]?.let { field to it }
        }.toMap()
    }

    /** Override map for one manga (absent fields are omitted). */
    fun overrides(mangaId: Long): Flow<Map<ReaderField, String>> = dataStore.data.map { prefs ->
        ReaderField.entries.mapNotNull { field ->
            prefs[mangaKey(mangaId, field)]?.let { field to it }
        }.toMap()
    }

    suspend fun setGlobal(field: ReaderField, raw: String?) {
        dataStore.edit { prefs ->
            if (raw == null) prefs.remove(globalKey(field)) else prefs[globalKey(field)] = raw
        }
    }

    /**
     * Writes the override for this manga. With [keep] false or a null value
     * the key is deleted, so resolution falls back to
     * "manga metadata › global default".
     */
    suspend fun setOverride(mangaId: Long, field: ReaderField, raw: String?, keep: Boolean = true) {
        // Device-only fields (volume keys, waifu2x upscaling) are global by definition: a per-manga
        // override would silently pin a GPU setting to one book, so the write is dropped instead.
        if (field.deviceOnly) return
        dataStore.edit { prefs ->
            if (!keep || raw == null) {
                prefs.remove(mangaKey(mangaId, field))
            } else {
                prefs[mangaKey(mangaId, field)] = raw
            }
        }
    }

    /** Clears every override for this manga (design screen 41 "Reset to defaults"). */
    suspend fun clearOverrides(mangaId: Long) {
        dataStore.edit { prefs ->
            ReaderField.entries.forEach { prefs.remove(mangaKey(mangaId, it)) }
        }
    }
}

/** Mapping from manga.viewer metadata to the reading direction (matching webui
 *  dirFromViewer / modeFromViewer: Vertical/Webtoon do not decide the
 *  left/right direction). */
object ViewerReading {
    fun dirOf(viewer: Viewer?): String? = when (viewer) {
        Viewer.RIGHT_TO_LEFT -> "rtl"
        Viewer.LEFT_TO_RIGHT -> "ltr"
        else -> null
    }

    fun modeOf(viewer: Viewer?): String? = when (viewer) {
        Viewer.WEBTOON -> "webtoon"
        Viewer.VERTICAL -> "continuous"
        else -> null
    }
}

/** Double-page layout: single page / double page / auto (double pages in
 *  landscape, applied on rotation). */
enum class PageLayout(val value: String) {
    SINGLE("single"),
    DOUBLE("double"),
    AUTO("auto");

    companion object {
        fun of(value: String): PageLayout = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/** The three reading modes: paged / continuous scroll (vertical strip with
 *  gaps) / webtoon (no gaps). */
object ReadingMode {
    const val PAGED = "paged"
    const val CONTINUOUS = "continuous"
    const val WEBTOON = "webtoon"

    fun isVertical(mode: String) = mode != PAGED
}


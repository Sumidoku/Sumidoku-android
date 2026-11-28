package org.sumidoku.android.core.image.upscale

/**
 * Waifu2x upscaling trigger: off, or auto when the screen would enlarge the page.
 *
 * There is deliberately no "always" entry: a page that already has enough pixels for the screen
 * gains nothing from a 2x pass, only GPU time and a 4x-larger cache entry, and the threshold
 * already covers "be more eager".
 */
enum class UpscaleMode {
    OFF,
    AUTO,
    ;

    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): UpscaleMode =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: OFF
    }
}

/**
 * Waifu2x denoise weights: one "no denoise" model plus the five noise levels of the bundled
 * upconv7 set. [modelSuffix] is the asset file stem (`<suffix>_model.bin/.param`).
 */
enum class DenoiseLevel(val modelSuffix: String) {
    NONE("scale2.0x"),
    LOW("noise0_scale2.0x"),
    MEDIUM("noise1_scale2.0x"),
    HIGH("noise2_scale2.0x"),
    MAX("noise3_scale2.0x"),
    ;

    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): DenoiseLevel =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: LOW
    }
}

/**
 * Waifu2x compute precision. Auto prefers FP16 and falls back to FP32 when the driver fails its
 * self-test; every real page is still range-checked, so a driver that overflows only on real-world
 * input is rejected instead of painting a "flower screen". FP32 remains the deterministic escape
 * hatch. **The ordinal is part of the JNI contract** (0 = Auto, 1 = Fp16, 2 = Fp32).
 */
enum class UpscalePrecision {
    AUTO,
    FP16,
    FP32,
    ;

    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): UpscalePrecision =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

/**
 * Persisted, user-facing upscaling preferences (the defaults screen and the in-reader sheet write
 * the same object). These are device properties, not book properties, so the reader stores them
 * globally only.
 *
 * @param autoTriggerScale Auto-mode threshold: a page is upscaled when the painted scale exceeds
 *   this factor. Clamped to [MIN_AUTO_TRIGGER_SCALE]..[MAX_AUTO_TRIGGER_SCALE].
 * @param preloadAhead how many following pages are preprocessed in the background; 0 means
 *   "current page only". Notice that arbitrary backgrounds are not free: each is a full 2x run.
 */
data class UpscaleSettings(
    val mode: UpscaleMode = UpscaleMode.OFF,
    val denoise: DenoiseLevel = DenoiseLevel.LOW,
    val autoTriggerScale: Float = DEFAULT_AUTO_TRIGGER_SCALE,
    val precision: UpscalePrecision = UpscalePrecision.AUTO,
    val preloadAhead: Int = DEFAULT_PRELOAD_AHEAD,
) {
    val enabled: Boolean get() = mode != UpscaleMode.OFF

    fun toConfig(): UpscaleConfig = UpscaleConfig(
        mode = mode,
        denoise = denoise,
        autoTriggerScale = autoTriggerScale,
        precision = precision,
    )

    companion object {
        const val DEFAULT_AUTO_TRIGGER_SCALE = 1.05f
        const val MIN_AUTO_TRIGGER_SCALE = 1f
        const val MAX_AUTO_TRIGGER_SCALE = 1.5f

        const val DEFAULT_PRELOAD_AHEAD = 3
        const val MAX_PRELOAD_AHEAD = 5

        fun clampAutoTriggerScale(value: Float): Float =
            if (!value.isFinite()) DEFAULT_AUTO_TRIGGER_SCALE
            else value.coerceIn(MIN_AUTO_TRIGGER_SCALE, MAX_AUTO_TRIGGER_SCALE)

        fun clampPreloadAhead(value: Int): Int = value.coerceIn(0, MAX_PRELOAD_AHEAD)
    }
}

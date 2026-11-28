package org.sumidoku.android.core.image.upscale

/**
 * Resolved upscaling configuration for one image request.
 *
 * The enhancement-cache key is derived from two independent axes:
 *
 * - [MODEL_SIGNATURE] covers the model architecture, the scale, the bundled-model version and the
 *   native output implementation. It is the stable, per-page part of the key, so changing any of
 *   those invalidates every entry at once.
 * - [denoise] and [precision] are *variant* dimensions of the same page rather than part of its
 *   identity: switching the level misses only the requested variant and can fall back to a still
 *   cached one instead of re-running the whole book (see `EnhancementCache.readNearest`), and
 *   precision is never mixed because FP32 exists precisely to escape a driver that corrupts FP16.
 */
data class UpscaleConfig(
    val mode: UpscaleMode = UpscaleMode.AUTO,
    val denoise: DenoiseLevel = DenoiseLevel.LOW,
    val autoTriggerScale: Float = UpscaleSettings.DEFAULT_AUTO_TRIGGER_SCALE,
    val precision: UpscalePrecision = UpscalePrecision.AUTO,
) {
    val enabled: Boolean get() = mode != UpscaleMode.OFF

    /** Suffix that names one variant of a page in the cache key. */
    val variant: String get() = "${denoise.raw}@${precision.raw}"

    companion object {
        /**
         * Bumped whenever the native path that turns a source page into pixels changes (tiling,
         * fused shaders, output conversion). Unlike the model version this says nothing about the
         * bundled weights, but it still has to invalidate every cached output they produced.
         */
        const val OUTPUT_VERSION = 1

        /**
         * Cache-key component shared by every variant of a page: the model architecture, the scale,
         * the bundled-model version and the native output implementation.
         */
        const val MODEL_SIGNATURE =
            "upconv7-s${Waifu2xBridge.SCALE}-mv${Waifu2xBridge.MODEL_CACHE_VERSION}-ov$OUTPUT_VERSION"
    }
}

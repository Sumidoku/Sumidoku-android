package org.sumidoku.android.core.image.upscale

import kotlin.math.max
import kotlin.math.min

/** How the reader paints a page; decides which axis bounds the painted scale. */
enum class DisplayScaleMode {
    /** Fitted into the cell (`ContentScale.Fit`): the tighter axis decides. */
    FIT,

    /** Filled to the cell width (`FillWidth`, vertical scroll allowed). */
    WIDTH,

    /** Filled to the cell height (`FillHeight`). */
    HEIGHT,

    /** Drawn 1:1 (`ContentScale.None`); never worth upscaling. */
    ORIGINAL,

    /**
     * One half of a split wide page: the reader fits the page into the cell and then stretches it
     * horizontally by 2 before clipping to the visible half, so the painted x-scale is twice the
     * fitted one.
     */
    SPLIT,
    ;

    companion object {
        /** Maps the reader's `scale` preference value onto a mode. */
        fun fromReaderScale(raw: String?): DisplayScaleMode = when (raw) {
            "width" -> WIDTH
            "height" -> HEIGHT
            "original" -> ORIGINAL
            "split" -> SPLIT
            else -> FIT
        }
    }
}

/**
 * Pure trigger decision: upscale only when a low-resolution source would be enlarged for display.
 * The point is to add detail to content the screen must scale up anyway, not to reprocess
 * already-crisp pages.
 */
object UpscaleDecision {

    /**
     * Screen pixels per source pixel for one page cell.
     *
     * @param targetWidth/targetHeight the cell size in px; a non-positive or undefined axis is
     *   ignored (Coil reports an undefined height for width-bound images such as a webtoon strip),
     *   so a single known axis still yields a usable ratio.
     */
    fun displayScale(
        mode: DisplayScaleMode,
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Float {
        if (srcWidth <= 0 || srcHeight <= 0) return 0f
        val widthRatio = if (targetWidth > 0) targetWidth.toFloat() / srcWidth else 0f
        val heightRatio = if (targetHeight > 0) targetHeight.toFloat() / srcHeight else 0f
        val fitted = when {
            widthRatio > 0f && heightRatio > 0f -> min(widthRatio, heightRatio)
            else -> max(widthRatio, heightRatio)
        }
        return when (mode) {
            DisplayScaleMode.FIT -> fitted
            DisplayScaleMode.WIDTH -> widthRatio
            DisplayScaleMode.HEIGHT -> heightRatio
            DisplayScaleMode.ORIGINAL -> 1f
            DisplayScaleMode.SPLIT -> fitted * 2f
        }
    }

    fun shouldUpscale(
        srcWidth: Int,
        srcHeight: Int,
        displayScale: Float,
        config: UpscaleConfig,
        maxTextureWidth: Int = 0,
        maxTextureHeight: Int = 0,
    ): Boolean {
        if (srcWidth <= 0 || srcHeight <= 0 || displayScale <= 0f || !displayScale.isFinite()) return false
        return when (config.mode) {
            UpscaleMode.OFF -> false
            UpscaleMode.AUTO ->
                fitsEngineBudget(srcWidth, srcHeight) &&
                    fitsTexture(srcWidth, srcHeight, maxTextureWidth, maxTextureHeight) &&
                    displayScale > config.autoTriggerScale
        }
    }

    /**
     * A page the engine cannot hold is not "not worth it" but "impossible": queueing it would
     * allocate the 2x bitmap, be refused natively and count as a failed run towards the enhancer's
     * circuit breaker — three tall webtoon strips in a row and the whole book loses enhancement.
     * The budget is the engine's own, so the decision and the refusal can never disagree.
     */
    fun fitsEngineBudget(srcWidth: Int, srcHeight: Int): Boolean {
        val outWidth = srcWidth * Waifu2xBridge.SCALE
        val outHeight = srcHeight * Waifu2xBridge.SCALE
        return outWidth <= Waifu2xBridge.MAX_OUTPUT_WIDTH &&
            outHeight <= Waifu2xBridge.MAX_OUTPUT_HEIGHT &&
            outWidth.toLong() * outHeight <= Waifu2xBridge.MAX_OUTPUT_PIXELS
    }

    /**
     * A 2x output that cannot fit a texture would be downscaled on draw, destroying the gain.
     * With only Auto left this is a safety net rather than a routine skip.
     */
    private fun fitsTexture(
        srcWidth: Int,
        srcHeight: Int,
        maxTextureWidth: Int,
        maxTextureHeight: Int,
    ): Boolean {
        if (maxTextureWidth > 0 && srcWidth * Waifu2xBridge.SCALE > maxTextureWidth) return false
        if (maxTextureHeight > 0 && srcHeight * Waifu2xBridge.SCALE > maxTextureHeight) return false
        return true
    }
}

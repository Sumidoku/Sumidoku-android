package org.sumidoku.android.core.image.upscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscaleDecisionTest {

    private fun auto(threshold: Float = 1.05f) =
        UpscaleConfig(mode = UpscaleMode.AUTO, autoTriggerScale = threshold)

    @Test
    fun `fit mode uses the tighter axis`() {
        // 800x1200 page in a 1080x2400 cell: 1.35 horizontally, 2.0 vertically -> 1.35 painted.
        val scale = UpscaleDecision.displayScale(DisplayScaleMode.FIT, 800, 1200, 1080, 2400)
        assertEquals(1.35f, scale, 0.001f)
        assertTrue(UpscaleDecision.shouldUpscale(800, 1200, scale, auto()))
    }

    @Test
    fun `crisp page is not upscaled`() {
        // A 1600x2400 page on a 1080x2400 screen is already smaller than the screen.
        val scale = UpscaleDecision.displayScale(DisplayScaleMode.FIT, 1600, 2400, 1080, 2400)
        assertFalse(UpscaleDecision.shouldUpscale(1600, 2400, scale, auto()))
    }

    @Test
    fun `width mode ignores the unknown height`() {
        // Webtoon strip: height is the whole scroll extent, so only the width counts.
        val scale = UpscaleDecision.displayScale(DisplayScaleMode.WIDTH, 800, 12000, 1080, 0)
        assertEquals(1.35f, scale, 0.001f)
        assertTrue(UpscaleDecision.shouldUpscale(800, 1200, scale, auto()))
    }

    @Test
    fun `tall webtoon strip is refused by the engine budget`() {
        // 800x12000 -> 1600x24000 output, beyond the 16384 height cap.
        assertFalse(UpscaleDecision.fitsEngineBudget(800, 12000))
        assertTrue(UpscaleDecision.fitsEngineBudget(800, 2000))
        // Output edges: 4096 width cap and 20M pixel budget.
        assertFalse(UpscaleDecision.fitsEngineBudget(2049, 1000))
        assertFalse(UpscaleDecision.fitsEngineBudget(3000, 3500))
    }

    @Test
    fun `split half doubles the painted scale`() {
        val fitted = UpscaleDecision.displayScale(DisplayScaleMode.FIT, 1200, 1200, 1080, 2400)
        val split = UpscaleDecision.displayScale(DisplayScaleMode.SPLIT, 1200, 1200, 1080, 2400)
        assertEquals(fitted * 2f, split, 0.001f)
        // A page that is not enlarged in fit mode is still enlarged once stretched into one half.
        assertFalse(UpscaleDecision.shouldUpscale(1200, 1200, fitted, auto()))
        assertTrue(UpscaleDecision.shouldUpscale(1200, 1200, split, auto()))
    }

    @Test
    fun `original scale never triggers`() {
        val scale = UpscaleDecision.displayScale(DisplayScaleMode.ORIGINAL, 400, 600, 1080, 2400)
        assertEquals(1f, scale, 0f)
        assertFalse(UpscaleDecision.shouldUpscale(400, 600, scale, auto()))
    }

    @Test
    fun `threshold is strict`() {
        // Painted scale equal to the threshold is already "as large as it needs to be".
        assertFalse(UpscaleDecision.shouldUpscale(800, 1200, 1.05f, auto(1.05f)))
        assertTrue(UpscaleDecision.shouldUpscale(800, 1200, 1.06f, auto(1.05f)))
        // A raised threshold must suppress a page that a default threshold would have accepted.
        assertFalse(UpscaleDecision.shouldUpscale(800, 1200, 1.2f, auto(1.49f)))
        assertTrue(UpscaleDecision.shouldUpscale(800, 1200, 1.5f, auto(1.49f)))
    }

    @Test
    fun `off mode never upscales`() {
        assertFalse(UpscaleDecision.shouldUpscale(400, 600, 3f, auto().copy(mode = UpscaleMode.OFF)))
    }

    @Test
    fun `output beyond the texture limit is skipped`() {
        // 1600x2400 source -> 3200x4800 output on a device with a 3000px texture limit.
        assertFalse(
            UpscaleDecision.shouldUpscale(
                1600, 2400, 3f, auto(), maxTextureWidth = 3000, maxTextureHeight = 6000,
            ),
        )
        assertTrue(
            UpscaleDecision.shouldUpscale(
                1600, 2400, 3f, auto(), maxTextureWidth = 4096, maxTextureHeight = 8192,
            ),
        )
    }

    @Test
    fun `unknown dimensions are never upscaled`() {
        assertFalse(UpscaleDecision.shouldUpscale(0, 0, 3f, auto()))
        assertFalse(UpscaleDecision.shouldUpscale(800, 1200, 0f, auto()))
    }
}

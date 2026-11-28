package org.sumidoku.android.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sumidoku.android.core.image.upscale.DenoiseLevel
import org.sumidoku.android.core.image.upscale.UpscaleMode
import org.sumidoku.android.core.image.upscale.UpscalePrecision
import org.sumidoku.android.core.image.upscale.UpscaleSettings

/** The reader's string preferences must clamp and map exactly like the settings screens assume. */
class ReaderUpscalePrefsTest {

    @Test
    fun `defaults resolve to off`() {
        val settings = ReaderPrefs().upscaleSettings()
        assertFalse(settings.enabled)
        assertEquals(DenoiseLevel.LOW, settings.denoise)
        assertEquals(UpscalePrecision.AUTO, settings.precision)
        assertEquals(UpscaleSettings.DEFAULT_PRELOAD_AHEAD, settings.preloadAhead)
    }

    @Test
    fun `stored strings map onto the upscaling enums`() {
        val prefs = ReaderPrefs()
            .set(ReaderField.UPSCALE, "auto")
            .set(ReaderField.UPSCALE_DENOISE, "high")
            .set(ReaderField.UPSCALE_PRECISION, "fp32")
            .set(ReaderField.UPSCALE_PRELOAD, "1")
        val settings = prefs.upscaleSettings()
        assertTrue(settings.enabled)
        assertEquals(UpscaleMode.AUTO, settings.mode)
        assertEquals(DenoiseLevel.HIGH, settings.denoise)
        assertEquals(UpscalePrecision.FP32, settings.precision)
        assertEquals(1, settings.preloadAhead)
        assertEquals("high@fp32", prefs.upscaleConfig().variant)
    }

    @Test
    fun `out-of-range and unknown values are rejected, not stored`() {
        val prefs = ReaderPrefs()
            .set(ReaderField.UPSCALE, "always")
            .set(ReaderField.UPSCALE_DENOISE, "extreme")
            .set(ReaderField.UPSCALE_PRECISION, "fp8")
            .set(ReaderField.UPSCALE_THRESHOLD, "3.0")
            .set(ReaderField.UPSCALE_PRELOAD, "-1")
        assertEquals(ReaderField.UPSCALE.fallback, prefs.upscale)
        assertEquals(ReaderField.UPSCALE_DENOISE.fallback, prefs.upscaleDenoise)
        assertEquals(ReaderField.UPSCALE_PRECISION.fallback, prefs.upscalePrecision)
        // Clamped to the supported ends rather than dropped.
        assertEquals(UpscaleSettings.MAX_AUTO_TRIGGER_SCALE, prefs.upscaleThreshold, 0f)
        assertEquals(0, prefs.upscalePreload)
    }

    @Test
    fun `raw export matches what the store writes`() {
        val prefs = ReaderPrefs(
            upscale = "auto",
            upscaleDenoise = "medium",
            upscaleThreshold = 1.2f,
            upscalePrecision = "fp16",
            upscalePreload = 5,
        )
        assertEquals("auto", prefs.raw(ReaderField.UPSCALE))
        assertEquals("medium", prefs.raw(ReaderField.UPSCALE_DENOISE))
        assertEquals("1.2", prefs.raw(ReaderField.UPSCALE_THRESHOLD))
        assertEquals("fp16", prefs.raw(ReaderField.UPSCALE_PRECISION))
        assertEquals("5", prefs.raw(ReaderField.UPSCALE_PRELOAD))
    }

    @Test
    fun `upscaling and volume keys are device-only`() {
        assertTrue(ReaderField.VOLUME.deviceOnly)
        assertTrue(ReaderField.UPSCALE.deviceOnly)
        assertTrue(ReaderField.UPSCALE_DENOISE.deviceOnly)
        assertTrue(ReaderField.UPSCALE_THRESHOLD.deviceOnly)
        assertTrue(ReaderField.UPSCALE_PRECISION.deviceOnly)
        assertTrue(ReaderField.UPSCALE_PRELOAD.deviceOnly)
        assertFalse(ReaderField.MODE.deviceOnly)
        assertFalse(ReaderField.SCALE.deviceOnly)
    }
}

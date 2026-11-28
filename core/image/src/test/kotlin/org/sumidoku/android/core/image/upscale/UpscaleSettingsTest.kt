package org.sumidoku.android.core.image.upscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscaleSettingsTest {

    @Test
    fun `defaults are off with a low denoise and three pages ahead`() {
        val settings = UpscaleSettings()
        assertFalse(settings.enabled)
        assertEquals(DenoiseLevel.LOW, settings.denoise)
        assertEquals(UpscalePrecision.AUTO, settings.precision)
        assertEquals(1.05f, settings.autoTriggerScale, 0f)
        assertEquals(3, settings.preloadAhead)
    }

    @Test
    fun `threshold and preload are clamped`() {
        assertEquals(1f, UpscaleSettings.clampAutoTriggerScale(0.4f), 0f)
        assertEquals(1.5f, UpscaleSettings.clampAutoTriggerScale(9f), 0f)
        assertEquals(1.05f, UpscaleSettings.clampAutoTriggerScale(Float.NaN), 0f)
        assertEquals(0, UpscaleSettings.clampPreloadAhead(-2))
        assertEquals(5, UpscaleSettings.clampPreloadAhead(99))
    }

    @Test
    fun `raw values round-trip and unknown strings fall back`() {
        DenoiseLevel.entries.forEach { assertEquals(it, DenoiseLevel.fromRaw(it.raw)) }
        UpscalePrecision.entries.forEach { assertEquals(it, UpscalePrecision.fromRaw(it.raw)) }
        UpscaleMode.entries.forEach { assertEquals(it, UpscaleMode.fromRaw(it.raw)) }

        assertEquals(UpscaleMode.OFF, UpscaleMode.fromRaw(null))
        assertEquals(UpscaleMode.OFF, UpscaleMode.fromRaw("bogus"))
        assertEquals(DenoiseLevel.LOW, DenoiseLevel.fromRaw(null))
        assertEquals(UpscalePrecision.AUTO, UpscalePrecision.fromRaw("half"))
    }

    @Test
    fun `config mirrors the settings and carries one variant suffix`() {
        val config = UpscaleSettings(
            mode = UpscaleMode.AUTO,
            denoise = DenoiseLevel.MAX,
            precision = UpscalePrecision.FP32,
        ).toConfig()
        assertTrue(config.enabled)
        assertEquals("max@fp32", config.variant)
        assertFalse(config.copy(mode = UpscaleMode.OFF).enabled)
    }

    @Test
    fun `model suffix matches the bundled asset stems`() {
        // These stems are the file names under assets/upscale-models/waifu2x-upconv7.
        assertEquals("scale2.0x", DenoiseLevel.NONE.modelSuffix)
        assertEquals("noise0_scale2.0x", DenoiseLevel.LOW.modelSuffix)
        assertEquals("noise1_scale2.0x", DenoiseLevel.MEDIUM.modelSuffix)
        assertEquals("noise2_scale2.0x", DenoiseLevel.HIGH.modelSuffix)
        assertEquals("noise3_scale2.0x", DenoiseLevel.MAX.modelSuffix)
    }

    @Test
    fun `precision ordinals are the JNI contract`() {
        assertEquals(0, UpscalePrecision.AUTO.ordinal)
        assertEquals(1, UpscalePrecision.FP16.ordinal)
        assertEquals(2, UpscalePrecision.FP32.ordinal)
    }
}

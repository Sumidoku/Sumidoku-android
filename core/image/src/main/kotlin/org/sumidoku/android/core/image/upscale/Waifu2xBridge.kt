package org.sumidoku.android.core.image.upscale

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JNI bridge to the ncnn+Vulkan waifu2x engine.
 *
 * The bundled models are extracted from `assets/upscale-models/waifu2x-upconv7/` into the cache
 * directory on first use; bump [MODEL_CACHE_VERSION] whenever the bundled models change so stale
 * extractions are refreshed. All calls are serialized through [mutex] because the ncnn Net is not
 * thread-safe and the GPU has a single compute queue anyway.
 *
 * Failure is always soft: a missing ABI (x86 emulator), a driver with no usable Vulkan device, or a
 * model that fails its self-test only makes the engine unavailable — every caller must fall back to
 * the source page.
 */
object Waifu2xBridge {

    const val SCALE = 2

    /**
     * Output budget of the native engine, mirrored by `waifu2x_jni.cpp` (`kMaxPageOutput*`) and
     * applied by [UpscaleDecision] before a page is queued. The width cap is one fused pass: that
     * path holds the source page and its 2x result in VRAM at once. The height cap is larger
     * because a tall page (webtoon strip) is processed as stacked bands, so what bounds it is the
     * output bitmap — hence the pixel budget, which keeps the allocation around 80 MB of ARGB.
     */
    const val MAX_OUTPUT_WIDTH = 4096
    const val MAX_OUTPUT_HEIGHT = 16384
    const val MAX_OUTPUT_PIXELS = 20_000_000L

    private const val TAG = "Sumidoku/Upscale"
    private const val MODEL_ASSET_DIR = "upscale-models/waifu2x-upconv7"

    /** Public because [UpscaleConfig.MODEL_SIGNATURE] embeds it so cache entries self-invalidate. */
    const val MODEL_CACHE_VERSION = 1

    private val mutex = Mutex()

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var activeDenoise: DenoiseLevel? = null

    @Volatile
    private var activePrecision: UpscalePrecision? = null

    /** False when the native library could not be loaded at all (missing ABI, ABI-filtered APK). */
    @Volatile
    var available: Boolean = false
        private set

    @Volatile
    var isVulkan: Boolean = false
        private set

    /** True when the engine initialized with FP16 storage (self-tested on this driver). */
    @Volatile
    var isFp16: Boolean = false
        private set

    /** True when preproc/postproc run as Vulkan compute shaders instead of CPU conversions. */
    @Volatile
    var isFused: Boolean = false
        private set

    val isReady: Boolean get() = handle != 0L

    init {
        available = runCatching { System.loadLibrary("waifu2x-jni") }.isSuccess
        if (!available) Log.w(TAG, "waifu2x-jni not loadable on this build/device; upscaling unavailable")
    }

    /**
     * Ensures the engine is initialized for [denoise] at [precision], re-initializing when either
     * changes. Returns false when the engine/model failed to load — callers must treat that as
     * "upscaling unavailable" and fall back to the source image.
     */
    suspend fun ensureInitialized(
        context: Context,
        denoise: DenoiseLevel,
        precision: UpscalePrecision = UpscalePrecision.AUTO,
    ): Boolean = mutex.withLock {
        if (!available) return@withLock false
        if (handle != 0L && activeDenoise == denoise && activePrecision == precision) return@withLock true
        releaseLocked()
        val dir = try {
            extractModels(context)
        } catch (e: IOException) {
            Log.e(TAG, "model extraction failed", e)
            return@withLock false
        }
        val h = nativeInit(dir.absolutePath, denoise.modelSuffix, precision.ordinal, null)
        if (h == 0L) {
            Log.e(TAG, "engine init failed (${denoise.modelSuffix}, ${precision.name}): ${lastError()}")
            return@withLock false
        }
        handle = h
        activeDenoise = denoise
        activePrecision = precision
        isVulkan = nativeIsVulkan(h)
        isFp16 = nativeIsFp16(h)
        isFused = nativeIsFused(h)
        Log.i(
            TAG,
            "engine initialized: ${denoise.modelSuffix}, request=${precision.name}, " +
                "vulkan=$isVulkan, precision=${if (isFp16) "fp16" else "fp32"}, " +
                "path=${if (isFused) "fused shaders" else "staged cpu"}",
        )
        true
    }

    /**
     * Upscales [input] (any config; converted to ARGB_8888 internally) by [SCALE].
     * Must be called after [ensureInitialized] returned true. Returns null on failure.
     */
    suspend fun process(input: Bitmap): Bitmap? = mutex.withLock {
        val h = handle
        if (h == 0L) return@withLock null
        val argb = if (input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, false)
        } ?: return@withLock null
        // Checked before the output bitmap exists: a refused page used to allocate its 2x bitmap
        // first (65 MB for a webtoon strip) and only then be turned down by the engine.
        val outWidth = argb.width * SCALE
        val outHeight = argb.height * SCALE
        if (outWidth > MAX_OUTPUT_WIDTH || outHeight > MAX_OUTPUT_HEIGHT ||
            outWidth.toLong() * outHeight > MAX_OUTPUT_PIXELS
        ) {
            Log.w(TAG, "refusing ${argb.width}x${argb.height}: 2x is ${outWidth}x$outHeight, outside the engine budget")
            return@withLock null
        }
        val output = try {
            Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "output bitmap allocation failed: ${outWidth}x$outHeight", e)
            return@withLock null
        }
        if (!nativeProcess(h, argb, output)) {
            Log.e(TAG, "native process failed: ${lastError()}")
            output.recycle()
            return@withLock null
        }
        lastStats()?.let { Log.i(TAG, it) }
        output
    }

    /**
     * Abandons the page currently inside [process] at its next tile boundary. The reader calls this
     * when it closes or switches chapters; without it a 15-second FP32 page keeps the GPU busy long
     * after the reader has disappeared.
     */
    fun cancelInFlight() {
        if (!available) return
        runCatching { nativeAdvanceGeneration() }
    }

    /** Park/resume the native tile loop while the user is actively scrolling. */
    fun setPaused(paused: Boolean) {
        if (!available) return
        runCatching { nativeSetPaused(paused) }
    }

    suspend fun release() = mutex.withLock { releaseLocked() }

    private fun releaseLocked() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
            activeDenoise = null
            activePrecision = null
            isVulkan = false
            isFp16 = false
            isFused = false
        }
    }

    /**
     * Copies the bundled models into the cache dir. Skipped entirely when the version marker
     * matches, so steady-state cost is one file-exists check.
     */
    private fun extractModels(context: Context): File {
        val dir = File(context.cacheDir, "upscale-models/$MODEL_ASSET_DIR")
        val marker = File(dir, ".v$MODEL_CACHE_VERSION")
        if (marker.exists()) return dir

        val assetManager = context.assets
        val names = assetManager.list(MODEL_ASSET_DIR)
            ?: throw IOException("asset dir $MODEL_ASSET_DIR not found")
        dir.deleteRecursively()
        dir.mkdirs()
        for (name in names) {
            assetManager.open("$MODEL_ASSET_DIR/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        marker.writeText("ok")
        Log.i(TAG, "models extracted: ${names.size} files (v$MODEL_CACHE_VERSION)")
        return dir
    }

    /** Last native failure text (null when the previous call succeeded). */
    private fun lastError(): String =
        runCatching { nativeLastError() }.getOrNull() ?: "unknown (no native error recorded)"

    /** Per-page timing breakdown of the last successful native run, for tuning. */
    private fun lastStats(): String? = runCatching { nativeLastStats() }.getOrNull()

    private external fun nativeInit(
        modelDir: String,
        modelSuffix: String,
        precisionMode: Int,
        logPath: String?,
    ): Long

    private external fun nativeProcess(handle: Long, input: Bitmap, output: Bitmap): Boolean

    private external fun nativeIsVulkan(handle: Long): Boolean

    private external fun nativeIsFp16(handle: Long): Boolean

    private external fun nativeIsFused(handle: Long): Boolean

    private external fun nativeRelease(handle: Long)

    private external fun nativeAdvanceGeneration()

    private external fun nativeSetPaused(paused: Boolean)

    private external fun nativeLastError(): String?

    private external fun nativeLastStats(): String?
}

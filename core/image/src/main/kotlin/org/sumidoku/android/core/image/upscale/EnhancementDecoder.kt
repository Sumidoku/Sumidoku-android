package org.sumidoku.android.core.image.upscale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import coil3.Extras
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Dimension

/**
 * Coil decoder that upscales page images with waifu2x.
 *
 * Registered on the app's single image loader, but only requests marked by [applyTo] reach it, so
 * covers, thumbnails and plain page loads keep the default decoder and pay nothing. Flow:
 *
 * 1. source dimensions: from the request's page metadata, or a bounds-only decode when unknown
 * 2. [EnhancementCache] hit → return the cached enhanced bitmap (before the trigger, so a layout
 *    change cannot hide a page that was already upscaled)
 * 3. [UpscaleDecision]: not worth it → plain sampled decode of the source
 * 4. miss, but another denoise variant of the page is cached → return that one immediately and
 *    regenerate the requested variant in the background (a denoise switch must not re-run the book)
 * 5. full-res decode → [ImageEnhancer] queue (priority from the request) → cache write
 * 6. any failure falls back to the source bitmap — enhancement must never break page display
 *
 * Diagnostics go to logcat under [APP_TAG]. There is no in-app log screen on this client, so every
 * line is written to be useful from `adb logcat`.
 *
 * Note: the page request must NOT override `diskCacheKey` with the config hash — the source entry
 * in Coil's disk cache is keyed by URL so it stays shared with the disk preloader; the config variant
 * lives in the enhancement-cache key only.
 */
class EnhancementDecoder private constructor(
    private val source: ImageSource,
    private val pageUrl: String,
    private val config: UpscaleConfig,
    private val priority: Int,
    private val options: Options,
    private val context: Context,
    private val cache: EnhancementCache,
    private val enhancer: ImageEnhancer,
    /** Which axis bounds the painted scale; see [UpscaleDecision.displayScale]. */
    private val scaleMode: DisplayScaleMode,
    /** Source dimensions from the page metadata; 0 when unknown and a bounds pass is needed. */
    private val srcWidth: Int,
    private val srcHeight: Int,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val (width, height) = if (srcWidth > 0 && srcHeight > 0) {
            srcWidth to srcHeight
        } else {
            decodeBounds() ?: run {
                // Malformed / unsupported source: leave it to the default decoder's error reporting.
                warn("unreadable source, deferring to default decoder: $pageUrl")
                return null
            }
        }

        val cacheKey = EnhancementCache.cacheKey(pageUrl, config.denoise, config.precision)

        if (priority == ImageEnhancer.PRIORITY_PRELOAD && enhancer.isSlow) {
            // Background precompute cannot keep up on this device. Do not even decode the full
            // source, or every settle would still allocate a multi-megapixel bitmap for nothing.
            return decodeSampled(width, height, PRELOAD_DECODE_TARGET, PRELOAD_DECODE_TARGET)
        }

        // Cache before the trigger: the trigger asks whether the layout would enlarge this page, and
        // the answer changes with the layout, while an enhanced page is better than the source in
        // all of them. Looking the cache up first also guards against re-running a page whose
        // enhancement is already on disk.
        cache.read(cacheKey)?.let {
            info("cache hit (${it.width}x${it.height}): $pageUrl")
            return DecodeResult(it.asImage(), false)
        }

        val (targetWidth, targetHeight) = resolveTargetSize()
        val displayScale = UpscaleDecision.displayScale(scaleMode, width, height, targetWidth, targetHeight)
        val upscale = UpscaleDecision.shouldUpscale(
            width,
            height,
            displayScale,
            config,
            maxTextureWidth = textureLimits.first,
            maxTextureHeight = textureLimits.second,
        )
        info(
            "decision: src=${width}x$height target=${targetWidth}x$targetHeight " +
                "mode=${config.mode} scaleMode=$scaleMode painted=${"%.2f".format(displayScale)} " +
                "threshold=${config.autoTriggerScale} -> upscale=$upscale",
        )
        if (!upscale) {
            // Preload requests only warm the disk source cache: decoding the full page here would
            // burn heap on a bitmap the sized display request would reject anyway.
            return if (priority == ImageEnhancer.PRIORITY_PRELOAD) {
                decodeSampled(width, height, PRELOAD_DECODE_TARGET, PRELOAD_DECODE_TARGET)
            } else {
                decodeSampled(width, height, targetWidth, targetHeight)
            }
        }

        // Stale-while-revalidate: the exact denoise variant is missing (the level was just changed,
        // or the bundled model was bumped), but another level of the same page is usually still on
        // disk. Serve it instead of paying a full native re-run on the critical path, and regenerate
        // the requested variant in the background; ImageEnhancer.readyKeys swaps it in.
        val stale = cache.readNearest(pageUrl, config.denoise, config.precision)
        if (stale != null) {
            info(
                "cache hit, stale denoise=${stale.denoise} for ${config.denoise} " +
                    "(${stale.bitmap.width}x${stale.bitmap.height}): $pageUrl",
            )
            if (!enhancer.isDisabled) {
                // The native input is the full-resolution source; decoding it here costs a few tens
                // of milliseconds against a multi-second re-run, and it cannot be deferred past this
                // frame anyway because ImageSource is single-shot.
                decodeSample(1)?.let { sourceBitmap ->
                    requestEnhancement(cacheKey, sourceBitmap, priority, keepAlso = stale.denoise)
                }
            }
            return DecodeResult(stale.bitmap.asImage(), false)
        }

        // Enhancement input is the full-resolution source: the 2x output IS the detail.
        val sourceBitmap = decodeSample(1) ?: run {
            warn("full-size decode failed, showing source page: $pageUrl")
            return null
        }
        // Do not hold the first frame hostage to a multi-second native run: hand the source bitmap
        // back immediately and let the enhancer fill the cache. The reader watches
        // ImageEnhancer.readyKeys and re-requests this page, which then hits the cache.
        requestEnhancement(cacheKey, sourceBitmap, priority, keepAlso = null)
        return DecodeResult(sourceBitmap.asImage(), false)
    }

    /**
     * Queues the native enhancement of [sourceBitmap] under [cacheKey] and writes the result to the
     * cache. [keepAlso] is the variant that was served instead of the requested one: it stays on disk
     * so toggling the denoise level back and forth remains a hit.
     */
    private fun requestEnhancement(
        cacheKey: String,
        sourceBitmap: Bitmap,
        priority: Int,
        keepAlso: DenoiseLevel?,
    ) {
        enhancer.prefetch(cacheKey, priority) {
            if (!Waifu2xBridge.ensureInitialized(context, config.denoise, config.precision)) {
                warn("engine unavailable (${config.denoise.modelSuffix})")
                return@prefetch null
            }
            val enhanced = Waifu2xBridge.process(sourceBitmap) ?: return@prefetch null
            cache.write(cacheKey, enhanced)
            // Bounded per-page footprint: the requested variant plus the one that was just served.
            cache.pruneVariants(
                pageUrl,
                config.precision,
                buildSet {
                    add(config.denoise)
                    if (keepAlso != null) add(keepAlso)
                },
            )
            info(
                "enhanced: ${sourceBitmap.width}x${sourceBitmap.height} -> " +
                    "${enhanced.width}x${enhanced.height} ($pageUrl)",
            )
            enhanced
        }
    }

    /** Device texture limits (0,0 = unknown), read once from a scratch software canvas. */
    private val textureLimits: Pair<Int, Int> by lazy {
        runCatching {
            val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            canvas.maximumBitmapWidth to canvas.maximumBitmapHeight
        }.getOrDefault(0 to 0)
    }

    /**
     * Backing bytes for repeated decodes. [ImageSource.source] can only be consumed once, so the
     * bounds pass and the real decode share one read: prefer the on-disk path Coil already has
     * (network fetches land in a temp file, disk-cache hits are files), and only buffer the bytes
     * when the source has no file at all.
     */
    private val sourcePath: String? by lazy {
        runCatching { source.file().toString() }.getOrNull()
    }

    private val sourceBytes: ByteArray? by lazy {
        if (sourcePath != null) {
            null
        } else {
            source.sourceOrNull()?.use { input -> runCatching { input.readByteArray() }.getOrNull() }
        }
    }

    /** Request target in px; prefetch requests have no target size, so fall back to the display metrics. */
    private fun resolveTargetSize(): Pair<Int, Int> {
        val width = (options.size.width as? Dimension.Pixels)?.px?.takeIf { it > 0 }
        val height = (options.size.height as? Dimension.Pixels)?.px?.takeIf { it > 0 }
        val metrics = context.resources.displayMetrics
        return (width ?: metrics.widthPixels) to (height ?: metrics.heightPixels)
    }

    /** Plain Coil-style sampled decode for pages that stay below the trigger scale. */
    private fun decodeSampled(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): DecodeResult? {
        val sample = sampleSizeFor(srcWidth, srcHeight, targetWidth, targetHeight)
        val bitmap = decodeSample(sample) ?: return null
        return DecodeResult(bitmap.asImage(), sample > 1)
    }

    private fun sampleSizeFor(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= targetWidth && srcHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun decodeBounds(): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { decode(opts) }
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    private fun decodeSample(sampleSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
        }
        return try {
            decode(opts)
        } catch (e: OutOfMemoryError) {
            Log.e(APP_TAG, "decode failed: $pageUrl (sample=$sampleSize)", e)
            null
        }
    }

    private fun decode(opts: BitmapFactory.Options): Bitmap? {
        val path = sourcePath
        return runCatching {
            if (path != null) {
                BitmapFactory.decodeFile(path, opts)
            } else {
                sourceBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size, opts) }
            }
        }.getOrElse { e ->
            warn("source decode failed (sample=${opts.inSampleSize}): $pageUrl (${e.message})")
            null
        }
    }

    private fun info(message: String) = Log.i(APP_TAG, message)

    private fun warn(message: String) = Log.w(APP_TAG, message)

    class Factory(
        private val context: Context,
        private val cache: EnhancementCache,
        private val enhancer: ImageEnhancer,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val extras = options.extras
            if (extras[KEY_ENABLED] != true) return null
            val pageUrl = extras[KEY_PAGE_URL] ?: return null
            val config = UpscaleConfig(
                mode = enumExtra(extras[KEY_MODE], UpscaleMode.AUTO),
                denoise = enumExtra(extras[KEY_DENOISE], DenoiseLevel.LOW),
                autoTriggerScale = extras[KEY_AUTO_SCALE] ?: UpscaleSettings.DEFAULT_AUTO_TRIGGER_SCALE,
                precision = enumExtra(extras[KEY_PRECISION], UpscalePrecision.AUTO),
            )
            val priority = extras[KEY_PRIORITY] ?: ImageEnhancer.PRIORITY_VISIBLE
            val scaleMode = enumExtra(extras[KEY_SCALE_MODE], DisplayScaleMode.FIT)
            Log.i(
                APP_TAG,
                "marked for upscaling: $pageUrl (mode=${config.mode}, denoise=${config.denoise}, " +
                    "priority=$priority, scaleMode=$scaleMode)",
            )
            return EnhancementDecoder(
                result.source,
                pageUrl,
                config,
                priority,
                options,
                context,
                cache,
                enhancer,
                scaleMode,
                extras[KEY_SRC_WIDTH] ?: 0,
                extras[KEY_SRC_HEIGHT] ?: 0,
            )
        }
    }

    companion object {
        /** In-app log tag, so upscaling diagnostics can be filtered from `adb logcat`. */
        const val APP_TAG = "Sumidoku/Upscale"

        /** Decode size for preloaded pages that do not need upscaling (result is discarded). */
        private const val PRELOAD_DECODE_TARGET = 512

        // Typed request extras (Coil 3 per-request storage); identity-shared with the request builder.
        private val KEY_ENABLED = Extras.Key(default = false)
        private val KEY_PAGE_URL = Extras.Key(default = "")
        private val KEY_MODE = Extras.Key(default = UpscaleMode.AUTO.name)
        private val KEY_DENOISE = Extras.Key(default = DenoiseLevel.LOW.name)
        private val KEY_AUTO_SCALE = Extras.Key(default = UpscaleSettings.DEFAULT_AUTO_TRIGGER_SCALE)
        private val KEY_PRECISION = Extras.Key(default = UpscalePrecision.AUTO.name)
        private val KEY_PRIORITY = Extras.Key(default = ImageEnhancer.PRIORITY_VISIBLE)
        private val KEY_SCALE_MODE = Extras.Key(default = DisplayScaleMode.FIT.name)
        private val KEY_SRC_WIDTH = Extras.Key(default = 0)
        private val KEY_SRC_HEIGHT = Extras.Key(default = 0)

        private inline fun <reified T : Enum<T>> enumExtra(name: String?, fallback: T): T =
            if (name == null) fallback else enumValues<T>().firstOrNull { it.name == name } ?: fallback

        /**
         * Marks [builder] as an upscaling page request and records the config for the decoder.
         *
         * [scaleMode] tells the trigger which axis bounds the painted scale (fitted cell, width- or
         * height-bound image, 1:1, or one half of a split wide page). [srcWidth]/[srcHeight] are the
         * page dimensions the server already reported; pass 0/0 when unknown and the decoder will
         * run a bounds pass.
         */
        fun applyTo(
            builder: ImageRequest.Builder,
            config: UpscaleConfig,
            pageUrl: String,
            priority: Int,
            scaleMode: DisplayScaleMode = DisplayScaleMode.FIT,
            srcWidth: Int = 0,
            srcHeight: Int = 0,
        ) {
            builder.extras.set(KEY_ENABLED, true)
            builder.extras.set(KEY_PAGE_URL, pageUrl)
            builder.extras.set(KEY_MODE, config.mode.name)
            builder.extras.set(KEY_DENOISE, config.denoise.name)
            builder.extras.set(KEY_AUTO_SCALE, config.autoTriggerScale)
            builder.extras.set(KEY_PRECISION, config.precision.name)
            builder.extras.set(KEY_PRIORITY, priority)
            builder.extras.set(KEY_SCALE_MODE, scaleMode.name)
            builder.extras.set(KEY_SRC_WIDTH, srcWidth)
            builder.extras.set(KEY_SRC_HEIGHT, srcHeight)
        }
    }
}

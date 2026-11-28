package org.sumidoku.android.core.image.upscale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil3.disk.DiskCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import org.sumidoku.android.core.data.ServerProfileRepository

/**
 * Disk cache for waifu2x-upscaled page images.
 *
 * Kept separate from Coil's page cache because the key is not a plain URL: it is [pageKey] (page
 * URL + [UpscaleConfig.MODEL_SIGNATURE]) with denoise/precision *variant* suffixes, so changing the
 * model architecture, the scale, the bundled-model version or the native output implementation
 * automatically misses every old entry — no manual purge is ever needed. Stored as PNG to stay
 * lossless; capped at [MAX_SIZE_BYTES] with LRU eviction inside Coil's [DiskCache]. A small
 * in-memory LRU keeps the last pages' enhanced bitmaps decoded, so paging back does not pay a large
 * PNG decode every time.
 *
 * Namespaced per server profile exactly like the app's other per-server data: switching profiles
 * neither mixes nor evicts another profile's enhanced pages, and the directory is rebuilt (previous
 * cache shut down first — two [DiskCache] instances must never share a journal).
 */
@Singleton
class EnhancementCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ServerProfileRepository,
) {

    /** One decoded denoise variant plus the level it was produced at. */
    class Hit(val bitmap: Bitmap, val denoise: DenoiseLevel)

    /** Bytes and entry count of the active profile's on-disk enhanced pages. */
    data class Stats(val bytes: Long = 0L, val count: Int = 0)

    companion object {
        private const val TAG = "Sumidoku/UpscaleCache"

        /** Upscaled pages are ~4x the source pixels; 512 MB holds roughly one long series. */
        const val MAX_SIZE_BYTES = 512L * 1024 * 1024

        /** Decoded enhanced bitmaps kept for paging back; deliberately small enough for 2 GB devices. */
        private const val MAX_MEMORY_BYTES = 64L * 1024 * 1024

        /**
         * Denoise variants kept for one page: the level in use plus the previous one, so A→B→A stays
         * a hit. Bounded because each variant is a full 2x page (~4x the source pixels).
         */
        const val MAX_VARIANTS_PER_PAGE = 2

        private const val PREFS = "upscale-cache"
        private const val KEY_LAYOUT = "layout"

        /** Bumping it clears every enhanced page once, after a key-scheme change. */
        private const val LAYOUT = 1

        private const val ROOT_DIR = "upscale"

        /**
         * Stable per-page part of the cache key: page URL + model signature (architecture, scale,
         * bundled-model version, native output implementation). Deliberately excludes denoise and
         * precision — every variant of a page shares this key, which is what lets a denoise switch
         * fall back to a cached variant without changing the page identity.
         */
        fun pageKey(pageUrl: String): String = sha256(pageUrl, UpscaleConfig.MODEL_SIGNATURE)

        /** One denoise + precision variant of [pageKey]. */
        fun variantKey(pageKey: String, denoise: DenoiseLevel, precision: UpscalePrecision): String =
            "$pageKey#${denoise.raw}@${precision.raw}"

        /** Full cache key for [pageUrl] — what [EnhancementDecoder] looks up and writes. */
        fun cacheKey(pageUrl: String, denoise: DenoiseLevel, precision: UpscalePrecision): String =
            variantKey(pageKey(pageUrl), denoise, precision)

        /** Directory name for one server profile; stable across renames of the profile's display name. */
        fun instanceKey(baseUrl: String): String =
            sha256(baseUrl.trimEnd('/').lowercase()).take(16)

        /**
         * sha256 over the parts, NUL-separated so no two distinct inputs can collide in the framing.
         * Never reversible to the URL, consistent with Coil's page keys.
         */
        private fun sha256(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEachIndexed { index, part ->
                if (index > 0) digest.update(0)
                digest.update(part.toByteArray(Charsets.UTF_8))
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val diskCache = MutableStateFlow<DiskCache?>(null)

    @Volatile
    private var currentDir: File? = null

    @Volatile
    private var currentInstanceKey: String? = null

    // Access-ordered LRU. Bitmaps are immutable and may still be painted elsewhere, so eviction drops
    // the reference and lets GC reclaim it instead of recycling a bitmap Compose might be drawing.
    private val memory = LinkedHashMap<String, Bitmap>(4, 0.75f, true)
    private var memoryBytes = 0L

    init {
        scope.launch {
            runCatching {
                profileRepository.activeProfile
                    .distinctUntilChanged { old, new -> old?.id == new?.id && old?.baseUrl == new?.baseUrl }
                    .collect { profile ->
                        val previous = diskCache.value
                        diskCache.value = null
                        currentDir = null
                        currentInstanceKey = null
                        clearMemory()
                        runCatching { previous?.shutdown() }
                        if (profile != null) {
                            val instanceKey = instanceKey(profile.baseUrl)
                            val dir = File(File(context.cacheDir, ROOT_DIR), instanceKey).apply { mkdirs() }
                            val cache = DiskCache.Builder()
                                .directory(dir.toOkioPath())
                                .maxSizeBytes(MAX_SIZE_BYTES)
                                .build()
                            withContext(Dispatchers.IO) { migrateLayout(cache, instanceKey) }
                            currentDir = dir
                            currentInstanceKey = instanceKey
                            diskCache.value = cache
                        }
                    }
            }.onFailure { Log.w(TAG, "cache namespace collection failed", it) }
        }
    }

    private fun migrateLayout(cache: DiskCache, instanceKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "$KEY_LAYOUT:$instanceKey"
        if (prefs.getInt(key, 0) >= LAYOUT) return
        Log.i(TAG, "layout migration: clearing enhanced cache of $instanceKey (layout < $LAYOUT)")
        runCatching { cache.clear() }
            .onFailure { Log.w(TAG, "layout migration: clear failed", it) }
        prefs.edit().putInt(key, LAYOUT).apply()
    }

    /** The cached bitmap for [cacheKey], or null on a miss / unreadable entry. */
    suspend fun read(cacheKey: String): Bitmap? = withContext(Dispatchers.IO) {
        readMemory(cacheKey) ?: readKey(cacheKey)?.also { putMemory(cacheKey, it) }
    }

    /** Exact-variant hit for [pageUrl] at [denoise] and [precision]. */
    suspend fun read(pageUrl: String, denoise: DenoiseLevel, precision: UpscalePrecision): Bitmap? =
        read(cacheKey(pageUrl, denoise, precision))

    /**
     * Nearest cached variant when [denoise] itself is not cached, ordered by distance in
     * [DenoiseLevel] so the closest look wins. Only variants produced at [precision] are eligible:
     * falling back to the other precision would reintroduce the FP16 artifacts FP32 is meant to
     * escape. Null means the page has no variant at this precision.
     */
    suspend fun readNearest(
        pageUrl: String,
        denoise: DenoiseLevel,
        precision: UpscalePrecision,
    ): Hit? = withContext(Dispatchers.IO) {
        val page = pageKey(pageUrl)
        val candidates = DenoiseLevel.entries.sortedBy { abs(it.ordinal - denoise.ordinal) }
        for (candidate in candidates) {
            if (candidate == denoise) continue
            val key = variantKey(page, candidate, precision)
            val bitmap = readMemory(key) ?: readKey(key)?.also { putMemory(key, it) } ?: continue
            return@withContext Hit(bitmap, candidate)
        }
        null
    }

    suspend fun write(cacheKey: String, bitmap: Bitmap): Unit = withContext(Dispatchers.IO) {
        putMemory(cacheKey, bitmap)
        val cache = diskCache.value ?: return@withContext
        runCatching {
            val editor = cache.openEditor(cacheKey) ?: return@runCatching
            try {
                FileOutputStream(editor.data.toFile()).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                editor.commit()
            } catch (e: Throwable) {
                runCatching { editor.abort() }
                throw e
            }
        }.onFailure { Log.w(TAG, "write failed: $cacheKey", it) }
    }

    /**
     * Drops every variant of [pageUrl] except [keep] at [precision], bounding the per-page footprint
     * to [MAX_VARIANTS_PER_PAGE]. Variants of the other precision are always removed: they are not
     * interchangeable and keeping them only extends the life of a bad driver result.
     */
    suspend fun pruneVariants(
        pageUrl: String,
        precision: UpscalePrecision,
        keep: Set<DenoiseLevel>,
    ): Unit = withContext(Dispatchers.IO) {
        val page = pageKey(pageUrl)
        val keepKeys = keep.mapTo(HashSet()) { variantKey(page, it, precision) }
        for (denoise in DenoiseLevel.entries) {
            for (variantPrecision in UpscalePrecision.entries) {
                val key = variantKey(page, denoise, variantPrecision)
                if (key in keepKeys) continue
                removeMemory(key)
                runCatching { diskCache.value?.remove(key) }
                    .onFailure { Log.w(TAG, "prune failed: $key", it) }
            }
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        clearMemory()
        runCatching { diskCache.value?.clear() }
            .onFailure { Log.w(TAG, "clear failed", it) }
    }

    /** Evict every variant of the given page URLs (used before a book's pages are deleted). */
    suspend fun evictPages(pageUrls: List<String>): Unit = withContext(Dispatchers.IO) {
        val cache = diskCache.value
        for (url in pageUrls) {
            val page = pageKey(url)
            for (denoise in DenoiseLevel.entries) {
                for (precision in UpscalePrecision.entries) {
                    val key = variantKey(page, denoise, precision)
                    removeMemory(key)
                    runCatching { cache?.remove(key) }
                }
            }
        }
    }

    /**
     * Bytes and entry count of the active profile's enhanced pages. Coil's disk cache stores one
     * entry as `<sha>.0` and `<sha>.1` (plus a `journal`); counting distinct sha prefixes yields the
     * entry count without keeping a side index that could drift from what is on disk.
     */
    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val dir = currentDir ?: return@withContext Stats()
        val files = dir.listFiles() ?: return@withContext Stats()
        var bytes = 0L
        val entries = HashSet<String>(files.size)
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (name.startsWith("journal")) continue
            bytes += file.length()
            // A `.tmp` file belongs to an unfinished write and must not be counted as an entry.
            if (name.endsWith(".tmp")) continue
            entries += name.substringBeforeLast('.', name)
        }
        Stats(bytes = bytes, count = entries.size)
    }

    private fun readKey(cacheKey: String): Bitmap? {
        val cache = diskCache.value ?: return null
        return runCatching {
            cache.openSnapshot(cacheKey)?.use { snapshot ->
                BitmapFactory.decodeFile(snapshot.data.toFile().absolutePath)
            }
        }.onFailure { Log.w(TAG, "read failed: $cacheKey", it) }.getOrNull()
    }

    private fun readMemory(key: String): Bitmap? = synchronized(memory) { memory[key] }

    private fun putMemory(key: String, bitmap: Bitmap) {
        val bytes = bitmap.allocationByteCount.toLong()
        synchronized(memory) {
            memory.remove(key)?.let { memoryBytes -= it.allocationByteCount }
            if (bytes > MAX_MEMORY_BYTES) return
            memory[key] = bitmap
            memoryBytes += bytes
            val iterator = memory.entries.iterator()
            while (memoryBytes > MAX_MEMORY_BYTES && iterator.hasNext()) {
                val eldest = iterator.next()
                iterator.remove()
                memoryBytes -= eldest.value.allocationByteCount
            }
        }
    }

    private fun removeMemory(key: String) {
        synchronized(memory) {
            memory.remove(key)?.let { memoryBytes -= it.allocationByteCount }
        }
    }

    private fun clearMemory() {
        synchronized(memory) {
            memory.clear()
            memoryBytes = 0L
        }
    }
}

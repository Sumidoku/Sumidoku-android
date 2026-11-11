package org.sumidoku.android.core.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.sumidoku.android.core.model.ServerProfile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Local offline download engine — design §6 Tier B.
 *  Pulls each queued chapter's CBZ from GET /chapter/{id}/file with the
 *  profile's bearer token into app-private storage and tracks rows in
 *  Room, so the reader can serve pages from disk when offline. Chapters
 *  are processed strictly one at a time (mirrors the server's
 *  serial-per-source rule at the coarsest granularity); page-level
 *  concurrency is unnecessary because the server hands us the whole
 *  archive in one stream. */
@Singleton
class LocalDownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LocalDownloadDatabase,
) {

    private val dao get() = database.localDownloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pumpMutex = Mutex()
    private var pumpJob: Job? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun observeAll() = dao.observeAll()
    fun observeForManga(mangaId: Long) = dao.observeForManga(mangaId)

    suspend fun init() {
        dao.requeueInterrupted()
    }

    suspend fun enqueue(profile: ServerProfile, chapterId: Long, mangaId: Long, mangaTitle: String, chapterLabel: String) {
        val existing = dao.get(chapterId)
        if (existing != null && existing.status != "error") {
            pump(profile)
            return
        }
        dao.upsert(
            LocalDownloadEntity(
                chapterId = chapterId,
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                chapterLabel = chapterLabel,
                path = "",
                bytes = 0L,
                status = "queued",
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        pump(profile)
    }

    suspend fun remove(chapterId: Long) {
        val row = dao.get(chapterId) ?: return
        if (row.path.isNotEmpty()) runCatching { File(row.path).delete() }
        val row2 = dao.get(chapterId)
        if (row2 != null) {
            val dir = File(context.filesDir, "downloads/${row2.mangaId}/$chapterId")
            if (dir.exists()) dir.deleteRecursively()
        }
        dao.delete(chapterId)
    }

    /** Extracted page files for offline reading, in archive order;
     *  null when the chapter has no finished local copy. */
    suspend fun localPages(chapterId: Long): List<File>? {
        val row = dao.get(chapterId) ?: return null
        if (row.status != "done") return null
        val dir = File(context.filesDir, "downloads/${row.mangaId}/$chapterId")
        val files = dir.listFiles()?.sortedBy { it.name } ?: return null
        return files.ifEmpty { null }
    }

    /** Extract the CBZ pages next to the archive so the reader can
     *  serve them as plain files without a zip decoder. */
    private fun extractPages(cbz: File, outDir: File) {
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()
        java.util.zip.ZipFile(cbz).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { entry ->
                    val name = entry.name.substringAfterLast('/').lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".avif")
                }
                .sortedBy { it.name }
                .toList()
            entries.forEachIndexed { index, entry ->
                val out = File(outDir, "%04d_%s".format(index, entry.name.substringAfterLast('/')))
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    suspend fun retry(profile: ServerProfile, chapterId: Long) {
        dao.updateState(chapterId, "queued", null, 0L, "")
        pump(profile)
    }

    /** Kick the serial pump; re-entrant and idempotent. */
    fun pump(profile: ServerProfile) {
        scope.launch {
            pumpMutex.withLock {
                if (pumpJob?.isActive == true) return@withLock
                pumpJob = launch { runQueue(profile) }
            }
        }
    }

    private suspend fun runQueue(profile: ServerProfile) {
        while (true) {
            val next = dao.nextQueued() ?: break
            _active.value = true
            downloadOne(profile, next)
        }
        _active.value = false
    }

    private suspend fun downloadOne(profile: ServerProfile, row: LocalDownloadEntity) {
        val client = org.sumidoku.android.core.network.SumidokuClientFactory()
        val targetDir = File(context.filesDir, "downloads/${row.mangaId}")
        targetDir.mkdirs()
        val target = File(targetDir, "${row.chapterId}.cbz")
        val tmp = File(targetDir, "${row.chapterId}.cbz.tmp")
        dao.updateState(row.chapterId, "downloading", null, 0L, tmp.absolutePath)
        try {
            val url = "${profile.baseUrl.trimEnd('/')}/api/v1/chapter/${row.chapterId}/file"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${profile.apiKey}")
                .build()
            client.okHttp(profile).newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                val body = response.body ?: throw java.io.IOException("empty body")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("rename failed")
            extractPages(target, File(targetDir, "${row.chapterId}"))
            dao.updateState(row.chapterId, "done", null, target.length(), target.absolutePath)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            dao.updateState(row.chapterId, "error", e.message ?: e.toString(), 0L, "")
        }
    }
}

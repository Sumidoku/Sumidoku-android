package org.sumidoku.android.core.stream

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.sumidoku.android.core.api.model.Download
import org.sumidoku.android.core.api.model.UpdateSummary
import org.sumidoku.android.core.model.ServerProfile
import org.sumidoku.android.core.network.SumidokuClientFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Named events of the server's single SSE endpoint `GET /api/v1/events`
 *  (mirror of webui src/api/sse.ts + protocol.ts). The connection opens
 *  with a `snapshot` (full download queue + update summary) and then
 *  streams incremental `download` / `running` / `update*` / `task`
 *  events; the server sends a heartbeat every 15s and supports
 *  Last-Event-ID replay. */
sealed interface ServerEvent {
    /** Initial full state: replaces any locally held queue/summary. */
    data class Snapshot(
        val running: Boolean,
        val downloads: List<Download>,
        val updateSummary: UpdateSummary?,
    ) : ServerEvent

    data class Running(val running: Boolean) : ServerEvent

    /** Incremental progress of one chapter download. */
    data class DownloadProgress(
        val kind: Kind,
        val chapterId: Long,
        val downloaded: Int,
        val total: Int,
    ) : ServerEvent {
        enum class Kind { QUEUED, STARTED, PROGRESS, PAUSED, FINISHED, ERROR }
    }

    data class SummaryChanged(val summary: UpdateSummary) : ServerEvent

    /** One library-update job moved. */
    data class UpdateJob(
        val status: Status,
        val mangaId: Long,
        val mangaTitle: String,
        val sourceId: Long,
        val addedChapters: Int,
    ) : ServerEvent {
        enum class Status { PENDING, RUNNING, COMPLETE, FAILED, SKIPPED }
    }

    data object UpdateComplete : ServerEvent

    /** An unrecognized named event (e.g. task) that consumers may ignore. */
    data class Other(val name: String) : ServerEvent

    data object Connected : ServerEvent

    data object Disconnected : ServerEvent
}

@Singleton
class EventsStream @Inject constructor(
    private val clientFactory: SumidokuClientFactory,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Cold flow of server events for [profile]; reconnects with backoff
     *  and replays missed events via Last-Event-ID until cancelled. */
    fun events(profile: ServerProfile): Flow<ServerEvent> = callbackFlow {
        val baseUrl = profile.baseUrl.trimEnd('/')
        val client = clientFactory
            .okHttp(profile)
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE: no read timeout
            .build()
        val factory = EventSources.createFactory(client)

        var lastEventId: String? = null
        var source: EventSource? = null
        var backoffMs = 1_000L

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                backoffMs = 1_000L
                trySend(ServerEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (id != null) lastEventId = id
                val event = parse(type, data) ?: return
                trySend(event)
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(ServerEvent.Disconnected)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                trySend(ServerEvent.Disconnected)
            }
        }

        while (isActive) {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/api/v1/events")
            lastEventId?.let { requestBuilder.header("Last-Event-ID", it) }
            var closed = false
            val wrapped = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) =
                    listener.onOpen(eventSource, response)

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) =
                    listener.onEvent(eventSource, id, type, data)

                override fun onClosed(eventSource: EventSource) {
                    closed = true
                    listener.onClosed(eventSource)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    closed = true
                    listener.onFailure(eventSource, t, response)
                }
            }
            source = factory.newEventSource(requestBuilder.build(), wrapped)
            // Park until the connection closes or fails, then reconnect.
            while (isActive && !closed) {
                delay(500)
            }
            if (!isActive) break
            trySend(ServerEvent.Disconnected)
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }

        awaitClose {
            source?.cancel()
        }
    }

    private fun parse(type: String?, data: String): ServerEvent? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (type) {
            "snapshot" -> ServerEvent.Snapshot(
                running = obj["running"]?.jsonPrimitive?.boolean ?: false,
                downloads = runCatching {
                    json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(Download.serializer()),
                        obj["downloads"]!!.jsonArray,
                    )
                }.getOrDefault(emptyList()),
                updateSummary = obj["updateSummary"]?.let {
                    runCatching { json.decodeFromJsonElement(UpdateSummary.serializer(), it) }.getOrNull()
                },
            )
            "running" -> ServerEvent.Running(obj["running"]?.jsonPrimitive?.boolean ?: false)
            "download" -> ServerEvent.DownloadProgress(
                kind = when (obj["type"]?.jsonPrimitive?.content) {
                    "queued" -> ServerEvent.DownloadProgress.Kind.QUEUED
                    "started" -> ServerEvent.DownloadProgress.Kind.STARTED
                    "progress" -> ServerEvent.DownloadProgress.Kind.PROGRESS
                    "paused" -> ServerEvent.DownloadProgress.Kind.PAUSED
                    "finished" -> ServerEvent.DownloadProgress.Kind.FINISHED
                    else -> ServerEvent.DownloadProgress.Kind.ERROR
                },
                chapterId = obj["chapterId"]?.jsonPrimitive?.long ?: return null,
                downloaded = obj["downloaded"]?.jsonPrimitive?.int ?: 0,
                total = obj["total"]?.jsonPrimitive?.int ?: 0,
            )
            "updateSummary" -> runCatching {
                ServerEvent.SummaryChanged(json.decodeFromJsonElement(UpdateSummary.serializer(), obj))
            }.getOrNull()
            "update" -> ServerEvent.UpdateJob(
                status = when (obj["status"]?.jsonPrimitive?.content) {
                    "pending" -> ServerEvent.UpdateJob.Status.PENDING
                    "running" -> ServerEvent.UpdateJob.Status.RUNNING
                    "complete" -> ServerEvent.UpdateJob.Status.COMPLETE
                    "skipped" -> ServerEvent.UpdateJob.Status.SKIPPED
                    else -> ServerEvent.UpdateJob.Status.FAILED
                },
                mangaId = obj["mangaId"]?.jsonPrimitive?.long ?: return null,
                mangaTitle = obj["mangaTitle"]?.jsonPrimitive?.content ?: "",
                sourceId = obj["sourceId"]?.jsonPrimitive?.long ?: 0L,
                addedChapters = obj["addedChapters"]?.jsonPrimitive?.int ?: 0,
            )
            "updateComplete" -> ServerEvent.UpdateComplete
            else -> type?.let { ServerEvent.Other(it) }
        }
    }
}

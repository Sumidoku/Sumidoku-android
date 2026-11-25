package org.sumidoku.android.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Where unhandled coroutine failures end up.
 *
 * A [CoroutineScope] built on a plain `SupervisorJob` — `viewModelScope` is one —
 * has no [CoroutineExceptionHandler]. An exception that escapes a `launch` body
 * is therefore handed to the thread's default handler, and on Android that kills
 * the process: no red screen, no snackbar, just a crash.
 *
 * That is exactly how an unreachable server used to take the app down the moment
 * a detail page tried to load. The network call threw inside a coroutine, the
 * surrounding `try` / `catch` could not see it (the failure surfaced on a *child*
 * coroutine started with `async`), and the process died instead of showing the
 * "could not reach server" state the UI already knows how to render.
 *
 * [launchGuarded] attaches [SafeCoroutineHandler] to the coroutine it starts, so
 * the same failure is recorded here and logged while the screen keeps working.
 */
object CoroutineFailures {

    /** How many failures to keep for diagnostics grabs (`adb shell dumpsys`, tests). */
    private const val MAX_RECENT = 20

    private val recent = ArrayDeque<Entry>(MAX_RECENT)

    /**
     * Set once from `Application.onCreate` to route failures to the platform log.
     * Defaults to printing, which keeps JVM unit tests and previews useful.
     */
    var sink: (Throwable) -> Unit = { error -> error.printStackTrace() }

    /** Records a failure that no `try` / `runCatching` handled. Never rethrows. */
    @Synchronized
    fun record(error: Throwable) {
        if (error is CancellationException) return
        while (recent.size >= MAX_RECENT) recent.removeFirst()
        recent.addLast(Entry(System.currentTimeMillis(), error))
        // A broken sink must not turn a handled failure back into a crash.
        runCatching { sink(error) }
    }

    /** Recent failures, oldest first. Copy, so callers cannot mutate the ring. */
    @Synchronized
    fun recent(): List<Entry> = recent.toList()

    @Synchronized
    fun clear() {
        recent.clear()
    }

    data class Entry(val atMillis: Long, val error: Throwable)
}

/**
 * Reports instead of crashing. Cancellation is normal control flow (a screen left,
 * a request superseded), not a failure worth logging as one.
 */
val SafeCoroutineHandler = CoroutineExceptionHandler { _, error ->
    if (error !is CancellationException) CoroutineFailures.record(error)
}

/**
 * [launch], except an escaping exception is reported through [CoroutineFailures]
 * instead of reaching the thread's uncaught-exception handler.
 *
 * Prefer this over `launch` in every view model. It does not make an error
 * invisible — it makes it loggable and non-fatal — and it is still correct to
 * `try` / `catch` around the work so the UI can show a specific message.
 */
fun CoroutineScope.launchGuarded(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context + SafeCoroutineHandler, start = start, block = block)

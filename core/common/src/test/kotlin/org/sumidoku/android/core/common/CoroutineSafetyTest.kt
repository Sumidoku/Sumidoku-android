package org.sumidoku.android.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The contract behind [launchGuarded]: a failure that no `try` handled is reported
 * to [CoroutineFailures] instead of reaching the thread's uncaught-exception
 * handler (which is a process crash on Android).
 */
class CoroutineSafetyTest {

    private val reported = AtomicReference<Throwable?>(null)
    private val originalSink = CoroutineFailures.sink

    @Before
    fun setUp() {
        reported.set(null)
        CoroutineFailures.clear()
        CoroutineFailures.sink = { error -> reported.set(error) }
    }

    @After
    fun tearDown() {
        CoroutineFailures.sink = originalSink
        CoroutineFailures.clear()
    }

    @Test
    fun `a failure escaping launchGuarded is reported, not thrown at the thread`() {
        val latch = CountDownLatch(1)
        CoroutineFailures.sink = { error ->
            reported.set(error)
            latch.countDown()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launchGuarded { error("manga request failed") }

        assertTrue("the failure should reach the sink", latch.await(5, TimeUnit.SECONDS))
        assertEquals("manga request failed", reported.get()?.message)
        assertEquals(1, CoroutineFailures.recent().size)
    }

    @Test
    fun `children of a guarded coroutine inherit the guard`() {
        val latch = CountDownLatch(1)
        CoroutineFailures.sink = { error ->
            reported.set(error)
            latch.countDown()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launchGuarded {
            launch {
                delay(10)
                error("chapter request failed")
            }
            delay(5_000)
        }

        assertTrue("the nested failure should reach the sink", latch.await(5, TimeUnit.SECONDS))
        assertEquals("chapter request failed", reported.get()?.message)
    }

    @Test
    fun `cancelling a guarded coroutine reports nothing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launchGuarded { delay(10_000) }

        job.cancel()
        job.join()

        assertTrue("cancellation is not a failure", CoroutineFailures.recent().isEmpty())
    }

    @Test
    fun `cancellation recorded directly is ignored`() {
        CoroutineFailures.record(CancellationException("superseded"))

        assertTrue(CoroutineFailures.recent().isEmpty())
    }
}

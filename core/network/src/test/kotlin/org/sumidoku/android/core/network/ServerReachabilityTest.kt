package org.sumidoku.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerReachabilityTest {

    @Test
    fun `starts online`() {
        val reachability = ServerReachability()
        assertFalse(reachability.offline.value)
    }

    @Test
    fun `goes offline only at the threshold of consecutive failures`() {
        val reachability = ServerReachability()
        repeat(ServerReachability.OFFLINE_THRESHOLD - 1) { reachability.recordFailure() }
        assertFalse(reachability.offline.value)
        reachability.recordFailure()
        assertTrue(reachability.offline.value)
    }

    @Test
    fun `a success resets the consecutive failure count`() {
        val reachability = ServerReachability()
        repeat(ServerReachability.OFFLINE_THRESHOLD - 1) { reachability.recordFailure() }
        reachability.recordSuccess()
        repeat(ServerReachability.OFFLINE_THRESHOLD - 1) { reachability.recordFailure() }
        // Still below the threshold: the success in the middle reset the count.
        assertFalse(reachability.offline.value)
        reachability.recordFailure()
        assertTrue(reachability.offline.value)
    }

    @Test
    fun `success recovers instantly from offline`() {
        val reachability = ServerReachability()
        repeat(ServerReachability.OFFLINE_THRESHOLD) { reachability.recordFailure() }
        assertTrue(reachability.offline.value)
        reachability.recordSuccess()
        assertFalse(reachability.offline.value)
    }

    @Test
    fun `reset clears the failure count and the offline flag`() {
        val reachability = ServerReachability()
        repeat(ServerReachability.OFFLINE_THRESHOLD) { reachability.recordFailure() }
        assertTrue(reachability.offline.value)
        reachability.reset()
        assertFalse(reachability.offline.value)
        // The count is back to zero: a full new streak is required.
        repeat(ServerReachability.OFFLINE_THRESHOLD - 1) { reachability.recordFailure() }
        assertFalse(reachability.offline.value)
    }
}

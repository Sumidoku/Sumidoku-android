package org.sumidoku.android.core.network

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global "can we reach the server" flag, fed by the reachability interceptor inside
 * SumidokuClientFactory: any HTTP response — even an error status — proves the server
 * answered, while a connection-level failure (all failover candidates exhausted)
 * counts towards a threshold before the app declares itself offline, so one flaky
 * request never flashes the banner.
 *
 * Screens collect [offline] to show the banner and to re-refresh on recovery; the
 * banner's retry fires one lightweight probe through the instrumented client, whose
 * response (or failure) does the bookkeeping here.
 */
@Singleton
class ServerReachability @Inject constructor() {

    private val failures = AtomicInteger(0)

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Any HTTP response arrived — the server answered, whatever the status code. */
    fun recordSuccess() {
        failures.set(0)
        _offline.value = false
    }

    /** One request failed at the connection level after every candidate was tried. */
    fun recordFailure() {
        if (failures.incrementAndGet() >= OFFLINE_THRESHOLD) {
            _offline.value = true
        }
    }

    /** Fresh start when the active server changes. */
    fun reset() {
        failures.set(0)
        _offline.value = false
    }

    companion object {
        const val OFFLINE_THRESHOLD = 3
    }
}

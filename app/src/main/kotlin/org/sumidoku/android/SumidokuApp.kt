package org.sumidoku.android

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.sumidoku.android.core.common.CoroutineFailures

@HiltAndroidApp
class SumidokuApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // View-model scopes carry no exception handler, so anything that escapes a
        // coroutine body used to kill the process. launchGuarded() routes those here.
        CoroutineFailures.sink = { error ->
            Log.e(LOG_TAG, "unhandled coroutine failure: ${error.message}", error)
        }
    }

    private companion object {
        const val LOG_TAG = "Sumidoku/Coroutine"
    }
}

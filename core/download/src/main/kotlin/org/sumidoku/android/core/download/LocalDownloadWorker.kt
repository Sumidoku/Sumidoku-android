package org.sumidoku.android.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.sumidoku.android.core.data.ServerProfileRepository

/** WorkManager continuation for the local offline queue (design §14
 *  M3): after process death the queued rows resume here — interrupted
 *  "downloading" rows are requeued and the serial pump runs until the
 *  queue drains, with a network constraint supplied by the engine's
 *  scheduleWork. */
class LocalDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DownloadEntryPoint::class.java,
            )
            val profile = entryPoint.profileRepository().activeProfile.first()
                ?: return Result.success()
            entryPoint.localDownloadEngine().runUntilIdle(profile)
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadEntryPoint {
        fun localDownloadEngine(): LocalDownloadEngine
        fun profileRepository(): ServerProfileRepository
    }
}

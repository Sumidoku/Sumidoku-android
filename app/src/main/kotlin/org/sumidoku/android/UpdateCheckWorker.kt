package org.sumidoku.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.sumidoku.android.core.api.UpdatesApi
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.network.SumidokuClientFactory
import retrofit2.await
import java.util.concurrent.TimeUnit

/** Periodic background check for new chapters — design §14 M4.
 *  The server runs its own update scheduler, so the worker only polls
 *  GET /update/recent and notifies when entries newer than the last
 *  seen timestamp appear. Tapping the notification opens the app. */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): ServerProfileRepository
        fun clientFactory(): SumidokuClientFactory
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val profile = deps.repository().activeProfile.first() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        return try {
            val recent = deps.clientFactory().create<UpdatesApi>(profile)
                .getRecentUpdates(page = 1)
                .await()
            val fresh = recent.propertyEntries.filter { it.fetchedAt > lastSeen }
            val newest = recent.propertyEntries.maxOfOrNull { it.fetchedAt } ?: lastSeen
            if (fresh.isNotEmpty() && lastSeen > 0L) {
                notifyNewChapters(fresh.size, fresh.first().manga.title)
            }
            prefs.edit().putLong(KEY_LAST_SEEN, newest).apply()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(Data.Builder().build())
        }
    }

    private fun notifyNewChapters(count: Int, firstTitle: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "章节更新", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count == 1) "「$firstTitle」有新章节" else "$count 部漫画有新章节"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sumidoku 更新")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFS = "update_check"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

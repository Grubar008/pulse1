package com.pulse.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.pulse.app.data.source.DataSource
import com.pulse.app.data.source.file.FileImportSource
import com.pulse.app.data.source.health.HealthConnectSource
import com.pulse.app.data.source.mock.MockDataSource
import com.pulse.app.notify.NotificationHelper
import java.time.Duration
import java.time.LocalDate

@HiltWorker
class DailySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: SyncCoordinator,
    private val health: HealthConnectSource,
    private val notifications: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sources = listOf<DataSource>(health, FileImportSource())
        return try {
            val outcome = coordinator.sync(sources)
            notifications.maybeNotifyRecovery(outcome.recoveryToday)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "pulse_periodic_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(Duration.ofMinutes(15))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object DemoHint {
    fun mockSource(): DataSource = MockDataSource()
}

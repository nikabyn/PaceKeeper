package org.htwk.pacing.backend.data_collection

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Hilfsklasse zur Planung des HealthSyncWorker.
 * Sorgt dafür, dass die Synchronisierung periodisch im Hintergrund läuft.
 */
object HealthConnectWorkerScheduler {
    fun scheduleHealthSync(wm: WorkManager) {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            1, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        ).build()

        wm.enqueueUniquePeriodicWork(
            "HealthSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
package org.htwk.pacing.backend.data_collection

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Hilfsklasse zur Planung des HealthSyncWorker.
 * Sorgt dafür, dass die Synchronisierung periodisch im Hintergrund läuft.
 */
object HealthConnectWorkerScheduler{

    fun scheduleHealthSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            1, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HealthSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

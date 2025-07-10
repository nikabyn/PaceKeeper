package org.htwk.pacing.backend.data_collection

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.ZonedDateTime

/**
 * Periodischer Hintergrund-Worker, der Health Connect-Daten synchronisiert.
 * Nutzt zentralen Helper und prüft Berechtigungen zur Laufzeit.
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HealthSyncWorker"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        return try {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()

            if (!granted.containsAll(HealthConnectHelper.requiredPermissions)) {
                Log.w(TAG, "Nicht die notwendigen Berechtigungen für den Zugriff auf Health Connect")
                return Result.failure()
            }

            val now = ZonedDateTime.now()
            val start = now.minusHours(1).toInstant()
            val end = now.toInstant()

            HealthConnectHelper.readAvgHeartRate(context, start, end)
            HealthConnectHelper.readHeartRateSamples(context, start, end)
            HealthConnectHelper.readStepsSum(context, start, end)

            Log.d(TAG, "Health data sync erfolgreich")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Health data sync gescheitert", e)
            Result.retry()
        }
    }
}

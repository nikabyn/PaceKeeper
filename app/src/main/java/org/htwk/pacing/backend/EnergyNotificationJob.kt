package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object EnergyNotificationJob {
    const val TAG = "EnergyNotificationJob"

    suspend fun run(context: Context, db: PacingDatabase) {
        while (true) {
            delay(1.minutes)

            val predictedEnergy = getRelevantPredictedEnergyLevel(db.predictedEnergyLevelDao())
                ?: 1.0 // if no data available, assume energy is ok and thus display no warning

            Log.d(TAG, "Predicted Energy level of %.2f".format(predictedEnergy))

            if (predictedEnergy < 0.2) {
                Log.d(TAG, "Energy is low, showing notification")
                showNotification(context)
            } else {
                Log.d(TAG, "Energy is sufficient, no notification")
            }
        }
    }


    private suspend fun getRelevantPredictedEnergyLevel(predictedEnergyLevelDao: PredictedEnergyLevelDao): Double? {
        val now = Clock.System.now()
        val energyLevelDataWindow: List<PredictedEnergyLevelEntry> =
            predictedEnergyLevelDao.getInRange(now, now + 6.hours)
        val minimumEntry =
            energyLevelDataWindow.minByOrNull { it.percentage.toDouble() }

        return minimumEntry?.percentage?.toDouble()
    }
}

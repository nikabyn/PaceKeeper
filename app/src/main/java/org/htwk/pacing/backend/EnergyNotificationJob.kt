package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.htwk.pacing.backend.EnergyNotificationJob.THRESHOLD
import org.htwk.pacing.backend.EnergyNotificationJob.checkEvery
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.UserProfileRepository
import kotlin.time.Duration.Companion.minutes

/**
 * Continuously monitors predicted energy levels and displays notifications when energy is low.
 *
 * Designed to run indefinitely in a coroutine scope. Assumes continuous availability of
 * predicted energy levels from [EnergyPredictionJob].
 */
object EnergyNotificationJob {
    const val TAG = "EnergyNotificationJob"

    private val checkEvery = 5.minutes
    private const val THRESHOLD = 0.2

    /**
     * Entry point for the [EnergyNotificationJob].
     *
     * - Checks predicted energy every [checkEvery].
     * - Retrieves predicted energy levels from the database.
     * - Shows a notification if the predicted energy falls below the [THRESHOLD].
     *
     * @param context Application context used to display notifications.
     * @param db [PacingDatabase] used to fetch predicted energy levels.
     */
    suspend fun run(
        context: Context,
        db: PacingDatabase,
        userProfileRepository: UserProfileRepository
    ) {
        while (true) {
            delay(checkEvery)

            // if no data available, assume energy is ok and thus display no warning
            val predictedEnergy = getRelevantPredictedEnergyLevel(db.predictedEnergyLevelDao())
                ?: continue

            Log.d(TAG, "Predicted Energy level of %.2f".format(predictedEnergy))

            if (predictedEnergy < THRESHOLD) {
                Log.d(TAG, "Energy is low, showing notification")
                showNotification(context, userProfileRepository)
            } else {
                Log.d(TAG, "Energy is sufficient, no notification")
            }
        }
    }

    /**
     * Retrieves the most relevant predicted energy level within a 6-hour future window.
     *
     * - Queries the [predictedEnergyLevelDao] for energy levels from the current time
     *   to 6 hours ahead.
     * - Returns the minimum value in this window as the most critical energy prediction.
     *
     * @param predictedEnergyLevelDao DAO used to query predicted energy levels.
     * @return The minimum predicted energy level as a [Double], or null if no data is available.
     * @throws Exception Any database exceptions are propagated to the caller.
     */
    private suspend fun getRelevantPredictedEnergyLevel(
        predictedEnergyLevelDao: PredictedEnergyLevelDao
    ): Double? {
        val latestEntry = predictedEnergyLevelDao.getLatest()
        return latestEntry?.percentageFuture?.toDouble()
    }
}
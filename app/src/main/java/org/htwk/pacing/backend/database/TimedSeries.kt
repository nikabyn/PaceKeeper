package org.htwk.pacing.backend.database

import androidx.room.Dao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@Dao
interface TimedSeries<E> {
    suspend fun getInRange(begin: Instant, end: Instant): List<E>

    /**
     * Emits `null` every time the data in the table changes.
     */
    fun getChangeTrigger(): Flow<Int?>

    fun getLastLive(duration: Duration): Flow<List<E>> =
        getChangeTrigger().map {
            val now = Clock.System.now()
            getInRange(now.minus(duration), now)
        }
}
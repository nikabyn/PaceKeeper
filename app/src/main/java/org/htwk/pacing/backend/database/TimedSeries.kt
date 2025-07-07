package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@Dao
interface TimedSeries<E> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: E)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMany(entries: List<E>)

    suspend fun deleteAll()

    suspend fun getAll(): List<E>

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

    // TODO / REVIEW: is this ok?
    fun getAllLive(): Flow<List<E>> =
        getChangeTrigger().map {
            getAll()
        }
}
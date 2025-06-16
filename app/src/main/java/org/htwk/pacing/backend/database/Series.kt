package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HeartRateEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMany(entries: List<HeartRateEntry>)

    @Query("delete from heart_rate")
    suspend fun deleteAllEntries()

    @Query("select * from heart_rate")
    suspend fun getAllEntries(): List<HeartRateEntry>

    @Query("select * from heart_rate where time between :begin and :end")
    suspend fun getEntriesInRange(begin: Instant, end: Instant): List<HeartRateEntry>

    fun getLastEntriesLive(duration: Duration): Flow<List<HeartRateEntry>> = flow {
        while (true) {
            val now = Clock.System.now()
            val begin = now.minus(duration)
            emit(getEntriesInRange(begin, now))
            delay(250)
        }
    }
}

@Entity(tableName = "heart_rate")
data class HeartRateEntry(
    val value: Double,
    @PrimaryKey
    val time: Instant,
)
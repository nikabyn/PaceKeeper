package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /**
     * Emits `null` every time the data in the table changes.
     */
    @Query("select null from heart_rate")
    fun getChangeTrigger(): Flow<Int?>

    fun getLastEntriesLive(duration: Duration): Flow<List<HeartRateEntry>> =
        getChangeTrigger().map {
            val now = Clock.System.now()
            getEntriesInRange(now.minus(duration), now)
        }
}

@Entity(tableName = "heart_rate")
data class HeartRateEntry(
    val value: Double,
    @PrimaryKey
    val time: Instant,
)
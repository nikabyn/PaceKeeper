package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "heart_rate_10min")
data class Heart10MinRateEntry(
    @PrimaryKey
    val time: Instant,
    val bpm: Float,
)

@Dao
interface HeartRate10MinDao : TimedSeries<HeartRateEntry> {
    @Query("delete from heart_rate_10min")
    override suspend fun deleteAll()

    @Query("select * from heart_rate_10min")
    override suspend fun getAll(): List<HeartRateEntry>

    @Query("select * from heart_rate_10min where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<HeartRateEntry>

    @Query("update heart_rate_10min set bpm = :bpm where time = (select max(time) from heart_rate_10min)")
    suspend fun updateMostRecentBpm(bpm: Float)

    @Query("delete from heart_rate_10min where time < :time")
    suspend fun deleteBefore(time: Instant): Flow<Int?>

    @Query("select * from heart_rate_10min order by time desc limit 1")
    suspend fun getMostRecent(): Flow<HeartRateEntry?>

    @Query("select null from heart_rate_10min")
    override fun getChangeTrigger(): Flow<Int?>
}

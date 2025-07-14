package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Entity(tableName = "predicted_heart_rate")
data class PredictedHeartRateEntry(
    @PrimaryKey
    val time: Instant,
    val bpm: Long,
)

@Dao
interface PredictedHeartRateDao : TimedSeries<PredictedHeartRateEntry> {
    @Query("delete from predicted_heart_rate")
    override suspend fun deleteAll()

    @Query("select * from predicted_heart_rate")
    override suspend fun getAll(): List<PredictedHeartRateEntry>

    @Query("select * from predicted_heart_rate where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<PredictedHeartRateEntry>

    @Query("select null from predicted_heart_rate")
    override fun getChangeTrigger(): Flow<Int?>

    fun getAllLive(): Flow<List<PredictedHeartRateEntry>> =
        getChangeTrigger().map {
            getAll()
        }
}

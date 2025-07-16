package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
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
) : TimedEntry {
    override val start get() = time
    override val end get() = time
}

@Dao
interface PredictedHeartRateDao : TimedSeries<PredictedHeartRateEntry> {
    @Query("delete from predicted_heart_rate")
    override suspend fun deleteAll()

    @Query("select * from predicted_heart_rate")
    override suspend fun getAll(): List<PredictedHeartRateEntry>

    @Query("select * from predicted_heart_rate order by time desc limit 1")
    override suspend fun getLatest(): PredictedHeartRateEntry?

    @Query("select * from predicted_heart_rate where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<PredictedHeartRateEntry>

    @Query("select null from predicted_heart_rate")
    override fun getChangeTrigger(): Flow<Int?>

    /* normally, accessing the whole table as a live flow should not be done as it's too slow for
    large amounts of data, but it's fine here as the prediction table is just MLModel::INPUT_Size */
    fun getAllLive(): Flow<List<PredictedHeartRateEntry>> =
        getChangeTrigger().map {
            getAll()
        }
}

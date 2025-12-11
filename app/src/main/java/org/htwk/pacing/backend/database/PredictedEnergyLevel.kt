package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Entity(tableName = "predicted_energy_level")
data class PredictedEnergyLevelEntry(
    @PrimaryKey
    val time: Instant,
    val percentageNow: Percentage,

    val timeFuture: Instant,
    val percentageFuture: Percentage,
) : TimedEntry {
    override val start get() = time
    override val end get() = time
}

@Dao
interface PredictedEnergyLevelDao :
    TimedSeries<PredictedEnergyLevelEntry> {
    @Query("delete from predicted_energy_level")
    override suspend fun deleteAll()

    @Query("select * from predicted_energy_level")
    override suspend fun getAll(): List<PredictedEnergyLevelEntry>

    @Query("select * from predicted_energy_level order by time desc limit 1")
    override suspend fun getLatest(): PredictedEnergyLevelEntry?

    @Query("select * from predicted_energy_level where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<PredictedEnergyLevelEntry>

    @Query("select null from predicted_energy_level")
    override fun getChangeTrigger(): Flow<Int?>

    /**
     * normally, accessing the whole table as a live flow should not be done as it's too slow for
     * large amounts of data, but it's fine here as the prediction table is just MLModel::INPUT_Size
     */
    fun getAllLive(): Flow<List<PredictedEnergyLevelEntry>> =
        getChangeTrigger().map {
            getAll()
        }
}

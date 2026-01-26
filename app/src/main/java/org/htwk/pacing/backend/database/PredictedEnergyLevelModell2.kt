package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Predicted energy level entry for Model 2 (HR-based with HRV and anchoring).
 * 
 */
@Entity(tableName = "predicted_energy_level_modell2")
data class PredictedEnergyLevelEntryModell2(
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
interface PredictedEnergyLevelModell2Dao :
    TimedSeries<PredictedEnergyLevelEntryModell2> {
    @Query("delete from predicted_energy_level_modell2")
    override suspend fun deleteAll()

    @Query("select * from predicted_energy_level_modell2")
    override suspend fun getAll(): List<PredictedEnergyLevelEntryModell2>

    @Query("select * from predicted_energy_level_modell2 order by time desc limit 1")
    override suspend fun getLatest(): PredictedEnergyLevelEntryModell2?

    @Query("select * from predicted_energy_level_modell2 where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<PredictedEnergyLevelEntryModell2>

    @Query("select null from predicted_energy_level_modell2")
    override fun getChangeTrigger(): Flow<Int?>

    fun getAllLive(): Flow<List<PredictedEnergyLevelEntryModell2>> =
        getChangeTrigger().map {
            getAll()
        }
}

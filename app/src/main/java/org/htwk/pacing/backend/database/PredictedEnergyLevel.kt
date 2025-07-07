package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "predicted_energy_level")
data class PredictedEnergyLevelEntry(
    @PrimaryKey
    val time: Instant,
    val percentage: Percentage,
)

@Dao
interface PredictedEnergyLevelDao : TimedSeries<PredictedEnergyLevelEntry> {
    @Query("delete from predicted_energy_level")
    override suspend fun deleteAll()

    @Query("select * from predicted_energy_level")
    override suspend fun getAll(): List<PredictedEnergyLevelEntry>

    @Query("select * from predicted_energy_level where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<PredictedEnergyLevelEntry>

    @Query("select null from predicted_energy_level")
    override fun getChangeTrigger(): Flow<Int?>
}

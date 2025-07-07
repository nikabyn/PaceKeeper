package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "oxygen_saturation")
data class OxygenSaturationEntry(
    @PrimaryKey
    val time: Instant,
    val percentage: Percentage,
)

@Dao
interface OxygenSaturationDao : TimedSeries<OxygenSaturationEntry> {
    @Query("delete from oxygen_saturation")
    override suspend fun deleteAll()

    @Query("select * from oxygen_saturation")
    override suspend fun getAll(): List<OxygenSaturationEntry>

    @Query("select * from oxygen_saturation where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<OxygenSaturationEntry>


    @Query("select null from oxygen_saturation")
    override fun getChangeTrigger(): Flow<Int?>
}

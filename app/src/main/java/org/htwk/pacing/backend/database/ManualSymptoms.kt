package org.htwk.pacing.backend.database

import androidx.annotation.IntRange
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

enum class Feeling(@IntRange(from = 0, to = 3) val level: Int) {
    VeryBad(3),
    Bad(2),
    Good(1),
    VeryGood(0),
}

@Entity(tableName = "manual_symptoms")
data class ManualSymptomsEntry(
    @PrimaryKey
    val time: Instant,
    val feeling: Feeling,
    val symptoms: Array<String>,
)

@Dao
interface ManualSymptomsDao : TimedSeries<ManualSymptomsEntry> {
    @Query("delete from energy_level")
    override suspend fun deleteAll()

    @Query("select * from energy_level")
    override suspend fun getAll(): List<ManualSymptomsEntry>

    @Query("select * from energy_level where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<ManualSymptomsEntry>


    @Query("select null from energy_level")
    override fun getChangeTrigger(): Flow<Int?>
}

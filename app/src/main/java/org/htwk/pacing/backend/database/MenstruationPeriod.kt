package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "menstruation_period")
data class MenstruationPeriodEntry(
    @PrimaryKey
    val start: Instant,
    val end: Instant,
)

@Dao
interface MenstruationPeriodDao : TimedSeries<MenstruationPeriodEntry> {
    @Query("delete from menstruation_period")
    override suspend fun deleteAll()

    @Query("select * from menstruation_period")
    override suspend fun getAll(): List<MenstruationPeriodEntry>

    @Query("""select * from menstruation_period where "start" <= :end and "end" >= :begin""")
    override suspend fun getInRange(begin: Instant, end: Instant): List<MenstruationPeriodEntry>

    @Query("select null from menstruation_period")
    override fun getChangeTrigger(): Flow<Int?>
}

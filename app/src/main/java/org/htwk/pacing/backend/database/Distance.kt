package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "distance")
data class DistanceEntry(
    @PrimaryKey
    val start: Instant,
    val end: Instant,
    val length: Length,
)

@Dao
interface DistanceDao : TimedSeries<DistanceEntry> {
    @Query("delete from distance")
    suspend fun deleteAll()

    @Query("select * from distance")
    suspend fun getAll(): List<DistanceEntry>

    @Query("""select * from distance where "start" <= :end and "end" >= :begin""")
    override suspend fun getInRange(begin: Instant, end: Instant): List<DistanceEntry>

    @Query("select null from distance")
    override fun getChangeTrigger(): Flow<Int?>
}

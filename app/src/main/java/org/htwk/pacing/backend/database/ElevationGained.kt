package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "elevation_gained")
data class ElevationGainedEntry(
    @PrimaryKey
    val start: Instant,
    val end: Instant,
    val length: Length,
)

@Dao
interface ElevationGainedDao : TimedSeries<ElevationGainedEntry> {
    @Query("delete from elevation_gained")
    override suspend fun deleteAll()

    @Query("select * from elevation_gained")
    override suspend fun getAll(): List<ElevationGainedEntry>

    @Query("""select * from elevation_gained where "start" <= :end and "end" >= :begin""")
    override suspend fun getInRange(begin: Instant, end: Instant): List<ElevationGainedEntry>

    @Query("select null from elevation_gained")
    override fun getChangeTrigger(): Flow<Int?>
}

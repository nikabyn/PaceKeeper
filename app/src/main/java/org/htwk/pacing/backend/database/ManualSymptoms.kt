package org.htwk.pacing.backend.database

import androidx.annotation.IntRange
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

enum class Feeling(val level: Int) {
    VeryBad(0),
    Bad(1),
    Good(2),
    VeryGood(3);

    companion object {
        fun fromInt(@IntRange(from = 0, to = 3) level: Int): Feeling =
            when (level) {
                0 -> VeryBad
                1 -> Bad
                2 -> Good
                3 -> VeryGood
                else -> throw RuntimeException("Invalid Feeling level $level")
            }
    }
}

@Entity(tableName = "symptom")
data class Symptom(
    @PrimaryKey
    val name: String
)

@Entity(tableName = "feeling")
data class FeelingEntry(
    @PrimaryKey
    val time: Instant,
    val feeling: Feeling,
)

@Entity(
    tableName = "symptom_for_feeling",
    primaryKeys = ["entryTime", "symptomName"],
    foreignKeys = [
        ForeignKey(
            entity = FeelingEntry::class,
            parentColumns = ["time"],
            childColumns = ["entryTime"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Symptom::class,
            parentColumns = ["name"],
            childColumns = ["symptomName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entryTime"]), Index(value = ["symptomName"])]
)
data class SymptomForFeeling(
    val entryTime: Instant,
    val symptomName: String
)

data class ManualSymptomEntry(
    @Embedded val feeling: FeelingEntry,

    @Relation(
        parentColumn = "time",
        entityColumn = "name",
        associateBy = Junction(
            value = SymptomForFeeling::class,
            parentColumn = "entryTime",
            entityColumn = "symptomName"
        )
    )
    val symptoms: List<Symptom>
)

@Dao
interface ManualSymptomDao : TimedSeries<ManualSymptomEntry> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptom(symptom: Symptom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeelingEntry(entry: FeelingEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptomForEntry(relation: SymptomForFeeling)

    suspend fun insertManualSymptomEntry(entry: ManualSymptomEntry) {
        insertFeelingEntry(entry.feeling)
        for (symptom in entry.symptoms) {
            insertSymptomForEntry(SymptomForFeeling(entry.feeling.time, symptom.name))
        }
    }

    @Query("select * from symptom")
    fun getAllSymptoms(): Flow<List<Symptom>>

    @Transaction
    @Query("select * from feeling where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<ManualSymptomEntry>

    /**
     * Emits `null` every time the data in the table changes.
     */
    @Query("select null from feeling")
    override fun getChangeTrigger(): Flow<Int?>
}
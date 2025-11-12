package org.htwk.pacing.backend.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

val cleanUuid: String = UUID.randomUUID().toString().replace("-", "")
val shortUserId: String = cleanUuid.substring(0, 15)

@Entity(tableName = "user_profile")
data class UserProfileEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val userId: String,
    val nickname: String?,

    val sex: Sex,
    val birthYear: Int?,
    val heightCm: Int?,
    val weightKg: Int?,
    val restingHeartRateBpm: Int?,

    val amputationLevel: AmputationLevel?,
    val fatigueSensitivity: Int?,
    val activityBaseline: Int?,
    val anaerobicThreshold: Int?,
    val bellScale: Int?,
    val illnessStartDate: Long?,
    val diagnosis: Diagnosis?,
    val fitnessTracker: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Sex { MALE, FEMALE, OTHER, UNSPECIFIED }

    enum class AmputationLevel {
        NONE,
        PARTIAL_FOOT,
        BELOW_KNEE_LEFT,
        BELOW_KNEE_RIGHT,
        ABOVE_KNEE_LEFT,
        ABOVE_KNEE_RIGHT,
        BOTH_LEGS_BELOW_KNEE,
        BOTH_LEGS_ABOVE_KNEE,
        ARM_LEFT,
        ARM_RIGHT,
        BOTH_ARMS,
        OTHER
    }

    enum class Diagnosis {
        LONG_COVID,
        MECFS,
        MULTIPLE_SCLEROSIS,
        PARKINSONS,
        FIBROMYALGIA,
        ASTHMA,
        OTHER
    }
    companion object {
        fun createInitial(): UserProfileEntry {
            return UserProfileEntry(
                // id wird von Room generiert (0L)
                userId = shortUserId,
                nickname = null,
                sex = Sex.UNSPECIFIED,
                birthYear = 1990,
                heightCm = null,
                weightKg = null,
                restingHeartRateBpm = 60,
                amputationLevel = null,
                fatigueSensitivity = null,
                activityBaseline = null,
                anaerobicThreshold = null,
                bellScale = null,
                illnessStartDate = null,
                diagnosis = null,
                fitnessTracker = null
            )
        }
    }
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getCurrentProfile(): Flow<UserProfileEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntry)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getCurrentProfileDirect(): UserProfileEntry?
}
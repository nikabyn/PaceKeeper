package org.htwk.pacing.backend.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val userId: String,
    val nickname: String?,

    val sex: Sex,
    val birthYear: Int?,
    val heightCm: Int?,
    val weightKg: Int?,

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
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getCurrentProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}

@Database(
    entities = [UserProfile::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(UserProfileConverters::class)
abstract class UserProfileDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
}

class UserProfileConverters {
    @TypeConverter
    fun fromSex(value: UserProfile.Sex): String = value.name

    @TypeConverter
    fun toSex(value: String): UserProfile.Sex = enumValueOf(value)

    @TypeConverter
    fun fromAmputationLevel(value: UserProfile.AmputationLevel?): String? = value?.name

    @TypeConverter
    fun toAmputationLevel(value: String?): UserProfile.AmputationLevel? =
        value?.let { enumValueOf<UserProfile.AmputationLevel>(it) }

    @TypeConverter
    fun fromDiagnosis(value: UserProfile.Diagnosis?): String? = value?.name

    @TypeConverter
    fun toDiagnosis(value: String?): UserProfile.Diagnosis? =
        value?.let { enumValueOf<UserProfile.Diagnosis>(it) }
}

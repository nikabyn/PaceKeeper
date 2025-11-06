package org.htwk.pacing.backend.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Datenbank-Tabelle für das Benutzerprofil.
 *
 * In dieser Tabelle werden grundlegende und datensparsame Benutzerdaten gespeichert,
 * die für die Berechnung von Pacing-Werten (z. B. Energielevel, Belastung, Ermüdung) erforderlich sind.
 *
 * Folgende Daten werden gespeichert:
 *
 * - **userId**: Eine eindeutige Benutzerkennung (z. B. UUID), um den Benutzer zu identifizieren.
 * - **nickname**: Ein optionaler Anzeigename für den Benutzer.
 * - **sex**: Biologisches Geschlecht des Benutzers (zur Berechnung von Energieverbrauch, Herzfrequenz).
 * - **birthYear**: Geburtsjahr des Benutzers, um das Alter zu berechnen.
 * - **weightKg**: Das Gewicht des Benutzers (für Kalorienverbrauch und Energieberechnungen).
 * - **height**: Die Körpergröße des Benutzers (optional, aber nützlich für gewisse Berechnungen).
 * - **amputationLevel**: Beschreibt den Grad der Amputation (z. B. "Bein rechts unterhalb des Knies").
 * - **fatigueSensitivity**: Wie empfindlich der Benutzer auf Ermüdung reagiert (z. B. 1–10).
 * - **activityBaseline**: Die Basisaktivität des Benutzers (z. B. geringe, mittlere oder hohe Aktivität).
 * - **anaerobicThreshold**: Die Herzfrequenz an der anaeroben Schwelle (optional).
 * - **bellScale**: Ein Wert, der die allgemeine Belastungswahrnehmung des Benutzers angibt (BELL-Skala von 1–10).
 * - **illnessStartDate**: Datum, an dem die Beschwerden oder die Krankheit begannen.
 * - **diagnosis**: Die Erkrankung des Benutzers (enum mit vordefinierten Krankheiten wie Long-COVID, ME/CFS).
 * - **fitnessTracker**: Der verwendete Fitness-Tracker (z. B. „Fitbit“, „Garmin“, „Apple Watch“, etc.).
 */
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
)

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
    fun fromSex(value: Sex): String = value.name

    @TypeConverter
    fun toSex(value: String): Sex = enumValueOf(value)

    @TypeConverter
    fun fromAmputationLevel(value: AmputationLevel?): String? = value?.name

    @TypeConverter
    fun toAmputationLevel(value: String?): AmputationLevel? =
        value?.let { enumValueOf<AmputationLevel>(it) }

    @TypeConverter
    fun fromDiagnosis(value: Diagnosis?): String? = value?.name

    @TypeConverter
    fun toDiagnosis(value: String?): Diagnosis? =
        value?.let { enumValueOf<Diagnosis>(it) }
}

package org.htwk.pacing.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileEntry
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests für UserProfile DAO-Operationen
 *
 */
class UserProfileDaoTest {

    private lateinit var fakeDao: FakeUserProfileDao

    @Before
    fun setup() {
        fakeDao = FakeUserProfileDao()
    }

    @Test
    fun testInsertProfileWithFullData() {
        // Given
        val profile = UserProfileEntry(
            userId = "test-user",
            nickname = "TestUser",
            sex = UserProfileEntry.Sex.MALE,
            birthYear = 1990,
            heightCm = 180,
            weightKg = 75,
            restingHeartRateBpm = 60,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = 5,
            activityBaseline = 100,
            anaerobicThreshold = 150,
            bellScale = 7,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = "Fitbit"
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        // Then
        assert(fakeDao.insertOrUpdateCalled)
        assert(fakeDao.lastInsertedProfile == profile)
        assert(fakeDao.lastInsertedProfile?.nickname == "TestUser")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1990)
        assert(fakeDao.lastInsertedProfile?.heightCm == 180)
        assert(fakeDao.lastInsertedProfile?.sex == UserProfileEntry.Sex.MALE)
    }

    @Test
    fun testInsertProfileWithPartialData() {
        // Given
        val profile = UserProfileEntry(
            userId = "partial-user",
            nickname = "PartialUser",
            sex = UserProfileEntry.Sex.UNSPECIFIED,
            birthYear = null,
            heightCm = null,
            weightKg = null,
            restingHeartRateBpm = null,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        // Then
        assert(fakeDao.insertOrUpdateCalled)
        assert(fakeDao.lastInsertedProfile?.nickname == "PartialUser")
        assert(fakeDao.lastInsertedProfile?.birthYear == null)
        assert(fakeDao.lastInsertedProfile?.heightCm == null)
    }

    @Test
    fun testInsertProfileWithDiagnosis() {
        // Given
        val profile = UserProfileEntry(
            userId = "diagnosis-user",
            nickname = "DiagnosisUser",
            sex = UserProfileEntry.Sex.FEMALE,
            birthYear = 1988,
            heightCm = 175,
            weightKg = 80,
            restingHeartRateBpm = 70,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = 7,
            activityBaseline = 90,
            anaerobicThreshold = 145,
            bellScale = 8,
            illnessStartDate = null,
            diagnosis = UserProfileEntry.Diagnosis.MECFS,
            fitnessTracker = "Apple Watch"
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        // Then
        assert(fakeDao.lastInsertedProfile?.diagnosis == UserProfileEntry.Diagnosis.MECFS)
        assert(fakeDao.lastInsertedProfile?.fatigueSensitivity == 7)
        assert(fakeDao.lastInsertedProfile?.bellScale == 8)
    }

    @Test
    fun testInsertProfileWithAmputationLevel() {
        // Given
        val profile = UserProfileEntry(
            userId = "amputation-user",
            nickname = "AmputationUser",
            sex = UserProfileEntry.Sex.MALE,
            birthYear = 1992,
            heightCm = 170,
            weightKg = 65,
            restingHeartRateBpm = 68,
            amputationLevel = UserProfileEntry.AmputationLevel.ABOVE_KNEE_LEFT,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        // Then
        assert(fakeDao.lastInsertedProfile?.amputationLevel == UserProfileEntry.AmputationLevel.ABOVE_KNEE_LEFT)
        assert(fakeDao.lastInsertedProfile?.nickname == "AmputationUser")
    }

    @Test
    fun testMultipleInsertCalls() {
        // Given
        val profile1 = UserProfileEntry(
            userId = "user1",
            nickname = "User1",
            sex = UserProfileEntry.Sex.MALE,
            birthYear = 1990,
            heightCm = 180,
            weightKg = 75,
            restingHeartRateBpm = 60,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        val profile2 = profile1.copy(
            nickname = "User2",
            birthYear = 1995
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile1)
            fakeDao.insertOrUpdate(profile2)
        }

        // Then
        assert(fakeDao.insertOrUpdateCallCount == 2)
        assert(fakeDao.lastInsertedProfile?.nickname == "User2")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1995)
    }

    @Test
    fun testUpdateExistingProfile() {
        // Given
        val initialProfile = UserProfileEntry(
            userId = "user123",
            nickname = "InitialName",
            sex = UserProfileEntry.Sex.MALE,
            birthYear = 1990,
            heightCm = 180,
            weightKg = 75,
            restingHeartRateBpm = 60,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        val updatedProfile = initialProfile.copy(
            nickname = "UpdatedName",
            birthYear = 1995,
            heightCm = 175
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(initialProfile)
            fakeDao.insertOrUpdate(updatedProfile)
        }

        // Then
        assert(fakeDao.insertOrUpdateCallCount == 2)
        assert(fakeDao.lastInsertedProfile?.nickname == "UpdatedName")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1995)
        assert(fakeDao.lastInsertedProfile?.heightCm == 175)
    }

    @Test
    fun testDeleteAll() {
        // Given
        val profile = UserProfileEntry(
            userId = "test-user",
            nickname = "TestUser",
            sex = UserProfileEntry.Sex.MALE,
            birthYear = 1990,
            heightCm = 180,
            weightKg = 75,
            restingHeartRateBpm = 60,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
            fakeDao.deleteAll()
        }

        // Then
        assert(fakeDao.deleteAllCalled)
        runBlocking {
            val currentProfile = fakeDao.getCurrentProfileDirect()
            assert(currentProfile == null)
        }
    }

    @Test
    fun testGetCurrentProfileDirect() {
        // Given
        val profile = UserProfileEntry(
            userId = "test-user",
            nickname = "TestUser",
            sex = UserProfileEntry.Sex.FEMALE,
            birthYear = 1992,
            heightCm = 165,
            weightKg = 60,
            restingHeartRateBpm = 65,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )

        // When
        runBlocking {
            fakeDao.insertOrUpdate(profile)
            val retrieved = fakeDao.getCurrentProfileDirect()

            // Then
            assert(retrieved != null)
            assert(retrieved?.userId == "test-user")
            assert(retrieved?.nickname == "TestUser")
            assert(retrieved?.sex == UserProfileEntry.Sex.FEMALE)
        }
    }
}

// Fake DAO Implementation für Tests
class FakeUserProfileDao : UserProfileDao {

    var insertOrUpdateCalled = false
    var insertOrUpdateCallCount = 0
    var deleteAllCalled = false
    var lastInsertedProfile: UserProfileEntry? = null

    private val profileFlow = MutableStateFlow<UserProfileEntry?>(null)

    override fun getCurrentProfile() = profileFlow

    override suspend fun getCurrentProfileDirect(): UserProfileEntry? {
        return profileFlow.value
    }

    override suspend fun insertOrUpdate(profile: UserProfileEntry) {
        insertOrUpdateCalled = true
        insertOrUpdateCallCount++
        lastInsertedProfile = profile
        profileFlow.value = profile
    }

    override suspend fun deleteAll() {
        deleteAllCalled = true
        profileFlow.value = null
    }
}
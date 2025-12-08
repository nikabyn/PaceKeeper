package org.htwk.pacing.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileEntry
import org.junit.Before
import org.junit.Test

/**
 * Test class for verifying the functionality and interaction contract of the UserProfileDao.
 * It uses a mock implementation, FakeUserProfileDao, to isolate the data access logic
 * and ensure that profile entries, whether full or partial, are correctly inserted/updated,
 * retrieved, and deleted.
 */
class UserProfileDaoTest {

    private lateinit var fakeDao: FakeUserProfileDao

    @Before
    fun setup() {
        fakeDao = FakeUserProfileDao()
    }

    @Test
    fun testInsertProfileWithFullData() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        assert(fakeDao.insertOrUpdateCalled)
        assert(fakeDao.lastInsertedProfile == profile)
        assert(fakeDao.lastInsertedProfile?.nickname == "TestUser")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1990)
        assert(fakeDao.lastInsertedProfile?.heightCm == 180)
        assert(fakeDao.lastInsertedProfile?.sex == UserProfileEntry.Sex.MALE)
    }

    @Test
    fun testInsertProfileWithPartialData() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        assert(fakeDao.insertOrUpdateCalled)
        assert(fakeDao.lastInsertedProfile?.nickname == "PartialUser")
        assert(fakeDao.lastInsertedProfile?.birthYear == null)
        assert(fakeDao.lastInsertedProfile?.heightCm == null)
    }

    @Test
    fun testInsertProfileWithDiagnosis() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        assert(fakeDao.lastInsertedProfile?.diagnosis == UserProfileEntry.Diagnosis.MECFS)
        assert(fakeDao.lastInsertedProfile?.fatigueSensitivity == 7)
        assert(fakeDao.lastInsertedProfile?.bellScale == 8)
    }

    @Test
    fun testInsertProfileWithAmputationLevel() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
        }

        assert(fakeDao.lastInsertedProfile?.amputationLevel == UserProfileEntry.AmputationLevel.ABOVE_KNEE_LEFT)
        assert(fakeDao.lastInsertedProfile?.nickname == "AmputationUser")
    }

    @Test
    fun testMultipleInsertCalls() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile1)
            fakeDao.insertOrUpdate(profile2)
        }

        assert(fakeDao.insertOrUpdateCallCount == 2)
        assert(fakeDao.lastInsertedProfile?.nickname == "User2")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1995)
    }

    @Test
    fun testUpdateExistingProfile() {
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

        runBlocking {
            fakeDao.insertOrUpdate(initialProfile)
            fakeDao.insertOrUpdate(updatedProfile)
        }

        assert(fakeDao.insertOrUpdateCallCount == 2)
        assert(fakeDao.lastInsertedProfile?.nickname == "UpdatedName")
        assert(fakeDao.lastInsertedProfile?.birthYear == 1995)
        assert(fakeDao.lastInsertedProfile?.heightCm == 175)
    }

    @Test
    fun testDeleteAll() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
            fakeDao.deleteAll()
        }

        assert(fakeDao.deleteAllCalled)
        runBlocking {
            val currentProfile = fakeDao.getProfile()
            assert(currentProfile == null)
        }
    }

    @Test
    fun testGetCurrentProfileDirect() {
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

        runBlocking {
            fakeDao.insertOrUpdate(profile)
            val retrieved = fakeDao.getProfile()

            assert(retrieved != null)
            assert(retrieved?.userId == "test-user")
            assert(retrieved?.nickname == "TestUser")
            assert(retrieved?.sex == UserProfileEntry.Sex.FEMALE)
        }
    }
}

class FakeUserProfileDao : UserProfileDao {

    var insertOrUpdateCalled = false
    var insertOrUpdateCallCount = 0
    var deleteAllCalled = false
    var lastInsertedProfile: UserProfileEntry? = null

    private val profileFlow = MutableStateFlow<UserProfileEntry?>(null)

    override fun getProfileLive() = profileFlow

    override suspend fun getProfile(): UserProfileEntry? {
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
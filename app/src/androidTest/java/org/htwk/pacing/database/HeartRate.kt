package org.htwk.pacing.database

// Does not work, the testing framework does not seem to have access to the filesystem.
//@RunWith(AndroidJUnit4::class)
//class ExampleUnitTest {
//    @Test
//    suspend fun roundTrip_works() {
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        val db = PacingDatabase.getInstance(appContext)
//        val heartRateDao = db.heartRateDao()
//
//        val entry = HeartRateEntry(0.0, Instant.fromEpochMilliseconds(0))
//        heartRateDao.insert(entry)
//        assert(heartRateDao.getAllEntries().first() == entry)
//    }
//}
package org.htwk.pacing.backend

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyBackgroundWorkerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("energy_prefs", Context.MODE_PRIVATE)
    }

    @Test
    fun testNotificationIsShownWhenEnergyBelow20() {
        prefs.edit().putInt("energy", 15).apply()

        val worker = TestListenableWorkerBuilder<MyBackgroundWorker>(context).build()
        val result = worker.startWork().get()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testNotificationIsNotShownWhenEnergyAboveOrEqual20() {
        prefs.edit().putInt("energy", 50).apply()

        val worker = TestListenableWorkerBuilder<MyBackgroundWorker>(context).build()
        val result = worker.startWork().get()

        assertEquals(Result.success(), result)
    }
}

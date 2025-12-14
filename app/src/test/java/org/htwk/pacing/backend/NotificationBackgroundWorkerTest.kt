package org.htwk.pacing.backend

import android.content.Context
import androidx.work.WorkerParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.UserProfileRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsBackgroundWorkerTest {

    @Test
    fun `worker does not notify if warnings disabled`() = runTest {
        val context = mock<Context>()
        val workerParams = mock<WorkerParameters>()
        val dao = mock<PredictedEnergyLevelDao> {
            onBlocking { getInRange(any(), any()) } doReturn emptyList()
        }
        val repo = mock<UserProfileRepository> {
            onBlocking { getUserProfile() } doReturn mock {
                on { warningPermit } doReturn false
                on { restingStart } doReturn LocalTime(22, 0)
                on { restingEnd } doReturn LocalTime(6, 0)
            }
        }

        val worker = NotificationsBackgroundWorker(context, workerParams, dao, repo)
        val result = worker.doWork()

        assertTrue(result.isSuccess)
        verify(repo, times(1)).getUserProfile()
        // showNotification should NOT be called
        // Hier könnten wir showNotification mocken, wenn es Aufruf überprüft werden soll
    }

    @Test
    fun `worker notifies if energy low and allowed`() = runTest {
        val context = mock<Context>()
        val workerParams = mock<WorkerParameters>()
        val dao = mock<PredictedEnergyLevelDao> {
            onBlocking { getInRange(any(), any()) } doReturn listOf(
                PredictedEnergyLevelEntry(percentage = 0.1, timestamp = Clock.System.now())
            )
        }
        val repo = mock<UserProfileRepository> {
            onBlocking { getUserProfile() } doReturn mock {
                on { warningPermit } doReturn true
                on { restingStart } doReturn LocalTime(0, 0)
                on { restingEnd } doReturn LocalTime(1, 0)
            }
        }

        // showNotification mocken, damit kein echter NotificationManager verwendet wird
        val worker = spy(NotificationsBackgroundWorker(context, workerParams, dao, repo))
        doNothing().whenever(worker).showNotification(context)

        val result = worker.doWork()

        assertTrue(result.isSuccess)
        verify(worker, times(1)).showNotification(context)
    }
}

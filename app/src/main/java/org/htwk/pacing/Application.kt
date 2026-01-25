package org.htwk.pacing

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.ForegroundWorker
import org.htwk.pacing.backend.appModule
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileEntry
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Entry point for non UI related work.
 */
open class ProductionApplication : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        startInjection()
        val wm = getWorkManager(this)
        enqueueForegroundWorker(wm)
        runBlocking {
            val dao = getKoin().get<UserProfileDao>()
            if (dao.getProfile() == null) {
                dao.insertOrUpdate(UserProfileEntry.createInitial())
            }
        }
    }

    open fun startInjection() {
        startKoin {
            androidContext(this@ProductionApplication)
            modules(appModule)
        }
        Log.d("ProductionApplication", "Started Koin for production")
    }
}

// TODO: Figure out how to enable this without using it in all debug builds
//class TestApplication : ProductionApplication() {
//    override fun onCreate() {
//        super.onCreate()
//        Log.d("TestApplication", "TestApplication onCreate called")
//    }
//
//    override fun startInjection() {
//        startKoin {
//            androidContext(this@TestApplication)
//            modules(testModule, appModule)
//        }
//        Log.d("TestApplication", "Started Koin for testing")
//    }
//}

class BroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BroadcastReceiver", "Received intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_UNSUSPENDED -> {
                val wm = getWorkManager(context)
                enqueueForegroundWorker(wm)
            }
        }
    }
}

fun enqueueForegroundWorker(wm: WorkManager) {
    val workRequest = OneTimeWorkRequestBuilder<ForegroundWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            WorkRequest.MIN_BACKOFF_MILLIS.seconds.toJavaDuration()
        )
        .build()
    wm.enqueueUniqueWork(
        "HealthDataCollection",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
    Log.d("PacingApp", "Enqueued ForegroundWorker")
}

fun hardKillApp(context: Context) {
    // 1. Alle Worker stoppen
    val wm = getWorkManager(context)
    wm.cancelUniqueWork("HealthDataCollection")
    wm.pruneWork()

    // 2. Alle Activities schließen
    (context as? Activity)?.finishAffinity()

    // 3. Prozess beenden (hart)
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(0)
}

fun restartApp(context: Context) {
    // 1. Worker stoppen
    val wm = getWorkManager(context)
    wm.cancelUniqueWork("HealthDataCollection")
    wm.pruneWork()

    // 2. Restart-Intent erzeugen
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(context.packageName)
        ?: return

    val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)

    // 3. App neu starten
    context.startActivity(restartIntent)

    // 4. Alten Prozess sicher beenden
    android.os.Process.killProcess(android.os.Process.myPid())
}

@Volatile
private var initializedWorkManager = false

/**
 * Replaces the default WorkManagerInitializer,
 * which must be disabled in AndroidManifest.xml for this to work.
 */
private fun getWorkManager(context: Context): WorkManager {
    if (!initializedWorkManager) {
        val workerFactory = KoinWorkerFactory()
        val config = Configuration.Builder().setWorkerFactory(workerFactory).build()
        WorkManager.initialize(context, config)
        initializedWorkManager = true
    }
    return WorkManager.getInstance(context)
}

/**
 * Queries and constructs workers using dependency injection with koin.
 * Had to write this ourselves because the koin workmanager library does not
 * properly query for the functions that create workers.
 */
private class KoinWorkerFactory : WorkerFactory(), KoinComponent {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val createFun: (Koin.(Context, WorkerParameters) -> ListenableWorker)? =
            getKoin().getOrNull(qualifier = named(workerClassName))
        val worker = createFun?.invoke(getKoin(), appContext, workerParameters)
        if (worker != null) Log.d("KoinWorkerFactory", "Constructed: $worker")
        return worker
    }
}
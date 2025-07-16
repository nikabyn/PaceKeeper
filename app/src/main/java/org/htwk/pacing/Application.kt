package org.htwk.pacing

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.htwk.pacing.backend.appModule
import org.htwk.pacing.backend.data_collection.HealthConnectWorkerScheduler
import org.htwk.pacing.backend.mlmodel.PredictionWorker
import org.htwk.pacing.backend.data_collection.health_connect.HealthConnectWorker
import org.htwk.pacing.backend.productionModule
import org.htwk.pacing.backend.scheduleEnergyCheckWorker
import org.htwk.pacing.backend.testModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

/**
 * Entry point for non UI related work.
 */
open class ProductionApplication : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        startInjection()
        val wm = getWorkManager(this)
        enqueueWorkers(wm)
    }

    open fun startInjection() {
        startKoin {
            androidContext(this@ProductionApplication)
            modules(productionModule, appModule)
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
                enqueueWorkers(wm)
            }
        }
    }
}

fun enqueueWorkers(wm: WorkManager) {
    enqueueRandomHeartRateWorker(wm)
    HealthConnectWorkerScheduler.scheduleHealthSync(wm)
    enqueuePredictionWorker(wm)
    scheduleEnergyCheckWorker(wm)
}

fun enqueueRandomHeartRateWorker(wm: WorkManager) {
    val workRequest = OneTimeWorkRequestBuilder<RandomHeartRateWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    wm.enqueueUniqueWork(
        "RandomHeartRateGeneration",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
    Log.d("PacingApp", "Enqueued RandomHeartRateWorker")
}

fun enqueuePredictionWorker(wm: WorkManager) {
    val workRequest = OneTimeWorkRequestBuilder<PredictionWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    wm.enqueueUniqueWork(
        "PredictionWorker",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
    Log.d("PacingApp", "Enqueued MLModelWorker")
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
package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.mock.RandomHeartRateWorker
import org.htwk.pacing.ui.screens.MeasurementsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ext.getFullName

val appModule = module {
    single<PacingDatabase> { PacingDatabase.getInstance(androidContext()) }
    single<HeartRateDao> { get<PacingDatabase>().heartRateDao() }

    viewModel { MeasurementsViewModel(get()) }

    worker { context, params -> RandomHeartRateWorker(context, params, get()) }
}

/**
 * Registers a function that has access to koin and constructs a ListenableWorker.
 */
inline fun <reified T : ListenableWorker> Module.worker(
    noinline createFun: Koin.(Context, WorkerParameters) -> T
) {
    single<Koin.(Context, WorkerParameters) -> ListenableWorker>(qualifier = named<T>()) { createFun }
    Log.d("KoinModule", "Registered: ${T::class.getFullName()}")
}
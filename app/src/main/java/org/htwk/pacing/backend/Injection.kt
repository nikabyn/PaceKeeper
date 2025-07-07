package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.htwk.pacing.backend.database.DistanceDao
import org.htwk.pacing.backend.database.ElevationGainedDao
import org.htwk.pacing.backend.database.EnergyLevelDao
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateVariabilityDao
import org.htwk.pacing.backend.database.MenstruationPeriodDao
import org.htwk.pacing.backend.database.OxygenSaturationDao
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.SkinTemperatureDao
import org.htwk.pacing.backend.database.SleepSessionDao
import org.htwk.pacing.backend.database.SpeedDao
import org.htwk.pacing.backend.database.StepsDao
import org.htwk.pacing.backend.mlmodel.MLModel
import org.htwk.pacing.backend.mlmodel.MLModelWorker
import org.htwk.pacing.backend.mlmodel.PredictionWorker
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
    single<DistanceDao> { get<PacingDatabase>().distanceDao() }
    single<ElevationGainedDao> { get<PacingDatabase>().elevationGainedDao() }
    single<EnergyLevelDao> { get<PacingDatabase>().energyLevelDao() }
    single<HeartRateDao> { get<PacingDatabase>().heartRateDao() }
    single<HeartRateVariabilityDao> { get<PacingDatabase>().heartRateVariabilityDao() }
    single<MenstruationPeriodDao> { get<PacingDatabase>().menstruationPeriodDao() }
    single<OxygenSaturationDao> { get<PacingDatabase>().oxygenSaturationDao() }
    single<SkinTemperatureDao> { get<PacingDatabase>().skinTemperatureDao() }
    single<SleepSessionDao> { get<PacingDatabase>().sleepSessionsDao() }
    single<SpeedDao> { get<PacingDatabase>().speedDao() }
    single<StepsDao> { get<PacingDatabase>().stepsDao() }

    viewModel { MeasurementsViewModel(get()) }

    worker { context, params -> RandomHeartRateWorker(context, params, get()) }
    worker { context, params -> PredictionWorker(context, params, get(), get(), get()) }
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
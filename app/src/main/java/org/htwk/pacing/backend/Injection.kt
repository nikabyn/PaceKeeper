package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.htwk.pacing.backend.data_collection.HealthConnectWorker
import org.htwk.pacing.backend.database.DistanceDao
import org.htwk.pacing.backend.database.ElevationGainedDao
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateVariabilityDao
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.MenstruationPeriodDao
import org.htwk.pacing.backend.database.OxygenSaturationDao
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedHeartRateDao
import org.htwk.pacing.backend.database.SkinTemperatureDao
import org.htwk.pacing.backend.database.SleepSessionDao
import org.htwk.pacing.backend.database.SpeedDao
import org.htwk.pacing.backend.database.StepsDao
import org.htwk.pacing.backend.mlmodel.MLModel
import org.htwk.pacing.backend.mlmodel.PredictionWorker
import org.htwk.pacing.backend.mock.RandomHeartRateWorker
import org.htwk.pacing.ui.screens.MeasurementsViewModel
import org.htwk.pacing.ui.screens.SettingsViewModel
import org.htwk.pacing.ui.screens.SymptomsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ext.getFullName

val testModule = module {
    single<PacingDatabase> {
        Room.inMemoryDatabaseBuilder(androidContext(), PacingDatabase::class.java)
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}

val productionModule = module {
    single<PacingDatabase> {
        Room.databaseBuilder(androidContext(), PacingDatabase::class.java, "pacing.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}

val appModule = module {
    single<DistanceDao> { get<PacingDatabase>().distanceDao() }
    single<ElevationGainedDao> { get<PacingDatabase>().elevationGainedDao() }
    single<HeartRateDao> { get<PacingDatabase>().heartRateDao() }
    single<HeartRateVariabilityDao> { get<PacingDatabase>().heartRateVariabilityDao() }
    single<ManualSymptomDao> { get<PacingDatabase>().manualSymptomDao() }
    single<MenstruationPeriodDao> { get<PacingDatabase>().menstruationPeriodDao() }
    single<OxygenSaturationDao> { get<PacingDatabase>().oxygenSaturationDao() }
    single<SkinTemperatureDao> { get<PacingDatabase>().skinTemperatureDao() }
    single<SleepSessionDao> { get<PacingDatabase>().sleepSessionsDao() }
    single<SpeedDao> { get<PacingDatabase>().speedDao() }
    single<StepsDao> { get<PacingDatabase>().stepsDao() }

    single<PredictedHeartRateDao> { get<PacingDatabase>().predictedHeartRateDao() }
    single<PredictedEnergyLevelDao> { get<PacingDatabase>().predictedEnergyLevelDao() }

    single<MLModel> { MLModel(get()) }

    viewModel { MeasurementsViewModel(get(), get(), get(), get()) }
    viewModel { SymptomsViewModel(get()) }
    viewModel { SettingsViewModel(get()) }

    worker { context, params -> HealthConnectWorker(context, params, get()) }
    worker { context, params -> PredictionWorker(context, params, get(), get(), get(), get()) }

    /*koin sets up the dependencies for the worker class instance here, the actual execution/
    scheduling is handled elsewhere*/
    worker { context, params -> NotificationsBackgroundWorker(context, params, get()) }
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
package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.htwk.pacing.backend.data_collection.fitbit.Fitbit
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
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileRepository
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.ui.screens.HomeViewModel
import org.htwk.pacing.ui.screens.MeasurementsViewModel
import org.htwk.pacing.ui.screens.SettingsViewModel
import org.htwk.pacing.ui.screens.SymptomsViewModel
import org.htwk.pacing.ui.screens.UserProfileViewModel
import org.htwk.pacing.ui.screens.WelcomeViewModel
import org.htwk.pacing.ui.screens.settings.ConnectionsAndServicesViewModel
import org.htwk.pacing.ui.screens.settings.FitbitViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ext.getFullName

data class DemoStatus(val inDemoModus: Boolean)

val appModule = module {
    single<DemoStatus> { DemoStatus(false) }
    single<PacingDatabase> {
        val dbName = if (get<DemoStatus>().inDemoModus) {
            "demo.db"
        } else {
            "pacing.db"
        }
        Room.databaseBuilder(androidContext(), PacingDatabase::class.java, dbName)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

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

    single<ValidatedEnergyLevelDao> { get<PacingDatabase>().validatedEnergyLevelDao() }

    single<PredictedHeartRateDao> { get<PacingDatabase>().predictedHeartRateDao() }
    single<PredictedEnergyLevelDao> { get<PacingDatabase>().predictedEnergyLevelDao() }

    single<UserProfileDao> {
        get<PacingDatabase>().userProfileDao()
    }
    single<OAuth2Provider>(qualifier = named(Fitbit.TAG)) { Fitbit.oAuth2Provider }
    single { UserProfileRepository(get()) }


    viewModel { HomeViewModel(get(), get()) }
    viewModel { MeasurementsViewModel(get(), get(), get(), get(), get()) }
    viewModel { SymptomsViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { ConnectionsAndServicesViewModel(androidContext(), get()) }
    viewModel { FitbitViewModel(get(), get(qualifier = named(Fitbit.TAG))) }
    viewModel { UserProfileViewModel(get()) }
    viewModel { WelcomeViewModel(get()) }

    /**
     * koin sets up the dependencies for the worker class instance here,
     * the actual execution/scheduling is handled in Application.kt
     */
    worker { context, params -> ForegroundWorker(context, params, get(), get()) }
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
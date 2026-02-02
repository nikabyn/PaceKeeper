package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.PredictedEnergyLevelModell2Dao
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.DemoBanner
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.ModeViewModel
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

data class EnergyGraphData(
    val entries: List<PredictedEnergyLevelEntry>,
    val currentValue: Double,
    val futureValue: Double,
    val simulationEnabled: Boolean
)

@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    modeViewModel: ModeViewModel = koinViewModel()
) {

    val energyGraphData by viewModel.predictedEnergyLevel.collectAsState()
    val currentEnergy = energyGraphData.currentValue

    val currentTime =
        if (energyGraphData.simulationEnabled and energyGraphData.entries.isNotEmpty()) {
            energyGraphData.entries.last().time
        } else {
            Clock.System.now()
        }

    val futureValues = energyGraphData.entries
        .filter { it.timeFuture in currentTime..(currentTime + IPredictionModel.PredictionHorizon.FUTURE.howFar) }
        .map { it.percentageFuture.toDouble() }

    val minPrediction = futureValues.minOrNull() ?: currentEnergy
    val maxPrediction = futureValues.maxOrNull() ?: currentEnergy
    val avgPrediction = energyGraphData.futureValue

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column {

            DemoBanner(modeViewModel = modeViewModel)
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
                modifier = Modifier.padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                )
            ) {
                EnergyPredictionCard(
                    data = energyGraphData.entries,
                    currentEnergy = currentEnergy.toFloat(),
                    minPrediction = minPrediction.toFloat(),
                    avgPrediction = avgPrediction.toFloat(),
                    maxPrediction = maxPrediction.toFloat(),
                    modifier = Modifier.height(300.dp),
                    currentTime = currentTime
                )
                LabelCard(energy = currentEnergy)
                BatteryCard(
                    energy = currentEnergy,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                )
                FeelingSelectionCard(navController)
            }
        }
    }
}

class HomeViewModel(
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val predictedEnergyLevelModell2Dao: PredictedEnergyLevelModell2Dao,
    private val validatedEnergyLevelDao: ValidatedEnergyLevelDao,
    private val userProfileDao: UserProfileDao,
) : ViewModel() {

    @OptIn(FlowPreview::class)
    val predictedEnergyLevel = userProfileDao.getProfileLive()
        .flatMapLatest { profile ->
            //capture state from profile, to know which model to use and whether to show simulation
            val model = profile?.predictionModel ?: "DEFAULT"
            val simulationEnabled = profile?.simulationEnabled == true

            //switch DAO based on model selection
            val daoFlow = if (model == "MODEL2") {
                predictedEnergyLevelModell2Dao.getAllLive().map { entries ->
                    entries.map {
                        PredictedEnergyLevelEntry(
                            it.time, it.percentageNow, it.timeFuture, it.percentageFuture
                        )
                    }
                }
            } else {
                predictedEnergyLevelDao.getAllLive()
            }

            //map entries to UI State, also pass simulation flag
            daoFlow.map { entries ->
                val sortedEntries = entries.sortedBy { it.time }
                val latest = sortedEntries.lastOrNull()

                EnergyGraphData(
                    entries = sortedEntries,
                    currentValue = latest?.percentageNow?.toDouble() ?: 0.5,
                    futureValue = latest?.percentageFuture?.toDouble() ?: 0.5,
                    simulationEnabled = simulationEnabled
                )
            }
        }
        .debounce(200)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EnergyGraphData(emptyList(), 0.5, 0.5, false)
        )

    fun storeValidatedEnergyLevel(validation: Validation, energy: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            validatedEnergyLevelDao.insert(
                ValidatedEnergyLevelEntry(
                    Clock.System.now(),
                    validation,
                    Percentage.fromDouble(energy)
                )
            )
        }
    }

    val latestValidatedEnergyLevel = validatedEnergyLevelDao
        .getLatestLive()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}

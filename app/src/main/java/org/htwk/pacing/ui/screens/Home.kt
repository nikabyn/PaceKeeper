package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.DemoBanner
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

data class EnergyGraphData(
    val seriesPastToNow: Series<List<Double>>,
    val futureValue: Double
)

@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val latest by viewModel.predictedEnergyLevel.collectAsState()

    // cache remembers the most recent non‑empty series to fix flickering
    var cached by remember {
        mutableStateOf(
            EnergyGraphData(Series(listOf(0.0, 0.5), listOf(0.5, 0.5)), 0.5)
        )
    }

    if (latest.seriesPastToNow.y.isNotEmpty()) cached = latest

    val energyGraphData = cached
    // always draw the cache
    if (energyGraphData.seriesPastToNow.y.isEmpty()) return//should never happen in runtime, but may happen during startup

    val futureValue = energyGraphData.futureValue

    val currentEnergy = energyGraphData.seriesPastToNow.y.last()
    val minPrediction = futureValue - 0.1
    val maxPrediction = futureValue + 0.1
    val avgPrediction = futureValue
    Column(modifier = modifier.fillMaxSize()) {
        DemoBanner(visible = true)
        Box(modifier = modifier.verticalScroll(rememberScrollState())) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
                modifier = Modifier.padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                )
            ) {
                EnergyPredictionCard(
                    series = energyGraphData.seriesPastToNow,
                    currentEnergy = currentEnergy.toFloat(),
                    minPrediction = minPrediction.toFloat(),
                    avgPrediction = avgPrediction.toFloat(),
                    maxPrediction = maxPrediction.toFloat(),
                    modifier = Modifier.height(300.dp)
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
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val validatedEnergyLevelDao: ValidatedEnergyLevelDao,
) : ViewModel() {
    @OptIn(FlowPreview::class)
    val predictedEnergyLevel = predictedEnergyLevelDao
        .getAllLive()
        .filter { it.isNotEmpty() }
        .debounce(200)
        .map { entries ->
            val energySeries = Series(mutableListOf(), mutableListOf())

            entries.forEach { entry ->
                energySeries.x.add(entry.time.toEpochMilliseconds().toDouble())
                energySeries.y.add(entry.percentageNow.toDouble())
            }

            EnergyGraphData(
                Series(energySeries.x.toList(), energySeries.y.toList()),
                entries.last().percentageFuture.toDouble()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EnergyGraphData(Series(listOf(0.0, 0.5), listOf(0.5, 0.5)), 0.5)
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
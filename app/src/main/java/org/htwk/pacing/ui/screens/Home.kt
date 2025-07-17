package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.Series
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel()
) {
    val energySeries by viewModel.predictedEnergyLevel.collectAsState()

    /*var timeNow by remember { mutableStateOf(Clock.System.now()) }
    var time7daysAgo by remember { mutableStateOf(timeNow - 7.days) }
    var time12hoursAgo by remember { mutableStateOf(timeNow - 12.hours) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeNow = Clock.System.now()
            time7daysAgo = timeNow - 7.days
            time12hoursAgo = timeNow - 12.hours
        }
    }*/

    val now = Clock.System.now()

    if (energySeries.y.isEmpty()) return

    val currentEnergy = energySeries.y.last()

    /*val minPrediction = 0.1f
    val avgPrediction = 0.35f
    val maxPrediction = 0.4f*/
    val minPrediction = energySeries.y.min().toFloat()
    val maxPrediction = energySeries.y.max().toFloat()
    val avgPrediction = energySeries.y.average().toFloat() - currentEnergy.toFloat() + 0.5f

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            EnergyPredictionCard(
                series = energySeries,
                minPrediction,
                avgPrediction,
                maxPrediction,
                modifier = Modifier.height(300.dp)
            )
            LabelCard(energy = currentEnergy)
            BatteryCard(energy = currentEnergy)
            FeelingSelectionCard(navController)
        }
    }
}

class HomeViewModel(
    heartRateDao: HeartRateDao,
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
) : ViewModel() {
    val predictedEnergyLevel = predictedEnergyLevelDao
        .getAllLive()
        .map { entries ->
            val updated = Series(mutableListOf(), mutableListOf())
            entries.forEach { (time, value) ->
                updated.x.add(time.toEpochMilliseconds().toDouble())
                updated.y.add(value.toDouble())
            }
            updated
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Series(emptyList(), emptyList())
        )
}
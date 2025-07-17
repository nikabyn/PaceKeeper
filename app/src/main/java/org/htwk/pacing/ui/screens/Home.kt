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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
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
import kotlin.time.Duration.Companion.hours

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel()
) {
    val latest by viewModel.predictedEnergyLevel.collectAsState()

    // cache remembers the most recent non‑empty series to fix flickering
    var cached by remember {
        mutableStateOf<Series<out List<Double>>>(
            Series(
                emptyList(),
                emptyList()
            )
        )
    }

    if (latest.y.isNotEmpty()) cached = latest

    val series = cached
    // always draw the cache
    if (series.y.isEmpty()) return //should never happen in runtime, but may happen during startup

    val mid = series.x.size / 2

    val secondHalfValues = series.y.drop(mid)

    /*val minPrediction = 0.1f
   val avgPrediction = 0.35f
   val maxPrediction = 0.4f*/

    val currentEnergy = series.y[mid]
    val minPrediction = secondHalfValues.min().toFloat()
    val maxPrediction = secondHalfValues.max().toFloat()
    val avgPrediction = (secondHalfValues.average() - currentEnergy + 0.5f).toFloat()


    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            EnergyPredictionCard(
                series = series,
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
        .getAllLive()                            // Flow<List<Entry>>
        .filter { it.isNotEmpty() }              // skip the “[]” emission
        .debounce(200)                           // 200 ms of silence = stable
        .map { entries ->
            val updated = Series(mutableListOf(), mutableListOf())

            //add dummy values if no data available
            if (entries.isEmpty()) {
                updated.x.add((Clock.System.now() - 6.hours).toEpochMilliseconds().toDouble())
                updated.y.add(0.5)

                updated.x.add((Clock.System.now() + 6.hours).toEpochMilliseconds().toDouble())
                updated.y.add(0.5)
            }

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
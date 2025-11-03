package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import org.htwk.pacing.ui.components.CardWithTitle
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.Series
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val latest by viewModel.predictedEnergyLevel.collectAsState()

    // cache remembers the most recent non‑empty series to fix flickering
    var cached by remember {
        mutableStateOf<Series<out List<Double>>>(
            Series<List<Double>>(
                listOf(0.0, 0.0), // dummy value
                listOf(0.0, 0.0)  // dummy value
            )
        )
    }

    if (latest.y.isNotEmpty()) cached = latest

    val series = cached
    // always draw the cache
    if (series.y.isEmpty()) return//should never happen in runtime, but may happen during startup

    val mid = series.x.size / 2

    val secondHalfValues = series.y.drop(mid)

    val currentEnergy = secondHalfValues.first()
    val minPrediction = secondHalfValues.min().toFloat()
    val maxPrediction = secondHalfValues.max().toFloat()
    val avgPrediction = secondHalfValues.average().toFloat()

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            EnergyPredictionCard(
                series = series,
                currentEnergy = currentEnergy.toFloat(),
                minPrediction = minPrediction,
                avgPrediction = avgPrediction,
                maxPrediction = maxPrediction,
                modifier = Modifier.height(300.dp)
            )
            LabelCard(energy = currentEnergy)
            BatteryCard(energy = currentEnergy)
            EnergyValidationCard(viewModel, currentEnergy)
            FeelingSelectionCard(navController)
        }
    }
}

/**
 * Allows the user to accept the current energy prediction as correct
 * or adjust it based on how they feel.
 */
@Composable
fun EnergyValidationCard(viewModel: HomeViewModel, currentEnergy: Double) {
    val adjustingEnergy = remember { mutableStateOf(false) }
    val adjustedEnergy = remember { mutableDoubleStateOf(currentEnergy) }

    CardWithTitle("Validate Energy") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.storeValidatedEnergyLevel(Validation.Correct, currentEnergy)
                },
                enabled = !adjustingEnergy.value,
                modifier = Modifier.weight(1f)
            ) { Text("Correct") }
            Button(
                onClick = { adjustingEnergy.value = true },
                enabled = !adjustingEnergy.value,
                modifier = Modifier.weight(1f)
            ) { Text("Adjust") }
        }

        TextField(
            value = adjustedEnergy.doubleValue.toString(),
            enabled = adjustingEnergy.value,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            onValueChange = { value: String ->
                adjustedEnergy.doubleValue = value.toDoubleOrNull() ?: currentEnergy
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = { adjustingEnergy.value = false },
                enabled = adjustingEnergy.value,
                modifier = Modifier.weight(1f)
            ) { Text("Cancel") }
            TextButton(
                onClick = {
                    adjustingEnergy.value = false;
                    viewModel.storeValidatedEnergyLevel(
                        Validation.Adjusted,
                        adjustedEnergy.doubleValue
                    )
                },
                enabled = adjustingEnergy.value && adjustedEnergy.doubleValue in 0.0..1.0,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}

class HomeViewModel(
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
    val validatedEnergyLevelDao: ValidatedEnergyLevelDao,
) : ViewModel() {
    @OptIn(FlowPreview::class)
    val predictedEnergyLevel = predictedEnergyLevelDao
        .getAllLive()                            // Flow<List<Entry>>
        .filter { it.isNotEmpty() }              // skip the “[]” emission
        .debounce(200)                           // 200 ms of silence = stable
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
            initialValue = Series<List<Double>>(
                listOf(0.0, 0.0), // dummy value
                listOf(0.0, 0.0)  // dummy value
            )
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
}
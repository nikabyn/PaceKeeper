package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelModell2Dao
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import org.htwk.pacing.backend.GlobalTime
import java.util.Calendar

data class EnergyGraphData(
    val seriesPastToNow : Series<List<Double>>,
    val futureValue : Double
)



@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val latest by viewModel.predictedEnergyLevel.collectAsState()
    val context = LocalContext.current

    // ... existing cache logic ...
    var cached by remember {
        mutableStateOf(
            EnergyGraphData(Series(listOf(0.0, 0.5), listOf(0.5, 0.5)), 0.5)
        )
    }
    if (latest.seriesPastToNow.y.isNotEmpty()) cached = latest
    val energyGraphData = cached
    if (energyGraphData.seriesPastToNow.y.isEmpty()) return
    val futureValue = energyGraphData.futureValue
    val currentEnergy = energyGraphData.seriesPastToNow.y.last()
    val minPrediction = futureValue - 0.1
    val maxPrediction = futureValue + 0.1
    val avgPrediction = futureValue

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
        ) {
            EnergyPredictionCard(
                series = energyGraphData.seriesPastToNow,
                currentEnergy = currentEnergy.toFloat(),
                minPrediction = minPrediction.toFloat(),
                avgPrediction = avgPrediction.toFloat(),
                maxPrediction = maxPrediction.toFloat(),
                modifier = Modifier.height(300.dp)
            )

            // Simpler Simulation Button
            Button(onClick = {
                val calendar = Calendar.getInstance()
                // Date Picker
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        // Time Picker
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                                val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(calendar.timeInMillis)
                                GlobalTime.setTime(instant)
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Zeitpunkt ändern (Simulation)")
            }

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

/**
 * Common interface for energy entries from both models.
 */
private data class EnergyEntry(
    val timeMillis: Long,
    val percentageNow: Double,
    val percentageFuture: Double
)

class HomeViewModel(
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val predictedEnergyLevelModell2Dao: PredictedEnergyLevelModell2Dao,
    private val validatedEnergyLevelDao: ValidatedEnergyLevelDao,
    private val userProfileDao: UserProfileDao,
) : ViewModel() {

    @OptIn(FlowPreview::class)
    val predictedEnergyLevel = kotlinx.coroutines.flow.combine(
        userProfileDao.getProfileLive(),
        GlobalTime.offsetFlow
    ) { profile, _ -> profile?.predictionModel ?: "DEFAULT" }
        .flatMapLatest { model ->
            // Choose the correct DAO based on the model setting
            if (model == "MODEL2") {
                predictedEnergyLevelModell2Dao.getAllLive().map { entries ->
                    entries.map { EnergyEntry(it.time.toEpochMilliseconds(), it.percentageNow.toDouble(), it.percentageFuture.toDouble()) }
                }
            } else {
                predictedEnergyLevelDao.getAllLive().map { entries ->
                    entries.map { EnergyEntry(it.time.toEpochMilliseconds(), it.percentageNow.toDouble(), it.percentageFuture.toDouble()) }
                }
            }
        }
        .debounce(200)
        .map { entries ->
            val targetTime = GlobalTime.now()
            val targetMillis = targetTime.toEpochMilliseconds()

            // Filter entries to only show data up to simulated time
            val filteredEntries = entries.filter {
                it.timeMillis <= targetMillis
            }.sortedBy { it.timeMillis }

            if (filteredEntries.isEmpty()) {
                return@map EnergyGraphData(Series(listOf(0.0, 0.5), listOf(0.5, 0.5)), 0.5)
            }

            val energySeries = Series(mutableListOf(), mutableListOf())
            filteredEntries.forEach { entry ->
                energySeries.x.add(entry.timeMillis.toDouble())
                energySeries.y.add(entry.percentageNow)
            }

            val latestEntry = filteredEntries.last()

            EnergyGraphData(
                Series(energySeries.x.toList(), energySeries.y.toList()),
                latestEntry.percentageFuture
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

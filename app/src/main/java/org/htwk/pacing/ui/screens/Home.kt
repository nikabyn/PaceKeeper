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
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

import kotlin.time.Duration.Companion.hours

data class EnergyGraphData(
    val entries: List<PredictedEnergyLevelEntry>,
    val currentValue: Double,
    val futureValue: Double
)

@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val energyGraphData by viewModel.predictedEnergyLevel.collectAsState()
    val currentEnergy = energyGraphData.currentValue
    val minPrediction = energyGraphData.futureValue - 0.1
    val maxPrediction = energyGraphData.futureValue + 0.1
    val avgPrediction = energyGraphData.futureValue

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
        ) {
            EnergyPredictionCard(
                data = energyGraphData.entries,
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

class HomeViewModel(
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val predictedEnergyLevelModell2Dao: PredictedEnergyLevelModell2Dao,
    private val validatedEnergyLevelDao: ValidatedEnergyLevelDao,
    private val userProfileDao: UserProfileDao,
) : ViewModel() {
    @OptIn(FlowPreview::class)
    val predictedEnergyLevel = userProfileDao.getProfileLive()
        .map { profile -> profile?.predictionModel ?: "DEFAULT" }
        .flatMapLatest { model ->
            // Choose the correct DAO based on the model setting
            if (model == "MODEL2") {
                predictedEnergyLevelModell2Dao.getAllLive().map { entries ->
                    entries.map { PredictedEnergyLevelEntry(
                        time = it.time,
                        percentageNow = it.percentageNow,
                        timeFuture = it.timeFuture,
                        percentageFuture = it.percentageFuture) }
                }
            } else {
                predictedEnergyLevelDao.getAllLive()
            }
        }
        .debounce(200)
        .map { entries ->
            val now = Clock.System.now()
            val windowStart = (now - 24.hours).toEpochMilliseconds()
            val windowEnd = now.toEpochMilliseconds()

            // Filter entries to show last 24 hours
            val filteredEntries = entries.filter {
                it.time.toEpochMilliseconds() in windowStart..windowEnd
            }.sortedBy { it.time }

            if (filteredEntries.isEmpty()) {
                return@map EnergyGraphData(emptyList(), 0.5, 0.5)
            }

            val latestEntry = filteredEntries.last()

            EnergyGraphData(
                    filteredEntries,
                latestEntry.percentageNow.toDouble(),
                latestEntry.percentageFuture.toDouble()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EnergyGraphData(emptyList(), 0.5, 0.5)
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

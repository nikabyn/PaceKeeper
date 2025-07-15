package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.datetime.Clock
import org.htwk.pacing.ui.components.BatteryCard
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.FeelingSelectionCard
import org.htwk.pacing.ui.components.Series
import kotlin.time.Duration.Companion.hours

@Composable
fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val now = Clock.System.now()
    val energySeries = Series(
        listOf(
            now - 12.hours,
            now - 11.hours,
            now - 10.hours,
            now - 6.hours,
            now - 4.hours,
            now - 2.hours,
            now
        ).map { it.toEpochMilliseconds().toDouble() },
        listOf(0.8, 0.82, 0.7, 0.65, 0.67, 0.45, 0.4),
    )
    val currentEnergy = energySeries.y.last()
    val minPrediction = 0.1f
    val avgPrediction = 0.35f
    val maxPrediction = 0.4f

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

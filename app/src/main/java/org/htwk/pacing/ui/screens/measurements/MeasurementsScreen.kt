package org.htwk.pacing.ui.screens.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.Graph
import org.htwk.pacing.ui.components.GraphCanvas
import org.htwk.pacing.ui.components.graphToPaths
import org.htwk.pacing.ui.screens.measurements.Measurement.Distance
import org.htwk.pacing.ui.screens.measurements.Measurement.ElevationGained
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRate
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRateVariabilityRmssd
import org.htwk.pacing.ui.screens.measurements.Measurement.MenstruationPeriod
import org.htwk.pacing.ui.screens.measurements.Measurement.OxygenSaturation
import org.htwk.pacing.ui.screens.measurements.Measurement.SkinTemperature
import org.htwk.pacing.ui.screens.measurements.Measurement.Sleep
import org.htwk.pacing.ui.screens.measurements.Measurement.Speed
import org.htwk.pacing.ui.screens.measurements.Measurement.Steps
import org.htwk.pacing.ui.screens.measurements.Measurement.Symptoms
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.minutes

@Composable
fun MeasurementsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
) {
    var measurements by remember { mutableStateOf(viewModel.initialMeasurementsToday()) }

    LaunchedEffect(Unit) {
        while (true) {
            measurements = viewModel.measurementsToday()
            delay(1.minutes)
        }
    }

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
        ) {
            MeasurementsCategory("Activity")
            MeasurementsCard(navController, Steps, measurements)
            MeasurementsCard(navController, Distance, measurements)
            MeasurementsCard(navController, ElevationGained, measurements)
            MeasurementsCard(navController, Speed, measurements)

            Spacer(Modifier.height(Spacing.medium))
            MeasurementsCategory("Health")
            MeasurementsCard(navController, HeartRate, measurements)
            MeasurementsCard(navController, Sleep, measurements)
            MeasurementsCard(navController, Symptoms, measurements)
            MeasurementsCard(navController, MenstruationPeriod, measurements)
            MeasurementsCard(navController, OxygenSaturation, measurements)
            MeasurementsCard(navController, HeartRateVariabilityRmssd, measurements)
            MeasurementsCard(navController, SkinTemperature, measurements)
        }
    }
}

@Composable
private fun MeasurementsCategory(name: String) =
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(start = Spacing.medium),
    )


@Composable
private fun MeasurementsCard(
    navController: NavController,
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>,
) {
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        onClick = { navController.navigate(Route.measurement(measurement)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("MeasurementsCard")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
                .fillMaxSize(),
        ) {
            Text(
                text = measurement.title(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("MeasurementsCardTitle"),
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            Row {
                TitleAndStats(
                    measurement,
                    measurements,
                    modifier = Modifier
                        .weight(1f)
                )
                GraphPreview(
                    measurement,
                    measurements,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                )
            }
        }
    }
}

@Composable
private fun TitleAndStats(
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val stats = accumulateStatistics(measurement, measurements)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Text(
            text = stats.value ?: "–",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.testTag("MeasurementsStatsValue"),
        )

        Text(
            text = stats.unit() ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.testTag("MeasurementsStatsUnit"),
        )
    }

    Text(
        text = stats.note(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.testTag("MeasurementsStatsNote"),
    )
}

@Composable
private fun GraphPreview(
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>,
    modifier: Modifier = Modifier,
) {
    val entries = measurements[measurement]
    if (entries.isNullOrEmpty()) return

    val (xData, yData) = entries.map { measurement.toGraphValue(it) }.unzip()

    val xRange = TimeRange.today().toEpochDoubleRange()
    val yRange = measurement.yRange(yData)
    val strokeStyle = Graph.defaultStrokeStyle()
    val strokeColor = MaterialTheme.colorScheme.onSurface

    GraphCanvas(modifier) {
        drawPath(
            graphToPaths(xData, yData, size, xRange, yRange).line,
            color = strokeColor,
            style = strokeStyle,
        )
    }
}

class MeasurementsViewModel(private val db: PacingDatabase) : ViewModel() {
    fun initialMeasurementsToday() = Measurement.entries.associateWith {
        emptyList<TimedEntry>()
    }

    suspend fun measurementsToday() = Measurement.entries.associateWith {
        it.dao(db).getInRange(
            TimeRange.today().start,
            TimeRange.today().end
        )
    }
}
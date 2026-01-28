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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.GraphCanvas
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

/**
 * Top-level screen composable that displays today’s measurements grouped by category.
 *
 * It periodically refreshes today’s measurements from the [MeasurementsViewModel]
 * and renders a scrollable list of measurement cards. Each card shows a summary
 * statistic and a small graph preview, and navigates to a detailed screen when tapped.
 *
 * @param navController Used to navigate to individual measurement detail screens.
 * @param modifier Optional [Modifier] for styling and layout.
 * @param viewModel Provides access to measurement data for today.
 */
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
            MeasurementsCategory(stringResource(R.string.activity))
            MeasurementsCard(navController, Steps, measurements)
            MeasurementsCard(navController, Distance, measurements)
            MeasurementsCard(navController, ElevationGained, measurements)
            MeasurementsCard(navController, Speed, measurements)

            Spacer(Modifier.height(Spacing.medium))
            MeasurementsCategory(stringResource(R.string.health))
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

/**
 * Displays a category header for a group of measurements.
 *
 * Used to visually separate measurement cards into logical sections
 * such as "Activity" or "Health".
 *
 * @param name The display name of the category.
 */
@Composable
private fun MeasurementsCategory(name: String) =
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(start = Spacing.medium),
    )

/**
 * A clickable card that summarizes a single measurement.
 *
 * The card shows the measurement title, aggregated statistics for today,
 * and a compact graph preview. Clicking the card navigates to the
 * detailed measurement screen.
 *
 * @param navController Used to navigate to the measurement detail screen.
 * @param measurement The measurement type represented by this card.
 * @param measurements A map of all measurements and their timed entries for today.
 */
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

/**
 * Displays the primary statistic and supplementary note for a measurement.
 *
 * This composable computes aggregated statistics for the given measurement
 * and renders the value, unit, and an explanatory note.
 *
 * @param measurement The measurement whose statistics are shown.
 * @param measurements A map of all measurements and their timed entries.
 * @param modifier Optional [Modifier] for styling and layout.
 */
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

/**
 * Renders a small, graph preview for a measurement.
 *
 * The graph type and drawing strategy depend on the measurement:
 * accumulated values, line graphs, or sleep stage segments. Measurements
 * without meaningful preview data are intentionally left blank.
 *
 * @param measurement The measurement to visualize.
 * @param measurements A map of all measurements and their timed entries.
 * @param modifier Optional [Modifier] for styling and layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphPreview(
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>,
    modifier: Modifier = Modifier
) {
    val entries = measurements[measurement]

    val colorLine = MaterialTheme.colorScheme.primary
    val colorAwake = MaterialTheme.colorScheme.error
    val colorREM = MaterialTheme.colorScheme.tertiary
    val colorLightSleep = MaterialTheme.colorScheme.primary
    val colorDeepSleep = MaterialTheme.colorScheme.secondary

    GraphCanvas(modifier.fillMaxWidth()) {
        drawMeasurementPreview(
            measurement,
            entries.orEmpty(),
            colorLine,
            colorAwake,
            colorREM,
            colorLightSleep,
            colorDeepSleep,
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
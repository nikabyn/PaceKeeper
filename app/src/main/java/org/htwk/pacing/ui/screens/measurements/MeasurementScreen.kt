package org.htwk.pacing.ui.screens.measurements

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.ui.components.AxisLabel
import org.htwk.pacing.ui.components.CardWithTitle
import org.htwk.pacing.ui.components.GraphCanvas
import org.htwk.pacing.ui.components.GraphLayout
import org.htwk.pacing.ui.components.drawLines
import org.htwk.pacing.ui.theme.Spacing
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    navController: NavController,
    measurement: Measurement,
    modifier: Modifier = Modifier,
    viewModel: MeasurementViewModel = MeasurementViewModel(measurement, koinInject()),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        FullscreenGraphOverlay(
            viewModel = viewModel,
            onDismiss = { expanded = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painterResource(R.drawable.rounded_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        text = measurement.title(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.large)) {
                GraphPreview(
                    viewModel,
                    onClick = { expanded = true },
                    modifier = Modifier.height(350.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphPreview(
    viewModel: MeasurementViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entriesToday by viewModel.entriesToday.collectAsState()
    val yDataToday = entriesToday.fastMap { viewModel.measurement.entryToYValue(it) }
    val yRange = viewModel.measurement.yRange(yDataToday)
    val ySteps = viewModel.measurement.ySteps(yRange)

    Log.d("abc " + viewModel.measurement.toString(), ySteps.toString())

    val colorLine = MaterialTheme.colorScheme.primary
    val colorAwake = MaterialTheme.colorScheme.error
    val colorREM = MaterialTheme.colorScheme.tertiary
    val colorLightSleep = MaterialTheme.colorScheme.primary
    val colorDeepSleep = MaterialTheme.colorScheme.secondary

    CardWithTitle(
        title = stringResource(R.string.today),
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("GraphPreviewCard")
    ) {
        GraphLayout(
            xLabels = {
                for (i in 0..24 step 4) {
                    AxisLabel(i.toString())
                }
            },
            yLabels = {
                ySteps.fastForEachReversed {
                    AxisLabel(it)
                }
            }
        ) {
            GraphCanvas(
                Modifier
                    .padding(Spacing.small)
                    .drawLines(ySteps.size)
            ) {
                drawMeasurementPreview(
                    viewModel.measurement,
                    entriesToday,
                    colorLine,
                    colorAwake,
                    colorREM,
                    colorLightSleep,
                    colorDeepSleep,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenGraphOverlay(
    viewModel: MeasurementViewModel,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BackHandler(onBack = onDismiss)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDismiss,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(painterResource(R.drawable.rounded_expand_content), /* TODO */ "")
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = offset.copy(x = offset.x + pan.x)
                    }
                }
        ) {
            val entriesToday by viewModel.entriesToday.collectAsState()
            val yDataToday = entriesToday.fastMap { viewModel.measurement.entryToYValue(it) }
            val yRange = viewModel.measurement.yRange(yDataToday)
            val ySteps = viewModel.measurement.ySteps(yRange)

            val colorLine = MaterialTheme.colorScheme.primary
            val colorAwake = MaterialTheme.colorScheme.error
            val colorREM = MaterialTheme.colorScheme.tertiary
            val colorLightSleep = MaterialTheme.colorScheme.primary
            val colorDeepSleep = MaterialTheme.colorScheme.secondary

            GraphCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .drawLines(ySteps.size)
            ) {
                drawMeasurementPreview(
                    viewModel.measurement,
                    entriesToday,
                    colorLine,
                    colorAwake,
                    colorREM,
                    colorLightSleep,
                    colorDeepSleep,
                )
            }
        }
    }
}

class MeasurementViewModel(val measurement: Measurement, val db: PacingDatabase) : ViewModel() {
    private val dao = measurement.dao(db)

    val entriesToday = dao.getChangeTrigger()
        .map {
            val today = TimeRange.today()
            dao.getInRange(today.start, today.end)
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val entries = dao.getChangeTrigger()
        .map { dao.getAll() }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

package org.htwk.pacing.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.logo.BlinkLogo
import org.htwk.pacing.ui.logo.Floaty
import org.htwk.pacing.ui.logo.RollingEntry
import org.htwk.pacing.ui.logo.shuffleSmileys
import org.htwk.pacing.ui.theme.Spacing
import org.htwk.pacing.ui.theme.extendedColors
import kotlin.random.Random

data class OnboardingPage(
    val title: String,
    val description: String,
    val optionalDescription2: String,
    val optionalDescription3: String,
    val icon: Int,
    val optionalIcon2: Int? = null,
    val optionalIcon3: Int? = null,
    val optionalIcon4: Int? = null
)

val pages = listOf(
    OnboardingPage(
        "Willkommen",
        "PaceKeeper hilft dir, dein Energie-Budget im Auge zu behalten.",
        "",
        "",
        R.drawable.ic_logo_open,
        R.drawable.ic_logo_closed
    ),
    OnboardingPage(
        "Pacing verstehen",
        "Vermeide den Crash.",
        "Bleibe in deinem sicheren Bereich.",
        "Deine Tagesenergie auf einen Blick.",
        R.drawable.rounded_show_chart_24
    ),
    OnboardingPage(
        "Dich selbst entdecken",
        "Höre auf deinen Körper.",
        "Erkenne deine Muster.",
        "Deine Daten und Symptome im Verlauf.",
        R.drawable.very_happy,
        R.drawable.happy,
        R.drawable.sad,
        R.drawable.very_sad
    ),
    OnboardingPage(
        "Loslegen",
        "Höre auf deinen Körper. Wir starten jetzt.",
        " ",
        "",
        R.drawable.ic_logo_open,
        R.drawable.ic_logo_closed
    )
)

fun randomSeries(size: Int): Series<List<Double>> {
    val xValues = List(size) { Random.nextDouble() }
    val yValues = List(size) { Random.nextDouble() }
    return Series(xValues, yValues)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var termsAccepted by remember { mutableStateOf(false) }
    val isLastPage = pagerState.currentPage == pages.size - 1

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.extendedColors.pink)
            .padding(Spacing.medium)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(
                    page = pages[pageIndex],
                    pageIndex = pageIndex,
                    showCheckbox = pageIndex == pages.size - 1,
                    isChecked = termsAccepted,
                    onCheckedChange = { termsAccepted = it }
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.logo)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    repeat(pages.size) { iteration ->
                        val color =
                            if (pagerState.currentPage == iteration) MaterialTheme.extendedColors.logo else MaterialTheme.colorScheme.outlineVariant
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(10.dp)
                        )
                    }
                }

                val buttonEnabled = if (isLastPage) termsAccepted else true
                Button(
                    onClick = {
                        if (isLastPage) onFinished() else scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1
                            )
                        }
                    },
                    enabled = buttonEnabled,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.logo)
                ) {
                    AnimatedVisibility(visible = isLastPage) { Icon(Icons.Default.Check, "Fertig") }
                    AnimatedVisibility(visible = !isLastPage) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            "Weiter"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    pageIndex: Int,
    showCheckbox: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (pageIndex) {
            0 -> {
                RollingEntry {
                    Floaty {
                        if (page.optionalIcon2 != null) {
                            BlinkLogo(
                                open = page.icon,
                                closed = page.optionalIcon2
                            )
                        } else {
                            Image(
                                painter = painterResource(page.icon),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
            }

            1 -> {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    val exSeries: Series<List<Double>> =
                        Series(
                            x = List(10) { Random.nextDouble() },
                            y = List(10) { Random.nextDouble() }
                        )

                    EnergyPredictionCard(
                        series = exSeries,
                        currentEnergy = 0.8f,
                        minPrediction = 0.0f,
                        avgPrediction = 0.5f,
                        maxPrediction = 1.0f,
                        modifier = Modifier.height(300.dp)

                    )
                }
            }

            2 -> {
                RollingEntry {
                    Floaty {
                        if (page.optionalIcon2 != null && page.optionalIcon3 != null && page.optionalIcon4 != null) {
                            shuffleSmileys(
                                page.icon,
                                page.optionalIcon2,
                                page.optionalIcon3,
                                page.optionalIcon4
                            )
                        } else {
                            Image(
                                painter = painterResource(page.icon),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }

            }

            3 -> {
                if (isChecked && page.optionalIcon2 != null) {
                    Image(
                        painter = painterResource(id = page.optionalIcon2),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(page.icon),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.large)
        )
        if (page.optionalDescription2 != "")
            Text(
                text = page.optionalDescription2,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.large)
            )
        if (page.optionalDescription3 != "")
            Text(
                text = page.optionalDescription3,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.large)
            )

        if (showCheckbox) {
            ScrollBox()
            Spacer(modifier = Modifier.height(Spacing.extraLarge))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(Spacing.medium))
                    .background(MaterialTheme.extendedColors.logo.copy(alpha = 0.1f))
                    .padding(Spacing.small)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(Spacing.extraLarge))
                Text(
                    text = "Ich habe die Datennutzungsbestimmungen gelesen und stimme der Datennutzung zu.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.small),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

class WelcomeViewModel(
    private val db: PacingDatabase
) : ViewModel() {

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            //TODO save in db
            onSuccess()
        }
    }
}

@Composable
private fun ScrollBox() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.medium))
            .background(MaterialTheme.extendedColors.logo.copy(alpha = 0.1f))
            .fillMaxWidth()
            .size(100.dp)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.small)
    ) {
        repeat(10) {
            Text(
                text = "Helloo",
                modifier = Modifier.padding(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
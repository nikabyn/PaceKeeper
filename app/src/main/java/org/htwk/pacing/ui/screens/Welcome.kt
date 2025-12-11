package org.htwk.pacing.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.logo.BlinkLogo
import org.htwk.pacing.ui.logo.Floaty
import org.htwk.pacing.ui.logo.RollingEntry
import org.htwk.pacing.ui.logo.shuffleSmileys
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel
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
        "Wir starten jetzt.",
        " ",
        "",
        R.drawable.ic_logo_open,
        R.drawable.ic_logo_closed
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit, viewModel: WelcomeViewModel = koinViewModel()) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Scaffold { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    OnboardingPageContent(
                        page = pages[pageIndex],
                        pageIndex = pageIndex,
                        viewModel,
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
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        repeat(pages.size) { iteration ->
                            val color =
                                if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(10.dp)
                            )
                        }
                    }

                    val buttonEnabled = if (isLastPage) viewModel.termsAccepted else true
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
                    ) {
                        AnimatedVisibility(visible = isLastPage) {
                            Icon(
                                Icons.Default.Check,
                                "Fertig"
                            )
                        }
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
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    pageIndex: Int,
    viewModel: WelcomeViewModel,
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
                WelcomePage(page)
            }

            1 -> {
                PredictionPage()
            }

            2 -> {
                SymptomPage(page)
            }

            3 -> {
                DataUsagePage(viewModel, page, 1)
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
        if (pageIndex == 3) DataUsagePage(viewModel, page, 2)
    }
}

@Composable
fun WelcomePage(page: OnboardingPage) {
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

@Composable
fun PredictionPage() {
    Box(
        contentAlignment = Alignment.Center
    ) {
        val exSeries: Series<List<Double>> =
            Series(
                x = List(10) { Random.nextDouble(0.0, 1.0) },
                y = List(10) { Random.nextDouble(0.0, 1.0) }
            )

        EnergyPredictionCard(
            series = exSeries,
            currentEnergy = 0.5f,
            minPrediction = 0.0f,
            avgPrediction = 0.6f,
            maxPrediction = 1.0f,
            modifier = Modifier.height(300.dp)

        )
    }
}

@Composable
fun SymptomPage(page: OnboardingPage) {
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

@Composable
fun DataUsagePage(viewModel: WelcomeViewModel, page: OnboardingPage, part: Int) {
    val onAcceptedChange = { newVal: Boolean -> viewModel.termsAccepted = newVal }
    if (part == 1) {
        if (viewModel.termsAccepted && page.optionalIcon2 != null) {
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
    } else if (part == 2) {
        ScrollBox()
        Spacer(modifier = Modifier.height(Spacing.extraLarge))
        Card(
            shape = CardStyle.shape,
            colors = CardStyle.colors,
            onClick = { onAcceptedChange(!viewModel.termsAccepted) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(Spacing.medium),
            ) {
                Checkbox(
                    checked = viewModel.termsAccepted,
                    onCheckedChange = onAcceptedChange
                )
                Text(
                    text = "Ich habe die Datennutzungsbestimmungen gelesen und stimme der Datennutzung zu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

class WelcomeViewModel(
    private val dao: UserProfileDao
) : ViewModel() {
    var termsAccepted by mutableStateOf(false)

    val checkedIn: StateFlow<Boolean> = dao.getCheckedIn()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            dao.updateCheckedIn(true)
            onSuccess()
        }
    }
}

@Composable
private fun ScrollBox() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.medium))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .fillMaxWidth()
            .size(100.dp)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.small)
    ) {
        Text(
            text = "1. Wer sind wir?\n" +
                    "\n" +
                    "Dieses Projekt wird im Rahmen einer hochschulischen Lehrveranstaltung an der HTWK Leipzig in Zusammenarbeit mit dem Unternehmen FAKT Software GMBH entwickelt.\n" +
                    "Ziel ist die Entwicklung und Erprobung eines App-Prototyps, der Gesundheits- und Aktivitätsdaten zur Prognose des Energiehaushaltes sowie zur Aufstellung von Handlungsempfehlungen nutzt.\n" +
                    "Eine kommerzielle Nutzung ist nicht vorgesehen.\n" +
                    "\n" +
                    "Verantwortlich für die Datenverarbeitung:\n" +
                    "HTWK Leipzig\n" +
                    "Karl-Liebknecht-Str. 132\n" +
                    "04277 Leipzig\n" +
                    "Deutschland\n" +
                    "\n" +
                    "Durchgeführt im Rahmen eines studentischen Projekts an der Hochschule.\n" +
                    "Bei Fragen zum Datenschutz kannst du dich an die Hochschule oder den\n" +
                    "Datenschutzbeauftragten wenden.\n" +
                    "\n" +
                    "2. Welche Daten verarbeiten wir?\n" +
                    "\n" +
                    "Wir verarbeiten nur die Daten, die du uns freiwillig zur Verfügung stellst oder die du über Google Health Connect aktiv freigibst.\n" +
                    "Dazu gehören insbesondere:\n" +
                    "• Aktivitäts- und Gesundheitsdaten (z. B. Schritte, Puls, Schlafdauer, Vitalparameter)\n" +
                    "Die Daten werden ausschließlich abgerufen, wenn du uns über Health Connect\n" +
                    "ausdrücklich erlaubst, darauf zuzugreifen.\n" +
                    "\n" +
                    "2a. Datenverarbeitung bei Nutzung von Drittanbieterdiensten (z. B. Fitbit)\n" +
                    "\n" +
                    "Wenn du externen Dienst verbindest (z. B. einen Fitness- oder Gesundheitsdienst wie Fitbit), erhalten wir nur die Daten, die du dort ausdrücklich freigibst. Dazu können – abhängig vom jeweiligen Dienst – insbesondere Aktivitäts-, Gesundheits- oder Sensordaten gehören (z. B. Schritte, Puls oder Workout-Daten).\n" +
                    "Die Datenübertragung erfolgt ausschließlich auf Grundlage deiner ausdrücklichen Einwilligung (Art. 6 Abs. 1 lit. a, Art. 9 Abs. 2 lit. a DSGVO). Du kannst diese Freigabe jederzeit in den Einstellungen des jeweiligen Drittanbieters widerrufen. Sobald der Zugriff widerrufen wird, erhalten wir keine weiteren Daten über diesen Dienst.\n" +
                    "Wir integrieren solche Drittanbieter ausschließlich, um dir zusätzliche Funktionen zu ermöglichen (z. B. die Synchronisierung deiner Fitness-Daten). Eine Weitergabe deiner Daten an andere Stellen findet nicht statt. Sollten wir in Zukunft weitere kompatible Dienste anbinden, gelten die oben beschriebenen Grundsätze entsprechend.\n" +
                    "\n" +
                    "3. Warum verarbeiten wir deine Daten?\n" +
                    "\n" +
                    "Wir verwenden die erhobenen Daten ausschließlich zu folgenden Zwecken:\n" +
                    "• Test und Analyse der App-Funktionen im Rahmen des hochschulischen Projekts\n" +
                    "• Verbesserung und Evaluation unseres Machine-Learning-Modells\n" +
                    "• Synchronisierung und Anzeige deiner Daten über Health Connect\n" +
                    "Es findet keine kommerzielle Nutzung deiner Daten statt.\n" +
                    "\n" +
                    "4. Rechtsgrundlage\n" +
                    "\n" +
                    "Die Verarbeitung deiner Gesundheitsdaten erfolgt nur auf Grundlage deiner\n" +
                    "ausdrücklichen Einwilligung (Art. 6 Abs. 1 lit. a, Art. 9 Abs. 2 lit. a DSGVO).\n" +
                    "Du kannst diese Einwilligung jederzeit mit Wirkung für die Zukunft widerrufen – zum Beispiel direkt in der App oder durch Nachricht an das Projektteam.\n" +
                    "\n" +
                    "5. Wie und wo werden deine Daten gespeichert?\n" +
                    "\n" +
                    "• Deine Daten werden ausschließlich auf deinem eigenen Gerät gespeichert.\n" +
                    "• Eine Übermittlung an das Projektteam oder an FAKT Software GMBH erfolgt nur, wenn\n" +
                    "du dies manuell und ausdrücklich erlaubst (z. B. durch das Senden von\n" +
                    "Feedbackdaten zur Verbesserung des Prototyps).\n" +
                    "\n" +
                    "6. Datensicherheit\n" +
                    "\n" +
                    "Die App ist architektonisch so konzipiert, dass alle Daten ausschließlich auf dem eigenen Gerät gespeichert werden. Dadurch wird eine missbräuchliche Verwendung oder Nutzung durch Dritte wirksam verhindert.\n" +
                    "\n" +
                    "7. Deine Rechte\n" +
                    "\n" +
                    "Du hast jederzeit das Recht auf:\n" +
                    "• Auskunft über die zu deiner Person gespeicherten Daten\n" +
                    "• Berichtigung oder Löschung deiner Daten\n" +
                    "• Einschränkung der Verarbeitung\n" +
                    "• Datenübertragbarkeit\n" +
                    "• Widerruf deiner Einwilligung,\n" +
                    "Wenn du Fragen oder Bedenken zum Umgang mit deinen Daten hast, kannst du dich jederzeit an uns wenden.\n" +
                    "Natürlich hast du auch das Recht, dich an die Datenschutzstelle der HTWK Leipzig oder an eine Datenschutzaufsichtsbehörde zu wenden.\n" +
                    "\n" +
                    "8. Keine automatisierten Entscheidungen\n" +
                    "\n" +
                    "Es findet keine automatisierte Entscheidungsfindung im Sinne von Art. 22 DSGVO statt.\n" +
                    "Alle Empfehlungen der App dienen ausschließlich zu Informationszwecken und haben keine rechtlichen oder medizinisch verbindlichen Folgen.\n" +
                    "\n" +
                    "9. Automatisierte Auswertung / Profiling\n" +
                    "\n" +
                    "Im Rahmen dieses Projekts werden deine Gesundheitsdaten mithilfe von Machine-Learning-Verfahren analysiert.\n" +
                    "Ziel ist es, Muster in deinem Aktivitätsverhalten zu erkennen und Empfehlungen zur Energieeinsparung oder Ruhepausen zu geben.\n" +
                    "Diese Auswertungen dienen ausschließlich zu Forschungs- und Testzwecken innerhalb des Projekts.\n" +
                    "Die Ergebnisse sind nicht medizinisch validiert und sollen dir lediglich informative Hinweise bieten.\n" +
                    "Die Verarbeitung und Auswertung dieser Daten erfolgt nur mit deiner ausdrücklichen Einwilligung.\n" +
                    "Du kannst diese Einwilligung jederzeit widerrufen.",
            modifier = Modifier.padding(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
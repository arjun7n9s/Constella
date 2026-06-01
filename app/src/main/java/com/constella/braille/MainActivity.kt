package com.constella.braille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.constella.braille.domain.grade.HeuristicGradeDetector
import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.ScanResult
import com.constella.braille.domain.notify.Notifier
import com.constella.braille.domain.orchestration.ScanCoordinator
import com.constella.braille.domain.orchestration.SessionState
import com.constella.braille.domain.recognize.DefaultPatternRecognizer
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.ui.results.ResultsContent
import com.constella.braille.ui.scanning.ScanningScreen
import com.constella.braille.ui.settings.SettingsScreen
import com.constella.braille.ui.theme.BrailleScannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppScreen {
    SCANNING,
    SETTINGS,
    RESULTS
}

class MainViewModel(
    val coordinator: ScanCoordinator,
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(AppScreen.SCANNING)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _cloudOptInEnabled = MutableStateFlow(false)
    val cloudOptInEnabled: StateFlow<Boolean> = _cloudOptInEnabled.asStateFlow()

    // Temporary state to hold guidance since it's updated frequently.
    private val _guidance = MutableStateFlow<AlignmentGuidance?>(AlignmentGuidance.ReadyToScan)
    val guidance: StateFlow<AlignmentGuidance?> = _guidance.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleTorch() {
        _torchEnabled.value = !_torchEnabled.value
    }

    fun toggleCloudOptIn(enabled: Boolean) {
        _cloudOptInEnabled.value = enabled
    }

    fun simulateCapture() {
        // For UI testing/wiring. In reality, this would trigger Camera capture,
        // which then calls coordinator.executePipeline(...)
        val result = coordinator.executePipeline(
            dots = emptyList(),
            structureInferable = false,
            perspectiveCorrected = false,
            displayAvailable = true
        )
        if (result != null) {
            navigateTo(AppScreen.RESULTS)
        }
    }
}

class MainViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Stub implementation of TranslationEngine to satisfy the compiler
        val dummyTranslationEngine = object : TranslationEngine {
            override fun translate(
                cells: List<com.constella.braille.domain.model.RecognizedCell>,
                grade: com.constella.braille.domain.model.Grade
            ): com.constella.braille.domain.translate.TranslationOutput {
                return com.constella.braille.domain.translate.TranslationOutput("", emptyList(), emptyList())
            }
        }

        val coordinator = ScanCoordinator(
            recognizer = DefaultPatternRecognizer(),
            gradeDetector = HeuristicGradeDetector(),
            translationEngine = dummyTranslationEngine,
            notifier = Notifier()
        )

        @Suppress("UNCHECKED_CAST")
        return MainViewModel(coordinator) as T
    }
}

/**
 * Single-activity host for the Compose UI (UI layer).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrailleScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrailleApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun BrailleApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    when (currentScreen) {
        AppScreen.SCANNING -> {
            val torchEnabled by viewModel.torchEnabled.collectAsState()
            val guidance by viewModel.guidance.collectAsState()
            val sessionState = viewModel.coordinator.session.state

            ScanningScreen(
                guidance = guidance,
                isScanning = sessionState == SessionState.SCANNING,
                activeModeName = viewModel.coordinator.scanningMode.activeModeName,
                torchEnabled = torchEnabled,
                onCapture = { viewModel.simulateCapture() },
                onTorchToggle = { viewModel.toggleTorch() },
                onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
        }
        AppScreen.SETTINGS -> {
            val cloudOptInEnabled by viewModel.cloudOptInEnabled.collectAsState()
            val session = viewModel.coordinator.session

            SettingsScreen(
                currentScanningMode = session.scanningMode,
                currentGradeMode = session.gradeMode,
                cloudOptInEnabled = cloudOptInEnabled,
                onScanningModeSelected = { viewModel.coordinator.selectScanningMode(it) },
                onGradeModeSelected = { viewModel.coordinator.selectGradeMode(it) },
                onCloudOptInToggle = { viewModel.toggleCloudOptIn(it) }
            )
        }
        AppScreen.RESULTS -> {
            // Retrieve retained result or show empty state if null
            val result = viewModel.coordinator.retainer.consume()

            if (result != null) {
                ResultsContent(result = result)
            } else {
                // Fallback for demo/wiring if no result was retained
                val fallbackResult = ScanResult(
                    recognizedText = "No result found.",
                    charSpans = emptyList(),
                    overallConfidence = com.constella.braille.domain.model.Confidence.ZERO,
                    scanningMode = viewModel.coordinator.session.scanningMode,
                    resolvedGrade = com.constella.braille.domain.model.Grade.GRADE_1,
                    gradeMode = viewModel.coordinator.session.gradeMode,
                    gradeWasAutoDetected = true,
                    untranslatableCells = emptyList(),
                    perspectiveCorrected = false,
                    status = com.constella.braille.domain.model.ScanStatus.Success
                )
                ResultsContent(result = fallbackResult)
            }
        }
    }
}

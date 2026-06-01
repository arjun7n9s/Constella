package com.constella.braille.domain.orchestration

import com.constella.braille.domain.alignment.AlignmentEvaluator
import com.constella.braille.domain.alignment.AlignmentMetrics
import com.constella.braille.domain.alignment.ReadyToScanStateMachine
import com.constella.braille.domain.grade.GradeController
import com.constella.braille.domain.grade.GradeDetector
import com.constella.braille.domain.mode.ScanningModeController
import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.ScanResult
import com.constella.braille.domain.model.ScanStatus
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.notify.LowConfidencePolicy
import com.constella.braille.domain.notify.Notification
import com.constella.braille.domain.notify.NotificationCategory
import com.constella.braille.domain.notify.Notifier
import com.constella.braille.domain.notify.ScanStatusMessages
import com.constella.braille.domain.recognize.PatternRecognizer
import com.constella.braille.domain.results.ScanResultRetainer
import com.constella.braille.domain.segmentation.DegradedRegionHandler
import com.constella.braille.domain.translate.TranslationEngine

/**
 * Central scan pipeline orchestrator (Req 4.5, 4.6, 14.1, 14.3, 15.1).
 *
 * Wires all pipeline stages together:
 * ```
 * Dots → Cell_Segmenter → Pattern_Recognizer → Grade_Detector → Translation_Engine
 * ```
 *
 * The camera capture (CameraModule → ImagePreprocessor → DotDetector) is
 * handled by the upper `:pipeline`/`:runtime` layers; this coordinator operates
 * on the domain side, accepting already-detected dots and producing a
 * [ScanResult].
 *
 * ### Responsibilities
 *
 * - **Pipeline execution:** runs the domain stages in sequence.
 * - **Outcome classification:** reduces the pipeline output to a [ScanStatus]:
 *   [ScanStatus.NoBrailleRecognized], [ScanStatus.StructureButNoCell],
 *   [ScanStatus.LowConfidence], [ScanStatus.ProcessingError], or
 *   [ScanStatus.Success].
 * - **Error isolation:** wraps the entire pipeline so no uncaught exception
 *   crashes the session (Req 15.1).
 * - **Alignment guidance:** provides debounced ready-to-scan state via the
 *   [ReadyToScanStateMachine].
 * - **Notification wiring:** surfaces error/low-confidence messages through the
 *   [Notifier].
 * - **Result retention:** retains the [ScanResult] via [ScanResultRetainer]
 *   when the display is unavailable.
 *
 * This class is intentionally framework-free Kotlin; Android lifecycle,
 * coroutine dispatchers, and CameraX wiring are the `:app` layer's concern.
 *
 * _Requirements: 4.5, 4.6, 14.1, 14.3, 15.1_
 */
class ScanCoordinator(
    private val recognizer: PatternRecognizer,
    private val gradeDetector: GradeDetector,
    private val translationEngine: TranslationEngine,
    private val notifier: Notifier,
    private val modeController: ScanningModeController = ScanningModeController(),
    private val resultRetainer: ScanResultRetainer = ScanResultRetainer(),
) {

    /** The session state container. */
    val session: ScanSession = ScanSession()

    /** The grade controller, managing Auto/Manual grade selection. */
    val gradeController: GradeController = GradeController(gradeDetector, translationEngine)

    /** The debounced ready-to-scan state machine for alignment guidance. */
    val readyStateMachine: ReadyToScanStateMachine = ReadyToScanStateMachine()

    /** Exposes the mode controller for UI binding. */
    val scanningMode: ScanningModeController get() = modeController

    /** Exposes the result retainer for UI binding. */
    val retainer: ScanResultRetainer get() = resultRetainer

    /**
     * Feed an alignment reading and return the debounced guidance to surface.
     *
     * @param nowMs current virtual clock time, in milliseconds.
     * @param metrics the instantaneous alignment metrics for this cycle.
     * @return the [AlignmentGuidance] to display/speak this cycle.
     */
    fun evaluateAlignment(nowMs: Long, metrics: AlignmentMetrics): AlignmentGuidance {
        val update = readyStateMachine.update(nowMs, metrics)

        // Announce readiness on the transition edge (Req 2.7, 2.13).
        if (update.readyAnnounced) {
            notifier.notify(
                "Ready to scan. Hold steady.",
                NotificationCategory.GUIDANCE,
            )
        }

        return update.guidance
    }

    /**
     * Execute the domain pipeline on already-detected [dots] and produce a
     * [ScanResult].
     *
     * The caller (the `:pipeline` layer) is responsible for running the camera →
     * preprocessor → dot-detector stages and providing the detector output here.
     * This method runs the remaining stages:
     *
     * 1. Cell segmentation (GridClusterer + LineGrouper + DegradedRegionHandler)
     * 2. Pattern recognition
     * 3. Grade detection/resolution
     * 4. Translation
     * 5. Outcome classification (ScanStatus)
     * 6. Notification delivery for non-success outcomes
     *
     * The entire pipeline is wrapped in a try/catch so no exception crashes the
     * session (Req 15.1).
     *
     * @param dots the accepted dot detections from the Dot_Detector.
     * @param structureInferable the Dot_Detector's coarse structure flag.
     * @param perspectiveCorrected whether perspective correction was applied.
     * @param failedAlignmentCondition the alignment condition most likely
     *   responsible for poor quality, used for [ScanStatus.LowConfidence].
     * @param displayAvailable whether the UI display surface is currently
     *   available. When `false`, the result is retained for later presentation.
     * @return the [ScanResult], or `null` if an error was handled internally.
     */
    fun executePipeline(
        dots: List<DetectedDot>,
        structureInferable: Boolean,
        perspectiveCorrected: Boolean,
        failedAlignmentCondition: AlignmentGuidance = AlignmentGuidance.ReadyToScan,
        displayAvailable: Boolean = true,
    ): ScanResult? {
        session.state = SessionState.SCANNING

        return try {
            val result = runPipeline(
                dots = dots,
                structureInferable = structureInferable,
                perspectiveCorrected = perspectiveCorrected,
                failedAlignmentCondition = failedAlignmentCondition,
            )

            // Deliver notification for non-success outcomes.
            ScanStatusMessages.notificationFor(result.status)?.let { notifier.notify(it) }

            // Handwritten first-scan notice (Req 9.6).
            modeController.firstHandwrittenNoticeText()?.let { notice ->
                notifier.notify(Notification(notice, NotificationCategory.GUIDANCE))
            }

            session.state = SessionState.PRESENTING_RESULTS

            // Retain result if display is unavailable (Req 10.6).
            if (!displayAvailable) {
                resultRetainer.retain(result)
            }

            result
        } catch (e: Exception) {
            // Req 15.1: no uncaught exception crashes the session.
            val errorStatus = ScanStatus.ProcessingError(e.message ?: "Unknown error")
            ScanStatusMessages.notificationFor(errorStatus)?.let { notifier.notify(it) }
            session.state = SessionState.ERROR

            // Return a minimal error result.
            ScanResult(
                recognizedText = "",
                charSpans = emptyList(),
                overallConfidence = Confidence.ZERO,
                scanningMode = modeController.currentMode,
                resolvedGrade = gradeController.resolveGrade(emptyList()),
                gradeMode = gradeController.currentMode,
                gradeWasAutoDetected = gradeController.currentMode == GradeMode.AUTO,
                untranslatableCells = emptyList(),
                perspectiveCorrected = perspectiveCorrected,
                status = errorStatus,
            )
        }
    }

    /**
     * The core pipeline logic, factored out for testability and so the
     * try/catch in [executePipeline] can handle any exception uniformly.
     */
    private fun runPipeline(
        dots: List<DetectedDot>,
        structureInferable: Boolean,
        perspectiveCorrected: Boolean,
        failedAlignmentCondition: AlignmentGuidance,
    ): ScanResult {
        val currentScanningMode = modeController.currentMode

        // 1. Cell segmentation.
        val document = DegradedRegionHandler.segment(dots)

        // 2. Classify early outcomes (Req 4.5, 4.6).
        if (document.lines.isEmpty() || document.lines.all { it.cells.isEmpty() }) {
            val status = if (structureInferable) {
                ScanStatus.StructureButNoCell
            } else {
                ScanStatus.NoBrailleRecognized
            }
            return ScanResult(
                recognizedText = "",
                charSpans = emptyList(),
                overallConfidence = Confidence.ZERO,
                scanningMode = currentScanningMode,
                resolvedGrade = gradeController.resolveGrade(emptyList()),
                gradeMode = gradeController.currentMode,
                gradeWasAutoDetected = gradeController.currentMode == GradeMode.AUTO,
                untranslatableCells = emptyList(),
                perspectiveCorrected = perspectiveCorrected,
                status = status,
            )
        }

        // 3. Pattern recognition.
        val recognizedCells = recognizer.recognize(document)

        // 4. Grade resolution.
        val resolvedGrade = gradeController.resolveGrade(recognizedCells)
        val gradeWasAutoDetected = gradeController.currentMode == GradeMode.AUTO

        // 5. Translation.
        val translationOutput = translationEngine.translate(recognizedCells, resolvedGrade)

        // 6. Compute overall confidence (mean of recognized cells).
        val overallConfidence = if (recognizedCells.isEmpty()) {
            Confidence.ZERO
        } else {
            Confidence.of(
                recognizedCells.map { it.confidence.value }.average().toFloat(),
            )
        }

        // 7. Classify confidence → ScanStatus.
        val status = LowConfidencePolicy.classifyConfidence(
            overallConfidence.value,
            failedAlignmentCondition,
        )

        return ScanResult(
            recognizedText = translationOutput.text,
            charSpans = translationOutput.charSpans,
            overallConfidence = overallConfidence,
            scanningMode = currentScanningMode,
            resolvedGrade = resolvedGrade,
            gradeMode = gradeController.currentMode,
            gradeWasAutoDetected = gradeWasAutoDetected,
            untranslatableCells = translationOutput.untranslatableCells,
            perspectiveCorrected = perspectiveCorrected,
            status = status,
        )
    }

    /**
     * Switch the scanning mode and apply it to subsequent scans (Req 9.1, 9.3).
     */
    fun selectScanningMode(mode: ScanningMode) {
        modeController.selectMode(mode)
        session.scanningMode = mode
    }

    /**
     * Switch the grade mode (Req 8.3).
     */
    fun selectGradeMode(mode: GradeMode) {
        gradeController.override(mode, emptyList()) // Will be re-resolved at scan time.
        session.gradeMode = mode
    }

    /**
     * Reset the coordinator and session state for a fresh session.
     */
    fun resetSession() {
        session.reset()
        readyStateMachine.reset()
        gradeController.resetToAuto()
        resultRetainer.clear()
    }
}

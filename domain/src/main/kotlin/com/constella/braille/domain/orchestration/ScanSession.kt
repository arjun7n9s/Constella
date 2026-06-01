package com.constella.braille.domain.orchestration

import com.constella.braille.domain.model.CaptureMode
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.ScanningMode

/**
 * The possible states of a [ScanSession].
 *
 * The session progresses through a linear flow:
 * ```
 * IDLE → ALIGNING → SCANNING → PRESENTING_RESULTS
 *                                      ↓
 *                                    IDLE (new scan or session end)
 * ```
 *
 * Error conditions at any stage push to [ERROR] which may transition to IDLE
 * on retry.
 *
 * _Requirements: 4.5, 4.6, 14.1, 14.3, 15.1_
 */
enum class SessionState {
    /** No scan in progress. Awaiting user action. */
    IDLE,
    /** Camera preview is active. Alignment guidance is being provided. */
    ALIGNING,
    /** A scan is in progress (pipeline stages running). */
    SCANNING,
    /** Results are available for display. */
    PRESENTING_RESULTS,
    /** An error occurred. May retry or return to IDLE. */
    ERROR,
}

/**
 * Session-scoped state container for a single scanning session.
 *
 * Holds the current [SessionState] plus the configuration selections
 * ([ScanningMode], [GradeMode], [CaptureMode]) that persist across individual
 * scans within a session. The ScanCoordinator mutates session state as the
 * pipeline progresses; the UI layer observes it.
 *
 * This type is intentionally a plain, mutable state holder — not a ViewModel —
 * so it is framework-free and testable on the JVM. Thread safety is the
 * caller's responsibility (typically via the coordinator's coroutine scope).
 *
 * _Requirements: 9.1, 8.6, 12.1_
 */
class ScanSession(
    initialScanningMode: ScanningMode = ScanningMode.EMBOSSED,
    initialGradeMode: GradeMode = GradeMode.AUTO,
    initialCaptureMode: CaptureMode = CaptureMode.CAPTURE,
) {
    /** The current session state. */
    var state: SessionState = SessionState.IDLE
        internal set

    /** The active scanning mode for subsequent scans (Req 9.1). */
    var scanningMode: ScanningMode = initialScanningMode
        internal set

    /** The active grade mode (Req 8.6 — defaults to AUTO). */
    var gradeMode: GradeMode = initialGradeMode
        internal set

    /** The active capture mode (Live or Capture, Req 12.1). */
    var captureMode: CaptureMode = initialCaptureMode
        internal set

    /** Whether a scan is currently in flight. */
    val isScanning: Boolean get() = state == SessionState.SCANNING

    /** Whether results are ready for display. */
    val hasResults: Boolean get() = state == SessionState.PRESENTING_RESULTS

    /** Reset the session to its initial state. */
    fun reset() {
        state = SessionState.IDLE
    }
}

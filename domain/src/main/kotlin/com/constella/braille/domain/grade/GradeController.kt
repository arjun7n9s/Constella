package com.constella.braille.domain.grade

import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.domain.translate.TranslationOutput

/**
 * Manages the active [GradeMode] and resolves the effective [Grade] for
 * translation, triggering re-translation via the injected [TranslationEngine]
 * when the Operator overrides the grade (Req 8.3, 8.4).
 *
 * ### Responsibilities
 *
 * 1. **Mode management.** Holds the current [GradeMode] and exposes it via
 *    [currentMode]. Defaults to [GradeMode.AUTO] at construction (Req 8.6).
 *
 * 2. **Grade resolution.** [resolveGrade] delegates to [GradeDetector.resolveGrade]:
 *    for [GradeMode.AUTO] it returns the heuristic estimate; for the manual
 *    modes it returns the Operator's chosen grade verbatim (Req 8.1, 8.3).
 *
 * 3. **Override re-translation.** [override] changes [currentMode] to the
 *    requested mode and immediately re-translates the already-recognized [cells]
 *    via [GradeOverride.retranslate] — it never re-scans, re-detects, or
 *    re-segments (Req 8.4). The updated [TranslationOutput] is returned so the
 *    caller can update the displayed Recognized_Text.
 *
 * This class is pure Kotlin with no Android dependency. It is not thread-safe;
 * callers on the UI layer are expected to invoke it from a single thread or
 * coroutine scope.
 *
 * _Requirements: 8.1, 8.3, 8.4, 8.6_
 */
class GradeController(
    private val detector: GradeDetector,
    private val engine: TranslationEngine,
    initialMode: GradeMode = GradeMode.AUTO,
) {

    /**
     * The currently active [GradeMode]. Starts as [GradeMode.AUTO] by default
     * (Req 8.6) and changes when the Operator taps the one-tap override control.
     */
    var currentMode: GradeMode = initialMode
        private set

    /**
     * Resolve the [Grade] to use for translation given the current [currentMode]
     * and the already-recognized [cells].
     *
     * For [GradeMode.AUTO] the [GradeDetector] heuristic is consulted (Req 8.1).
     * For [GradeMode.GRADE_1] / [GradeMode.GRADE_2] the Operator's choice wins
     * unconditionally (Req 8.3). The [cells] are only read in the Auto case.
     *
     * _Requirements: 8.1, 8.3_
     */
    fun resolveGrade(cells: List<RecognizedCell>): Grade =
        detector.resolveGrade(currentMode, cells)

    /**
     * Apply a one-tap grade override: change [currentMode] to [newMode] and
     * immediately re-translate the already-recognized [cells] under the newly
     * resolved grade.
     *
     * This is the Req 8.4 path: **only translation is re-run** — there is no
     * camera capture, dot detection, or cell segmentation involved. The returned
     * [TranslationOutput] contains the updated Recognized_Text that the UI
     * should display.
     *
     * Calling [override] with the same [newMode] that is already active is
     * idempotent: [currentMode] stays the same and translation is re-run on the
     * same cells, producing the same output.
     *
     * @param newMode the [GradeMode] the Operator selected via the one-tap control.
     * @param cells the patterns already recognized by the current scan — reused
     *   as-is, never re-scanned.
     * @return the [TranslationOutput] produced by translating [cells] under the
     *   grade resolved from [newMode].
     *
     * _Requirements: 8.3, 8.4_
     */
    fun override(newMode: GradeMode, cells: List<RecognizedCell>): TranslationOutput {
        currentMode = newMode
        val resolvedGrade = detector.resolveGrade(newMode, cells)
        return GradeOverride.retranslate(engine, cells, resolvedGrade)
    }

    /**
     * Reset [currentMode] to [GradeMode.AUTO], as required at the start of each
     * session (Req 8.6).
     */
    fun resetToAuto() {
        currentMode = GradeMode.AUTO
    }
}

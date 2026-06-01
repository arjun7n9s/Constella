package com.constella.braille.domain.grade

import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.domain.translate.TranslationOutput

/**
 * The one-tap grade-override path (Req 8.4).
 *
 * When the Operator overrides the grade, the System must update the displayed
 * Recognized_Text by **re-running only translation** on the patterns it has
 * *already* recognized — it must never re-capture, re-detect, or re-segment
 * (Req 8.4). This helper makes that guarantee structural: it accepts the
 * already-recognized [RecognizedCell]s and a newly chosen [Grade] and returns
 * the new [TranslationOutput] by calling [TranslationEngine.translate] and
 * nothing else.
 *
 * Because the only inputs are the existing cells and the chosen grade — there
 * is no camera, detector, or segmenter dependency in sight — it is impossible
 * for this path to trigger a rescan. The result is exactly
 * `engine.translate(cells, newGrade)`, so re-applying the originally resolved
 * grade reproduces the original output (the override is a pure re-translation).
 *
 * This logic is pure and deterministic given a deterministic [TranslationEngine].
 *
 * _Requirements: 8.4_
 */
object GradeOverride {

    /**
     * Re-translate the already-recognized [cells] under [newGrade] without any
     * rescan, returning the updated [TranslationOutput] to display.
     *
     * @param engine the translation engine to re-run; only
     *   [TranslationEngine.translate] is invoked.
     * @param cells the patterns recognized by the original scan — reused as-is.
     * @param newGrade the grade the Operator selected via the one-tap override.
     *
     * _Requirements: 8.4_
     */
    fun retranslate(
        engine: TranslationEngine,
        cells: List<RecognizedCell>,
        newGrade: Grade,
    ): TranslationOutput = engine.translate(cells, newGrade)
}

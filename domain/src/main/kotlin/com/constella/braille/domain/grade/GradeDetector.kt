package com.constella.braille.domain.grade

import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.RecognizedCell

/**
 * Estimates the English Braille grade of a scan and resolves the grade that
 * should actually be handed to the Translation_Engine.
 *
 * When Grade_Mode is Auto the System has no Operator-chosen grade, so it must
 * *estimate* whether the recognized patterns are Grade 1 (uncontracted) or
 * Grade 2 (contracted) Braille and select that grade (Req 8.1). The estimate is
 * explicitly a best guess — it is always backed by a one-tap manual override
 * (Req 8.3), so [detectGrade] never needs to be perfect, only useful as a
 * default.
 *
 * This contract is pure, deterministic Kotlin: the same cells always resolve to
 * the same [Grade]. It depends only on the `:domain` models and never touches
 * the camera, detector, or segmenter — grade resolution operates on
 * already-recognized cells (see [resolveGrade]) and an override re-runs only
 * translation, never a rescan (Req 8.4, handled by [GradeOverride]).
 *
 * _Requirements: 8.1_
 */
interface GradeDetector {

    /**
     * Estimate the Braille [Grade] of the already-recognized [cells].
     *
     * Always resolves to exactly [Grade.GRADE_1] or [Grade.GRADE_2] (the only
     * two resolved grades), so callers in Auto mode always get a concrete grade
     * to translate with.
     *
     * _Requirements: 8.1_
     */
    fun detectGrade(cells: List<RecognizedCell>): Grade

    /**
     * Resolve the [Grade] to translate with for the given [mode] and [cells].
     *
     * For the manual modes ([GradeMode.GRADE_1] / [GradeMode.GRADE_2]) the
     * Operator's chosen grade is returned verbatim — the override always wins
     * over the heuristic (Req 8.3). For [GradeMode.AUTO] the grade is the
     * [detectGrade] estimate (Req 8.1). The [cells] are read only for the Auto
     * case; the manual cases ignore them.
     *
     * This is a total function over [GradeMode]: every mode maps to exactly one
     * resolved [Grade].
     *
     * _Requirements: 8.1_
     */
    fun resolveGrade(mode: GradeMode, cells: List<RecognizedCell>): Grade = when (mode) {
        GradeMode.GRADE_1 -> Grade.GRADE_1
        GradeMode.GRADE_2 -> Grade.GRADE_2
        GradeMode.AUTO -> detectGrade(cells)
    }
}

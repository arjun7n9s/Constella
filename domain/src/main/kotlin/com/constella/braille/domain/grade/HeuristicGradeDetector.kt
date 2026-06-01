package com.constella.braille.domain.grade

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell

/**
 * The default, pure [GradeDetector] heuristic.
 *
 * The detector scores the presence of cells whose six-dot patterns are
 * **only meaningful in contracted (Grade 2) Braille** — contraction indicator
 * / prefix cells, whole-word signs, and common groupsigns — against a plain
 * Grade-1-only interpretation. If those contraction signals make up at least
 * [signalRatioThreshold] of the non-blank cells it estimates Grade 2; otherwise
 * Grade 1 (Req 8.1). The estimate is explicitly a best guess backed by the
 * one-tap manual override (Req 8.3), so it favours a simple, transparent rule
 * over perfect accuracy.
 *
 * ### Why these patterns
 *
 * Every signal pattern in [GRADE_2_SIGNAL_PATTERNS] is a pattern that is **not**
 * a standalone Grade 1 letter `a`–`z`. A document that is genuinely Grade 1
 * (uncontracted) therefore never contains them, so they are unambiguous
 * evidence of contraction. Patterns that double as both a Grade 1 letter and a
 * Grade 2 whole-word contraction (for example `b` / "but") are deliberately
 * **excluded** — they look identical to plain letters and carry no signal.
 *
 * Blank cells (spaces, [BrailleDots.EMPTY]) are ignored for both the numerator
 * and the denominator: they appear in both grades and say nothing about
 * contraction. An input with no non-blank cells (empty or all-blank) has no
 * evidence either way and defaults to [Grade.GRADE_1] — the safe, uncontracted
 * default.
 *
 * The heuristic is pure and deterministic: the same cells always yield the same
 * [Grade], and it touches only the cell patterns — never the camera, detector,
 * or segmenter.
 *
 * @property signalRatioThreshold the minimum fraction of non-blank cells that
 *   must be Grade-2 signal patterns to estimate Grade 2. Must be in `(0, 1]`.
 *
 * _Requirements: 8.1_
 */
class HeuristicGradeDetector(
    private val signalRatioThreshold: Float = DEFAULT_SIGNAL_RATIO_THRESHOLD,
) : GradeDetector {

    init {
        require(signalRatioThreshold > 0f && signalRatioThreshold <= 1f) {
            "signalRatioThreshold must be in (0, 1] but was $signalRatioThreshold"
        }
    }

    override fun detectGrade(cells: List<RecognizedCell>): Grade {
        var nonBlankCount = 0
        var signalCount = 0
        for (cell in cells) {
            if (cell.dots.raised.isEmpty()) continue // blank cell: no evidence
            nonBlankCount++
            if (cell.dots in GRADE_2_SIGNAL_PATTERNS) signalCount++
        }

        // No non-blank cells -> no evidence of contraction -> uncontracted default.
        if (nonBlankCount == 0) return Grade.GRADE_1

        val signalRatio = signalCount.toFloat() / nonBlankCount
        return if (signalRatio >= signalRatioThreshold) Grade.GRADE_2 else Grade.GRADE_1
    }

    companion object {
        /**
         * Default contraction-signal ratio for selecting Grade 2. With the
         * default, a scan whose non-blank cells are at least ~15% unambiguous
         * contraction signals is estimated as Grade 2. A purely uncontracted
         * (alphabet-only) scan scores `0` and is always Grade 1.
         */
        const val DEFAULT_SIGNAL_RATIO_THRESHOLD: Float = 0.15f

        private fun dots(vararg positions: Int): BrailleDots =
            BrailleDots(positions.toSortedSet())

        /**
         * Patterns that are unambiguous evidence of contracted (Grade 2)
         * Braille: contraction indicator / prefix cells, whole-word signs, and
         * common groupsigns. None of these patterns is a standalone Grade 1
         * letter `a`–`z`, so their presence is genuine signal.
         */
        val GRADE_2_SIGNAL_PATTERNS: Set<BrailleDots> = setOf(
            // --- Contraction indicator / prefix cells ---
            dots(5),              // dot 5 prefix
            dots(6),              // dot 6 prefix
            dots(4, 5),           // dots 4-5 prefix
            dots(4, 6),           // dots 4-6 prefix
            dots(5, 6),           // dots 5-6 prefix
            dots(4, 5, 6),        // dots 4-5-6 prefix

            // --- Whole-word signs (not Grade 1 letters) ---
            dots(2, 3, 4, 6),     // "the"
            dots(2, 3, 4, 5, 6),  // "with"
            dots(1, 2, 3, 4, 6),  // "and"
            dots(1, 2, 3, 5, 6),  // "of"
            dots(1, 2, 3, 4, 5, 6), // "for"

            // --- Common groupsigns (not Grade 1 letters) ---
            dots(1, 6),           // "ch"
            dots(1, 2, 6),        // "gh"
            dots(1, 4, 6),        // "sh"
            dots(1, 4, 5, 6),     // "th"
            dots(1, 5, 6),        // "wh"
            dots(1, 2, 4, 6),     // "ed"
            dots(1, 2, 4, 5, 6),  // "er"
            dots(1, 2, 5, 6),     // "ou"
            dots(2, 4, 6),        // "ow"
            dots(3, 4),           // "st"
            dots(3, 4, 5),        // "ar"
            dots(3, 4, 6),        // "ing"
        )
    }
}

package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots

/**
 * Pure display-formatting utility for untranslatable Braille cells (Req 7.5).
 *
 * When the Translation_Engine cannot translate one or more recognized cells,
 * the System must "display the untranslatable cell patterns and inform the
 * Operator that those cells could not be translated" (Req 7.5). This object
 * provides the two building blocks for that display:
 *
 *  1. [formatPattern] — renders a raw [BrailleDots] pattern as a human-readable
 *     dot-number string (e.g. `{1, 3, 5}` → `"dots 1-3-5"`), independent of
 *     any translation table, so the Operator can see exactly what was on the
 *     page.
 *  2. [formatMessage] — produces the full "could not translate" display string
 *     for a single cell, combining its one-based reading position, its raw
 *     pattern, and the explanatory message.
 *
 * This is intentionally a thin, focused formatter — it does not build the full
 * [UntranslatableReport] (that is [UntranslatableReporter]'s job). Its output
 * is consumed by the UI/TTS layer through the dual-delivery Notifier so the
 * Operator receives both a visual and a spoken description of each
 * untranslatable cell.
 *
 * Everything here is pure and deterministic: no native dependency, no Android
 * dependency, fully JVM-testable.
 *
 * _Requirements: 7.5_
 */
object UntranslatableFormatter {

    /**
     * The prefix used when rendering a pattern for display, e.g. `"dots 1-3-5"`.
     * The blank-cell case uses [BLANK_LABEL] instead.
     */
    const val DOTS_PREFIX: String = "dots"

    /**
     * Display label for a blank cell (no raised dots). A blank cell is normally
     * a space and therefore translatable; this label only appears in the unusual
     * case where one is reported untranslatable.
     */
    const val BLANK_LABEL: String = "blank cell"

    /**
     * The fixed suffix appended to every "could not translate" message.
     * Kept as a constant so tests can assert on it without hard-coding the
     * full sentence.
     */
    const val COULD_NOT_TRANSLATE: String = "could not be translated"

    /**
     * Render a six-dot [BrailleDots] pattern as a human-readable display string
     * for the Operator, e.g.:
     *
     * - `{1, 3, 5}` → `"dots 1-3-5"`
     * - `{2}`       → `"dots 2"`
     * - `{}`        → `"blank cell"`
     *
     * Dot positions are always emitted in ascending order. The result is
     * suitable for both on-screen display and TTS announcement.
     *
     * _Requirements: 7.5_
     */
    fun formatPattern(dots: BrailleDots): String =
        if (dots.raised.isEmpty()) {
            BLANK_LABEL
        } else {
            "$DOTS_PREFIX ${dots.raised.sorted().joinToString(separator = "-")}"
        }

    /**
     * Produce the full "could not translate" display message for a single
     * untranslatable cell.
     *
     * The message names the cell by its **one-based** reading position (how an
     * Operator counts cells on the page), includes the raw pattern from
     * [formatPattern], and states that the cell could not be translated.
     *
     * Example output: `"Cell 2 (dots 3-6) could not be translated."`
     *
     * @param cellIndex zero-based index of the cell in the recognized-cell
     *   sequence (matches an entry in [TranslationOutput.untranslatableCells]).
     * @param dots the cell's raw six-dot pattern.
     *
     * _Requirements: 7.5_
     */
    fun formatMessage(cellIndex: Int, dots: BrailleDots): String {
        val pattern = formatPattern(dots)
        return "Cell ${cellIndex + 1} ($pattern) $COULD_NOT_TRANSLATE."
    }

    /**
     * Produce a summary sentence for a collection of untranslatable cells,
     * listing their one-based positions and raw patterns.
     *
     * Example: `"2 cells could not be translated: Cell 1 (dots 1-2), Cell 3 (dots 3-6)."`
     *
     * Returns an empty string when [entries] is empty (no untranslatable cells).
     *
     * @param entries pairs of (zero-based cell index, [BrailleDots] pattern),
     *   in reading order.
     *
     * _Requirements: 7.5_
     */
    fun formatSummary(entries: List<Pair<Int, BrailleDots>>): String {
        if (entries.isEmpty()) return ""
        val count = entries.size
        val noun = if (count == 1) "cell" else "cells"
        val details = entries.joinToString(separator = ", ") { (index, dots) ->
            "Cell ${index + 1} (${formatPattern(dots)})"
        }
        return "$count $noun $COULD_NOT_TRANSLATE: $details."
    }
}

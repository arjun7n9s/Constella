package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.RecognizedCell

/**
 * Surfacing layer for untranslatable cells (Req 7.5).
 *
 * The Translation_Engine already *aggregates* which source cells it could not
 * translate into [TranslationOutput.untranslatableCells] (see
 * [CharSpanAssembler], which folds segments marked
 * [TranslationSegment.translatable] `= false` into that sorted, de-duplicated
 * index list). This layer turns those bare indices into something the Operator
 * can act on: for each untranslatable cell it pairs the cell's **raw six-dot
 * pattern** with an explanatory "could not be translated" message, and produces
 * a single summary sentence for the whole scan.
 *
 * Requirement 7.5: *"IF the Translation_Engine cannot translate one or more
 * recognized cells, THEN THE System SHALL display the untranslatable cell
 * patterns and SHALL inform the Operator that those cells could not be
 * translated."* — this report carries exactly that information so the UI/TTS
 * layer can render it through the dual-delivery Notifier.
 *
 * Everything here is pure and deterministic: it reads only
 * [TranslationOutput.untranslatableCells] and the recognized cells' patterns,
 * never the native engine, so it is fully JVM-testable.
 */
object UntranslatableReporter {

    /**
     * Label used for a blank cell (no raised dots). A blank cell is normally a
     * space and therefore translatable; this label only appears in the unusual
     * case where one is reported untranslatable, so it stays human-readable
     * rather than rendering an empty string.
     */
    const val BLANK_PATTERN: String = "blank"

    /**
     * Placeholder pattern for an untranslatable index that has no corresponding
     * recognized cell. The Translation_Engine never produces such an index
     * (every entry in [TranslationOutput.untranslatableCells] refers to a real
     * source cell), so this is purely defensive — it keeps the reported index
     * set equal to [TranslationOutput.untranslatableCells] even if a malformed
     * output is passed in, instead of dropping the entry or throwing.
     */
    const val UNKNOWN_PATTERN: String = "unknown"

    /**
     * Render a six-dot [BrailleDots] pattern as its raw dot-number string, e.g.
     * `{1, 3, 5}` -> `"1-3-5"`. Positions are always emitted in ascending
     * order. The empty pattern renders as [BLANK_PATTERN].
     *
     * This is the pure raw-pattern helper required by Req 7.5 ("display the
     * untranslatable cell patterns"): it shows the dots themselves, independent
     * of any translation table, so the Operator can read what was actually on
     * the page.
     */
    fun renderPattern(dots: BrailleDots): String =
        if (dots.raised.isEmpty()) {
            BLANK_PATTERN
        } else {
            dots.raised.sorted().joinToString(separator = "-")
        }

    /**
     * Build the per-cell "could not be translated" message for the cell at
     * (zero-based) [cellIndex] whose raw pattern is [rawPattern]. The message
     * refers to the cell by its **one-based** reading position, which is how an
     * Operator counts cells on the page.
     */
    fun cellMessage(cellIndex: Int, rawPattern: String): String =
        "Cell ${cellIndex + 1} (dots $rawPattern) could not be translated."

    /**
     * Produce the [UntranslatableReport] for [output] given the original
     * recognized [cells].
     *
     * The reported entries correspond **exactly** to
     * [TranslationOutput.untranslatableCells] (same indices, same order — that
     * list is already sorted and de-duplicated by [CharSpanAssembler]); each
     * entry is enriched with the raw dot pattern of its source cell and a
     * per-cell explanatory message. When there are no untranslatable cells the
     * report is [UntranslatableReport.NONE] (empty list, blank summary).
     *
     * @param output the translation result whose
     *   [TranslationOutput.untranslatableCells] drives the report.
     * @param cells the recognized cells that were translated, used to look up
     *   each untranslatable cell's raw [BrailleDots]. An index without a
     *   matching cell (which the engine never emits) is reported with
     *   [UNKNOWN_PATTERN] rather than being dropped.
     */
    fun report(output: TranslationOutput, cells: List<RecognizedCell>): UntranslatableReport {
        if (output.untranslatableCells.isEmpty()) return UntranslatableReport.NONE

        val entries = output.untranslatableCells.map { cellIndex ->
            val dots = cells.getOrNull(cellIndex)?.dots
            val rawPattern = if (dots != null) renderPattern(dots) else UNKNOWN_PATTERN
            UntranslatableCell(
                cellIndex = cellIndex,
                dots = dots,
                rawPattern = rawPattern,
                message = cellMessage(cellIndex, rawPattern),
            )
        }
        return UntranslatableReport(cells = entries, summary = summarize(entries))
    }

    private fun summarize(entries: List<UntranslatableCell>): String {
        val count = entries.size
        val noun = if (count == 1) "cell" else "cells"
        val patterns = entries.joinToString(separator = ", ") { it.rawPattern }
        return "$count $noun could not be translated (dots $patterns)."
    }
}

/**
 * One untranslatable cell, surfaced for display/announcement (Req 7.5).
 *
 * @property cellIndex zero-based index of the cell in the recognized-cell
 *   sequence; matches an entry in [TranslationOutput.untranslatableCells].
 * @property dots the cell's raw six-dot pattern, or `null` in the defensive
 *   case where no recognized cell exists for [cellIndex].
 * @property rawPattern the human-readable dot-number rendering of [dots] (e.g.
 *   `"1-3-5"`, or [UntranslatableReporter.BLANK_PATTERN] /
 *   [UntranslatableReporter.UNKNOWN_PATTERN]).
 * @property message the per-cell "could not be translated" message.
 */
data class UntranslatableCell(
    val cellIndex: Int,
    val dots: BrailleDots?,
    val rawPattern: String,
    val message: String,
)

/**
 * The full untranslatable-cell report for a translation (Req 7.5).
 *
 * @property cells one [UntranslatableCell] per untranslatable source cell, in
 *   the same order as [TranslationOutput.untranslatableCells].
 * @property summary a single sentence naming how many cells could not be
 *   translated and listing their raw patterns; the empty string when there are
 *   none.
 */
data class UntranslatableReport(
    val cells: List<UntranslatableCell>,
    val summary: String,
) {
    /** `true` when at least one cell could not be translated. */
    val hasUntranslatableCells: Boolean
        get() = cells.isNotEmpty()

    /** The zero-based indices of every reported untranslatable cell, in order. */
    val cellIndices: List<Int>
        get() = cells.map { it.cellIndex }

    companion object {
        /** The report for a translation with no untranslatable cells. */
        val NONE: UntranslatableReport = UntranslatableReport(emptyList(), "")
    }
}

/**
 * Detects which source cells liblouis **echoed instead of translating** — the
 * gap left by [BackTranslationMapper], which marks every segment translatable.
 *
 * ## Why this seam exists
 *
 * [BackTranslationMapper.toSegments] groups liblouis's output into segments but
 * always sets [TranslationSegment.translatable] `= true`, so the runtime
 * liblouis path never populates [TranslationOutput.untranslatableCells] for a
 * cell that liblouis *could not* translate (the in-memory Grade 1 fake does,
 * which is why the surfacing layer above and its tests work today). When
 * liblouis cannot back-translate a cell it leaves the cell's Unicode-Braille
 * character (`U+2800`–`U+283F`) in the output verbatim instead of producing
 * text. This detector flags exactly those pass-through cells, table-independent
 * and pure.
 *
 * It is intentionally additive: it does **not** change
 * [BackTranslationMapper]'s existing signature or behaviour. The `:runtime`
 * `LiblouisTranslationEngine` (owned by the liblouis-integration task) can call
 * [untranslatableCellIndices] on the native result and feed the indices into
 * its segment construction to close the gap, after which the surfacing layer
 * above reports them unchanged.
 */
object UntranslatableDetection {

    /** Inclusive upper bound of the Unicode Braille Patterns block (`U+283F`). */
    private const val BRAILLE_PATTERN_MAX: Int = BrailleUnicode.BRAILLE_PATTERN_BLANK or 0x3F

    /**
     * `true` if [ch] is a Unicode Braille Patterns character (`U+2800`–`U+283F`)
     * — i.e. a cell liblouis echoed rather than translating it to text.
     */
    fun isPassthroughBrailleChar(ch: Char): Boolean =
        ch.code in BrailleUnicode.BRAILLE_PATTERN_BLANK..BRAILLE_PATTERN_MAX

    /**
     * The (sorted, de-duplicated) indices of source cells liblouis could not
     * translate, derived from a back-translation's [outputText] and its
     * per-output-character source map [inputPositions]
     * (`inputPositions[i]` is the source cell that produced `outputText[i]` —
     * the same contract [BackTranslationMapper] consumes).
     *
     * A cell is untranslatable iff it contributed at least one output character
     * and **every** output character it produced is a pass-through Braille
     * character ([isPassthroughBrailleChar]); a cell that yielded any real text
     * is considered translated.
     *
     * @throws IllegalArgumentException if [inputPositions] length does not match
     *   [outputText] length (a violated native contract), matching
     *   [BackTranslationMapper.toSegments].
     */
    fun untranslatableCellIndices(outputText: String, inputPositions: IntArray): List<Int> {
        require(inputPositions.size == outputText.length) {
            "inputPositions length (${inputPositions.size}) must match output text " +
                "length (${outputText.length})"
        }
        if (outputText.isEmpty()) return emptyList()

        // Per source cell: did it produce any char, and were all chars pass-through?
        val produced = HashSet<Int>()
        val allPassthrough = HashMap<Int, Boolean>()
        for (i in outputText.indices) {
            val cell = inputPositions[i]
            produced.add(cell)
            val passthrough = isPassthroughBrailleChar(outputText[i])
            allPassthrough[cell] = (allPassthrough[cell] ?: true) && passthrough
        }
        return produced
            .filter { allPassthrough[it] == true }
            .sorted()
    }
}

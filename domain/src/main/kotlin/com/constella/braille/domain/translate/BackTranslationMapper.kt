package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Confidence

/**
 * Turns a back-translation's per-output-character source mapping into ordered
 * [TranslationSegment]s, grouping contiguous output characters that came from
 * the same source cell.
 *
 * liblouis reports, for each output character, the index of the input Braille
 * cell that produced it (its `inputPos` array). Because Grade 2 contractions
 * make the cell→character mapping non-1:1, consecutive output characters often
 * share a source cell (one cell expanded to several characters) — this mapper
 * collapses each such run into a single segment whose
 * [TranslationSegment.cellRefs] is that one cell and whose
 * [TranslationSegment.confidence] is that cell's confidence. Downstream,
 * [CharSpanAssembler] folds these segments into the final text + char spans.
 *
 * This logic is pure and deterministic — it depends only on the integer
 * position array and the per-cell confidences, never on the native engine — so
 * it is fully unit-/property-testable on the JVM.
 *
 * _Requirements: 7.2, 10.3_
 */
object BackTranslationMapper {

    /**
     * Group [outputText] into segments using [inputPositions], the source cell
     * index for each output character (`inputPositions[i]` is the cell that
     * produced `outputText[i]`).
     *
     * @param cellConfidences confidence per source cell, indexed by cell. A
     *   source position outside its bounds yields [Confidence.ZERO] for that
     *   segment (defensive; a well-formed native result never does this).
     * @throws IllegalArgumentException if [inputPositions] length does not match
     *   [outputText] length, which would indicate a violated native contract.
     */
    fun toSegments(
        outputText: String,
        inputPositions: IntArray,
        cellConfidences: List<Confidence>,
    ): List<TranslationSegment> {
        require(inputPositions.size == outputText.length) {
            "inputPositions length (${inputPositions.size}) must match output text " +
                "length (${outputText.length})"
        }
        if (outputText.isEmpty()) return emptyList()

        val segments = ArrayList<TranslationSegment>()
        var runStart = 0
        var runCell = inputPositions[0]

        fun emit(cell: Int, start: Int, end: Int) {
            segments.add(
                TranslationSegment(
                    cellRefs = listOf(cell),
                    text = outputText.substring(start, end),
                    confidence = confidenceFor(cell, cellConfidences),
                    translatable = true,
                ),
            )
        }

        for (i in 1 until outputText.length) {
            val cell = inputPositions[i]
            if (cell != runCell) {
                emit(runCell, runStart, i)
                runStart = i
                runCell = cell
            }
        }
        emit(runCell, runStart, outputText.length)
        return segments
    }

    private fun confidenceFor(cell: Int, cellConfidences: List<Confidence>): Confidence =
        cellConfidences.getOrElse(cell) { Confidence.ZERO }
}

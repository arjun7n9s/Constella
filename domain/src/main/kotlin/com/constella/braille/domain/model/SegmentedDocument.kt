package com.constella.braille.domain.model

/**
 * One line of Braille cells in reading order, left to right.
 *
 * [wordBoundaryAfter] holds the indices `i` into [cells] such that a word
 * boundary (space) falls between `cells[i]` and `cells[i + 1]`. The
 * Cell_Segmenter inserts a boundary wherever the horizontal gap between two
 * adjacent cells exceeds the word-spacing threshold. Indices must be valid
 * positions that have a following cell, i.e. in `0 until cells.size - 1`.
 *
 * _Requirements: 5.2, 5.3, 5.4_
 */
data class TextLine(
    val cells: List<BrailleCell>,
    val wordBoundaryAfter: Set<Int> = emptySet(),
) {
    init {
        require(wordBoundaryAfter.all { it in 0 until maxOf(cells.size - 1, 0) }) {
            "wordBoundaryAfter indices must reference a cell with a following cell " +
                "(0 until ${cells.size - 1}) but was $wordBoundaryAfter"
        }
    }
}

/**
 * The ordered structure produced by the Cell_Segmenter: lines of cells in
 * reading order, top to bottom.
 *
 * An empty [lines] list represents a frame in which no cells were formed (for
 * example, when the Dot_Detector produced no dots).
 *
 * _Requirements: 5.2, 5.3, 5.6_
 */
data class SegmentedDocument(val lines: List<TextLine>) {
    companion object {
        /** A document with no lines — the result of empty detector input. */
        val EMPTY: SegmentedDocument = SegmentedDocument(emptyList())
    }
}

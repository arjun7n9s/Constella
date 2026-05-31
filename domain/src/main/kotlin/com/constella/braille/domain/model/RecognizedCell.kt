package com.constella.braille.domain.model

/**
 * A Braille cell after pattern recognition.
 *
 * The Pattern_Recognizer maps the cell's assigned dots to a six-dot [dots]
 * pattern, aggregates a per-cell [confidence], and sets [uncertain] when that
 * confidence falls below the cell-confidence threshold. [source] is the
 * segmented cell this recognition came from, retained so downstream stages can
 * trace a recognized pattern back to its geometry.
 *
 * _Requirements: 6.1, 6.2, 6.3_
 */
data class RecognizedCell(
    val source: BrailleCell,
    val dots: BrailleDots,
    val confidence: Confidence,
    val uncertain: Boolean,
)

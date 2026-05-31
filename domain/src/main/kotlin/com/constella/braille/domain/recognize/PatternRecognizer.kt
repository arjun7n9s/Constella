package com.constella.braille.domain.recognize

import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.model.SegmentedDocument

/**
 * The Pattern_Recognizer pipeline stage (Req 6).
 *
 * Maps each segmented [com.constella.braille.domain.model.BrailleCell] to its
 * six-dot Braille pattern, assigns a per-cell `Confidence_Score`, and flags
 * low-confidence cells as uncertain.
 *
 * The returned list is flattened across the document in reading order: lines
 * top-to-bottom, cells left-to-right within each line, matching the ordering
 * the Cell_Segmenter established in the [SegmentedDocument].
 *
 * _Requirements: 6.1, 6.2, 6.3_
 */
interface PatternRecognizer {

    /**
     * Recognize every cell of [doc], in reading order.
     *
     * An empty document (no lines, or lines with no cells) yields an empty
     * list. Recognition is pure and deterministic: the same document always
     * produces the same result.
     */
    fun recognize(doc: SegmentedDocument): List<RecognizedCell>
}

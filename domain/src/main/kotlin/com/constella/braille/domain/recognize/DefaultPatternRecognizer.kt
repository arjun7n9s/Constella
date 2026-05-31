package com.constella.braille.domain.recognize

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.model.SegmentedDocument

/**
 * Default, deterministic [PatternRecognizer].
 *
 * For every segmented [BrailleCell] it:
 *
 * 1. **Maps the assigned dots to the six-position pattern (Req 6.1).** Standard
 *    Braille numbering is a 2-column by 3-row grid:
 *    ```
 *      1  4
 *      2  5
 *      3  6
 *    ```
 *    Each dot's position within the cell's [BrailleCell.boundingBox] decides its
 *    column (left half -> 1/2/3, right half -> 4/5/6) and its row (top / middle /
 *    bottom third -> top / middle / bottom row). The resulting position numbers
 *    form the [BrailleDots.raised] set, which is therefore always a subset of
 *    `{1..6}`. A cell with no assigned dots yields the empty (blank) pattern.
 *
 * 2. **Aggregates a per-cell confidence (Req 6.2).** The constituent dot
 *    confidences and the cell's grid-fit quality are treated as two independent
 *    reliability factors and combined multiplicatively, so a weak signal in
 *    *either* the detected dots or the grid fit lowers the cell's confidence.
 *    The grid-fit quality is the segmenter's per-cell [BrailleCell.confidence],
 *    which already carries a sub-threshold value for degraded (non-`validGrid`)
 *    regions (Req 5.5); that low quality therefore propagates into the
 *    recognized cell. A blank cell has no constituent dots, so its confidence is
 *    the grid-fit quality alone.
 *
 * 3. **Flags low-confidence cells (Req 6.3).** [RecognizedCell.uncertain] is set
 *    exactly when the aggregated confidence is below
 *    [ConfidenceThresholds.CELL_CONFIDENCE] — the single source of truth for the
 *    cell-confidence threshold.
 *
 * Recognition is pure: the same [SegmentedDocument] always produces the same
 * list of [RecognizedCell]s, in reading order.
 *
 * _Requirements: 6.1, 6.2, 6.3_
 */
class DefaultPatternRecognizer : PatternRecognizer {

    override fun recognize(doc: SegmentedDocument): List<RecognizedCell> =
        doc.lines.flatMap { line -> line.cells.map(::recognizeCell) }

    /** Recognize a single segmented cell into its pattern + confidence. */
    private fun recognizeCell(cell: BrailleCell): RecognizedCell {
        val dots = BrailleDots(
            cell.dots.mapTo(mutableSetOf()) { positionOf(it, cell.boundingBox) },
        )
        val confidence = aggregateConfidence(cell)
        return RecognizedCell(
            source = cell,
            dots = dots,
            confidence = confidence,
            uncertain = confidence.value < ConfidenceThresholds.CELL_CONFIDENCE,
        )
    }

    /**
     * Combine the constituent dot confidences with the cell's grid-fit quality.
     *
     * Both factors live in `[0, 1]`, so their product does too — no clamping is
     * strictly required, but [Confidence.of] guards against float rounding.
     */
    private fun aggregateConfidence(cell: BrailleCell): Confidence {
        val gridFitQuality = cell.confidence.value
        if (cell.dots.isEmpty()) {
            // Blank cell: no constituent dots, so reliability is the grid fit.
            return Confidence.of(gridFitQuality)
        }
        val meanDotConfidence =
            cell.dots.map { it.confidence.value }.average().toFloat()
        return Confidence.of(meanDotConfidence * gridFitQuality)
    }

    /**
     * Determine the six-dot position (`1..6`) of [dot] from its location within
     * [box]. Robust to a degenerate (zero-width or zero-height) box, which
     * collapses the corresponding axis to its first column/row.
     */
    private fun positionOf(dot: DetectedDot, box: BoundingBox): Int {
        val columnFraction = fractionWithin(dot.x, box.left, box.width)
        val rowFraction = fractionWithin(dot.y, box.top, box.height)

        // Column: left half -> 0 (positions 1,2,3), right half -> 1 (positions 4,5,6).
        val column = if (columnFraction < COLUMN_SPLIT) 0 else 1
        // Row: top / middle / bottom third -> 0 / 1 / 2.
        val row = when {
            rowFraction < ONE_THIRD -> 0
            rowFraction < TWO_THIRDS -> 1
            else -> 2
        }
        return column * 3 + row + 1
    }

    /**
     * The position of [value] within `[origin, origin + extent]` expressed as a
     * fraction clamped to `[0, 1]`. A non-positive [extent] (degenerate box)
     * yields `0` so the axis collapses deterministically to its first band.
     */
    private fun fractionWithin(value: Float, origin: Float, extent: Float): Float {
        if (extent <= 0f) return 0f
        return ((value - origin) / extent).coerceIn(0f, 1f)
    }

    private companion object {
        /** Column boundary: fractions below this are the left column. */
        const val COLUMN_SPLIT = 0.5f

        /** Row band boundaries (thirds of the cell height). */
        const val ONE_THIRD = 1f / 3f
        const val TWO_THIRDS = 2f / 3f
    }
}

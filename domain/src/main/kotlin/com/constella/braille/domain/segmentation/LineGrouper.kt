package com.constella.braille.domain.segmentation

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.SegmentedDocument
import com.constella.braille.domain.model.TextLine

/**
 * Line grouping, reading order, and word boundaries — the second stage of Cell
 * Segmentation (Req 5.2, 5.3, 5.4).
 *
 * Task 7.1 ([GridClusterer]) turns a cloud of accepted [com.constella.braille.domain.model.DetectedDot]s
 * into a flat list of [BrailleCell]s (2-column by 3-row grids), unordered with
 * respect to reading order. This stage consumes that flat list and assembles a
 * [SegmentedDocument]: cells grouped into lines, ordered into reading order, and
 * annotated with word boundaries. The single entry point is [group].
 *
 * The three behaviours, each tied to its requirement:
 *
 *  1. **Line grouping (Req 5.2).** Cells whose vertical centers lie within
 *     [ScanConstants.Segmentation.LINE_GROUPING_CELL_HEIGHT_FACTOR] times the
 *     *median cell height* of each other are grouped into a common line. The
 *     grouping is single-linkage over the cells sorted by vertical center: a new
 *     line starts only when the vertical gap to the previous cell exceeds that
 *     tolerance. Because Braille line spacing is far larger than the within-line
 *     vertical jitter, this cleanly separates lines while tolerating gradual
 *     baseline drift across a line.
 *  2. **Reading order (Req 5.3).** Cells within a line are ordered left to right
 *     by horizontal center; lines are ordered top to bottom by vertical center.
 *     Because lines are separated by a vertical gap exceeding the line tolerance,
 *     every cell in an earlier line sits above every cell in a later line, so the
 *     top-to-bottom line order is unambiguous.
 *  3. **Word boundaries (Req 5.4).** The *median intra-line cell-to-cell
 *     spacing* is the median center-to-center horizontal distance between
 *     adjacent cells, taken across every line. A word boundary is inserted after
 *     a cell whenever the gap to the next cell exceeds
 *     [ScanConstants.Segmentation.WORD_BOUNDARY_SPACING_FACTOR] times that
 *     median. The gap and the spacing are measured the same way (center to
 *     center) so the comparison is consistent; the median is robust to the
 *     minority of larger word gaps it includes.
 *
 * All tolerance factors are read from [ScanConstants.Segmentation] — never
 * hard-coded — and are applied to per-document medians (cell height, intra-line
 * spacing) rather than absolute pixel values, so the result scales with the
 * document's apparent size in the frame.
 *
 * This object is pure and stateless, mirroring [GridClusterer] and the rest of
 * the deterministic domain core, so it is directly property-testable. Degraded
 * regions (`validGrid = false`) are carried through unchanged — they still have
 * a position and participate in ordering — leaving the sub-threshold-confidence
 * handling to its own stage (task 7.6). An empty cell list yields
 * [SegmentedDocument.EMPTY].
 *
 * _Requirements: 5.2, 5.3, 5.4_
 */
object LineGrouper {

    /**
     * Group [cells] into a [SegmentedDocument] in reading order with word
     * boundaries inserted.
     *
     * The input is the flat, reading-order-agnostic cell list produced by
     * [GridClusterer]; the order of [cells] does not affect the result. An empty
     * input yields [SegmentedDocument.EMPTY] (Req 5.6 is owned by task 7.6, but
     * an empty list trivially produces no lines here).
     */
    fun group(cells: List<BrailleCell>): SegmentedDocument {
        if (cells.isEmpty()) return SegmentedDocument.EMPTY

        val lineTolerance =
            ScanConstants.Segmentation.LINE_GROUPING_CELL_HEIGHT_FACTOR * medianCellHeight(cells)

        val lines = groupIntoLines(cells, lineTolerance)
            .map { line -> line.sortedWith(compareBy({ it.boundingBox.centerX }, { it.centerY })) }

        val wordSpacingThreshold = wordSpacingThreshold(lines)

        val textLines = lines.map { line -> toTextLine(line, wordSpacingThreshold) }
        return SegmentedDocument(textLines)
    }

    /**
     * Single-linkage grouping of [cells] into lines by vertical center.
     *
     * Cells are visited in ascending vertical-center order (ties broken by
     * horizontal center, then a stable original-index tiebreak for determinism).
     * A cell joins the current line when its vertical center is within
     * [tolerance] of the previous (next-highest) cell's center; otherwise it
     * starts a new line. The lines are returned top to bottom.
     */
    private fun groupIntoLines(
        cells: List<BrailleCell>,
        tolerance: Float,
    ): List<List<BrailleCell>> {
        val byCenterY = cells.withIndex().sortedWith(
            compareBy({ it.value.centerY }, { it.value.boundingBox.centerX }, { it.index }),
        )

        val lines = ArrayList<MutableList<BrailleCell>>()
        var previousCenterY = Float.NaN
        for ((_, cell) in byCenterY) {
            if (lines.isEmpty() || cell.centerY - previousCenterY > tolerance) {
                lines += mutableListOf(cell)
            } else {
                lines.last() += cell
            }
            previousCenterY = cell.centerY
        }
        return lines
    }

    /**
     * The word-boundary distance threshold:
     * [ScanConstants.Segmentation.WORD_BOUNDARY_SPACING_FACTOR] times the median
     * center-to-center spacing of adjacent cells across all [lines].
     *
     * Returns [Float.POSITIVE_INFINITY] when no line has two or more cells (no
     * adjacent pair exists to measure), so no word boundary can be inserted.
     */
    private fun wordSpacingThreshold(lines: List<List<BrailleCell>>): Float {
        val spacings = ArrayList<Float>()
        for (line in lines) {
            for (i in 0 until line.size - 1) {
                spacings += line[i + 1].boundingBox.centerX - line[i].boundingBox.centerX
            }
        }
        if (spacings.isEmpty()) return Float.POSITIVE_INFINITY
        return ScanConstants.Segmentation.WORD_BOUNDARY_SPACING_FACTOR * median(spacings)
    }

    /**
     * Build a [TextLine] from a left-to-right ordered [line], inserting a word
     * boundary after cell `i` when the center-to-center gap to cell `i + 1`
     * exceeds [wordSpacingThreshold].
     */
    private fun toTextLine(line: List<BrailleCell>, wordSpacingThreshold: Float): TextLine {
        val boundaries = HashSet<Int>()
        for (i in 0 until line.size - 1) {
            val gap = line[i + 1].boundingBox.centerX - line[i].boundingBox.centerX
            if (gap > wordSpacingThreshold) boundaries += i
        }
        return TextLine(cells = line, wordBoundaryAfter = boundaries)
    }

    /** Median bounding-box height across [cells]; the input is always non-empty here. */
    private fun medianCellHeight(cells: List<BrailleCell>): Float =
        median(cells.map { it.boundingBox.height })

    /** Median of a list of values, or 0 when empty. */
    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}

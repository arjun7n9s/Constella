package com.constella.braille.domain.segmentation

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import com.constella.braille.domain.model.SegmentedDocument

/**
 * Degraded-region handling and empty-input policy for the Cell_Segmenter
 * (Req 5.5, 5.6).
 *
 * This stage wraps [GridClusterer] + [LineGrouper] and adds two behaviours:
 *
 *  1. **Degraded regions (Req 5.5).** Dots that the [GridClusterer] excludes as
 *     noise (they fit no valid 2×3 grid) are not simply discarded. Instead, they
 *     are collected into synthetic cells with [BrailleCell.validGrid] `= false`
 *     and [BrailleCell.confidence] set below
 *     [ConfidenceThresholds.CELL_CONFIDENCE], so downstream stages (the
 *     Pattern_Recognizer, the Results presentation) can flag them appropriately
 *     while the remaining valid regions continue to be processed normally. This
 *     ensures degraded regions do not halt segmentation.
 *
 *  2. **Empty input (Req 5.6).** When the Dot_Detector produces zero accepted
 *     dots, the result is [SegmentedDocument.EMPTY] — an empty ordered set of
 *     cells with no lines.
 *
 * The result is a fully ordered [SegmentedDocument] (valid cells in reading
 * order via [LineGrouper], degraded cells appended at the end in their original
 * order). The degraded cells are included so the downstream pattern recognizer
 * can still attempt recognition (and flag them uncertain), rather than silently
 * dropping evidence.
 *
 * This object is pure, stateless, and deterministic — the same dots always
 * produce the same document.
 *
 * _Requirements: 5.5, 5.6_
 */
object DegradedRegionHandler {

    /**
     * The confidence value assigned to a degraded-region cell. Guaranteed to be
     * strictly below [ConfidenceThresholds.CELL_CONFIDENCE] so the
     * Pattern_Recognizer's `uncertain` flag fires (Req 6.3). Clamped to `[0, 1]`.
     */
    private val DEGRADED_CONFIDENCE: Confidence = Confidence.of(
        (ConfidenceThresholds.CELL_CONFIDENCE * 0.5f).coerceIn(0f, 1f),
    )

    /**
     * Segment [dots] into a [SegmentedDocument], handling empty input and
     * degraded regions.
     *
     * Flow:
     * 1. If [dots] is empty → [SegmentedDocument.EMPTY] (Req 5.6).
     * 2. Cluster dots into valid cells and noise via [GridClusterer.cluster].
     * 3. Build synthetic degraded [BrailleCell]s from noise dots.
     * 4. Group valid cells into reading-ordered lines via [LineGrouper.group].
     * 5. Append degraded cells to the document so they participate in
     *    downstream recognition (flagged uncertain).
     *
     * @return a [SegmentedDocument] covering all input dots.
     */
    fun segment(dots: List<DetectedDot>): SegmentedDocument {
        if (dots.isEmpty()) return SegmentedDocument.EMPTY

        val clusterResult = GridClusterer.cluster(dots)

        // Build degraded cells from noise dots (Req 5.5).
        val degradedCells = buildDegradedCells(clusterResult.noise)

        // Combine valid cells with degraded cells and group into reading order.
        val allCells = clusterResult.cells + degradedCells
        if (allCells.isEmpty()) return SegmentedDocument.EMPTY

        return LineGrouper.group(allCells)
    }

    /**
     * Build synthetic, degraded [BrailleCell]s from [noiseDots] — dots that the
     * [GridClusterer] excluded because they fit no valid 2×3 grid.
     *
     * Each noise dot becomes its own single-dot degraded cell with:
     * - [BrailleCell.validGrid] `= false` (indicating degraded region)
     * - [BrailleCell.confidence] below the cell-confidence threshold
     *
     * Grouping multiple noise dots into multi-dot degraded cells would require
     * heuristics that re-duplicate the grid clusterer's logic with looser
     * constraints. Instead, one cell per noise dot is the simplest correct
     * approach: each dot gets flagged, none are silently lost, and the
     * Pattern_Recognizer's uncertain flag will fire on every one of them.
     */
    private fun buildDegradedCells(noiseDots: List<DetectedDot>): List<BrailleCell> =
        noiseDots.map { dot ->
            BrailleCell(
                dots = listOf(dot),
                boundingBox = BoundingBox(
                    left = dot.x - dot.radius,
                    top = dot.y - dot.radius,
                    right = dot.x + dot.radius,
                    bottom = dot.y + dot.radius,
                ),
                centerY = dot.y,
                validGrid = false,
                confidence = DEGRADED_CONFIDENCE,
            )
        }
}

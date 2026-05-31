package com.constella.braille.domain.segmentation

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import kotlin.math.hypot

/**
 * Outcome of clustering a cloud of accepted [DetectedDot]s into Braille cells.
 *
 * [cells] are the formed cells, each occupying a single 2-column by 3-row grid
 * with at most one dot per grid position (so at most six dots per cell). [noise]
 * holds the accepted dots that fell inside a cell's grid footprint but did not
 * align to any of its six candidate positions (or lost a position to a closer
 * dot); they fit no cell and are therefore excluded from recognition.
 *
 * Every accepted dot appears in exactly one of these two collections, so
 * `cells.flatMap { it.dots } + noise` is a partition of the accepted input:
 * each accepted dot is assigned to at most one cell (Req 5.1).
 */
data class CellClusterResult(
    val cells: List<BrailleCell>,
    val noise: List<DetectedDot>,
)

/**
 * Cell grid clustering and noise exclusion — the first stage of Cell
 * Segmentation (Req 5.1).
 *
 * Given the accepted dot candidates produced by the Dot_Detector (already
 * filtered to the minimum dot-detection confidence; this stage does not
 * re-filter), the clusterer:
 *
 *  1. **Estimates the dot pitch** from nearest-neighbor spacing — the
 *     characteristic intra-cell distance between adjacent dot positions.
 *  2. **Fits 2-column by 3-row grids.** A Braille cell's six positions sit on a
 *     regular lattice one pitch apart:
 *
 *     ```
 *       1 4        (col1,row1)=(0,0)   (col2,row1)=(p,0)
 *       2 5        (col1,row2)=(0,p)   (col2,row2)=(p,p)
 *       3 6        (col1,row3)=(0,2p)  (col2,row3)=(p,2p)
 *     ```
 *
 *  3. **Assigns each accepted dot to at most one cell.** Cells are grown
 *     greedily from the topmost-then-leftmost unassigned dot; each dot snaps to
 *     at most one of its cell's six candidate positions, and each position holds
 *     at most one dot.
 *  4. **Excludes dots that fit no cell as noise.** A dot inside a cell's grid
 *     footprint that does not align (within tolerance) to a free grid position
 *     is excluded as noise.
 *
 * This class deliberately implements *only* clustering/noise exclusion. Line
 * grouping, reading order, and word boundaries (Req 5.2–5.4) and degraded-region
 * / empty-input handling (Req 5.5, 5.6) are layered on top by the Cell_Segmenter
 * in separate units. The single entry point [clusterIntoCells] is the "cells
 * from dots" primitive those later stages build upon.
 *
 * The class is deterministic and side-effect free, making it a direct target of
 * the segmentation property-based tests.
 *
 * _Requirements: 5.1_
 */
class CellGridClusterer(
    /**
     * Snap tolerance as a fraction of the estimated dot pitch. A dot is accepted
     * onto a grid position only when it lies within `snapToleranceFactor * pitch`
     * of that position. Defaults to the centralized
     * [ScanConstants.Segmentation.DOT_SNAP_TOLERANCE_FACTOR] so tuning happens in
     * one place.
     */
    private val snapToleranceFactor: Float =
        ScanConstants.Segmentation.DOT_SNAP_TOLERANCE_FACTOR,
) {
    init {
        require(snapToleranceFactor > 0f && snapToleranceFactor < 1f) {
            "snapToleranceFactor must be in (0, 1) so adjacent grid-position snap " +
                "regions do not overlap but was $snapToleranceFactor"
        }
    }

    /**
     * Cluster [dots] into 2x3 Braille cells, excluding off-grid dots as noise.
     *
     * Returns a [CellClusterResult] partitioning the input into formed [cells]
     * and excluded [noise]. An empty input yields an empty result (no cells, no
     * noise). The returned cells are unordered with respect to reading order;
     * ordering is a later segmentation concern (Req 5.2–5.3).
     */
    fun clusterIntoCells(dots: List<DetectedDot>): CellClusterResult {
        if (dots.isEmpty()) return CellClusterResult(emptyList(), emptyList())

        val pitch = estimateDotPitch(dots)
        val tolerance = pitch * snapToleranceFactor

        // consumed[i] == true once dot i has been assigned to a cell OR excluded
        // as noise, so each dot is touched by at most one cell.
        val consumed = BooleanArray(dots.size)
        val cells = ArrayList<BrailleCell>()
        val noise = ArrayList<DetectedDot>()

        while (true) {
            val seed = nextSeedIndex(dots, consumed) ?: break

            // The seed is the topmost (then leftmost) unassigned dot, so no other
            // unassigned dot lies above it: anchoring the cell's top row at the
            // seed's y maximizes downward reach over the three rows. The seed's
            // column is ambiguous (it may be the cell's left OR right column), so
            // try both origins and keep whichever captures more dots, preferring
            // "seed in the left column" on a tie.
            val originY = dots[seed].y
            val leftOrigin = fitCell(dots, consumed, dots[seed].x, originY, pitch, tolerance)
            val rightOrigin = fitCell(dots, consumed, dots[seed].x - pitch, originY, pitch, tolerance)
            val fit = if (rightOrigin.assignedCount > leftOrigin.assignedCount) rightOrigin else leftOrigin

            // Consume every dot inside the chosen footprint: those on a grid
            // position join the cell, the remainder are excluded as noise.
            for (i in fit.footprintIndices) consumed[i] = true

            cells += buildCell(dots, fit, pitch)
            for (i in fit.footprintIndices) {
                if (i !in fit.assignedIndices) noise += dots[i]
            }
        }

        return CellClusterResult(cells, noise)
    }

    /**
     * Estimate the intra-cell dot pitch — the spacing between adjacent dot
     * positions — as the median nearest-neighbor distance across [dots].
     *
     * The median is robust to the larger inter-cell and inter-line gaps that
     * appear as nearest-neighbor distances for sparsely populated cells. When the
     * pitch cannot be measured from spacing (fewer than two dots, or all dots
     * coincident) a positive fallback derived from dot radius is used so the grid
     * geometry stays well-defined.
     */
    fun estimateDotPitch(dots: List<DetectedDot>): Float {
        if (dots.size < 2) return fallbackPitch(dots)

        val nearestNeighborDistances = ArrayList<Float>(dots.size)
        for (i in dots.indices) {
            var best = Float.MAX_VALUE
            for (j in dots.indices) {
                if (i == j) continue
                val d = hypot(
                    (dots[i].x - dots[j].x).toDouble(),
                    (dots[i].y - dots[j].y).toDouble(),
                ).toFloat()
                if (d < best) best = d
            }
            if (best.isFinite()) nearestNeighborDistances += best
        }

        val median = medianOf(nearestNeighborDistances)
        return if (median > 0f) median else fallbackPitch(dots)
    }

    // --- internals -----------------------------------------------------------

    /** The six grid-position offsets (dx, dy) relative to a cell origin, in dot-number order 1..6. */
    private fun gridOffsets(pitch: Float): Array<FloatArray> = arrayOf(
        floatArrayOf(0f, 0f),          // dot 1: col1 row1
        floatArrayOf(0f, pitch),       // dot 2: col1 row2
        floatArrayOf(0f, 2f * pitch),  // dot 3: col1 row3
        floatArrayOf(pitch, 0f),       // dot 4: col2 row1
        floatArrayOf(pitch, pitch),    // dot 5: col2 row2
        floatArrayOf(pitch, 2f * pitch), // dot 6: col2 row3
    )

    /** Index of the next unconsumed dot in topmost-then-leftmost order, or null when none remain. */
    private fun nextSeedIndex(dots: List<DetectedDot>, consumed: BooleanArray): Int? {
        var seed = -1
        for (i in dots.indices) {
            if (consumed[i]) continue
            if (seed == -1) {
                seed = i
                continue
            }
            val a = dots[i]
            val b = dots[seed]
            val better = a.y < b.y || (a.y == b.y && a.x < b.x)
            if (better) seed = i
        }
        return if (seed == -1) null else seed
    }

    /**
     * Result of attempting to fit a single cell at a candidate origin: which
     * unconsumed dots fall inside the grid footprint, and which of those snapped
     * to a distinct grid position (at most one dot per position).
     */
    private class CellFit(
        val originX: Float,
        val originY: Float,
        val footprintIndices: List<Int>,
        /** Dot index assigned to each of the six positions, or -1 if the position is empty. */
        val positionDots: IntArray,
    ) {
        val assignedIndices: Set<Int> = positionDots.filter { it >= 0 }.toSet()
        val assignedCount: Int get() = assignedIndices.size
    }

    /**
     * Fit a cell whose top-left grid position is at ([originX], [originY]).
     * Gathers the unconsumed dots within the grid footprint (expanded by
     * [tolerance]) and snaps each to its nearest free grid position within
     * [tolerance], resolving contention by ascending distance.
     */
    private fun fitCell(
        dots: List<DetectedDot>,
        consumed: BooleanArray,
        originX: Float,
        originY: Float,
        pitch: Float,
        tolerance: Float,
    ): CellFit {
        val minX = originX - tolerance
        val maxX = originX + pitch + tolerance
        val minY = originY - tolerance
        val maxY = originY + 2f * pitch + tolerance

        val footprint = ArrayList<Int>()
        for (i in dots.indices) {
            if (consumed[i]) continue
            val d = dots[i]
            if (d.x in minX..maxX && d.y in minY..maxY) footprint += i
        }

        val offsets = gridOffsets(pitch)
        // Candidate (distance, dotIndex, positionIndex) snaps within tolerance.
        data class Snap(val distance: Float, val dotIndex: Int, val positionIndex: Int)
        val candidates = ArrayList<Snap>()
        for (i in footprint) {
            val d = dots[i]
            for (p in offsets.indices) {
                val dist = hypot(
                    (d.x - (originX + offsets[p][0])).toDouble(),
                    (d.y - (originY + offsets[p][1])).toDouble(),
                ).toFloat()
                if (dist <= tolerance) candidates += Snap(dist, i, p)
            }
        }
        // Greedily assign closest dot/position pairs first; deterministic ties.
        candidates.sortWith(
            compareBy<Snap> { it.distance }.thenBy { it.dotIndex }.thenBy { it.positionIndex },
        )

        val positionDots = IntArray(6) { -1 }
        val usedDots = HashSet<Int>()
        for (c in candidates) {
            if (positionDots[c.positionIndex] != -1) continue
            if (c.dotIndex in usedDots) continue
            positionDots[c.positionIndex] = c.dotIndex
            usedDots += c.dotIndex
        }

        return CellFit(originX, originY, footprint, positionDots)
    }

    /**
     * Build a [BrailleCell] from a chosen [fit]. Cell geometry uses the fitted
     * grid footprint (origin plus one pitch wide and two pitches tall) rather
     * than only the occupied dots, so a cell's bounding box and vertical center
     * stay stable regardless of which positions happen to be raised — the
     * property line grouping relies on (Req 5.2). The provisional confidence is
     * the mean of the assigned dots' confidences; the Pattern_Recognizer refines
     * per-cell confidence later (Req 6.2).
     */
    private fun buildCell(dots: List<DetectedDot>, fit: CellFit, pitch: Float): BrailleCell {
        val assignedDots = fit.positionDots.filter { it >= 0 }.map { dots[it] }
        val meanConfidence = assignedDots.map { it.confidence.value }.average().toFloat()
        return BrailleCell(
            dots = assignedDots,
            boundingBox = BoundingBox(
                left = fit.originX,
                top = fit.originY,
                right = fit.originX + pitch,
                bottom = fit.originY + 2f * pitch,
            ),
            centerY = fit.originY + pitch,
            validGrid = true,
            confidence = Confidence.of(meanConfidence),
        )
    }

    private fun fallbackPitch(dots: List<DetectedDot>): Float {
        if (dots.isEmpty()) return 1f
        val meanRadius = dots.map { it.radius.toDouble() }.average().toFloat()
        // A Braille dot's center-to-center pitch is roughly three dot radii; use
        // that as a geometry-preserving fallback, never returning a non-positive
        // pitch (radii may be reported as zero).
        val estimate = meanRadius * 3f
        return if (estimate > 0f) estimate else 1f
    }

    private fun medianOf(values: List<Float>): Float {
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

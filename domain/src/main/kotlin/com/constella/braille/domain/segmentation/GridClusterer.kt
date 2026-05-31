package com.constella.braille.domain.segmentation

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import kotlin.math.hypot

/**
 * Result of the cell-grid clustering step (task 7.1).
 *
 * [cells] are the 2-column by 3-row Braille cells formed from the accepted
 * dots, in the order they were greedily seeded (top-to-bottom, then
 * left-to-right). Reading-order grouping into lines and word boundaries is a
 * *later* concern (task 7.3) that consumes these cells; this step only forms
 * them.
 *
 * [noise] holds the accepted dots that were *excluded* because they did not fit
 * any cell's six candidate positions (Req 5.1). The union of every cell's dots
 * and [noise] is exactly the input set, with no dot appearing twice — each
 * accepted dot is assigned to at most one cell.
 *
 * [estimatedPitch] is the dot pitch (within-cell spacing) estimated from the
 * input, or `null` when it could not be estimated (fewer than two distinct dot
 * locations).
 *
 * _Requirements: 5.1_
 */
data class GridClusteringResult(
    val cells: List<BrailleCell>,
    val noise: List<DetectedDot>,
    val estimatedPitch: Float?,
)

/**
 * Cell-grid clustering and noise exclusion for the Cell_Segmenter (Req 5.1).
 *
 * This is the first segmentation step: it takes the accepted [DetectedDot]s
 * from the Dot_Detector, estimates the dot pitch, fits 2-column by 3-row cell
 * grids over them, assigns each accepted dot to **at most one** cell's six
 * candidate positions, and excludes dots that fit no cell as noise. It produces
 * [BrailleCell]s with their bounding boxes; downstream concerns own the rest:
 *
 * - Line grouping, reading order, and word boundaries (task 7.3).
 * - Degraded-region handling (`validGrid = false`, sub-threshold confidence)
 *   and empty-input policy (task 7.6).
 *
 * Those steps are deliberately *not* implemented here so they can be added in
 * separate files without rewriting this clustering logic. Every cell produced
 * here fits a valid 2x3 grid by construction, so [BrailleCell.validGrid] is
 * always `true`; task 7.6 introduces the degraded-region case.
 *
 * ### Algorithm
 *
 * 1. **Pitch estimation.** The pitch is the within-cell dot spacing. Dots
 *    inside a cell are closer to each other than dots across cells or lines, so
 *    the median of each dot's nearest-neighbour distance is a robust estimate.
 * 2. **Greedy seed-and-grow.** Dots are processed top-to-bottom then
 *    left-to-right. The first unassigned dot seeds a cell: because it is the
 *    topmost-leftmost remaining dot it sits in the cell's top occupied row, so
 *    the cell origin's row is fixed at the seed's `y`. The seed may be in the
 *    left or right column, so both column anchorings are tried and the one that
 *    captures more dots is kept (ties favour the left-column anchoring).
 * 3. **Grid snapping.** Each of the six candidate positions claims the nearest
 *    unclaimed dot within [ScanConstants.Segmentation.DOT_SNAP_TOLERANCE_FACTOR]
 *    times the pitch. That tolerance is below half the inter-position distance,
 *    so a dot can snap to at most one position and each position holds at most
 *    one dot — a cell therefore holds at most six dots.
 * 4. **Noise exclusion.** Dots lying inside the committed cell's footprint but
 *    off every grid position are consumed as noise (Req 5.1), so they neither
 *    join the cell nor seed a spurious one. Dots outside every footprint that
 *    form their own (possibly sparse) grid remain legitimate cells.
 *
 * The object is pure and stateless, mirroring the rest of the deterministic
 * domain core (e.g. `AlignmentEvaluator`), so it is directly property-testable.
 *
 * _Requirements: 5.1_
 */
object GridClusterer {

    /** Column offsets of the six 2x3 grid positions, in pitch units. */
    private val COLUMN_OFFSETS = intArrayOf(0, 1)

    /** Row offsets of the six 2x3 grid positions, in pitch units. */
    private val ROW_OFFSETS = intArrayOf(0, 1, 2)

    /**
     * Cluster [dots] into 2x3 Braille cells, returning the formed cells, the
     * excluded noise dots, and the estimated pitch.
     *
     * Every input dot is treated as an *accepted* candidate (the Dot_Detector
     * has already applied the minimum dot-detection confidence filter, Req 4.3);
     * this step does not re-filter by confidence. The result partitions the
     * input: each dot ends up in exactly one cell or in [GridClusteringResult.noise],
     * never both and never duplicated.
     */
    fun cluster(dots: List<DetectedDot>): GridClusteringResult {
        if (dots.isEmpty()) {
            return GridClusteringResult(cells = emptyList(), noise = emptyList(), estimatedPitch = null)
        }

        val pitch = estimatePitch(dots)
        if (pitch == null) {
            // Either a single dot (a legitimate one-dot cell) or a set of
            // fully-coincident dots (only one can occupy a grid position; the
            // rest are duplicate noise). Form one cell from the first dot.
            val cell = buildCell(listOf(dots.first()))
            return GridClusteringResult(
                cells = listOf(cell),
                noise = dots.drop(1),
                estimatedPitch = null,
            )
        }

        val snapTolerance = ScanConstants.Segmentation.DOT_SNAP_TOLERANCE_FACTOR * pitch
        val assigned = BooleanArray(dots.size)
        // Process topmost-leftmost first; index is the final tie-break for determinism.
        val seedOrder = dots.indices.sortedWith(
            compareBy({ dots[it].y }, { dots[it].x }, { it }),
        )

        val cells = ArrayList<BrailleCell>()
        val noise = ArrayList<DetectedDot>()

        for (seedIndex in seedOrder) {
            if (assigned[seedIndex]) continue
            val seed = dots[seedIndex]

            // The seed is the top occupied row, so the origin row is the seed's
            // y. Try the seed in the left column (origin x = seed.x) and in the
            // right column (origin x = seed.x - pitch); keep whichever grid
            // captures more dots, favouring the left-column anchoring on a tie.
            val leftAnchored = capture(seed.x, seed.y, pitch, snapTolerance, dots, assigned)
            val rightAnchored = capture(seed.x - pitch, seed.y, pitch, snapTolerance, dots, assigned)
            val chosen = if (rightAnchored.captured.size > leftAnchored.captured.size) {
                rightAnchored
            } else {
                leftAnchored
            }

            // Commit: the snapped dots form the cell; dots inside the footprint
            // but off every grid position are excluded as noise so they do not
            // seed a spurious cell (Req 5.1).
            for (index in chosen.captured) assigned[index] = true
            for (index in chosen.footprintMisses) assigned[index] = true

            cells += buildCell(chosen.captured.map { dots[it] })
            for (index in chosen.footprintMisses) noise += dots[index]
        }

        return GridClusteringResult(cells = cells, noise = noise, estimatedPitch = pitch)
    }

    /**
     * The dots a candidate cell origin claims.
     *
     * [captured] are the dot indices snapped onto the six grid positions (at
     * most six). [footprintMisses] are the unassigned dots that lie inside the
     * cell's footprint but snapped to no position — the noise this cell consumes.
     */
    private data class Capture(
        val captured: List<Int>,
        val footprintMisses: List<Int>,
    )

    /**
     * Snap unassigned dots onto the six positions of the 2x3 grid whose
     * top-left position is `(originX, originY)`, and collect the off-grid dots
     * inside the grid footprint.
     *
     * Each position claims the nearest not-yet-claimed, unassigned dot within
     * [snapTolerance]. Because the tolerance is below half the spacing between
     * adjacent positions, a dot matches at most one position and each position
     * matches at most one dot.
     */
    private fun capture(
        originX: Float,
        originY: Float,
        pitch: Float,
        snapTolerance: Float,
        dots: List<DetectedDot>,
        assigned: BooleanArray,
    ): Capture {
        val captured = ArrayList<Int>(6)
        val claimed = HashSet<Int>()

        for (column in COLUMN_OFFSETS) {
            for (row in ROW_OFFSETS) {
                val positionX = originX + column * pitch
                val positionY = originY + row * pitch

                var bestIndex = -1
                var bestDistance = Float.MAX_VALUE
                for (index in dots.indices) {
                    if (assigned[index] || index in claimed) continue
                    val distance = hypot(dots[index].x - positionX, dots[index].y - positionY)
                    if (distance <= snapTolerance && distance < bestDistance) {
                        bestDistance = distance
                        bestIndex = index
                    }
                }
                if (bestIndex >= 0) {
                    claimed += bestIndex
                    captured += bestIndex
                }
            }
        }

        // Footprint: the grid extent (origin .. origin + pitch x 2*pitch)
        // expanded by the snap tolerance on every side. Unassigned, unclaimed
        // dots inside it are the noise this cell consumes.
        val left = originX - snapTolerance
        val right = originX + pitch + snapTolerance
        val top = originY - snapTolerance
        val bottom = originY + 2f * pitch + snapTolerance

        val footprintMisses = ArrayList<Int>()
        for (index in dots.indices) {
            if (assigned[index] || index in claimed) continue
            val dot = dots[index]
            if (dot.x in left..right && dot.y in top..bottom) {
                footprintMisses += index
            }
        }

        return Capture(captured = captured, footprintMisses = footprintMisses)
    }

    /**
     * Build a [BrailleCell] from its assigned dots.
     *
     * The bounding box encloses every member dot's disc (centre ± radius) in
     * preprocessed-image pixel space; [BrailleCell.centerY] is the box's vertical
     * centre, which the later line-grouping step (task 7.3) orders cells by. The
     * cell's confidence is the mean of its member dots' confidences — a
     * provisional aggregate; the Pattern_Recognizer (task 9) refines per-cell
     * confidence with grid-fit quality. Cells formed here always fit a valid 2x3
     * grid, so [BrailleCell.validGrid] is `true`; the degraded-region case is
     * added by task 7.6.
     */
    private fun buildCell(members: List<DetectedDot>): BrailleCell {
        require(members.isNotEmpty()) { "A cell must contain at least one dot" }

        val left = members.minOf { it.x - it.radius }
        val top = members.minOf { it.y - it.radius }
        val right = members.maxOf { it.x + it.radius }
        val bottom = members.maxOf { it.y + it.radius }
        val boundingBox = BoundingBox(left = left, top = top, right = right, bottom = bottom)

        val meanConfidence = members.map { it.confidence.value }.average().toFloat()

        return BrailleCell(
            dots = members.sortedWith(compareBy({ it.y }, { it.x })),
            boundingBox = boundingBox,
            centerY = boundingBox.centerY,
            validGrid = true,
            confidence = Confidence.of(meanConfidence),
        )
    }

    /**
     * Estimate the within-cell dot pitch as the median of each dot's nearest
     * positive neighbour distance.
     *
     * Returns `null` when no positive distance exists — i.e. fewer than two
     * distinct dot locations (a single dot, or a fully-coincident set) — in
     * which case a meaningful grid cannot be fitted.
     */
    private fun estimatePitch(dots: List<DetectedDot>): Float? {
        if (dots.size < 2) return null

        val nearestDistances = ArrayList<Float>(dots.size)
        for (i in dots.indices) {
            var nearest = Float.MAX_VALUE
            for (j in dots.indices) {
                if (i == j) continue
                val distance = hypot(dots[i].x - dots[j].x, dots[i].y - dots[j].y)
                if (distance < nearest) nearest = distance
            }
            // Ignore coincident neighbours (distance 0) so duplicate detections
            // do not collapse the pitch estimate toward zero.
            if (nearest > 0f && nearest.isFinite()) nearestDistances += nearest
        }

        if (nearestDistances.isEmpty()) return null
        return median(nearestDistances)
    }

    /** Median of a non-empty list of values. */
    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }
}

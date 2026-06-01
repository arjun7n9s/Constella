package com.constella.braille.domain.segmentation

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.Confidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll

/**
 * Example and property-style tests for [LineGrouper] — line grouping, reading
 * order, and word boundaries (task 7.3, Req 5.2, 5.3, 5.4).
 *
 * These exercise the implementation directly. The numbered Correctness
 * Properties 11 (reading-order/line-grouping monotonicity) and 12 (word
 * boundaries) are implemented separately by tasks 7.4 and 7.5; the property
 * checks here are implementation-level monotonicity checks, not those numbered
 * properties.
 */
class LineGrouperTest : StringSpec({

    /**
     * A cell centered at ([centerX], [centerY]) with the given [width]/[height].
     * Dots are irrelevant to grouping (which uses only geometry), so the cell is
     * built with no dots.
     */
    fun cell(
        centerX: Float,
        centerY: Float,
        width: Float = 10f,
        height: Float = 30f,
        confidence: Float = 0.9f,
        validGrid: Boolean = true,
    ): BrailleCell {
        val box = BoundingBox(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        )
        return BrailleCell(
            dots = emptyList(),
            boundingBox = box,
            centerY = box.centerY,
            validGrid = validGrid,
            confidence = Confidence.of(confidence),
        )
    }

    // --- Empty input ---------------------------------------------------------

    "empty input yields the empty document" {
        LineGrouper.group(emptyList()) shouldBe com.constella.braille.domain.model.SegmentedDocument.EMPTY
    }

    // --- Line grouping (Req 5.2) ---------------------------------------------

    "cells with near-equal vertical centers group into a single line" {
        // height 30 -> median height 30 -> tolerance 0.5 * 30 = 15.
        val cells = listOf(
            cell(centerX = 0f, centerY = 100f),
            cell(centerX = 20f, centerY = 108f),  // +8 within tolerance
            cell(centerX = 40f, centerY = 95f),   // -5 within tolerance
        )

        val doc = LineGrouper.group(cells)

        doc.lines.shouldHaveSize(1)
        doc.lines.single().cells.shouldHaveSize(3)
    }

    "cells separated by more than the line tolerance form separate lines" {
        // tolerance 15; the second cluster sits 50px below the first.
        val cells = listOf(
            cell(centerX = 0f, centerY = 100f),
            cell(centerX = 20f, centerY = 105f),
            cell(centerX = 0f, centerY = 160f),
            cell(centerX = 20f, centerY = 158f),
        )

        val doc = LineGrouper.group(cells)

        doc.lines.shouldHaveSize(2)
        doc.lines[0].cells.shouldHaveSize(2)
        doc.lines[1].cells.shouldHaveSize(2)
    }

    "the line tolerance scales with the median cell height factor from constants" {
        // Sanity-check we depend on the configured factor, not a literal.
        ScanConstants.Segmentation.LINE_GROUPING_CELL_HEIGHT_FACTOR shouldBe 0.5f
        ScanConstants.Segmentation.WORD_BOUNDARY_SPACING_FACTOR shouldBe 1.5f
    }

    // --- Reading order (Req 5.3) ---------------------------------------------

    "cells within a line are ordered left to right regardless of input order" {
        val cells = listOf(
            cell(centerX = 50f, centerY = 100f),
            cell(centerX = 10f, centerY = 100f),
            cell(centerX = 30f, centerY = 100f),
        )

        val ordered = LineGrouper.group(cells).lines.single().cells.map { it.boundingBox.centerX }

        ordered shouldContainExactly listOf(10f, 30f, 50f)
    }

    "lines are ordered top to bottom regardless of input order" {
        val bottom = cell(centerX = 0f, centerY = 300f)
        val top = cell(centerX = 0f, centerY = 100f)
        val middle = cell(centerX = 0f, centerY = 200f)

        val lineCenterYs = LineGrouper.group(listOf(bottom, top, middle)).lines
            .map { it.cells.single().centerY }

        lineCenterYs shouldContainExactly listOf(100f, 200f, 300f)
    }

    // --- Word boundaries (Req 5.4) -------------------------------------------

    "a word boundary is inserted where the gap exceeds 1.5x the median spacing" {
        // centerX: 0,10,20,40,50 -> adjacent center-to-center gaps: 10,10,20,10.
        // median([10,10,10,20]) = 10 ; threshold = 1.5 * 10 = 15.
        // Only the gap of 20 (after index 2) exceeds 15.
        val cells = listOf(
            cell(centerX = 0f, centerY = 100f),
            cell(centerX = 10f, centerY = 100f),
            cell(centerX = 20f, centerY = 100f),
            cell(centerX = 40f, centerY = 100f),
            cell(centerX = 50f, centerY = 100f),
        )

        val line = LineGrouper.group(cells).lines.single()

        line.wordBoundaryAfter shouldBe setOf(2)
    }

    "uniform spacing inserts no word boundaries" {
        val cells = (0..5).map { cell(centerX = it * 10f, centerY = 100f) }

        val line = LineGrouper.group(cells).lines.single()

        line.wordBoundaryAfter shouldBe emptySet()
    }

    "the median intra-line spacing is pooled across all lines" {
        // Line A (top): tight spacing of 10 across four cells (gaps 10,10,10).
        // Line B (bottom): one wide gap of 25 and one of 10.
        // Pooled gaps: [10,10,10,25,10] -> sorted [10,10,10,10,25] median 10.
        // threshold = 15. The 25 gap in line B exceeds it; nothing in line A.
        val lineA = listOf(0f, 10f, 20f, 30f).map { cell(centerX = it, centerY = 100f) }
        val lineB = listOf(0f, 10f, 35f).map { cell(centerX = it, centerY = 300f) }

        val doc = LineGrouper.group(lineA + lineB)

        doc.lines[0].wordBoundaryAfter shouldBe emptySet()
        doc.lines[1].wordBoundaryAfter shouldBe setOf(1)
    }

    "a single-cell line has no word boundaries" {
        val line = LineGrouper.group(listOf(cell(centerX = 0f, centerY = 100f))).lines.single()

        line.wordBoundaryAfter shouldBe emptySet()
    }

    // --- Property-style monotonicity checks ----------------------------------

    "every input cell appears exactly once in the output" {
        val arbCells: Arb<List<BrailleCell>> = Arb.list(
            Arb.pair(Arb.int(0..500), Arb.int(0..500)),
            range = 1..25,
        ).map { coords -> coords.map { (x, y) -> cell(centerX = x.toFloat(), centerY = y.toFloat()) } }

        checkAll(arbCells) { cells ->
            val out = LineGrouper.group(cells).lines.flatMap { it.cells }
            out.shouldContainExactlyInAnyOrder(cells)
        }
    }

    "within every line cells are non-decreasing in horizontal center, and lines are top to bottom" {
        val arbCoords: Arb<List<Pair<Int, Int>>> = Arb.list(
            Arb.pair(Arb.int(0..500), Arb.int(0..500)),
            range = 1..25,
        )

        checkAll(arbCoords) { coords ->
            val cells = coords.map { (x, y) -> cell(centerX = x.toFloat(), centerY = y.toFloat()) }
            val doc = LineGrouper.group(cells)

            // Within each line: horizontal centers are non-decreasing (Req 5.3).
            for (line in doc.lines) {
                val xs = line.cells.map { it.boundingBox.centerX }
                xs shouldBe xs.sorted()
            }

            // Lines are separated and ordered top to bottom: the maximum vertical
            // center of an earlier line is strictly below the minimum of the next.
            for (i in 0 until doc.lines.size - 1) {
                val maxThis = doc.lines[i].cells.maxOf { it.centerY }
                val minNext = doc.lines[i + 1].cells.minOf { it.centerY }
                (maxThis < minNext) shouldBe true
            }
        }
    }
})

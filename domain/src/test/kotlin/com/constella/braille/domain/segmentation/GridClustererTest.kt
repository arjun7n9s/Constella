package com.constella.braille.domain.segmentation

import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [GridClusterer] — the cell-grid clustering and noise-exclusion
 * step (task 7.1, Req 5.1).
 *
 * These verify specific examples and edge cases. The universal "each accepted
 * dot is assigned to at most one cell" property is covered separately by the
 * Property 10 test (task 7.2).
 */
class GridClustererTest : StringSpec({

    fun dot(x: Float, y: Float, radius: Float = 1f, confidence: Float = 0.9f) =
        DetectedDot(x = x, y = y, radius = radius, confidence = Confidence.of(confidence))

    /**
     * A complete 2x3 cell with the given pitch and top-left position. Dot
     * order is intentionally scrambled so tests do not depend on input order.
     */
    fun fullCell(originX: Float, originY: Float, pitch: Float): List<DetectedDot> = listOf(
        dot(originX + pitch, originY + 2 * pitch),
        dot(originX, originY),
        dot(originX + pitch, originY),
        dot(originX, originY + 2 * pitch),
        dot(originX, originY + pitch),
        dot(originX + pitch, originY + pitch),
    )

    "empty input yields no cells, no noise, and no pitch" {
        val result = GridClusterer.cluster(emptyList())

        result.cells.shouldHaveSize(0)
        result.noise.shouldHaveSize(0)
        result.estimatedPitch.shouldBeNull()
    }

    "a single dot forms one cell with no noise and no pitch estimate" {
        val single = dot(10f, 10f)

        val result = GridClusterer.cluster(listOf(single))

        result.cells.shouldHaveSize(1)
        result.cells.single().dots.shouldContainExactlyInAnyOrder(single)
        result.noise.shouldHaveSize(0)
        result.estimatedPitch.shouldBeNull()
    }

    "a full 2x3 grid forms exactly one valid six-dot cell" {
        val cellDots = fullCell(originX = 100f, originY = 200f, pitch = 10f)

        val result = GridClusterer.cluster(cellDots)

        result.cells.shouldHaveSize(1)
        val cell = result.cells.single()
        cell.dots.shouldHaveSize(6)
        cell.validGrid.shouldBeTrue()
        cell.dots.shouldContainExactlyInAnyOrder(cellDots)
        result.noise.shouldHaveSize(0)
        result.estimatedPitch.shouldNotBeNull()
        result.estimatedPitch!!.toDouble() shouldBe (10.0 plusOrMinus 0.001)
    }

    "the cell bounding box and centerY enclose the member dots" {
        val cellDots = fullCell(originX = 0f, originY = 0f, pitch = 10f) // radius 1f each

        val cell = GridClusterer.cluster(cellDots).cells.single()

        // Extremes are the corner dots' discs: centres at 0..10 with radius 1.
        cell.boundingBox.left shouldBe -1f
        cell.boundingBox.top shouldBe -1f
        cell.boundingBox.right shouldBe 11f
        cell.boundingBox.bottom shouldBe 21f
        cell.centerY shouldBe cell.boundingBox.centerY
    }

    "two horizontally separated full cells form two cells with no noise" {
        val pitch = 10f
        // Second cell starts well past the first cell's right column + footprint.
        val first = fullCell(originX = 0f, originY = 0f, pitch = pitch)
        val second = fullCell(originX = 100f, originY = 0f, pitch = pitch)

        val result = GridClusterer.cluster(first + second)

        result.cells.shouldHaveSize(2)
        result.noise.shouldHaveSize(0)
        result.cells.sumOf { it.dots.size } shouldBe 12
    }

    "a stray dot inside a cell footprint but off every grid position is excluded as noise" {
        val pitch = 10f
        val cellDots = fullCell(originX = 0f, originY = 0f, pitch = pitch)
        // Sits at the centre of the 2x3 footprint (5, 10) — far from all six
        // grid positions relative to the snap tolerance (0.5 * pitch = 5).
        val stray = dot(5f, 10f)

        val result = GridClusterer.cluster(cellDots + stray)

        result.cells.shouldHaveSize(1)
        result.cells.single().dots.shouldContainExactlyInAnyOrder(cellDots)
        result.noise.shouldContainExactlyInAnyOrder(stray)
    }

    "every input dot ends up in exactly one cell or in noise, never both" {
        val pitch = 10f
        val cellDots = fullCell(originX = 0f, originY = 0f, pitch = pitch)
        val stray = dot(5f, 10f)
        val allDots = cellDots + stray

        val result = GridClusterer.cluster(allDots)

        val assignedToCells = result.cells.flatMap { it.dots }
        val partition = assignedToCells + result.noise
        // The partition is exactly the input (no loss, no duplication).
        partition.shouldContainExactlyInAnyOrder(allDots)
        partition.shouldHaveSize(allDots.size)
    }

    "fully coincident dots keep one as a cell and treat the rest as noise" {
        val a = dot(50f, 50f)
        val b = dot(50f, 50f)
        val c = dot(50f, 50f)

        val result = GridClusterer.cluster(listOf(a, b, c))

        result.cells.shouldHaveSize(1)
        result.cells.single().dots.shouldHaveSize(1)
        result.noise.shouldHaveSize(2)
        result.estimatedPitch.shouldBeNull()
    }

    "a partial cell (some positions missing) still forms a single valid cell" {
        val pitch = 10f
        // Only the left column of three dots — a legitimate sparse cell.
        val leftColumn = listOf(
            dot(0f, 0f),
            dot(0f, pitch),
            dot(0f, 2 * pitch),
        )

        val result = GridClusterer.cluster(leftColumn)

        result.cells.shouldHaveSize(1)
        result.cells.single().dots.shouldContainExactlyInAnyOrder(leftColumn)
        result.noise.shouldHaveSize(0)
    }
})

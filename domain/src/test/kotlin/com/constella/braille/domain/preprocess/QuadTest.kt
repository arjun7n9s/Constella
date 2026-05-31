package com.constella.braille.domain.preprocess

import com.constella.braille.domain.config.ScanConstants
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [Quad] — canonical corner ordering, shoelace area, and the
 * Req 3.1 minimum-frame-area acceptance test (task 5.1).
 */
class QuadTest : StringSpec({

    "ordered sorts arbitrary corner input into TL/TR/BR/BL" {
        val tl = Point2D(10f, 20f)
        val tr = Point2D(110f, 22f)
        val br = Point2D(112f, 120f)
        val bl = Point2D(8f, 118f)

        // Feed them in a scrambled order.
        val quad = Quad.ordered(br, bl, tr, tl)

        quad.topLeft shouldBe tl
        quad.topRight shouldBe tr
        quad.bottomRight shouldBe br
        quad.bottomLeft shouldBe bl
    }

    "ordered requires exactly four corners" {
        shouldThrow<IllegalArgumentException> {
            Quad.ordered(listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f)))
        }
    }

    "area of an axis-aligned rectangle is width times height" {
        val quad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(100f, 0f),
            bottomRight = Point2D(100f, 50f),
            bottomLeft = Point2D(0f, 50f),
        )
        quad.area shouldBe (5000.0 plusOrMinus 1e-6)
    }

    "area is winding-independent (same value regardless of corner order fed to ordered)" {
        val corners = listOf(
            Point2D(0f, 0f), Point2D(40f, 0f), Point2D(40f, 30f), Point2D(0f, 30f),
        )
        val a = Quad.ordered(corners)
        val b = Quad.ordered(corners.reversed())
        a.area shouldBe (b.area plusOrMinus 1e-6)
        a.area shouldBe (1200.0 plusOrMinus 1e-6)
    }

    "enclosesAtLeastFraction accepts a boundary at exactly the threshold" {
        val frame = ImageSize(200, 100) // area 20_000
        // 0.25 is exactly representable in binary float, so the threshold
        // (0.25 * 20_000 = 5_000) is exact; a 100x50 rectangle has area 5_000.
        val quad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(100f, 0f),
            bottomRight = Point2D(100f, 50f),
            bottomLeft = Point2D(0f, 50f),
        )
        quad.area shouldBe (5000.0 plusOrMinus 1e-6)
        quad.enclosesAtLeastFraction(frame, 0.25f).shouldBeTrue()
    }

    "enclosesAtLeastFraction rejects a boundary just below the threshold" {
        val frame = ImageSize(200, 100) // area 20_000
        // 0.25 threshold = 5_000; a 100x49 rectangle (area 4_900) is below it.
        val quad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(100f, 0f),
            bottomRight = Point2D(100f, 49f),
            bottomLeft = Point2D(0f, 49f),
        )
        quad.enclosesAtLeastFraction(frame, 0.25f).shouldBeFalse()
    }

    "enclosesAtLeastFraction uses the ScanConstants default fraction" {
        val frame = ImageSize(100, 100) // area 10_000
        val minFraction = ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION
        val minArea = minFraction.toDouble() * frame.area

        // Comfortably above the default minimum: accepted.
        val big = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(80f, 0f),
            bottomRight = Point2D(80f, 80f),
            bottomLeft = Point2D(0f, 80f),
        )
        big.area shouldBe (6400.0 plusOrMinus 1e-6)
        (big.area > minArea).shouldBeTrue() // sanity: 6_400 > 1_000
        big.enclosesAtLeastFraction(frame).shouldBeTrue()

        // Comfortably below the default minimum: rejected.
        val small = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(20f, 0f),
            bottomRight = Point2D(20f, 20f),
            bottomLeft = Point2D(0f, 20f),
        )
        small.area shouldBe (400.0 plusOrMinus 1e-6)
        (small.area < minArea).shouldBeTrue() // sanity: 400 < 1_000
        small.enclosesAtLeastFraction(frame).shouldBeFalse()
    }

    "enclosesAtLeastFraction rejects a fraction outside [0,1]" {
        val frame = ImageSize(10, 10)
        val quad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(5f, 0f),
            bottomRight = Point2D(5f, 5f),
            bottomLeft = Point2D(0f, 5f),
        )
        shouldThrow<IllegalArgumentException> { quad.enclosesAtLeastFraction(frame, 1.5f) }
    }
})

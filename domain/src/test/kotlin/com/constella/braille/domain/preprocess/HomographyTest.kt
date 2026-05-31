package com.constella.braille.domain.preprocess

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [Homography] — the 4-point projective solve, point mapping,
 * and inverse used by the perspective warp (task 5.1, Req 3.2).
 */
class HomographyTest : StringSpec({

    "the identity correspondence maps points to themselves" {
        val pts = listOf(
            Point2D(0f, 0f), Point2D(10f, 0f), Point2D(10f, 10f), Point2D(0f, 10f),
        )
        val h = Homography.solve(pts, pts)

        val sample = Point2D(3f, 7f)
        val mapped = h.apply(sample)
        mapped.x.toDouble() shouldBe (3.0 plusOrMinus 1e-4)
        mapped.y.toDouble() shouldBe (7.0 plusOrMinus 1e-4)
    }

    "solve maps each source corner exactly onto its destination corner" {
        // A perspective (non-affine) source quad.
        val src = listOf(
            Point2D(12f, 9f),
            Point2D(190f, 30f),
            Point2D(205f, 160f),
            Point2D(4f, 140f),
        )
        val dst = listOf(
            Point2D(0f, 0f),
            Point2D(200f, 0f),
            Point2D(200f, 150f),
            Point2D(0f, 150f),
        )
        val h = Homography.solve(src, dst)

        for (i in src.indices) {
            val mapped = h.apply(src[i])
            mapped.x.toDouble() shouldBe (dst[i].x.toDouble() plusOrMinus 1e-3)
            mapped.y.toDouble() shouldBe (dst[i].y.toDouble() plusOrMinus 1e-3)
        }
    }

    "inverse composed with forward recovers the original point" {
        val src = listOf(
            Point2D(12f, 9f),
            Point2D(190f, 30f),
            Point2D(205f, 160f),
            Point2D(4f, 140f),
        )
        val dst = listOf(
            Point2D(0f, 0f),
            Point2D(200f, 0f),
            Point2D(200f, 150f),
            Point2D(0f, 150f),
        )
        val h = Homography.solve(src, dst)
        val inv = h.inverse()

        val sample = Point2D(123.4f, 88.1f)
        val round = inv.apply(h.apply(sample))
        round.x.toDouble() shouldBe (sample.x.toDouble() plusOrMinus 1e-2)
        round.y.toDouble() shouldBe (sample.y.toDouble() plusOrMinus 1e-2)
    }

    "solve rejects a degenerate (collinear) source configuration" {
        val collinear = listOf(
            Point2D(0f, 0f), Point2D(1f, 1f), Point2D(2f, 2f), Point2D(3f, 3f),
        )
        val dst = listOf(
            Point2D(0f, 0f), Point2D(10f, 0f), Point2D(10f, 10f), Point2D(0f, 10f),
        )
        shouldThrow<IllegalArgumentException> { Homography.solve(collinear, dst) }
    }

    "solve requires exactly four points on each side" {
        shouldThrow<IllegalArgumentException> {
            Homography.solve(
                listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f)),
                listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f)),
            )
        }
    }
})

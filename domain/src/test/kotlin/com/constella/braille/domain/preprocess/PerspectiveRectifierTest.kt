package com.constella.braille.domain.preprocess

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [PerspectiveRectifier] — target-rectangle sizing, the warp
 * homography, and the edge-alignment metric (task 5.1, Req 3.2).
 */
class PerspectiveRectifierTest : StringSpec({

    "target size takes the larger of each opposing edge pair" {
        // Top edge length 100, bottom edge length 120 -> width 120.
        // Left edge length 80, right edge length 60 -> height 80.
        val quad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(100f, 0f),
            bottomRight = Point2D(100f, 60f),
            bottomLeft = Point2D(-20f, 80f),
        )
        val plan = PerspectiveRectifier.plan(quad)

        // top width = 100, bottom width = dist((-20,80),(100,60)) ~= 121.66 -> 122
        plan.targetSize.width shouldBe 122
        // left height = dist((0,0),(-20,80)) ~= 82.46 -> 82, right height = 60 -> max 82
        plan.targetSize.height shouldBe 82
    }

    "the target quad corners are axis-aligned at the rectangle extents" {
        val quad = Quad(
            topLeft = Point2D(5f, 4f),
            topRight = Point2D(95f, 6f),
            bottomRight = Point2D(98f, 70f),
            bottomLeft = Point2D(2f, 66f),
        )
        val plan = PerspectiveRectifier.plan(quad)
        val w = plan.targetSize.width.toFloat()
        val h = plan.targetSize.height.toFloat()

        plan.targetQuad.topLeft shouldBe Point2D(0f, 0f)
        plan.targetQuad.topRight shouldBe Point2D(w, 0f)
        plan.targetQuad.bottomRight shouldBe Point2D(w, h)
        plan.targetQuad.bottomLeft shouldBe Point2D(0f, h)
    }

    "target dimensions are always at least one pixel" {
        val tiny = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(0.2f, 0f),
            bottomRight = Point2D(0.2f, 0.2f),
            bottomLeft = Point2D(0f, 0.2f),
        )
        val plan = PerspectiveRectifier.plan(tiny)
        plan.targetSize.width shouldBeGreaterThanOrEqual 1
        plan.targetSize.height shouldBeGreaterThanOrEqual 1
    }

    "a perspective-distorted quad rectifies to near-zero edge misalignment" {
        val quad = Quad(
            topLeft = Point2D(12f, 9f),
            topRight = Point2D(190f, 30f),
            bottomRight = Point2D(205f, 160f),
            bottomLeft = Point2D(4f, 140f),
        )
        val plan = PerspectiveRectifier.plan(quad)

        // After applying the warp the document edges land on the image axes.
        PerspectiveRectifier.maxEdgeMisalignmentDegrees(plan).shouldBeLessThan(0.5)
    }

    "an already-axis-aligned rectangle stays aligned after rectification" {
        val quad = Quad(
            topLeft = Point2D(10f, 10f),
            topRight = Point2D(210f, 10f),
            bottomRight = Point2D(210f, 110f),
            bottomLeft = Point2D(10f, 110f),
        )
        val plan = PerspectiveRectifier.plan(quad)
        plan.targetSize.width shouldBe 200
        plan.targetSize.height shouldBe 100
        PerspectiveRectifier.maxEdgeMisalignmentDegrees(plan).shouldBeLessThan(1e-3)
    }
})

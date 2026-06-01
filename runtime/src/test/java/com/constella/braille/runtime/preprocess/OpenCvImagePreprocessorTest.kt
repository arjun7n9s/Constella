package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.IlluminationNormalizer
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Point2D
import com.constella.braille.domain.preprocess.Quad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [OpenCvImagePreprocessor] — the boundary acceptance +
 * perspective-correction orchestration (task 5.1, Req 3.1, 3.2) and the
 * lighting-normalization + no-boundary graceful-degradation path (task 5.3,
 * Req 3.3, 3.4, 3.5).
 *
 * These run on the JVM with no OpenCV: a [FakeBridge] injects the boundary the
 * (OpenCV) finder would return and delegates the warp to the real,
 * dependency-free [SoftwareOpenCvBridge], so the deterministic acceptance,
 * rectification, and normalization behaviour is exercised end to end.
 */
class OpenCvImagePreprocessorTest {

    /** Solid-gray frame of the given size (content is irrelevant to the geometry). */
    private fun frame(width: Int, height: Int, value: Int = 128): CapturedImage {
        val pixels = ByteArray(width * height) { value.toByte() }
        return CapturedImage(ImageBuffer(ImageSize(width, height), pixels))
    }

    /**
     * Frame with a strong horizontal illumination gradient (0..255 across the
     * width) — the kind of uneven lighting normalization must flatten (Req 3.3).
     */
    private fun gradientFrame(width: Int, height: Int): CapturedImage {
        val pixels = ByteArray(width * height) { i ->
            val x = i % width
            (x * 255 / (width - 1)).toByte()
        }
        return CapturedImage(ImageBuffer(ImageSize(width, height), pixels))
    }

    /** Bridge that returns [boundary] from the finder and warps via software. */
    private class FakeBridge(private val boundary: Quad?) : OpenCvBridge {
        private val software = SoftwareOpenCvBridge()
        override fun findDocumentBoundary(image: ImageBuffer): Quad? = boundary
        override fun warpToRectangle(
            image: ImageBuffer,
            plan: PerspectiveRectifier.RectificationPlan,
        ): ImageBuffer = software.warpToRectangle(image, plan)
    }

    @Test
    fun `accepted boundary rectifies and records the document quad`() = runBlocking {
        val input = frame(200, 100)
        // A quad covering most of the frame (well above the 10% minimum).
        val boundary = Quad(
            topLeft = Point2D(10f, 8f),
            topRight = Point2D(188f, 12f),
            bottomRight = Point2D(190f, 92f),
            bottomLeft = Point2D(6f, 88f),
        )
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(boundary))

        val out = pre.process(input, ScanningMode.EMBOSSED)

        assertTrue("expected rectified output", out.rectified)
        assertNotNull("expected the document quad to be recorded", out.documentQuadInPixels)
        assertEquals(boundary, out.documentQuadInPixels)

        // The rectified image is the size of the computed target rectangle
        // (lighting normalization preserves dimensions).
        val expected = PerspectiveRectifier.plan(boundary).targetSize
        assertEquals(expected.width, out.image.width)
        assertEquals(expected.height, out.image.height)
    }

    @Test
    fun `boundary below the minimum area is rejected and the unrectified frame is normalized`() = runBlocking {
        val input = frame(200, 100) // frame area 20_000; 10% min => 2_000
        // Tiny quad of area 1_000 (5% of the frame) — below the default minimum.
        val tiny = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(20f, 0f),
            bottomRight = Point2D(20f, 50f),
            bottomLeft = Point2D(0f, 50f),
        )
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(tiny))

        val out = pre.process(input, ScanningMode.EMBOSSED)

        assertFalse("sub-minimum boundary must not be rectified", out.rectified)
        assertNull(out.documentQuadInPixels)
        // The unrectified frame is lighting-normalized (Req 3.5), not passed
        // through unchanged: same dimensions, normalized pixels.
        assertEquals(input.buffer.width, out.image.width)
        assertEquals(input.buffer.height, out.image.height)
        val expectedNormalized = IlluminationNormalizer.normalize(input.buffer.pixels, input.buffer.size)
        assertArrayEquals(expectedNormalized, out.image.pixels)
    }

    @Test
    fun `no detected boundary normalizes the unrectified frame and records skipped correction`() = runBlocking {
        val input = gradientFrame(120, 90)
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(boundary = null))

        val out = pre.process(input, ScanningMode.HANDWRITTEN)

        // Provenance: perspective correction skipped (Req 3.5).
        assertFalse(out.rectified)
        assertNull(out.documentQuadInPixels)
        assertEquals(input.buffer.width, out.image.width)
        assertEquals(input.buffer.height, out.image.height)

        // Normalization still applied: illumination variation is reduced below
        // the defined uniformity threshold (Req 3.3).
        val before = LightingNormalization.illuminationVariation(input.buffer)
        val after = LightingNormalization.illuminationVariation(out.image)
        assertTrue("input gradient should be strongly uneven", before > 0.5)
        assertTrue(
            "normalized variation $after must be <= ${IlluminationNormalizer.ILLUMINATION_UNIFORMITY_THRESHOLD}",
            after <= IlluminationNormalizer.ILLUMINATION_UNIFORMITY_THRESHOLD,
        )
        assertTrue("normalization must reduce illumination variation", after < before)
    }

    @Test
    fun `accepted boundary path normalizes the rectified image`() = runBlocking {
        val input = gradientFrame(120, 80)
        // Full-frame boundary => rectifying is the identity, so the gradient is
        // preserved into the rectified image and then must be normalized.
        val boundary = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(120f, 0f),
            bottomRight = Point2D(120f, 80f),
            bottomLeft = Point2D(0f, 80f),
        )
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(boundary))

        val out = pre.process(input, ScanningMode.EMBOSSED)

        assertTrue(out.rectified)
        val after = LightingNormalization.illuminationVariation(out.image)
        assertTrue(
            "rectified image variation $after must be <= ${IlluminationNormalizer.ILLUMINATION_UNIFORMITY_THRESHOLD}",
            after <= IlluminationNormalizer.ILLUMINATION_UNIFORMITY_THRESHOLD,
        )
    }

    @Test
    fun `software warp of an axis-aligned subrectangle reproduces source pixels`() {
        // Warp correctness is a property of the bridge, exercised directly here
        // so the assertion is independent of the lighting normalization that
        // OpenCvImagePreprocessor.process applies afterwards.
        val w = 64
        val h = 16
        val pixels = ByteArray(w * h) { i -> ((i % w)).toByte() }
        val image = ImageBuffer(ImageSize(w, h), pixels)

        // An axis-aligned boundary selecting the full frame; rectifying it is
        // effectively the identity, so the ramp must be preserved.
        val boundary = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(w.toFloat(), 0f),
            bottomRight = Point2D(w.toFloat(), h.toFloat()),
            bottomLeft = Point2D(0f, h.toFloat()),
        )
        val plan = PerspectiveRectifier.plan(boundary)
        val warped = SoftwareOpenCvBridge().warpToRectangle(image, plan)

        // Sample a few interior pixels; the ramp value should match the column.
        val mid = warped.height / 2
        for (x in intArrayOf(5, 20, 40, 60)) {
            val got = warped.luminanceAt(x, mid)
            // Allow a small bilinear-sampling tolerance.
            assertTrue("column $x expected ~$x but got $got", kotlin.math.abs(got - x) <= 1)
        }
    }
}

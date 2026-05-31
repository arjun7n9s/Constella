package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Point2D
import com.constella.braille.domain.preprocess.Quad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [OpenCvImagePreprocessor] — the task 5.1 boundary
 * acceptance + perspective-correction orchestration (Req 3.1, 3.2).
 *
 * These run on the JVM with no OpenCV: a [FakeBridge] injects the boundary the
 * (OpenCV) finder would return and delegates the warp to the real,
 * dependency-free [SoftwareOpenCvBridge], so the deterministic acceptance and
 * rectification behaviour is exercised end to end.
 */
class OpenCvImagePreprocessorTest {

    /** Solid-gray frame of the given size (content is irrelevant to the geometry). */
    private fun frame(width: Int, height: Int, value: Int = 128): CapturedImage {
        val pixels = ByteArray(width * height) { value.toByte() }
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

        // The rectified image is the size of the computed target rectangle.
        val expected = PerspectiveRectifier.plan(boundary).targetSize
        assertEquals(expected.width, out.image.width)
        assertEquals(expected.height, out.image.height)
    }

    @Test
    fun `boundary below the minimum area is rejected (pass-through, no quad)`() = runBlocking {
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
        // Pass-through returns the original frame buffer unchanged (task 5.3 adds
        // lighting normalization on this path).
        assertSame(input.buffer, out.image)
    }

    @Test
    fun `no detected boundary yields unrectified pass-through`() = runBlocking {
        val input = frame(120, 90)
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(boundary = null))

        val out = pre.process(input, ScanningMode.HANDWRITTEN)

        assertFalse(out.rectified)
        assertNull(out.documentQuadInPixels)
        assertSame(input.buffer, out.image)
    }

    @Test
    fun `software warp of an axis-aligned subrectangle reproduces source pixels`() = runBlocking {
        // Build a frame with a horizontal luminance ramp so warp correctness is
        // observable: column x has luminance x (mod 256).
        val w = 64
        val h = 16
        val pixels = ByteArray(w * h) { i -> ((i % w)).toByte() }
        val input = CapturedImage(ImageBuffer(ImageSize(w, h), pixels))

        // An axis-aligned boundary selecting the full frame; rectifying it is
        // effectively the identity, so the ramp must be preserved.
        val boundary = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(w.toFloat(), 0f),
            bottomRight = Point2D(w.toFloat(), h.toFloat()),
            bottomLeft = Point2D(0f, h.toFloat()),
        )
        val pre = OpenCvImagePreprocessor(bridge = FakeBridge(boundary))

        val out = pre.process(input, ScanningMode.EMBOSSED)

        assertTrue(out.rectified)
        // Sample a few interior pixels; the ramp value should match the column.
        val mid = out.image.height / 2
        for (x in intArrayOf(5, 20, 40, 60)) {
            val got = out.image.luminanceAt(x, mid)
            // Allow a small bilinear-sampling tolerance.
            assertTrue("column $x expected ~$x but got $got", kotlin.math.abs(got - x) <= 1)
        }
    }
}

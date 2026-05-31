package com.constella.braille.runtime.detect

import com.constella.braille.domain.config.ConfidenceThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure Dot_Detector post-processing / policy layer
 * (task 6.1). These exercise confidence filtering, coordinate mapping, and the
 * structure decision **without** a real TFLite model.
 *
 * _Requirements: 4.3, 4.7_
 */
class DotDetectionPostProcessorTest {

    private val w = 200
    private val h = 100

    /** Build a single-box [RawDetections] in the default YMIN_XMIN_YMAX_XMAX order. */
    private fun oneBox(
        score: Float,
        yMin: Float = 0.40f,
        xMin: Float = 0.40f,
        yMax: Float = 0.60f,
        xMax: Float = 0.60f,
    ) = RawDetections(
        boxes = floatArrayOf(yMin, xMin, yMax, xMax),
        scores = floatArrayOf(score),
    )

    @Test
    fun `keeps detections at or above the minimum confidence`() {
        val raw = oneBox(score = ConfidenceThresholds.MIN_DOT_DETECTION)
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertEquals(1, out.dots.size)
    }

    @Test
    fun `drops detections below the minimum confidence`() {
        val raw = oneBox(score = ConfidenceThresholds.MIN_DOT_DETECTION - 0.01f)
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertTrue(out.dots.isEmpty())
        assertFalse(out.structureInferable)
    }

    @Test
    fun `drops NaN scored detections`() {
        val raw = oneBox(score = Float.NaN)
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertTrue(out.dots.isEmpty())
    }

    @Test
    fun `maps a centered box to the image center in pixel coordinates`() {
        // Box spanning 0.40..0.60 in both axes => center at 0.50 of (size-1).
        val raw = oneBox(score = 0.9f)
        val out = DotDetectionPostProcessor.process(raw, w, h)
        val dot = out.dots.single()
        assertEquals(0.5f * (w - 1), dot.x, 1e-3f)
        assertEquals(0.5f * (h - 1), dot.y, 1e-3f)
        // radius = avg(halfWidth, halfHeight) of a 0.20-wide/tall normalized box.
        val expectedRadius = (0.20f * (w - 1) / 2f + 0.20f * (h - 1) / 2f) / 2f
        assertEquals(expectedRadius, dot.radius, 1e-3f)
    }

    @Test
    fun `clamps out-of-range boxes into image bounds`() {
        val raw = oneBox(score = 0.9f, yMin = -0.5f, xMin = -0.5f, yMax = 1.5f, xMax = 1.5f)
        val out = DotDetectionPostProcessor.process(raw, w, h)
        val dot = out.dots.single()
        assertTrue(dot.x in 0f..(w - 1).toFloat())
        assertTrue(dot.y in 0f..(h - 1).toFloat())
        assertTrue(dot.radius >= 0f)
    }

    @Test
    fun `confidence score is preserved and clamped into range`() {
        val raw = oneBox(score = 1.5f) // above 1.0, must clamp to 1.0
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertEquals(1.0f, out.dots.single().confidence.value, 1e-6f)
    }

    @Test
    fun `empty input yields empty output and no structure`() {
        val raw = RawDetections(boxes = FloatArray(0), scores = FloatArray(0))
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertTrue(out.dots.isEmpty())
        assertFalse(out.structureInferable)
    }

    @Test
    fun `single accepted dot is not enough to infer structure`() {
        val out = DotDetectionPostProcessor.process(oneBox(score = 0.9f), w, h)
        assertEquals(1, out.dots.size)
        assertFalse(out.structureInferable)
    }

    @Test
    fun `two spatially separated accepted dots infer structure`() {
        val raw = RawDetections(
            boxes = floatArrayOf(
                0.10f, 0.10f, 0.15f, 0.15f, // top-left dot
                0.80f, 0.80f, 0.85f, 0.85f, // bottom-right dot
            ),
            scores = floatArrayOf(0.9f, 0.9f),
        )
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertEquals(2, out.dots.size)
        assertTrue(out.structureInferable)
    }

    @Test
    fun `dots below threshold are excluded before the structure decision`() {
        // Two candidates but only one passes the threshold => no structure.
        val raw = RawDetections(
            boxes = floatArrayOf(
                0.10f, 0.10f, 0.15f, 0.15f,
                0.80f, 0.80f, 0.85f, 0.85f,
            ),
            scores = floatArrayOf(0.9f, ConfidenceThresholds.MIN_DOT_DETECTION - 0.2f),
        )
        val out = DotDetectionPostProcessor.process(raw, w, h)
        assertEquals(1, out.dots.size)
        assertFalse(out.structureInferable)
    }

    @Test
    fun `xmin-ymin box format is decoded correctly`() {
        // Same geometry but coordinates ordered [xMin, yMin, xMax, yMax].
        val raw = RawDetections(
            boxes = floatArrayOf(0.40f, 0.30f, 0.60f, 0.50f),
            scores = floatArrayOf(0.9f),
            format = BoxFormat.XMIN_YMIN_XMAX_YMAX,
        )
        val out = DotDetectionPostProcessor.process(raw, w, h)
        val dot = out.dots.single()
        assertEquals(0.5f * (w - 1), dot.x, 1e-3f) // xCenter = 0.50
        assertEquals(0.4f * (h - 1), dot.y, 1e-3f) // yCenter = 0.40
    }

    @Test
    fun `a custom minimum confidence overrides the default threshold`() {
        val raw = oneBox(score = 0.30f)
        val kept = DotDetectionPostProcessor.process(raw, w, h, minConfidence = 0.25f)
        assertEquals(1, kept.dots.size)
        val dropped = DotDetectionPostProcessor.process(raw, w, h, minConfidence = 0.35f)
        assertTrue(dropped.dots.isEmpty())
    }
}

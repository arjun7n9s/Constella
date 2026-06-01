package com.constella.braille.runtime.detect

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.ScanningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for scanning-mode [DetectorParams] selection (Req 4.4, 9.3).
 *
 * These exercise the pure [DetectorParams.paramsFor] selector and the two
 * parameter sets. They cover Property 8 territory — *for any* selected
 * [ScanningMode] the params handed to subsequent scans are exactly the set
 * associated with that mode, and Handwritten supplies the irregular-spacing /
 * variable-depth set — using example-based assertions over the (small, total)
 * [ScanningMode] domain plus an exhaustive sweep of all enum values.
 *
 * The :runtime test classpath uses JUnit4 (see runtime/build.gradle.kts), so
 * these are plain JUnit4 tests; the property-style "for every mode" coverage is
 * achieved by iterating [ScanningMode.entries].
 */
class DetectorParamsTest {

    @Test
    fun `embossed mode selects the embossed parameter set`() {
        val params = DetectorParams.paramsFor(ScanningMode.EMBOSSED)
        // Same instance as the canonical embossed set (Req 9.3 — exactly that set).
        assertSame(DetectorParams.forEmbossed, params)
    }

    @Test
    fun `handwritten mode selects the handwritten parameter set`() {
        val params = DetectorParams.paramsFor(ScanningMode.HANDWRITTEN)
        assertSame(DetectorParams.forHandwritten, params)
    }

    @Test
    fun `each scanning mode maps to a distinct parameter set`() {
        val embossed = DetectorParams.paramsFor(ScanningMode.EMBOSSED)
        val handwritten = DetectorParams.paramsFor(ScanningMode.HANDWRITTEN)
        // Distinct tiers must not collapse to the same knobs (Req 4.4).
        assertNotEquals(embossed, handwritten)
    }

    @Test
    fun `selector is total and deterministic over every scanning mode`() {
        // Property 8: for ANY selected mode, the params are exactly the set
        // associated with that mode, and repeated selection is stable.
        for (mode in ScanningMode.entries) {
            val first = DetectorParams.paramsFor(mode)
            val second = DetectorParams.paramsFor(mode)
            assertSame("selection must be stable for $mode", first, second)

            val expected = when (mode) {
                ScanningMode.EMBOSSED -> DetectorParams.forEmbossed
                ScanningMode.HANDWRITTEN -> DetectorParams.forHandwritten
            }
            assertSame("wrong set selected for $mode", expected, first)
        }
    }

    @Test
    fun `handwritten set reflects irregular spacing and variable depth`() {
        val handwritten = DetectorParams.forHandwritten

        // Flags explicitly mark the irregular-spacing / variable-depth tier (Req 4.4).
        assertTrue(handwritten.toleratesIrregularSpacing)
        assertTrue(handwritten.toleratesVariableDepth)
    }

    @Test
    fun `embossed set assumes regular spacing and uniform depth`() {
        val embossed = DetectorParams.forEmbossed

        assertFalse(embossed.toleratesIrregularSpacing)
        assertFalse(embossed.toleratesVariableDepth)
    }

    @Test
    fun `handwritten tolerances are wider than embossed`() {
        val embossed = DetectorParams.forEmbossed
        val handwritten = DetectorParams.forHandwritten

        // Irregular spacing => wider spacing tolerance (Req 4.4).
        assertTrue(
            "handwritten spacing tolerance must exceed embossed",
            handwritten.spacingTolerance > embossed.spacingTolerance,
        )
        // Variable depth => wider depth tolerance (Req 4.4).
        assertTrue(
            "handwritten depth tolerance must exceed embossed",
            handwritten.depthTolerance > embossed.depthTolerance,
        )
        // Looser merge distance to absorb hand-punched jitter.
        assertTrue(
            "handwritten merge distance must exceed embossed",
            handwritten.mergeDistanceFactor > embossed.mergeDistanceFactor,
        )
    }

    @Test
    fun `handwritten accepts fainter dots via a lower confidence floor`() {
        val embossed = DetectorParams.forEmbossed
        val handwritten = DetectorParams.forHandwritten

        // Lower confidence floor for shallow hand-punched dots (Req 4.4),
        // consistent with the lower-confidence second tier (Req 9.5).
        assertTrue(
            "handwritten min confidence must be below embossed",
            handwritten.minDotConfidence < embossed.minDotConfidence,
        )
    }

    @Test
    fun `embossed uses the centralized baseline dot-detection threshold`() {
        assertEquals(
            ConfidenceThresholds.MIN_DOT_DETECTION,
            DetectorParams.forEmbossed.minDotConfidence,
            0f,
        )
    }

    @Test
    fun `every selected mode yields params with valid normalized confidence`() {
        for (mode in ScanningMode.entries) {
            val c = DetectorParams.paramsFor(mode).minDotConfidence
            assertTrue("minDotConfidence out of range for $mode: $c", c in 0f..1f)
        }
    }

    @Test
    fun `selected params drive post-processor confidence filtering`() {
        // Two detections: one just above the handwritten floor but below the
        // embossed floor, one above both. The selected mode's params decide
        // whether the borderline dot survives (Req 9.3 — mode params apply to
        // subsequent scans).
        val borderline = (DetectorParams.HANDWRITTEN_MIN_DOT_CONFIDENCE +
            ConfidenceThresholds.MIN_DOT_DETECTION) / 2f
        val raw = RawDetections(
            boxes = floatArrayOf(
                0.10f, 0.10f, 0.20f, 0.20f, // dot A
                0.50f, 0.50f, 0.60f, 0.60f, // dot B
            ),
            scores = floatArrayOf(borderline, 0.95f),
        )

        val embossedOut = DotDetectionPostProcessor.process(
            raw = raw,
            imageWidthPx = 100,
            imageHeightPx = 100,
            params = DetectorParams.forEmbossed,
        )
        val handwrittenOut = DotDetectionPostProcessor.process(
            raw = raw,
            imageWidthPx = 100,
            imageHeightPx = 100,
            params = DetectorParams.forHandwritten,
        )

        // Embossed floor rejects the borderline dot; handwritten floor keeps it.
        assertEquals(1, embossedOut.dots.size)
        assertEquals(2, handwrittenOut.dots.size)
    }
}

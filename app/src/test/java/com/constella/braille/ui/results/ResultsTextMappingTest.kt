package com.constella.braille.ui.results

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.CharSpan
import com.constella.braille.domain.model.Confidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * JVM unit + property tests for the pure uncertainty-segmentation mapping
 * ([segmentByUncertainty]) backing the Results composable. These run without
 * Compose so the threshold rule (chars below the display-confidence threshold
 * flagged; at/above not) is verified in isolation.
 *
 * _Requirements: 10.3_
 */
class ResultsTextMappingTest : StringSpec({

    val threshold = ConfidenceThresholds.DISPLAY_CONFIDENCE

    "characters below the display-confidence threshold are flagged uncertain" {
        // "world" (6..11) is below threshold; "hello " (0..6) is above.
        val text = "hello world"
        val spans = listOf(
            CharSpan(0, 6, listOf(0), Confidence(0.95f)),
            CharSpan(6, 11, listOf(1), Confidence(0.30f)),
        )

        val segments = segmentByUncertainty(text, spans, threshold)

        segments shouldBe listOf(
            TextSegment("hello ", isUncertain = false),
            TextSegment("world", isUncertain = true),
        )
    }

    "a span exactly at the threshold is treated as certain (strict less-than)" {
        val text = "abc"
        val spans = listOf(CharSpan(0, 3, listOf(0), Confidence(threshold)))

        val segments = segmentByUncertainty(text, spans, threshold)

        segments shouldBe listOf(TextSegment("abc", isUncertain = false))
    }

    "a span just below the threshold is flagged uncertain" {
        val text = "abc"
        val justBelow = Confidence((threshold - 0.01f).coerceIn(0f, 1f))
        val spans = listOf(CharSpan(0, 3, listOf(0), justBelow))

        val segments = segmentByUncertainty(text, spans, threshold)

        segments shouldBe listOf(TextSegment("abc", isUncertain = true))
    }

    "characters covered by no span default to certain" {
        val text = "abcdef"
        val spans = listOf(CharSpan(0, 2, listOf(0), Confidence(0.10f)))

        val segments = segmentByUncertainty(text, spans, threshold)

        segments shouldBe listOf(
            TextSegment("ab", isUncertain = true),
            TextSegment("cdef", isUncertain = false),
        )
    }

    "empty text yields no segments" {
        segmentByUncertainty("", emptyList(), threshold) shouldBe emptyList()
    }

    "out-of-range spans are clamped and never crash" {
        val text = "abc"
        // endIndex beyond length and an entirely out-of-range span.
        val spans = listOf(
            CharSpan(2, 99, listOf(0), Confidence(0.10f)),
        )

        val segments = segmentByUncertainty(text, spans, threshold)

        // Only index 2 is in range and below threshold.
        segments shouldBe listOf(
            TextSegment("ab", isUncertain = false),
            TextSegment("c", isUncertain = true),
        )
    }

    "any covering low-confidence span marks the character uncertain (overlap)" {
        val text = "abcd"
        val spans = listOf(
            CharSpan(0, 4, listOf(0), Confidence(0.95f)), // high over whole word
            CharSpan(1, 3, listOf(1), Confidence(0.20f)), // low overlaps b,c
        )

        val segments = segmentByUncertainty(text, spans, threshold)

        segments shouldBe listOf(
            TextSegment("a", isUncertain = false),
            TextSegment("bc", isUncertain = true),
            TextSegment("d", isUncertain = false),
        )
    }

    // ---- Property: segmentation is lossless and the flag matches the threshold ----

    val confidenceArb: Arb<Confidence> = Arb.double(0.0, 1.0).map { Confidence(it.toFloat()) }

    "PROPERTY: concatenating segments reconstructs the original text" {
        checkAll(
            Arb.string(0, 40),
            Arb.list(spanArb(confidenceArb), 0..8),
        ) { text, rawSpans ->
            val spans = clampSpansTo(text, rawSpans)
            val segments = segmentByUncertainty(text, spans, threshold)
            segments.joinToString("") { it.text } shouldBe text
        }
    }

    "PROPERTY: a char is uncertain iff some covering span is below the threshold" {
        checkAll(
            Arb.string(1, 40),
            Arb.list(spanArb(confidenceArb), 0..8),
        ) { text, rawSpans ->
            val spans = clampSpansTo(text, rawSpans)
            val segments = segmentByUncertainty(text, spans, threshold)

            // Expand segments back to a per-character flag array.
            val actual = BooleanArray(text.length)
            var idx = 0
            for (seg in segments) {
                repeat(seg.text.length) { actual[idx++] = seg.isUncertain }
            }

            // Independently recompute the expected per-char flag.
            for (i in text.indices) {
                val expected = spans.any { span ->
                    span.confidence.value < threshold && i >= span.startIndex && i < span.endIndex
                }
                if (expected) actual[i].shouldBeTrue() else actual[i].shouldBeFalse()
            }
        }
    }

    "PROPERTY: adjacent segments never share the same uncertainty flag (minimal runs)" {
        checkAll(
            Arb.string(1, 40),
            Arb.list(spanArb(confidenceArb), 0..8),
        ) { text, rawSpans ->
            val spans = clampSpansTo(text, rawSpans)
            val segments = segmentByUncertainty(text, spans, threshold)
            for (i in 1 until segments.size) {
                (segments[i].isUncertain == segments[i - 1].isUncertain).shouldBeFalse()
            }
        }
    }
})

/** Intermediate raw span (a start offset + length) before clamping to a text length. */
private data class RawSpan(val start: Int, val len: Int, val confidence: Confidence)

/** Arb producing a raw span with non-negative start/length and a [0,1] confidence. */
private fun spanArb(confidenceArb: Arb<Confidence>): Arb<RawSpan> =
    Arb.bind(Arb.int(0, 50), Arb.int(0, 20), confidenceArb) { start, len, conf ->
        RawSpan(start, len, conf)
    }

/** Clamp raw spans into valid CharSpans for [text] (half-open, within bounds). */
private fun clampSpansTo(text: String, raw: List<RawSpan>): List<CharSpan> =
    raw.map { r ->
        val start = r.start.coerceIn(0, text.length)
        val end = (start + r.len).coerceIn(start, text.length)
        CharSpan(start, end, listOf(0), r.confidence)
    }

package com.constella.braille.domain.accuracy

import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.RecognizedCell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Unit (example/edge-case) tests for [AccuracyMetrics].
 *
 * These cover concrete examples and the edge cases called out by Req 13.7 and
 * the design (identical strings, empty strings, one-side-empty). The universal
 * property — accuracy == 1 - CER, in [0,1], for *any* pair of inputs — is left
 * to the Property 23 property test (task 14.2).
 */
class AccuracyMetricsTest : StringSpec({

    val tolerance = 1e-9

    // --- characterAccuracy edge cases (Req 13.7 / Property 23) ---------------

    "identical non-empty strings have character accuracy 1.0" {
        AccuracyMetrics.characterAccuracy("hello world", "hello world") shouldBe
            (1.0 plusOrMinus tolerance)
    }

    "two empty strings have character accuracy 1.0 (nothing to get wrong)" {
        AccuracyMetrics.characterAccuracy("", "") shouldBe (1.0 plusOrMinus tolerance)
    }

    "ground truth empty but recognized non-empty has character accuracy 0.0" {
        AccuracyMetrics.characterAccuracy("spurious", "") shouldBe (0.0 plusOrMinus tolerance)
    }

    "recognized empty but ground truth non-empty has character accuracy 0.0" {
        AccuracyMetrics.characterAccuracy("", "expected") shouldBe (0.0 plusOrMinus tolerance)
    }

    "single substitution over four characters yields 0.75 accuracy" {
        // "abcd" -> "abxd": one substitution, max length 4, CER = 0.25.
        AccuracyMetrics.characterAccuracy("abxd", "abcd") shouldBe (0.75 plusOrMinus tolerance)
    }

    "insertion error is normalized by the longer length" {
        // "kitten" -> "kittens": one insertion, max length 7, CER = 1/7.
        AccuracyMetrics.characterAccuracy("kittens", "kitten") shouldBe
            ((1.0 - 1.0 / 7.0) plusOrMinus tolerance)
    }

    "classic kitten/sitting distance gives expected accuracy" {
        // Levenshtein("kitten","sitting") = 3, max length 7, CER = 3/7.
        AccuracyMetrics.characterAccuracy("kitten", "sitting") shouldBe
            ((1.0 - 3.0 / 7.0) plusOrMinus tolerance)
    }

    "completely different strings of equal length have accuracy 0.0" {
        AccuracyMetrics.characterAccuracy("aaaa", "bbbb") shouldBe (0.0 plusOrMinus tolerance)
    }

    "character accuracy is symmetric in its arguments" {
        val forward = AccuracyMetrics.characterAccuracy("recognized", "groundtruth")
        val backward = AccuracyMetrics.characterAccuracy("groundtruth", "recognized")
        forward shouldBe (backward plusOrMinus tolerance)
    }

    "character error rate is one minus character accuracy" {
        val cer = AccuracyMetrics.characterErrorRate("abxd", "abcd")
        cer shouldBe (0.25 plusOrMinus tolerance)
    }

    // --- cellAccuracy edge cases ---------------------------------------------

    "identical cell sequences have cell accuracy 1.0" {
        val cells = listOf(dots(1), dots(1, 2), dots(1, 2, 3))
        AccuracyMetrics.cellAccuracy(cells, cells) shouldBe (1.0 plusOrMinus tolerance)
    }

    "two empty cell sequences have cell accuracy 1.0" {
        AccuracyMetrics.cellAccuracy(emptyList(), emptyList()) shouldBe
            (1.0 plusOrMinus tolerance)
    }

    "empty ground truth with non-empty recognized cells has cell accuracy 0.0" {
        AccuracyMetrics.cellAccuracy(listOf(dots(1), dots(2)), emptyList()) shouldBe
            (0.0 plusOrMinus tolerance)
    }

    "one wrong cell out of four yields 0.75 cell accuracy" {
        val groundTruth = listOf(dots(1), dots(2), dots(3), dots(4))
        val recognized = listOf(dots(1), dots(2), dots(6), dots(4)) // third cell wrong
        AccuracyMetrics.cellAccuracy(recognized, groundTruth) shouldBe
            (0.75 plusOrMinus tolerance)
    }

    "cells differing only by raised-dot set count as errors" {
        AccuracyMetrics.cellAccuracy(listOf(dots(1, 2)), listOf(dots(1, 2, 3))) shouldBe
            (0.0 plusOrMinus tolerance)
    }

    // --- cellAccuracyOfRecognized overload -----------------------------------

    "recognized-cell overload compares only the dot pattern" {
        val groundTruth = listOf(dots(1), dots(2), dots(3))
        val recognized = listOf(
            recognized(dots(1), confidence = 0.9f, uncertain = false),
            recognized(dots(2), confidence = 0.2f, uncertain = true),  // low confidence, still correct pattern
            recognized(dots(3), confidence = 0.95f, uncertain = false),
        )
        AccuracyMetrics.cellAccuracyOfRecognized(recognized, groundTruth) shouldBe
            (1.0 plusOrMinus tolerance)
    }
})

private fun dots(vararg raised: Int): BrailleDots = BrailleDots(raised.toSet())

private fun recognized(
    dots: BrailleDots,
    confidence: Float,
    uncertain: Boolean,
): RecognizedCell = RecognizedCell(
    source = BrailleCell(
        dots = emptyList(),
        boundingBox = BoundingBox(0f, 0f, 1f, 1f),
        centerY = 0.5f,
        validGrid = true,
        confidence = Confidence(confidence),
    ),
    dots = dots,
    confidence = Confidence(confidence),
    uncertain = uncertain,
)

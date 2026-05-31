package com.constella.braille.domain.recognize

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import com.constella.braille.domain.model.SegmentedDocument
import com.constella.braille.domain.model.TextLine
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Example-based unit tests for [DefaultPatternRecognizer] (task 9.1).
 *
 * These verify specific recognition behaviours and edge cases. The numbered
 * Correctness Properties 14 and 15 are implemented separately (tasks 9.2, 9.3).
 */
class DefaultPatternRecognizerTest : StringSpec({

    val recognizer = DefaultPatternRecognizer()

    // A 2x3 grid cell, 10 wide x 30 tall at the origin. The six position centers
    // are at column x = 2.5 (left) / 7.5 (right) and row y = 5 / 15 / 25.
    fun gridBox() = BoundingBox(left = 0f, top = 0f, right = 10f, bottom = 30f)

    fun dotAt(x: Float, y: Float, confidence: Float = 1f) =
        DetectedDot(x = x, y = y, radius = 1f, confidence = Confidence.of(confidence))

    fun cellOf(
        dots: List<DetectedDot>,
        validGrid: Boolean = true,
        confidence: Float = 1f,
        box: BoundingBox = gridBox(),
    ) = BrailleCell(
        dots = dots,
        boundingBox = box,
        centerY = box.centerY,
        validGrid = validGrid,
        confidence = Confidence.of(confidence),
    )

    fun docOf(vararg cells: BrailleCell) =
        SegmentedDocument(listOf(TextLine(cells.toList())))

    "maps each of the six grid positions to its standard Braille number" {
        // One dot placed at the center of each of the six positions.
        val cell = cellOf(
            listOf(
                dotAt(2.5f, 5f),   // position 1: left column, top row
                dotAt(2.5f, 15f),  // position 2: left column, middle row
                dotAt(2.5f, 25f),  // position 3: left column, bottom row
                dotAt(7.5f, 5f),   // position 4: right column, top row
                dotAt(7.5f, 15f),  // position 5: right column, middle row
                dotAt(7.5f, 25f),  // position 6: right column, bottom row
            ),
        )

        val recognized = recognizer.recognize(docOf(cell)).single()

        recognized.dots.raised shouldBe setOf(1, 2, 3, 4, 5, 6)
    }

    "maps a partial pattern (the letter 'b' = dots 1,2) correctly" {
        val cell = cellOf(listOf(dotAt(2.5f, 5f), dotAt(2.5f, 15f)))

        val recognized = recognizer.recognize(docOf(cell)).single()

        recognized.dots.raised shouldBe setOf(1, 2)
    }

    "a blank cell with no dots yields the empty pattern" {
        val recognized = recognizer.recognize(docOf(cellOf(emptyList()))).single()

        recognized.dots.raised shouldBe emptySet()
    }

    "an empty document yields an empty list" {
        recognizer.recognize(SegmentedDocument.EMPTY) shouldHaveSize 0
    }

    "preserves reading order across lines and cells" {
        val line1 = TextLine(
            listOf(
                cellOf(listOf(dotAt(2.5f, 5f))),                  // {1}
                cellOf(listOf(dotAt(2.5f, 5f), dotAt(2.5f, 15f))), // {1,2}
            ),
        )
        val line2 = TextLine(listOf(cellOf(listOf(dotAt(7.5f, 25f))))) // {6}
        val doc = SegmentedDocument(listOf(line1, line2))

        val patterns = recognizer.recognize(doc).map { it.dots.raised }

        patterns shouldContainExactly listOf(setOf(1), setOf(1, 2), setOf(6))
    }

    "aggregates dot confidence with grid-fit quality multiplicatively" {
        // Two dots at confidence 0.8, grid-fit quality 0.9 -> 0.8 * 0.9 = 0.72.
        val cell = cellOf(
            dots = listOf(dotAt(2.5f, 5f, 0.8f), dotAt(2.5f, 15f, 0.8f)),
            confidence = 0.9f,
        )

        val recognized = recognizer.recognize(docOf(cell)).single()

        recognized.confidence.value shouldBe (0.72f plusOrMinus 1e-6f)
    }

    "a blank cell's confidence is the grid-fit quality alone" {
        val recognized = recognizer.recognize(docOf(cellOf(emptyList(), confidence = 0.85f))).single()

        recognized.confidence.value shouldBe (0.85f plusOrMinus 1e-6f)
    }

    "flags a cell as uncertain exactly when confidence is below the cell-confidence threshold" {
        // High-confidence cell: 1.0 * 1.0 = 1.0 >= threshold -> certain.
        val confident = recognizer.recognize(docOf(cellOf(listOf(dotAt(2.5f, 5f))))).single()
        confident.uncertain.shouldBeFalse()

        // Degraded region: sub-threshold grid-fit quality drives uncertainty.
        val belowThreshold = ConfidenceThresholds.CELL_CONFIDENCE - 0.1f
        val degraded = recognizer.recognize(
            docOf(cellOf(listOf(dotAt(2.5f, 5f)), validGrid = false, confidence = belowThreshold)),
        ).single()
        degraded.uncertain.shouldBeTrue()
        degraded.confidence.value shouldBe (belowThreshold plusOrMinus 1e-6f)
    }

    "retains the source cell on each recognized cell" {
        val cell = cellOf(listOf(dotAt(7.5f, 15f)))

        val recognized = recognizer.recognize(docOf(cell)).single()

        recognized.source shouldBe cell
    }
})

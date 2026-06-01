package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Unit + property tests for the untranslatable-cell surfacing layer
 * ([UntranslatableReporter] / [UntranslatableReport]) and the additive
 * pass-through detection seam ([UntranslatableDetection]) — Req 7.5.
 *
 * The surfacing layer is exercised end-to-end through the in-memory Grade 1
 * fake engine (which already flags unknown patterns as untranslatable) using
 * the [recognizedCellsOf] helper, so no liblouis `.so` is required.
 */
class UntranslatableReportTest : StringSpec({

    val engine = InMemoryGrade1TranslationEngine()

    // A pattern that is not in the Grade 1 alphabet, hence untranslatable.
    val unknown = BrailleDots(setOf(3, 6))

    // --- renderPattern (raw dot-number helper) -------------------------------

    "renderPattern renders raised dots as an ascending dot-number string" {
        UntranslatableReporter.renderPattern(BrailleDots(setOf(1, 3, 5))) shouldBe "1-3-5"
        UntranslatableReporter.renderPattern(BrailleDots(setOf(5, 3, 1))) shouldBe "1-3-5"
        UntranslatableReporter.renderPattern(BrailleDots(setOf(2))) shouldBe "2"
    }

    "renderPattern renders the empty pattern as 'blank'" {
        UntranslatableReporter.renderPattern(BrailleDots.EMPTY) shouldBe
            UntranslatableReporter.BLANK_PATTERN
    }

    // --- report (surfacing layer) --------------------------------------------

    "report is NONE when nothing is untranslatable" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("cab"))
        val output = engine.translate(cells, Grade.GRADE_1)

        val report = UntranslatableReporter.report(output, cells)

        report shouldBe UntranslatableReport.NONE
        report.hasUntranslatableCells shouldBe false
    }

    "report surfaces each untranslatable cell with its raw pattern and a message" {
        val cells = recognizedCellsOf(
            listOf(Grade1Alphabet.toDots('a')!!, unknown, Grade1Alphabet.toDots('b')!!),
        )
        val output = engine.translate(cells, Grade.GRADE_1)

        val report = UntranslatableReporter.report(output, cells)

        // Reported indices equal the engine's untranslatableCells exactly.
        report.cellIndices shouldContainExactly output.untranslatableCells
        report.cellIndices shouldContainExactly listOf(1)

        val entry = report.cells.single()
        entry.cellIndex shouldBe 1
        entry.dots shouldBe unknown
        entry.rawPattern shouldBe "3-6"
        // The per-cell message names the one-based position, the raw pattern,
        // and states it could not be translated.
        entry.message shouldContain "Cell 2"
        entry.message shouldContain "3-6"
        entry.message shouldContain "could not be translated"
    }

    "report summary names the count and lists the raw patterns" {
        val cells = recognizedCellsOf(
            listOf(unknown, Grade1Alphabet.toDots('a')!!, unknown),
        )
        val output = engine.translate(cells, Grade.GRADE_1)

        val report = UntranslatableReporter.report(output, cells)

        report.cellIndices shouldContainExactly listOf(0, 2)
        report.summary shouldContain "2 cells"
        report.summary shouldContain "could not be translated"
        report.summary shouldContain "3-6"
    }

    "report tolerates an index without a matching cell defensively" {
        // Hand-built output whose untranslatable index has no recognized cell.
        val output = TranslationOutput(text = "", charSpans = emptyList(), untranslatableCells = listOf(5))

        val report = UntranslatableReporter.report(output, cells = emptyList())

        report.cellIndices shouldContainExactly listOf(5)
        val entry = report.cells.single()
        entry.dots shouldBe null
        entry.rawPattern shouldBe UntranslatableReporter.UNKNOWN_PATTERN
        entry.message shouldContain "could not be translated"
    }

    // --- UntranslatableDetection seam ----------------------------------------

    "detection flags a cell whose every output char is a pass-through Braille char" {
        // cell 0 -> "a" (translated), cell 1 -> echoed Braille char (untranslatable).
        val echoed = BrailleUnicode.dotsToChar(unknown)
        val outputText = "a$echoed"
        val inputPositions = intArrayOf(0, 1)

        UntranslatableDetection.untranslatableCellIndices(outputText, inputPositions) shouldContainExactly
            listOf(1)
    }

    "detection treats a cell with any real text as translated" {
        // cell 0 produced both an echoed braille char and a real letter -> translated.
        val echoed = BrailleUnicode.dotsToChar(unknown)
        val outputText = "${echoed}x"
        val inputPositions = intArrayOf(0, 0)

        UntranslatableDetection.untranslatableCellIndices(outputText, inputPositions) shouldContainExactly
            emptyList()
    }

    "detection returns sorted, de-duplicated indices" {
        val e = BrailleUnicode.dotsToChar(unknown)
        // cells 2 and 0 are fully echoed (each across two chars), cell 1 is text.
        val outputText = "${e}${e}b${e}${e}"
        val inputPositions = intArrayOf(2, 2, 1, 0, 0)

        UntranslatableDetection.untranslatableCellIndices(outputText, inputPositions) shouldContainExactly
            listOf(0, 2)
    }

    "isPassthroughBrailleChar recognizes only the U+2800..U+283F block" {
        UntranslatableDetection.isPassthroughBrailleChar('\u2800') shouldBe true
        UntranslatableDetection.isPassthroughBrailleChar('\u283F') shouldBe true
        UntranslatableDetection.isPassthroughBrailleChar('a') shouldBe false
        UntranslatableDetection.isPassthroughBrailleChar('\u27FF') shouldBe false
        UntranslatableDetection.isPassthroughBrailleChar('\u2840') shouldBe false
    }

    // --- Property: untranslatable cells are reported exactly (Property 17) ----

    "Feature: braille-scanner, Property 17: report surfaces exactly the untranslatable cells with raw patterns and messages" {
        val translatable = ('a'..'z').map { Grade1Alphabet.toDots(it)!! }
        val tokenArb: Arb<BrailleDots> = Arb.element(translatable + unknown)

        checkAll(200, Arb.list(tokenArb, 0..12)) { tokens ->
            val cells = recognizedCellsOf(tokens, Confidence.ONE)
            val output = engine.translate(cells, Grade.GRADE_1)
            val report = UntranslatableReporter.report(output, cells)

            // The reported set equals exactly the cells the engine could not translate.
            report.cellIndices shouldContainExactly output.untranslatableCells

            val expected = tokens.withIndex().filter { it.value == unknown }.map { it.index }
            report.cellIndices shouldContainExactly expected

            // Each reported cell carries its raw pattern and a "could not be
            // translated" message; the summary reflects presence/absence.
            for (entry in report.cells) {
                entry.dots shouldBe unknown
                entry.rawPattern shouldBe UntranslatableReporter.renderPattern(unknown)
                entry.message shouldContain entry.rawPattern
                entry.message shouldContain "could not be translated"
            }
            report.hasUntranslatableCells shouldBe expected.isNotEmpty()
            if (expected.isEmpty()) {
                report shouldBe UntranslatableReport.NONE
            } else {
                report.summary shouldContain "could not be translated"
            }
        }
    }

    "Feature: braille-scanner, Property 17: detection reports exactly the fully-echoed cells" {
        val translatable = ('a'..'z').map { Grade1Alphabet.toDots(it)!! }
        // Token tag: true => cell that liblouis echoed (untranslatable),
        // false => cell that produced a real letter.
        val tokenArb: Arb<Boolean> = Arb.element(listOf(true, false))

        checkAll(200, Arb.list(tokenArb, 0..12)) { tokens ->
            val sb = StringBuilder()
            val positions = ArrayList<Int>()
            val expected = ArrayList<Int>()
            tokens.forEachIndexed { index, echoed ->
                if (echoed) {
                    sb.append(BrailleUnicode.dotsToChar(unknown))
                    expected.add(index)
                } else {
                    sb.append(Grade1Alphabet.toChar(translatable[index % translatable.size])!!)
                }
                positions.add(index)
            }

            UntranslatableDetection.untranslatableCellIndices(
                outputText = sb.toString(),
                inputPositions = positions.toIntArray(),
            ) shouldContainExactly expected
        }
    }
})

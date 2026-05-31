package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Tests for the deterministic translation pipeline using the in-memory Grade 1
 * fake (no liblouis). These cover the char-span round-trip (Property 16-style)
 * and untranslatable-cell reporting (Property 17-style) end to end through the
 * [TranslationEngine] contract.
 */
class InMemoryGrade1TranslationEngineTest : StringSpec({

    val engine = InMemoryGrade1TranslationEngine()

    "translates Grade 1 cells to the expected text with one span per cell" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("cab"))
        val output = engine.translate(cells, Grade.GRADE_1)

        output.text shouldBe "cab"
        output.charSpans.size shouldBe 3
        output.charSpans.map { it.cellRefs } shouldContainExactly listOf(
            listOf(0), listOf(1), listOf(2),
        )
        output.untranslatableCells shouldBe emptyList()
    }

    "empty cell list yields empty output" {
        engine.translate(emptyList(), Grade.GRADE_1) shouldBe TranslationOutput.EMPTY
    }

    "reports cells with unknown patterns as untranslatable" {
        // Position 6-only is not in the Grade 1 alphabet, so it is untranslatable.
        val unknown = BrailleDots(setOf(6))
        val cells = recognizedCellsOf(
            listOf(Grade1Alphabet.toDots('a')!!, unknown, Grade1Alphabet.toDots('b')!!),
        )
        val output = engine.translate(cells, Grade.GRADE_1)

        output.text shouldBe "ab"
        output.untranslatableCells shouldContainExactly listOf(1)
    }

    "Feature: braille-scanner, Property 16: Grade 1 round-trip preserves text" {
        val words: Arb<String> = Arb.list(Arb.element(('a'..'z').toList()), 1..10)
            .map { chars -> chars.joinToString("") }
        checkAll(100, words) { text ->
            val cells = recognizedCellsOf(Grade1Alphabet.encode(text))
            val output = engine.translate(cells, Grade.GRADE_1)
            output.text shouldBe text
        }
    }

    "Feature: braille-scanner, Property 17: untranslatable cells are reported exactly via the engine" {
        // Mix translatable letters with an unknown pattern; track expected indices.
        val unknown = BrailleDots(setOf(3, 6))
        val translatable = ('a'..'z').map { Grade1Alphabet.toDots(it)!! }
        val tokenArb: Arb<BrailleDots> = Arb.element(translatable + unknown)

        checkAll(100, Arb.list(tokenArb, 0..10)) { tokens ->
            val cells = recognizedCellsOf(tokens, Confidence.ONE)
            val output = engine.translate(cells, Grade.GRADE_1)

            val expected = tokens.withIndex()
                .filter { it.value == unknown }
                .map { it.index }
            output.untranslatableCells shouldContainExactly expected
        }
    }
})

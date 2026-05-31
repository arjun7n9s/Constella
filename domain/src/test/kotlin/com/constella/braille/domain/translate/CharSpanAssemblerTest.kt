package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Confidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Unit + property tests for [CharSpanAssembler], the deterministic core of the
 * Translation_Engine's char-span construction (Req 7.2, 7.5, 10.3).
 *
 * These validate the char-span invariants without any liblouis dependency.
 */
class CharSpanAssemblerTest : StringSpec({

    "assembles concatenated text from translatable segments" {
        val output = CharSpanAssembler.assemble(
            listOf(
                seg(listOf(0), "he"),
                seg(listOf(1), "l"),
                seg(listOf(2), "lo"),
            ),
        )
        output.text shouldBe "hello"
        output.untranslatableCells shouldBe emptyList()
    }

    "produces one span per translatable segment mapping back to source cells" {
        val output = CharSpanAssembler.assemble(
            listOf(
                seg(listOf(0), "he"),
                seg(listOf(1, 2), "llo"),
            ),
        )
        output.charSpans.size shouldBe 2
        output.charSpans[0].startIndex shouldBe 0
        output.charSpans[0].endIndex shouldBe 2
        output.charSpans[0].cellRefs shouldContainExactly listOf(0)
        output.charSpans[1].startIndex shouldBe 2
        output.charSpans[1].endIndex shouldBe 5
        output.charSpans[1].cellRefs shouldContainExactly listOf(1, 2)
    }

    "untranslatable segments contribute no text and are aggregated, sorted and de-duplicated" {
        val output = CharSpanAssembler.assemble(
            listOf(
                seg(listOf(0), "a"),
                untranslatable(listOf(3, 1)),
                seg(listOf(2), "b"),
                untranslatable(listOf(1)),
            ),
        )
        output.text shouldBe "ab"
        output.untranslatableCells shouldContainExactly listOf(1, 3)
        // No char span is emitted for untranslatable segments.
        output.charSpans.size shouldBe 2
    }

    "empty segment list yields empty output" {
        CharSpanAssembler.assemble(emptyList()) shouldBe TranslationOutput.EMPTY
    }

    // --- Property tests ------------------------------------------------------

    "Feature: braille-scanner, Property 16: char spans tile the output text contiguously" {
        checkAll(100, arbSegments()) { segments ->
            val output = CharSpanAssembler.assemble(segments)

            // Spans are contiguous, non-overlapping, and exactly tile [0, text.length).
            var cursor = 0
            for (span in output.charSpans) {
                span.startIndex shouldBe cursor
                cursor = span.endIndex
            }
            cursor shouldBe output.text.length

            // Concatenating covered substrings reproduces the text.
            val rebuilt = buildString {
                for (span in output.charSpans) {
                    append(output.text.substring(span.startIndex, span.endIndex))
                }
            }
            rebuilt shouldBe output.text
        }
    }

    "Feature: braille-scanner, Property 17: untranslatable cells are reported exactly" {
        checkAll(100, arbSegments()) { segments ->
            val output = CharSpanAssembler.assemble(segments)

            val expected = segments
                .filterNot { it.translatable }
                .flatMap { it.cellRefs }
                .toSortedSet()
                .toList()
            output.untranslatableCells shouldContainExactly expected

            // Translatable cell refs are never reported as untranslatable
            // unless that same cell also appears in an untranslatable segment.
            output.charSpans.size shouldBe segments.count { it.translatable }
        }
    }
})

private fun seg(cellRefs: List<Int>, text: String): TranslationSegment =
    TranslationSegment(cellRefs, text, Confidence.ONE, translatable = true)

private fun untranslatable(cellRefs: List<Int>): TranslationSegment =
    TranslationSegment(cellRefs, "", Confidence.ZERO, translatable = false)

private fun arbSegments(): Arb<List<TranslationSegment>> =
    Arb.list(arbSegment(), 0..12)

private fun arbSegment(): Arb<TranslationSegment> =
    Arb.bind(
        Arb.list(Arb.int(0..20), 1..3),
        Arb.boolean(),
        Arb.string(0..4),
    ) { cellRefs, isTranslatable, text ->
        TranslationSegment(
            cellRefs = cellRefs,
            text = if (isTranslatable) text else "",
            confidence = Confidence.ONE,
            translatable = isTranslatable,
        )
    }

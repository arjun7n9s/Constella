package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Confidence
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Tests for [BackTranslationMapper], which groups liblouis's per-output-character
 * source mapping into segments — the bridge between a (Grade 2-capable) native
 * back-translation and the char-span assembler (Req 7.2, 10.3).
 */
class BackTranslationMapperTest : StringSpec({

    "one-to-one mapping yields one segment per character" {
        val segments = BackTranslationMapper.toSegments(
            outputText = "cat",
            inputPositions = intArrayOf(0, 1, 2),
            cellConfidences = listOf(Confidence.ONE, Confidence.ONE, Confidence.ONE),
        )
        segments.map { it.text } shouldContainExactly listOf("c", "a", "t")
        segments.map { it.cellRefs } shouldContainExactly listOf(listOf(0), listOf(1), listOf(2))
    }

    "a contraction (one cell -> several characters) collapses into a single segment" {
        // Source cell 0 expands to "the" (Grade 2 'the' contraction); cell 1 -> 're'.
        val segments = BackTranslationMapper.toSegments(
            outputText = "there",
            inputPositions = intArrayOf(0, 0, 0, 1, 1),
            cellConfidences = listOf(Confidence.ONE, Confidence.ONE),
        )
        segments.size shouldBe 2
        segments[0].text shouldBe "the"
        segments[0].cellRefs shouldContainExactly listOf(0)
        segments[1].text shouldBe "re"
        segments[1].cellRefs shouldContainExactly listOf(1)
    }

    "segments carry the source cell's confidence" {
        val low = Confidence(0.25f)
        val segments = BackTranslationMapper.toSegments(
            outputText = "ab",
            inputPositions = intArrayOf(0, 1),
            cellConfidences = listOf(Confidence.ONE, low),
        )
        segments[0].confidence shouldBe Confidence.ONE
        segments[1].confidence shouldBe low
    }

    "empty output yields no segments" {
        BackTranslationMapper.toSegments("", IntArray(0), emptyList()) shouldBe emptyList()
    }

    "mismatched position-array length is rejected" {
        shouldThrow<IllegalArgumentException> {
            BackTranslationMapper.toSegments("ab", intArrayOf(0), listOf(Confidence.ONE))
        }
    }

    "Feature: braille-scanner, Property 16: assembled text equals back-translation output" {
        // Model each source cell as a chunk of 0..3 output characters. This yields
        // an output text plus a non-decreasing source-position array (a cell may
        // expand to several characters, or to none), exactly liblouis's shape.
        val arbChunks: Arb<List<List<Char>>> =
            Arb.list(Arb.list(Arb.element(('a'..'z').toList()), 0..3), 0..8)

        checkAll(100, arbChunks) { chunks ->
            val text = buildString { chunks.forEach { chunk -> chunk.forEach { append(it) } } }
            val positions = ArrayList<Int>()
            chunks.forEachIndexed { cellIndex, chunk ->
                repeat(chunk.size) { positions.add(cellIndex) }
            }
            val confidences = List(chunks.size) { Confidence.ONE }

            val segments =
                BackTranslationMapper.toSegments(text, positions.toIntArray(), confidences)
            val output = CharSpanAssembler.assemble(segments)

            output.text shouldBe text
            var cursor = 0
            for (span in output.charSpans) {
                span.startIndex shouldBe cursor
                cursor = span.endIndex
            }
            cursor shouldBe text.length
        }
    }
})

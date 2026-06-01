package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Unit + property tests for [UntranslatableFormatter] — the pure display-
 * formatting utility for untranslatable Braille cells (Req 7.5).
 *
 * These tests verify that:
 *  - [UntranslatableFormatter.formatPattern] renders raw [BrailleDots] patterns
 *    as human-readable dot-number strings for display and TTS.
 *  - [UntranslatableFormatter.formatMessage] produces a complete "could not
 *    translate" message naming the one-based cell position and the raw pattern.
 *  - [UntranslatableFormatter.formatSummary] produces a correct summary sentence
 *    for a collection of untranslatable cells.
 *
 * _Requirements: 7.5_
 */
class UntranslatableFormatterTest : StringSpec({

    // -------------------------------------------------------------------------
    // formatPattern
    // -------------------------------------------------------------------------

    "formatPattern renders a single raised dot as 'dots N'" {
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(1))) shouldBe "dots 1"
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(4))) shouldBe "dots 4"
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(6))) shouldBe "dots 6"
    }

    "formatPattern renders multiple raised dots in ascending order separated by dashes" {
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(1, 3, 5))) shouldBe "dots 1-3-5"
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(5, 3, 1))) shouldBe "dots 1-3-5"
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(2, 4, 6))) shouldBe "dots 2-4-6"
        UntranslatableFormatter.formatPattern(BrailleDots(setOf(1, 2, 3, 4, 5, 6))) shouldBe "dots 1-2-3-4-5-6"
    }

    "formatPattern renders the empty pattern as the blank-cell label" {
        UntranslatableFormatter.formatPattern(BrailleDots.EMPTY) shouldBe
            UntranslatableFormatter.BLANK_LABEL
    }

    "formatPattern always starts with the DOTS_PREFIX for non-empty patterns" {
        val nonEmpty = BrailleDots(setOf(1, 2))
        UntranslatableFormatter.formatPattern(nonEmpty).startsWith(
            UntranslatableFormatter.DOTS_PREFIX,
        ) shouldBe true
    }

    // -------------------------------------------------------------------------
    // formatMessage
    // -------------------------------------------------------------------------

    "formatMessage uses one-based cell position in the message" {
        // cellIndex 0 -> "Cell 1"
        val msg = UntranslatableFormatter.formatMessage(0, BrailleDots(setOf(3, 6)))
        msg shouldContain "Cell 1"
    }

    "formatMessage uses one-based cell position for higher indices" {
        val msg = UntranslatableFormatter.formatMessage(4, BrailleDots(setOf(1)))
        msg shouldContain "Cell 5"
    }

    "formatMessage includes the raw pattern in the message" {
        val msg = UntranslatableFormatter.formatMessage(0, BrailleDots(setOf(3, 6)))
        msg shouldContain "3-6"
    }

    "formatMessage includes the COULD_NOT_TRANSLATE phrase" {
        val msg = UntranslatableFormatter.formatMessage(0, BrailleDots(setOf(1, 2)))
        msg shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
    }

    "formatMessage for blank cell includes the blank-cell label" {
        val msg = UntranslatableFormatter.formatMessage(2, BrailleDots.EMPTY)
        msg shouldContain UntranslatableFormatter.BLANK_LABEL
        msg shouldContain "Cell 3"
        msg shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
    }

    "formatMessage ends with a period" {
        val msg = UntranslatableFormatter.formatMessage(0, BrailleDots(setOf(1)))
        msg.endsWith(".") shouldBe true
    }

    // -------------------------------------------------------------------------
    // formatSummary
    // -------------------------------------------------------------------------

    "formatSummary returns empty string for empty entries" {
        UntranslatableFormatter.formatSummary(emptyList()) shouldBe ""
    }

    "formatSummary for a single cell uses singular 'cell'" {
        val summary = UntranslatableFormatter.formatSummary(
            listOf(0 to BrailleDots(setOf(3, 6))),
        )
        summary shouldContain "1 cell"
        summary shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
        summary shouldContain "3-6"
    }

    "formatSummary for multiple cells uses plural 'cells'" {
        val summary = UntranslatableFormatter.formatSummary(
            listOf(
                0 to BrailleDots(setOf(1, 2)),
                2 to BrailleDots(setOf(3, 6)),
            ),
        )
        summary shouldContain "2 cells"
        summary shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
        summary shouldContain "1-2"
        summary shouldContain "3-6"
    }

    "formatSummary includes one-based positions for all entries" {
        val summary = UntranslatableFormatter.formatSummary(
            listOf(
                0 to BrailleDots(setOf(1)),
                3 to BrailleDots(setOf(4)),
            ),
        )
        summary shouldContain "Cell 1"
        summary shouldContain "Cell 4"
    }

    "formatSummary ends with a period" {
        val summary = UntranslatableFormatter.formatSummary(
            listOf(0 to BrailleDots(setOf(1))),
        )
        summary.endsWith(".") shouldBe true
    }

    // -------------------------------------------------------------------------
    // Property tests (Req 7.5)
    // -------------------------------------------------------------------------

    "Feature: braille-scanner, Property 17: formatPattern output is non-empty for any valid BrailleDots" {
        val arbDots = Arb.set(Arb.int(1..6), 0..6).map { BrailleDots(it) }
        checkAll(200, arbDots) { dots ->
            UntranslatableFormatter.formatPattern(dots).shouldNotBeEmpty()
        }
    }

    "Feature: braille-scanner, Property 17: formatPattern preserves all raised dot positions in output" {
        val arbDots = Arb.set(Arb.int(1..6), 1..6).map { BrailleDots(it) }
        checkAll(200, arbDots) { dots ->
            val pattern = UntranslatableFormatter.formatPattern(dots)
            // Every raised dot position must appear in the output string.
            for (pos in dots.raised) {
                pattern shouldContain pos.toString()
            }
            // Output starts with the DOTS_PREFIX.
            pattern.startsWith(UntranslatableFormatter.DOTS_PREFIX) shouldBe true
        }
    }

    "Feature: braille-scanner, Property 17: formatMessage always contains the one-based position, pattern, and could-not-translate phrase" {
        val arbDots = Arb.set(Arb.int(1..6), 0..6).map { BrailleDots(it) }
        val arbIndex = Arb.nonNegativeInt(max = 99)
        checkAll(200, arbIndex, arbDots) { index, dots ->
            val msg = UntranslatableFormatter.formatMessage(index, dots)
            msg shouldContain "Cell ${index + 1}"
            msg shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
            msg.shouldNotBeEmpty()
        }
    }

    "Feature: braille-scanner, Property 17: formatSummary count matches entries size" {
        val arbEntries = Arb.list(
            Arb.int(0..20).map { idx -> idx to BrailleDots(setOf((idx % 6) + 1)) },
            1..10,
        )
        checkAll(200, arbEntries) { entries ->
            val summary = UntranslatableFormatter.formatSummary(entries)
            summary shouldContain "${entries.size}"
            summary shouldContain UntranslatableFormatter.COULD_NOT_TRANSLATE
            summary.shouldNotBeEmpty()
        }
    }
})

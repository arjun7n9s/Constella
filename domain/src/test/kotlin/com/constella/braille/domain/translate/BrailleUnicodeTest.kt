package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/**
 * Tests for [BrailleUnicode], the pure six-dot <-> Unicode Braille mapping that
 * feeds liblouis back-translation (Req 7.2).
 */
class BrailleUnicodeTest : StringSpec({

    "empty pattern maps to the blank Braille code point U+2800" {
        BrailleUnicode.dotsToCodePoint(BrailleDots.EMPTY) shouldBe 0x2800
        BrailleUnicode.dotsToChar(BrailleDots.EMPTY) shouldBe '\u2800'
    }

    "known patterns map to the standard code points" {
        // Dot 1 -> bit 0 -> U+2801 (letter 'a').
        BrailleUnicode.dotsToCodePoint(BrailleDots(setOf(1))) shouldBe 0x2801
        // Dots 1,2,5 -> bits 0,1,4 = 0x13 -> U+2813 (letter 'h').
        BrailleUnicode.dotsToCodePoint(BrailleDots(setOf(1, 2, 5))) shouldBe 0x2813
        // All six dots -> 0x3F -> U+283F.
        BrailleUnicode.dotsToCodePoint(BrailleDots(setOf(1, 2, 3, 4, 5, 6))) shouldBe 0x283F
    }

    "renders a sequence of cells one character per cell" {
        val cells = listOf(BrailleDots(setOf(1)), BrailleDots.EMPTY, BrailleDots(setOf(1, 2)))
        BrailleUnicode.toBrailleString(cells) shouldBe "\u2801\u2800\u2803"
    }

    "Feature: braille-scanner, Property 16: dots -> code point -> dots round-trips" {
        val arbDots: Arb<BrailleDots> = Arb.set(Arb.int(1..6), 0..6)
            .map { positions -> BrailleDots(positions) }
        checkAll(100, arbDots) { dots ->
            val codePoint = BrailleUnicode.dotsToCodePoint(dots)
            BrailleUnicode.codePointToDots(codePoint) shouldBe dots
        }
    }
})

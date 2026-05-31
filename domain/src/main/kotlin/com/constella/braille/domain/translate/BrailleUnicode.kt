package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BrailleDots

/**
 * Converts six-dot [BrailleDots] patterns to and from Unicode Braille Pattern
 * code points (the `U+2800`–`U+283F` block).
 *
 * liblouis back-translates a string of Braille characters into text. The
 * portable, table-independent way to feed it the recognized patterns is as
 * Unicode Braille: each cell becomes one character whose low six bits encode
 * the raised dots. The standard bit mapping is dot *n* → bit *(n − 1)*:
 *
 * ```
 *   dot 1 -> 0x01   dot 4 -> 0x08
 *   dot 2 -> 0x02   dot 5 -> 0x10
 *   dot 3 -> 0x04   dot 6 -> 0x20
 * ```
 *
 * so the code point is `0x2800 + bits`. An empty pattern maps to `U+2800`
 * (BRAILLE PATTERN BLANK), which liblouis treats as a space.
 *
 * This conversion is pure and deterministic, so the in-memory Grade 1 fake and
 * the property tests can build the same Braille strings the native engine would
 * receive — without any native dependency.
 */
object BrailleUnicode {

    /** Base code point of the Unicode Braille Patterns block (`U+2800`). */
    const val BRAILLE_PATTERN_BLANK: Int = 0x2800

    /** The six-bit dot mask (low six bits) for a single cell. */
    fun dotsToBits(dots: BrailleDots): Int {
        var bits = 0
        for (position in dots.raised) {
            bits = bits or (1 shl (position - 1))
        }
        return bits
    }

    /** The Unicode Braille code point (`U+2800`–`U+283F`) for a single cell. */
    fun dotsToCodePoint(dots: BrailleDots): Int = BRAILLE_PATTERN_BLANK or dotsToBits(dots)

    /** The single Unicode Braille character for a cell. */
    fun dotsToChar(dots: BrailleDots): Char = dotsToCodePoint(dots).toChar()

    /**
     * Render a sequence of cells as a Unicode Braille string (one character per
     * cell), suitable for passing to liblouis back-translation.
     */
    fun toBrailleString(cells: List<BrailleDots>): String {
        val sb = StringBuilder(cells.size)
        for (cell in cells) {
            sb.append(dotsToChar(cell))
        }
        return sb.toString()
    }

    /**
     * Inverse of [dotsToBits]: decode the low six bits of a Unicode Braille code
     * point back into a [BrailleDots] pattern. Bits outside the low six are
     * ignored. Useful for tests and diagnostics.
     */
    fun codePointToDots(codePoint: Int): BrailleDots {
        val bits = codePoint and 0x3F
        val raised = sortedSetOf<Int>()
        for (position in 1..6) {
            if (bits and (1 shl (position - 1)) != 0) {
                raised.add(position)
            }
        }
        return BrailleDots(raised)
    }
}

package com.constella.braille.domain.translate

import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell

/**
 * A pure, in-memory Grade 1 [TranslationEngine] used to exercise the
 * deterministic translation plumbing (char-span construction, untranslatable
 * aggregation, round-trip) on the JVM **without** the real liblouis native
 * library.
 *
 * It maps each recognized cell through the trivial uncontracted Grade 1
 * alphabet ([Grade1Alphabet]): one cell -> exactly one character. Cells with no
 * alphabet entry are marked untranslatable; their indices flow into
 * [TranslationOutput.untranslatableCells] (the same contract the real engine
 * honours for Req 7.5). It reuses the production [CharSpanAssembler] so the
 * char-span invariants it produces are identical to the real engine's.
 *
 * The engine ignores the requested [Grade] beyond requiring it be a resolved
 * grade — it only knows Grade 1 — which is sufficient for testing the
 * surrounding deterministic logic.
 */
class InMemoryGrade1TranslationEngine : TranslationEngine {

    override fun translate(cells: List<RecognizedCell>, grade: Grade): TranslationOutput {
        if (cells.isEmpty()) return TranslationOutput.EMPTY

        val segments = cells.mapIndexed { index, cell ->
            val letter = Grade1Alphabet.toChar(cell.dots)
            if (letter == null) {
                TranslationSegment(
                    cellRefs = listOf(index),
                    text = "",
                    confidence = cell.confidence,
                    translatable = false,
                )
            } else {
                TranslationSegment(
                    cellRefs = listOf(index),
                    text = letter.toString(),
                    confidence = cell.confidence,
                    translatable = true,
                )
            }
        }
        return CharSpanAssembler.assemble(segments)
    }
}

/**
 * The trivial uncontracted Grade 1 English alphabet: a bijection between the
 * letters `a`–`z` (plus the space, encoded as the empty cell) and their
 * six-dot [BrailleDots] patterns. Used only by tests and the in-memory fake.
 */
object Grade1Alphabet {

    private val charToDots: Map<Char, BrailleDots> = mapOf(
        'a' to dots(1),
        'b' to dots(1, 2),
        'c' to dots(1, 4),
        'd' to dots(1, 4, 5),
        'e' to dots(1, 5),
        'f' to dots(1, 2, 4),
        'g' to dots(1, 2, 4, 5),
        'h' to dots(1, 2, 5),
        'i' to dots(2, 4),
        'j' to dots(2, 4, 5),
        'k' to dots(1, 3),
        'l' to dots(1, 2, 3),
        'm' to dots(1, 3, 4),
        'n' to dots(1, 3, 4, 5),
        'o' to dots(1, 3, 5),
        'p' to dots(1, 2, 3, 4),
        'q' to dots(1, 2, 3, 4, 5),
        'r' to dots(1, 2, 3, 5),
        's' to dots(2, 3, 4),
        't' to dots(2, 3, 4, 5),
        'u' to dots(1, 3, 6),
        'v' to dots(1, 2, 3, 6),
        'w' to dots(2, 4, 5, 6),
        'x' to dots(1, 3, 4, 6),
        'y' to dots(1, 3, 4, 5, 6),
        'z' to dots(1, 3, 5, 6),
        ' ' to BrailleDots.EMPTY,
    )

    private val dotsToCharMap: Map<BrailleDots, Char> =
        charToDots.entries.associate { (char, dots) -> dots to char }

    /** The set of characters this alphabet can encode (used by generators). */
    val alphabet: Set<Char> = charToDots.keys

    /** The [BrailleDots] pattern for [char], or `null` if unsupported. */
    fun toDots(char: Char): BrailleDots? = charToDots[char]

    /** The character for [dots], or `null` if the pattern is not in the alphabet. */
    fun toChar(dots: BrailleDots): Char? = dotsToCharMap[dots]

    /** Encode [text] to a list of cell patterns; unsupported chars throw. */
    fun encode(text: String): List<BrailleDots> = text.map { char ->
        requireNotNull(charToDots[char]) { "Grade1Alphabet cannot encode '$char'" }
    }

    private fun dots(vararg positions: Int): BrailleDots = BrailleDots(positions.toSortedSet())
}

/**
 * Wrap raw [BrailleDots] patterns as [RecognizedCell]s with the given
 * [confidence], so they can be fed to a [TranslationEngine] in tests. The
 * geometry is filler — translation only reads [RecognizedCell.dots] and
 * [RecognizedCell.confidence].
 */
fun recognizedCellsOf(
    patterns: List<BrailleDots>,
    confidence: Confidence = Confidence.ONE,
): List<RecognizedCell> = patterns.map { pattern ->
    RecognizedCell(
        source = BrailleCell(
            dots = emptyList(),
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            centerY = 0f,
            validGrid = true,
            confidence = confidence,
        ),
        dots = pattern,
        confidence = confidence,
        uncertain = false,
    )
}

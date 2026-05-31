package com.constella.braille.domain.model

/**
 * The six-dot pattern of a Braille cell: which of the six positions are raised.
 *
 * Positions follow the standard Braille numbering, a 2-column by 3-row grid:
 *
 * ```
 *   1  4
 *   2  5
 *   3  6
 * ```
 *
 * [raised] is the subset of `{1, 2, 3, 4, 5, 6}` whose positions are raised. An
 * empty set is a valid pattern (a blank cell / space). The constructor rejects
 * any position outside `1..6`, so an invalid pattern can never exist.
 *
 * _Requirements: 6.1, 6.2_
 */
data class BrailleDots(val raised: Set<Int>) {
    init {
        require(raised.all { it in 1..6 }) {
            "BrailleDots positions must be a subset of {1..6} but was $raised"
        }
    }

    companion object {
        /** The valid range of Braille dot positions, `1..6`. */
        val VALID_POSITIONS: IntRange = 1..6

        /** The empty pattern: a blank cell with no raised dots. */
        val EMPTY: BrailleDots = BrailleDots(emptySet())
    }
}

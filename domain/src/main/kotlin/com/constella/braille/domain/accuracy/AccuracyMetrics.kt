package com.constella.braille.domain.accuracy

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.RecognizedCell

/**
 * Pure-Kotlin accuracy metrics used by the accuracy harness (Req 13) to score a
 * scan against ground truth.
 *
 * Two metrics are defined:
 *
 *  - [characterAccuracy] — `1 - characterErrorRate`, the proportion of
 *    correctly recognized characters in Recognized_Text relative to the
 *    ground-truth text (Req 13.7).
 *  - [cellAccuracy] — the proportion of correctly recognized Braille cells
 *    relative to the ground-truth cells (Req 13, Cell_Accuracy definition).
 *
 * ## Error-rate definition
 *
 * Both metrics are built on the Levenshtein edit distance (minimum number of
 * insertions, deletions, and substitutions to turn one sequence into the
 * other). The error rate normalizes that distance by the **longer** of the two
 * sequence lengths:
 *
 * ```
 * errorRate = editDistance(recognized, groundTruth) / max(len(recognized), len(groundTruth))
 * accuracy  = 1 - errorRate
 * ```
 *
 * Normalizing by the maximum length (rather than only the ground-truth length)
 * is deliberate: the Levenshtein distance can never exceed `max(lenA, lenB)`,
 * so the error rate is always in `[0, 1]` and the accuracy is therefore always
 * in `[0, 1]` — no clamping of a possibly-out-of-range value is required, and
 * the invariant `accuracy == 1 - errorRate` holds exactly (Property 23).
 *
 * ## Edge cases (Property 23)
 *
 *  - **Both empty** (identical empty sequences): there is nothing to get wrong,
 *    so the error rate is `0.0` and the accuracy is `1.0`.
 *  - **Identical non-empty sequences**: edit distance is `0`, error rate is
 *    `0.0`, accuracy is `1.0`.
 *  - **One side empty, the other not** (e.g. ground truth empty but recognized
 *    text non-empty, or vice versa): the edit distance equals the non-empty
 *    length, which also equals the max length, so the error rate is `1.0` and
 *    the accuracy is `0.0` — the result is "completely wrong" rather than an
 *    error or a divide-by-zero.
 *
 * The functions are symmetric in their two arguments (because both the edit
 * distance and `max` are symmetric), so passing recognized/ground-truth in
 * either order yields the same score; the parameter names document the intended
 * roles.
 */
object AccuracyMetrics {

    /**
     * Character error rate (CER) between [recognized] and [groundTruth]: the
     * Levenshtein edit distance over characters, normalized by the longer
     * string's length. Always in `[0, 1]`. Two empty strings yield `0.0`.
     */
    fun characterErrorRate(recognized: String, groundTruth: String): Double =
        normalizedErrorRate(
            distance = levenshtein(recognized, groundTruth),
            maxLength = maxOf(recognized.length, groundTruth.length),
        )

    /**
     * Character_Accuracy: `1 - characterErrorRate`. The proportion of correctly
     * recognized characters relative to the ground truth, measured as one minus
     * the character error rate (Req 13.7). Always in `[0, 1]`; `1.0` when the
     * strings are identical (including two empty strings).
     */
    fun characterAccuracy(recognized: String, groundTruth: String): Double =
        accuracyFrom(characterErrorRate(recognized, groundTruth))

    /**
     * Cell error rate between [recognized] and [groundTruth] cell patterns: the
     * Levenshtein edit distance over [BrailleDots] patterns (two cells are
     * equal when their raised-dot sets are equal), normalized by the longer
     * sequence's length. Always in `[0, 1]`. Two empty sequences yield `0.0`.
     */
    fun cellErrorRate(recognized: List<BrailleDots>, groundTruth: List<BrailleDots>): Double =
        normalizedErrorRate(
            distance = levenshtein(recognized, groundTruth),
            maxLength = maxOf(recognized.size, groundTruth.size),
        )

    /**
     * Cell_Accuracy: `1 - cellErrorRate`. The proportion of correctly
     * recognized Braille cells relative to the ground-truth cells. Always in
     * `[0, 1]`; `1.0` when the two cell sequences are identical (including two
     * empty sequences).
     */
    fun cellAccuracy(recognized: List<BrailleDots>, groundTruth: List<BrailleDots>): Double =
        accuracyFrom(cellErrorRate(recognized, groundTruth))

    /**
     * Convenience overload of [cellAccuracy] that scores the recognized cells
     * produced by the Pattern_Recognizer (a list of [RecognizedCell]) against
     * the ground-truth [BrailleDots] patterns by comparing each recognized
     * cell's [RecognizedCell.dots] pattern. Confidence/uncertainty flags do not
     * affect the metric — only the recognized pattern is compared.
     */
    fun cellAccuracyOfRecognized(
        recognized: List<RecognizedCell>,
        groundTruth: List<BrailleDots>,
    ): Double = cellAccuracy(recognized.map { it.dots }, groundTruth)

    // --- internals -----------------------------------------------------------

    /**
     * Turns an edit [distance] and the [maxLength] of the two compared
     * sequences into a normalized error rate in `[0, 1]`. When [maxLength] is
     * `0` both sequences are empty (identical), so the error rate is `0.0`.
     */
    private fun normalizedErrorRate(distance: Int, maxLength: Int): Double {
        if (maxLength == 0) return 0.0
        return (distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)
    }

    /** `1 - errorRate`, with a defensive clamp into `[0, 1]`. */
    private fun accuracyFrom(errorRate: Double): Double =
        (1.0 - errorRate).coerceIn(0.0, 1.0)

    /**
     * Levenshtein edit distance between two strings, compared character by
     * character. Uses the classic two-row dynamic-programming table for `O(n)`
     * extra space.
     */
    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            val aChar = a[i - 1]
            for (j in 1..b.length) {
                val substitutionCost = if (aChar == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,                 // deletion
                    current[j - 1] + 1,              // insertion
                    previous[j - 1] + substitutionCost, // substitution / match
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Levenshtein edit distance between two sequences of elements, compared by
     * structural equality (`==`). Used for cell-pattern sequences where each
     * element is a [BrailleDots].
     */
    private fun <T> levenshtein(a: List<T>, b: List<T>): Int {
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size

        var previous = IntArray(b.size + 1) { it }
        var current = IntArray(b.size + 1)

        for (i in 1..a.size) {
            current[0] = i
            val aElem = a[i - 1]
            for (j in 1..b.size) {
                val substitutionCost = if (aElem == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,                 // deletion
                    current[j - 1] + 1,              // insertion
                    previous[j - 1] + substitutionCost, // substitution / match
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.size]
    }
}

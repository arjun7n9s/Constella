package com.constella.braille.domain.translate

import com.constella.braille.domain.model.CharSpan

/**
 * Folds an ordered list of [TranslationSegment]s into a [TranslationOutput].
 *
 * This is the deterministic, liblouis-independent core of the Translation_Engine:
 * given what each cell (or contraction group) produced, it builds
 *
 *  1. the concatenated Recognized_Text,
 *  2. the per-character [CharSpan] mapping — each non-empty translatable segment
 *     becomes one half-open `[start, end)` span over the output text, tagged
 *     with the segment's source [TranslationSegment.cellRefs] and confidence so
 *     per-cell confidence can drive per-character display marking (Req 10.3),
 *  3. the de-duplicated, sorted list of untranslatable cell indices (Req 7.5).
 *
 * ## Char-span invariants (verified by property tests)
 *
 *  - Spans are emitted in output order and are **contiguous and
 *    non-overlapping**: each span starts exactly where the previous one ended.
 *  - The spans exactly tile the output text: the first span starts at `0`, the
 *    last ends at `text.length`, and concatenating the covered substrings
 *    reproduces `text`. (Empty-text segments contribute a zero-width span at the
 *    current cursor so no source cell is silently dropped from the mapping.)
 *  - Every span's `[start, end)` lies within `0..text.length` (guaranteed by
 *    construction here and re-checked by [CharSpan]'s own `init`).
 *  - `untranslatableCells` contains exactly the refs of segments marked
 *    untranslatable, each once, in ascending order.
 *
 * Untranslatable segments contribute **no** text and **no** char span (there is
 * no character to map); their cell refs flow only into `untranslatableCells`.
 */
object CharSpanAssembler {

    /**
     * Assemble [segments] into a [TranslationOutput].
     *
     * Segments are processed in the given order, which must be reading order.
     */
    fun assemble(segments: List<TranslationSegment>): TranslationOutput {
        val builder = StringBuilder()
        val spans = ArrayList<CharSpan>(segments.size)
        val untranslatable = LinkedHashSet<Int>()

        for (segment in segments) {
            if (!segment.translatable) {
                // No character is produced; record the source cells as
                // untranslatable and move on without advancing the cursor.
                untranslatable.addAll(segment.cellRefs)
                continue
            }

            val start = builder.length
            builder.append(segment.text)
            val end = builder.length

            // Emit a span for every translatable segment, including zero-width
            // ones, so each source cell remains traceable in the mapping.
            spans.add(
                CharSpan(
                    startIndex = start,
                    endIndex = end,
                    cellRefs = segment.cellRefs,
                    confidence = segment.confidence,
                ),
            )
        }

        return TranslationOutput(
            text = builder.toString(),
            charSpans = spans,
            untranslatableCells = untranslatable.sorted(),
        )
    }
}

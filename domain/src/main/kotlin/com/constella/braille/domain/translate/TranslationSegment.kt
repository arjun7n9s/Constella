package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Confidence

/**
 * An intermediate, backend-agnostic unit of translation output.
 *
 * A translation backend (the liblouis JNI wrapper in `:runtime`, or the
 * in-memory Grade 1 fake used in tests) emits an ordered list of
 * [TranslationSegment]s; [CharSpanAssembler] then folds them into the final
 * [TranslationOutput] (the concatenated text, the per-character [CharSpan]
 * mapping, and the untranslatable-cell indices).
 *
 * Separating "what each cell produced" (this type) from "assemble it into text
 * + char spans" (the assembler) keeps the char-span construction deterministic
 * and unit-testable on the JVM without any native dependency.
 *
 * @property cellRefs the source cell index/indices (into the recognized-cell
 *   list) that produced this segment, in reading order. A Grade 1 cell maps to
 *   exactly one ref; a Grade 2 contraction may span several.
 * @property text the output characters contributed by this segment. May be
 *   empty (e.g. a translatable indicator cell that emits no visible character).
 * @property confidence the aggregated confidence for this segment, propagated
 *   to the resulting [CharSpan] so per-cell confidence reaches per-character
 *   display marking (Req 10.3).
 * @property translatable `false` when [cellRefs] could not be translated; such
 *   segments contribute no text and their refs are collected into
 *   [TranslationOutput.untranslatableCells] (Req 7.5).
 */
data class TranslationSegment(
    val cellRefs: List<Int>,
    val text: String,
    val confidence: Confidence,
    val translatable: Boolean,
) {
    init {
        require(cellRefs.all { it >= 0 }) {
            "TranslationSegment cellRefs must be non-negative indices but was $cellRefs"
        }
    }
}

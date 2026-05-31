package com.constella.braille.domain.translate

import com.constella.braille.domain.model.CharSpan
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell

/**
 * Translates recognized six-dot Braille patterns into English text.
 *
 * This is the framework-free domain contract for the Translation_Engine
 * (Req 7). The production implementation is backed by liblouis compiled for
 * Android and invoked over JNI (see the `:runtime` layer), using only the
 * Grade 1 and Grade 2 English tables bundled in the Application_Package
 * (Req 7.1, 7.2, 7.4). The interface itself carries no Android or native
 * dependency so the deterministic surrounding logic (char-span construction,
 * grade/table plumbing, untranslatable-cell aggregation) can be exercised on
 * the JVM with an in-memory fake.
 *
 * Implementations translate the *already-recognized* cells; switching grade is
 * a pure re-translation of the same cells and never requires re-scanning
 * (Req 8.4). When the native library or its bundled tables are unavailable, an
 * implementation throws [TranslationUnavailableException] rather than returning
 * a silently-empty or fabricated result.
 *
 * _Requirements: 7.1, 7.2, 7.4_
 */
interface TranslationEngine {

    /**
     * Translate [cells] (in reading order) using the resolved [grade].
     *
     * @return a [TranslationOutput] with the Recognized_Text, the per-character
     *   [CharSpan] mapping back to the source cells, and the indices of any
     *   cells that could not be translated.
     * @throws TranslationUnavailableException if the engine cannot translate
     *   because the native library or bundled tables are not present.
     */
    fun translate(cells: List<RecognizedCell>, grade: Grade): TranslationOutput
}

/**
 * The result of translating a sequence of recognized cells.
 *
 * @property text the Recognized_Text displayed on screen (Req 7.3).
 * @property charSpans maps contiguous spans of [text] back to the source cell
 *   index/indices that produced them, with an aggregated confidence per span.
 *   Grade 2 contractions make the cell-to-character mapping non-1:1, so this
 *   mapping is what lets the System propagate per-cell confidence to
 *   per-character display marking (Req 10.3). Spans are ordered and cover the
 *   output left to right.
 * @property untranslatableCells indices (into the input `cells` list) of cells
 *   that the engine could not translate; surfaced to the Operator with their
 *   raw patterns and a "could not translate" message (Req 7.5).
 */
data class TranslationOutput(
    val text: String,
    val charSpans: List<CharSpan>,
    val untranslatableCells: List<Int>,
) {
    companion object {
        /** An empty translation: no text, no spans, nothing untranslatable. */
        val EMPTY: TranslationOutput = TranslationOutput("", emptyList(), emptyList())
    }
}

/**
 * Thrown when the Translation_Engine cannot run because the liblouis native
 * library and/or its bundled translation tables are not available on the
 * device. Callers surface this as a recoverable error rather than crashing,
 * keeping the rest of the scan pipeline intact.
 *
 * _Requirements: 7.1, 7.4_
 */
class TranslationUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

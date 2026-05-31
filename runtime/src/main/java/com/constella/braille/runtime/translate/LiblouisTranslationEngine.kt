package com.constella.braille.runtime.translate

import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.BackTranslationMapper
import com.constella.braille.domain.translate.BrailleTable
import com.constella.braille.domain.translate.BrailleUnicode
import com.constella.braille.domain.translate.CharSpanAssembler
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.domain.translate.TranslationOutput
import com.constella.braille.domain.translate.TranslationUnavailableException
import java.io.File

/**
 * liblouis-backed [TranslationEngine] for Android.
 *
 * Pipeline for one translation:
 *
 *  1. Encode the recognized cells as a Unicode-Braille string (one `U+28xx`
 *     character per cell) with [BrailleUnicode] — table-independent input.
 *  2. Resolve the bundled table for the requested [Grade] with [BrailleTable]
 *     (only bundled Grade 1/Grade 2 English tables are reachable — Req 7.4).
 *  3. Back-translate over JNI with [LiblouisBridge], receiving the output text
 *     plus liblouis's per-output-character source-cell mapping.
 *  4. Convert that mapping to segments with [BackTranslationMapper] and fold
 *     them into text + char spans with [CharSpanAssembler] — both pure,
 *     JVM-tested domain logic (Req 7.2, 10.3).
 *
 * **Graceful degradation (Req 7.1, 7.4).** The native `.so` and the table
 * assets are not bundled yet. When the native library cannot be loaded, when
 * the tables directory / required table files are absent, or when liblouis
 * reports a failure, [translate] throws [TranslationUnavailableException]
 * instead of crashing or fabricating output. The empty-cell case short-circuits
 * to [TranslationOutput.EMPTY] without touching native code.
 *
 * @param tablesDirectory filesystem directory the bundled liblouis tables were
 *   extracted to (assets are copied out at startup so liblouis can read them by
 *   name). May be `null` until packaging is wired (task 23), in which case the
 *   engine reports itself unavailable.
 * @param bridge native bridge; defaults to the real [LiblouisBridge]. Injectable
 *   so the deterministic plumbing can be exercised without the real `.so`.
 */
class LiblouisTranslationEngine(
    private val tablesDirectory: File?,
    private val bridge: LiblouisBackend = DefaultLiblouisBackend,
) : TranslationEngine {

    /**
     * `true` when the native library, the tables directory, and every bundled
     * table file are present, so translation can actually run.
     */
    val isAvailable: Boolean
        get() {
            if (!bridge.ensureLoaded()) return false
            val dir = tablesDirectory ?: return false
            if (!dir.isDirectory) return false
            return BrailleTable.bundledFileNames().all { File(dir, it).isFile }
        }

    override fun translate(cells: List<RecognizedCell>, grade: Grade): TranslationOutput {
        if (cells.isEmpty()) return TranslationOutput.EMPTY

        val dir = tablesDirectory
            ?: throw TranslationUnavailableException(
                "liblouis tables directory is not configured; bundle the Grade 1/Grade 2 " +
                    "English tables and extract them before translating.",
            )
        if (!bridge.ensureLoaded()) {
            throw TranslationUnavailableException(
                "liblouis native library is unavailable; bundle liblouis.so + liblouis_jni.so " +
                    "for the active ABI.",
            )
        }

        val table = BrailleTable.forGrade(grade)
        val tableFile = File(dir, table.fileName)
        if (!tableFile.isFile) {
            throw TranslationUnavailableException(
                "Bundled liblouis table '${table.fileName}' for $grade was not found in " +
                    "${dir.absolutePath}.",
            )
        }

        bridge.setTablesDirectory(dir.absolutePath)

        val braille = BrailleUnicode.toBrailleString(cells.map { it.dots })
        val result = bridge.backTranslate(tableFile.name, braille)
            ?: throw TranslationUnavailableException(
                "liblouis back-translation failed for table '${table.fileName}'.",
            )

        val cellConfidences: List<Confidence> = cells.map { it.confidence }
        val segments = BackTranslationMapper.toSegments(
            outputText = result.text,
            inputPositions = result.inputPositions,
            cellConfidences = cellConfidences,
        )
        return CharSpanAssembler.assemble(segments)
    }
}

/**
 * Narrow seam over the native [LiblouisBridge], so [LiblouisTranslationEngine]'s
 * deterministic plumbing can be tested with a substitute backend that needs no
 * real `.so`.
 */
interface LiblouisBackend {
    /** @see LiblouisBridge.ensureLoaded */
    fun ensureLoaded(): Boolean

    /** @see LiblouisBridge.nativeSetTablesDirectory */
    fun setTablesDirectory(absolutePath: String)

    /** @see LiblouisBridge.nativeBackTranslate */
    fun backTranslate(tableList: String, braille: String): NativeBackTranslation?
}

/** Production backend delegating to the real JNI [LiblouisBridge]. */
internal object DefaultLiblouisBackend : LiblouisBackend {
    override fun ensureLoaded(): Boolean = LiblouisBridge.ensureLoaded()

    override fun setTablesDirectory(absolutePath: String) =
        LiblouisBridge.nativeSetTablesDirectory(absolutePath)

    override fun backTranslate(tableList: String, braille: String): NativeBackTranslation? =
        LiblouisBridge.nativeBackTranslate(tableList, braille)
}

package com.constella.braille.runtime.translate

import com.constella.braille.domain.model.BoundingBox
import com.constella.braille.domain.model.BrailleCell
import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.BrailleTable
import com.constella.braille.domain.translate.TranslationOutput
import com.constella.braille.domain.translate.TranslationUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM unit tests for [LiblouisTranslationEngine] focused on its deterministic
 * plumbing and graceful degradation (Req 7.1, 7.4) — exercised through the
 * injectable [LiblouisBackend] seam, so no real liblouis `.so` is required.
 */
class LiblouisTranslationEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun emptyCellsReturnEmptyOutputWithoutTouchingNative() {
        val backend = FakeBackend(loaded = false)
        val engine = LiblouisTranslationEngine(tablesDirectory = null, bridge = backend)

        assertEquals(TranslationOutput.EMPTY, engine.translate(emptyList(), Grade.GRADE_1))
        assertFalse("native must not be queried for empty input", backend.backTranslateCalled)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun translateThrowsWhenTablesDirectoryMissing() {
        val engine = LiblouisTranslationEngine(
            tablesDirectory = null,
            bridge = FakeBackend(loaded = true),
        )
        engine.translate(cells("a"), Grade.GRADE_1)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun translateThrowsWhenNativeLibraryUnavailable() {
        val dir = tablesWith("en-us-g1.ctb", "en-us-g2.ctb")
        val engine = LiblouisTranslationEngine(tablesDirectory = dir, bridge = FakeBackend(loaded = false))
        engine.translate(cells("a"), Grade.GRADE_1)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun translateThrowsWhenRequiredTableFileMissing() {
        // Only the Grade 2 table is present; requesting Grade 1 must fail clearly.
        val dir = tablesWith("en-us-g2.ctb")
        val engine = LiblouisTranslationEngine(tablesDirectory = dir, bridge = FakeBackend(loaded = true))
        engine.translate(cells("a"), Grade.GRADE_1)
    }

    @Test
    fun isAvailableRequiresLoadedNativeDirectoryAndBothTables() {
        val backendLoaded = FakeBackend(loaded = true)
        val backendUnloaded = FakeBackend(loaded = false)

        assertFalse(LiblouisTranslationEngine(null, backendLoaded).isAvailable)
        assertFalse(
            LiblouisTranslationEngine(tablesWith("en-us-g1.ctb"), backendLoaded).isAvailable,
        )
        assertFalse(
            LiblouisTranslationEngine(
                tablesWith("en-us-g1.ctb", "en-us-g2.ctb"),
                backendUnloaded,
            ).isAvailable,
        )
        assertTrue(
            LiblouisTranslationEngine(
                tablesWith("en-us-g1.ctb", "en-us-g2.ctb"),
                backendLoaded,
            ).isAvailable,
        )
    }

    @Test
    fun translateUsesNativeResultToBuildTextAndCharSpans() {
        val dir = tablesWith("en-us-g1.ctb", "en-us-g2.ctb")
        // Fake liblouis: cell 0 -> "the" (contraction), cell 1 -> "y".
        val backend = FakeBackend(
            loaded = true,
            result = NativeBackTranslation("they", intArrayOf(0, 0, 0, 1)),
        )
        val engine = LiblouisTranslationEngine(tablesDirectory = dir, bridge = backend)

        val output = engine.translate(cells("ab"), Grade.GRADE_2)

        assertEquals("they", output.text)
        // Two source cells -> two contiguous spans.
        assertEquals(2, output.charSpans.size)
        assertEquals(0, output.charSpans[0].startIndex)
        assertEquals(3, output.charSpans[0].endIndex)
        assertEquals(listOf(0), output.charSpans[0].cellRefs)
        assertEquals(3, output.charSpans[1].startIndex)
        assertEquals(4, output.charSpans[1].endIndex)
        assertEquals(listOf(1), output.charSpans[1].cellRefs)
        // Grade 2 table file name was passed to the native call.
        assertEquals(BrailleTable.ENGLISH_GRADE_2.fileName, backend.lastTableList)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun translateThrowsWhenNativeReportsFailure() {
        val dir = tablesWith("en-us-g1.ctb", "en-us-g2.ctb")
        val backend = FakeBackend(loaded = true, result = null) // liblouis failure
        LiblouisTranslationEngine(dir, backend).translate(cells("a"), Grade.GRADE_1)
    }

    // --- helpers -------------------------------------------------------------

    private fun tablesWith(vararg fileNames: String): File {
        val dir = tempFolder.newFolder()
        fileNames.forEach { name -> File(dir, name).writeText("# stub table") }
        return dir
    }

    /** One [RecognizedCell] per character, using arbitrary distinct patterns. */
    private fun cells(text: String): List<RecognizedCell> = text.mapIndexed { i, _ ->
        RecognizedCell(
            source = BrailleCell(
                dots = emptyList(),
                boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                centerY = 0f,
                validGrid = true,
                confidence = Confidence.ONE,
            ),
            dots = BrailleDots(setOf((i % 6) + 1)),
            confidence = Confidence.ONE,
            uncertain = false,
        )
    }

    private class FakeBackend(
        private val loaded: Boolean,
        private val result: NativeBackTranslation? = NativeBackTranslation("", IntArray(0)),
    ) : LiblouisBackend {
        var backTranslateCalled = false
        var lastTableList: String? = null

        override fun ensureLoaded(): Boolean = loaded

        override fun setTablesDirectory(absolutePath: String) = Unit

        override fun backTranslate(tableList: String, braille: String): NativeBackTranslation? {
            backTranslateCalled = true
            lastTableList = tableList
            return result
        }
    }
}

package com.constella.braille.domain.grade

import com.constella.braille.domain.model.BrailleDots
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.Grade1Alphabet
import com.constella.braille.domain.translate.InMemoryGrade1TranslationEngine
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.domain.translate.TranslationOutput
import com.constella.braille.domain.translate.recognizedCellsOf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Tests for the [HeuristicGradeDetector] estimate, [GradeDetector.resolveGrade]
 * mode resolution, and the [GradeOverride] re-translate-without-rescan path
 * (Req 8.1, 8.4).
 */
class GradeDetectorTest : StringSpec({

    val detector = HeuristicGradeDetector()

    // ---- detectGrade heuristic ----

    "pure uncontracted alphabet text is estimated Grade 1" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("the cat sat on a mat"))
        detector.detectGrade(cells) shouldBe Grade.GRADE_1
    }

    "contraction-signal-heavy patterns are estimated Grade 2" {
        // All cells are unambiguous Grade-2 signal patterns.
        val cells = recognizedCellsOf(
            HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.toList(),
        )
        detector.detectGrade(cells) shouldBe Grade.GRADE_2
    }

    "empty input defaults to Grade 1" {
        detector.detectGrade(emptyList()) shouldBe Grade.GRADE_1
    }

    "all-blank cells default to Grade 1 (blanks carry no signal)" {
        val cells = recognizedCellsOf(List(5) { BrailleDots.EMPTY })
        detector.detectGrade(cells) shouldBe Grade.GRADE_1
    }

    "blank cells are ignored in the signal ratio" {
        // One contraction signal among blanks -> 100% of non-blank cells are
        // signals -> Grade 2, regardless of how many blanks pad it.
        val theSign = HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.first()
        val cells = recognizedCellsOf(
            listOf(BrailleDots.EMPTY, theSign, BrailleDots.EMPTY, BrailleDots.EMPTY),
        )
        detector.detectGrade(cells) shouldBe Grade.GRADE_2
    }

    "a single contraction signal in otherwise uncontracted text stays below threshold" {
        // 1 signal in 20 non-blank cells = 5% < 15% default threshold -> Grade 1.
        val letters = Grade1Alphabet.encode("aaaaaaaaaaaaaaaaaaa") // 19 'a' cells
        val theSign = HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.first()
        val cells = recognizedCellsOf(letters + theSign)
        detector.detectGrade(cells) shouldBe Grade.GRADE_1
    }

    "detectGrade always resolves to exactly Grade 1 or Grade 2" {
        val signalPlusAlphabet = (HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS +
            ('a'..'z').map { Grade1Alphabet.toDots(it)!! } + BrailleDots.EMPTY).toList()
        val patternArb = Arb.element(signalPlusAlphabet)
        checkAll(100, Arb.list(patternArb, 0..12)) { patterns ->
            val grade = detector.detectGrade(recognizedCellsOf(patterns))
            (grade == Grade.GRADE_1 || grade == Grade.GRADE_2) shouldBe true
        }
    }

    // ---- resolveGrade mode resolution ----

    "AUTO mode resolves to the detected grade" {
        val g1Cells = recognizedCellsOf(Grade1Alphabet.encode("cab"))
        val g2Cells = recognizedCellsOf(HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.toList())

        detector.resolveGrade(GradeMode.AUTO, g1Cells) shouldBe detector.detectGrade(g1Cells)
        detector.resolveGrade(GradeMode.AUTO, g1Cells) shouldBe Grade.GRADE_1
        detector.resolveGrade(GradeMode.AUTO, g2Cells) shouldBe detector.detectGrade(g2Cells)
        detector.resolveGrade(GradeMode.AUTO, g2Cells) shouldBe Grade.GRADE_2
    }

    "manual GRADE_1 mode resolves to Grade 1 regardless of patterns" {
        // Cells that would auto-detect as Grade 2, but the manual override wins.
        val g2Cells = recognizedCellsOf(HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.toList())
        detector.resolveGrade(GradeMode.GRADE_1, g2Cells) shouldBe Grade.GRADE_1
    }

    "manual GRADE_2 mode resolves to Grade 2 regardless of patterns" {
        // Cells that would auto-detect as Grade 1, but the manual override wins.
        val g1Cells = recognizedCellsOf(Grade1Alphabet.encode("cab"))
        detector.resolveGrade(GradeMode.GRADE_2, g1Cells) shouldBe Grade.GRADE_2
    }

    "manual modes ignore the cells entirely (resolve even for empty input)" {
        detector.resolveGrade(GradeMode.GRADE_1, emptyList()) shouldBe Grade.GRADE_1
        detector.resolveGrade(GradeMode.GRADE_2, emptyList()) shouldBe Grade.GRADE_2
    }

    "resolveGrade is total over GradeMode" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("hi"))
        checkAll(100, Arb.enum<GradeMode>()) { mode ->
            val expected = when (mode) {
                GradeMode.GRADE_1 -> Grade.GRADE_1
                GradeMode.GRADE_2 -> Grade.GRADE_2
                GradeMode.AUTO -> detector.detectGrade(cells)
            }
            detector.resolveGrade(mode, cells) shouldBe expected
        }
    }

    // ---- GradeOverride: re-translate on the same cells, no rescan ----

    "override re-runs translation on the same cells and yields the overridden grade's output" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("cab"))
        val engine = RecordingTranslationEngine(InMemoryGrade1TranslationEngine())

        val output = GradeOverride.retranslate(engine, cells, Grade.GRADE_2)

        // It produced exactly what translating the same cells under the chosen
        // grade produces — no rescan, just re-translation.
        output shouldBe InMemoryGrade1TranslationEngine().translate(cells, Grade.GRADE_2)

        // Exactly one translate call, with the SAME cells and the chosen grade.
        engine.calls.size shouldBe 1
        engine.calls.single().first shouldBe cells
        engine.calls.single().second shouldBe Grade.GRADE_2
    }

    "override re-applying the original grade reproduces the original output (pure re-translation)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("hello"))
        val engine = InMemoryGrade1TranslationEngine()

        val original = engine.translate(cells, Grade.GRADE_1)
        val reTranslated = GradeOverride.retranslate(engine, cells, Grade.GRADE_1)

        reTranslated shouldBe original
    }

    "override never mutates or replaces the recognized cells (no rescan input change)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("data"))
        val engine = RecordingTranslationEngine(InMemoryGrade1TranslationEngine())

        GradeOverride.retranslate(engine, cells, Grade.GRADE_1)
        GradeOverride.retranslate(engine, cells, Grade.GRADE_2)

        // Both overrides translated the identical, unchanged cell list.
        engine.calls.map { it.first } shouldContainExactly listOf(cells, cells)
    }
})

/**
 * A [TranslationEngine] decorator that records the (cells, grade) of every
 * [translate] call and delegates to [delegate]. Used to assert that the
 * override path re-runs translation on the same cells and nothing else — there
 * is no camera/detector/segmenter to record because none is reachable from the
 * override path.
 */
private class RecordingTranslationEngine(
    private val delegate: TranslationEngine,
) : TranslationEngine {

    val calls = mutableListOf<Pair<List<RecognizedCell>, Grade>>()

    override fun translate(cells: List<RecognizedCell>, grade: Grade): TranslationOutput {
        calls.add(cells to grade)
        return delegate.translate(cells, grade)
    }
}

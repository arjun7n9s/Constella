package com.constella.braille.domain.grade

import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.RecognizedCell
import com.constella.braille.domain.translate.Grade1Alphabet
import com.constella.braille.domain.translate.InMemoryGrade1TranslationEngine
import com.constella.braille.domain.translate.TranslationEngine
import com.constella.braille.domain.translate.TranslationOutput
import com.constella.braille.domain.translate.recognizedCellsOf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

/**
 * Tests for [GradeController]: mode management, grade resolution, one-tap
 * override re-translation, and session reset (Req 8.1, 8.3, 8.4, 8.6).
 */
class GradeControllerTest : StringSpec({

    fun makeController(initialMode: GradeMode = GradeMode.AUTO): GradeController =
        GradeController(
            detector = HeuristicGradeDetector(),
            engine = InMemoryGrade1TranslationEngine(),
            initialMode = initialMode,
        )

    // ---- Default mode ----

    "controller defaults to AUTO mode (Req 8.6)" {
        val controller = GradeController(
            detector = HeuristicGradeDetector(),
            engine = InMemoryGrade1TranslationEngine(),
        )
        controller.currentMode shouldBe GradeMode.AUTO
    }

    // ---- resolveGrade delegates to detector ----

    "resolveGrade in AUTO mode returns the heuristic estimate for Grade 1 cells" {
        val controller = makeController(GradeMode.AUTO)
        val cells = recognizedCellsOf(Grade1Alphabet.encode("hello"))
        controller.resolveGrade(cells) shouldBe Grade.GRADE_1
    }

    "resolveGrade in AUTO mode returns Grade 2 for contraction-signal cells" {
        val controller = makeController(GradeMode.AUTO)
        val cells = recognizedCellsOf(HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.toList())
        controller.resolveGrade(cells) shouldBe Grade.GRADE_2
    }

    "resolveGrade in GRADE_1 mode always returns Grade 1 regardless of patterns" {
        val controller = makeController(GradeMode.GRADE_1)
        // Even Grade-2-signal-heavy cells resolve to Grade 1 when mode is manual.
        val cells = recognizedCellsOf(HeuristicGradeDetector.GRADE_2_SIGNAL_PATTERNS.toList())
        controller.resolveGrade(cells) shouldBe Grade.GRADE_1
    }

    "resolveGrade in GRADE_2 mode always returns Grade 2 regardless of patterns" {
        val controller = makeController(GradeMode.GRADE_2)
        // Even Grade-1-only cells resolve to Grade 2 when mode is manual.
        val cells = recognizedCellsOf(Grade1Alphabet.encode("abc"))
        controller.resolveGrade(cells) shouldBe Grade.GRADE_2
    }

    // ---- override: mode change + re-translation ----

    "override changes currentMode to the requested mode" {
        val controller = makeController(GradeMode.AUTO)
        val cells = recognizedCellsOf(Grade1Alphabet.encode("cat"))

        controller.override(GradeMode.GRADE_1, cells)
        controller.currentMode shouldBe GradeMode.GRADE_1

        controller.override(GradeMode.GRADE_2, cells)
        controller.currentMode shouldBe GradeMode.GRADE_2

        controller.override(GradeMode.AUTO, cells)
        controller.currentMode shouldBe GradeMode.AUTO
    }

    "override re-translates the same cells without rescanning (Req 8.4)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("data"))
        val recording = CapturingTranslationEngine(InMemoryGrade1TranslationEngine())
        val controller = GradeController(
            detector = HeuristicGradeDetector(),
            engine = recording,
            initialMode = GradeMode.AUTO,
        )

        controller.override(GradeMode.GRADE_1, cells)

        // Exactly one translate call, with the SAME cells.
        recording.calls.size shouldBe 1
        recording.calls.single().first shouldBe cells
        recording.calls.single().second shouldBe Grade.GRADE_1
    }

    "override returns the translation output for the chosen grade" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("hi"))
        val engine = InMemoryGrade1TranslationEngine()
        val controller = GradeController(
            detector = HeuristicGradeDetector(),
            engine = engine,
            initialMode = GradeMode.AUTO,
        )

        val output = controller.override(GradeMode.GRADE_1, cells)

        // The output must equal what translating the same cells under Grade 1 produces.
        output shouldBe engine.translate(cells, Grade.GRADE_1)
    }

    "override with the same mode is idempotent (re-translates, same output)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("abc"))
        val engine = InMemoryGrade1TranslationEngine()
        val controller = GradeController(
            detector = HeuristicGradeDetector(),
            engine = engine,
            initialMode = GradeMode.GRADE_1,
        )

        val first = controller.override(GradeMode.GRADE_1, cells)
        val second = controller.override(GradeMode.GRADE_1, cells)

        controller.currentMode shouldBe GradeMode.GRADE_1
        first shouldBe second
    }

    "override never mutates the recognized cells (no rescan input change)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("test"))
        val recording = CapturingTranslationEngine(InMemoryGrade1TranslationEngine())
        val controller = GradeController(
            detector = HeuristicGradeDetector(),
            engine = recording,
            initialMode = GradeMode.AUTO,
        )

        controller.override(GradeMode.GRADE_1, cells)
        controller.override(GradeMode.GRADE_2, cells)

        // Both overrides translated the identical, unchanged cell list.
        recording.calls.map { it.first }.forEach { it shouldBe cells }
    }

    // ---- resetToAuto ----

    "resetToAuto restores AUTO mode from any manual override" {
        val controller = makeController(GradeMode.GRADE_2)
        controller.resetToAuto()
        controller.currentMode shouldBe GradeMode.AUTO
    }

    "resetToAuto is idempotent when already in AUTO mode" {
        val controller = makeController(GradeMode.AUTO)
        controller.resetToAuto()
        controller.currentMode shouldBe GradeMode.AUTO
    }

    // ---- Property: override always produces a valid TranslationOutput ----

    "override always produces a non-null TranslationOutput for any GradeMode (property)" {
        val cells = recognizedCellsOf(Grade1Alphabet.encode("hello"))
        checkAll(100, Arb.enum<GradeMode>()) { mode ->
            val controller = makeController()
            val output = controller.override(mode, cells)
            // Output is a valid data class — text must be non-empty for "hello".
            output.text.isNotEmpty() shouldBe true
        }
    }

    "resolveGrade is consistent with override output grade (property)" {
        // After an override, the resolved grade must match what the override used.
        val cells = recognizedCellsOf(Grade1Alphabet.encode("abc"))
        checkAll(100, Arb.enum<GradeMode>()) { mode ->
            val recording = CapturingTranslationEngine(InMemoryGrade1TranslationEngine())
            val controller = GradeController(
                detector = HeuristicGradeDetector(),
                engine = recording,
                initialMode = GradeMode.AUTO,
            )
            controller.override(mode, cells)
            // The grade used in the translate call must equal resolveGrade after the override.
            val usedGrade = recording.calls.last().second
            usedGrade shouldBe controller.resolveGrade(cells)
        }
    }
})

/**
 * A [TranslationEngine] decorator that records every (cells, grade) pair passed
 * to [translate] and delegates to [delegate]. Used to assert that the override
 * path re-runs translation on the same cells and nothing else.
 */
private class CapturingTranslationEngine(
    private val delegate: TranslationEngine,
) : TranslationEngine {

    val calls = mutableListOf<Pair<List<RecognizedCell>, Grade>>()

    override fun translate(cells: List<RecognizedCell>, grade: Grade): TranslationOutput {
        calls.add(cells to grade)
        return delegate.translate(cells, grade)
    }
}

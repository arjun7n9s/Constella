package com.constella.braille.pipeline.camera

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll

/**
 * JVM unit + property tests for the pure, framework-free [CameraPolicy].
 *
 * These cover the deterministic camera decisions task 18.1 owns — torch
 * default/override (Req 1.2, 1.3, 1.8), focus-distance clamping to the
 * supported 5–25 cm window (Req 1.4), and capture/analysis resolution selection
 * (Req 1.5) — with no CameraX (or Android) dependency. The device-dependent
 * CameraX wiring in CameraXCameraModule is verified separately on a device.
 */
class CameraPolicyTest : StringSpec({

    val tolerance = 1e-4f
    val minCm = ScanConstants.Camera.MIN_FOCUS_DISTANCE_CM.toFloat()
    val maxCm = ScanConstants.Camera.MAX_FOCUS_DISTANCE_CM.toFloat()

    // --- Torch default + override (Req 1.2, 1.3, 1.8) ------------------------

    "torch is on by default in Embossed_Mode for Raking_Light" {
        CameraPolicy.defaultTorchEnabled(ScanningMode.EMBOSSED).shouldBeTrue()
    }

    "torch is on by default in Handwritten_Mode" {
        CameraPolicy.defaultTorchEnabled(ScanningMode.HANDWRITTEN).shouldBeTrue()
    }

    "DEFAULT preference follows the mode default (torch on) in Embossed_Mode" {
        CameraPolicy.resolveTorchEnabled(ScanningMode.EMBOSSED, TorchPreference.DEFAULT)
            .shouldBeTrue()
    }

    "explicit OFF wins over the Embossed_Mode keep-on policy (Req 1.8)" {
        CameraPolicy.resolveTorchEnabled(ScanningMode.EMBOSSED, TorchPreference.OFF)
            .shouldBeFalse()
    }

    "explicit ON wins regardless of mode" {
        CameraPolicy.resolveTorchEnabled(ScanningMode.HANDWRITTEN, TorchPreference.ON)
            .shouldBeTrue()
    }

    // --- Focus-distance clamping to 5-25 cm (Req 1.4) ------------------------

    "a distance below the minimum clamps up to the minimum" {
        CameraPolicy.clampWorkingDistanceCm(minCm - 3f) shouldBe (minCm plusOrMinus tolerance)
    }

    "a distance above the maximum clamps down to the maximum" {
        CameraPolicy.clampWorkingDistanceCm(maxCm + 50f) shouldBe (maxCm plusOrMinus tolerance)
    }

    "a distance inside the window is returned unchanged" {
        val inside = (minCm + maxCm) / 2f
        CameraPolicy.clampWorkingDistanceCm(inside) shouldBe (inside plusOrMinus tolerance)
    }

    "the supported-distance predicate is inclusive of both ends" {
        CameraPolicy.isWithinWorkingDistance(minCm).shouldBeTrue()
        CameraPolicy.isWithinWorkingDistance(maxCm).shouldBeTrue()
        CameraPolicy.isWithinWorkingDistance(minCm - 0.1f).shouldBeFalse()
        CameraPolicy.isWithinWorkingDistance(maxCm + 0.1f).shouldBeFalse()
    }

    "the default working distance is the midpoint of the window and is supported" {
        CameraPolicy.defaultWorkingDistanceCm shouldBe (((minCm + maxCm) / 2f) plusOrMinus tolerance)
        CameraPolicy.isWithinWorkingDistance(CameraPolicy.defaultWorkingDistanceCm).shouldBeTrue()
    }

    // --- Working distance -> diopter conversion (Req 1.4) --------------------

    "diopters are 100 / clamped-centimeters" {
        CameraPolicy.workingDistanceToDiopters(10f) shouldBe (10f plusOrMinus tolerance)
        CameraPolicy.workingDistanceToDiopters(minCm) shouldBe ((100f / minCm) plusOrMinus tolerance)
        CameraPolicy.workingDistanceToDiopters(maxCm) shouldBe ((100f / maxCm) plusOrMinus tolerance)
    }

    "an out-of-range distance is clamped before diopter conversion" {
        // Far beyond the window clamps to maxCm -> the minimum diopters.
        CameraPolicy.workingDistanceToDiopters(maxCm + 1000f) shouldBe
            (CameraPolicy.minFocusDiopters plusOrMinus tolerance)
        // Below the window clamps to minCm -> the maximum diopters.
        CameraPolicy.workingDistanceToDiopters(0f) shouldBe
            (CameraPolicy.maxFocusDiopters plusOrMinus tolerance)
    }

    "nearer working distance yields larger diopters than farther distance" {
        val near = CameraPolicy.workingDistanceToDiopters(minCm)
        val far = CameraPolicy.workingDistanceToDiopters(maxCm)
        (near > far).shouldBeTrue()
    }

    // --- Still-capture resolution selection (Req 1.5) ------------------------

    "still capture selects the highest-area resolution" {
        val available = listOf(
            ImageSize(640, 480),
            ImageSize(4032, 3024),
            ImageSize(1920, 1080),
        )
        CameraPolicy.selectStillResolution(available) shouldBe ImageSize(4032, 3024)
    }

    "still capture returns null when no resolutions are reported" {
        CameraPolicy.selectStillResolution(emptyList()).shouldBeNull()
    }

    // --- Analysis-stream resolution selection (Req 12.2) ---------------------

    "analysis stream picks the largest resolution within the long-side budget" {
        val available = listOf(
            ImageSize(320, 240),
            ImageSize(1280, 720),
            ImageSize(4032, 3024),
        )
        CameraPolicy.selectAnalysisResolution(available, maxLongSidePx = 1280) shouldBe
            ImageSize(1280, 720)
    }

    "analysis stream falls back to the smallest option when all exceed the budget" {
        val available = listOf(
            ImageSize(4032, 3024),
            ImageSize(3000, 2000),
        )
        CameraPolicy.selectAnalysisResolution(available, maxLongSidePx = 1280) shouldBe
            ImageSize(3000, 2000)
    }

    "analysis stream returns null when no resolutions are reported" {
        CameraPolicy.selectAnalysisResolution(emptyList()).shouldBeNull()
    }

    // --- Universal invariants over arbitrary input ---------------------------

    "clamped working distance is always within the supported window" {
        checkAll(Arb.float(min = -1000f, max = 1000f)) { d ->
            // Skip NaN: coerceIn semantics for NaN are not meaningful here.
            if (!d.isNaN()) {
                CameraPolicy.isWithinWorkingDistance(CameraPolicy.clampWorkingDistanceCm(d))
                    .shouldBeTrue()
            }
        }
    }

    "diopters from any finite distance stay within the supported diopter range" {
        val epsilon = 1e-3f
        checkAll(Arb.float(min = -1000f, max = 1000f)) { d ->
            if (!d.isNaN()) {
                val diopters = CameraPolicy.workingDistanceToDiopters(d)
                (diopters >= CameraPolicy.minFocusDiopters - epsilon).shouldBeTrue()
                (diopters <= CameraPolicy.maxFocusDiopters + epsilon).shouldBeTrue()
            }
        }
    }
})

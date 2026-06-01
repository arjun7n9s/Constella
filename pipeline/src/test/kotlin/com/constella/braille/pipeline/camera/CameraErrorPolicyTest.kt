package com.constella.braille.pipeline.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * JVM unit tests for the pure, framework-free typed-error model task 18.2 owns:
 * [CameraError], [CameraErrorPolicy], [RecoveryAction], and the
 * [CameraState.error] factory.
 *
 * These verify the deterministic mapping from each typed camera/permission
 * error to its Operator-facing presentation — a non-empty dual-channel message,
 * the correct recovery control (open-settings for permission, retry for
 * unavailable/capture, none for the informational conditions), and the correct
 * preview-preservation behavior — with no CameraX (or Android) dependency. The
 * device-dependent detection wiring in [CameraXCameraModule] is verified
 * separately on a device.
 *
 * Covers Req 1.6 (NoTorch), 1.7 (PermissionDenied), 1.9 (NoMacroFocus),
 * 1.10 (Unavailable), and 1.11 (CaptureFailed).
 */
class CameraErrorPolicyTest : StringSpec({

    // --- Every error kind produces a well-formed presentation ----------------

    "every camera error maps to a non-empty dual-channel message" {
        CameraError.entries.forEach { error ->
            CameraErrorPolicy.present(error).message.shouldNotBeBlank()
        }
    }

    "present is total and echoes back the error it was asked about" {
        CameraError.entries.forEach { error ->
            CameraErrorPolicy.present(error).error shouldBe error
        }
    }

    "the policy covers exactly the five typed camera error kinds" {
        CameraError.entries.map { it } shouldContainExactlyInAnyOrder listOf(
            CameraError.NO_TORCH,
            CameraError.PERMISSION_DENIED,
            CameraError.NO_MACRO_FOCUS,
            CameraError.UNAVAILABLE,
            CameraError.CAPTURE_FAILED,
        )
    }

    // --- Recovery control per error kind -------------------------------------

    "no controllable torch offers no recovery control and keeps scanning (Req 1.6)" {
        val p = CameraErrorPolicy.present(CameraError.NO_TORCH)
        p.recoveryAction shouldBe RecoveryAction.NONE
        p.preservePreview.shouldBeTrue()
    }

    "denied permission offers an open-settings control and stops the preview (Req 1.7)" {
        val p = CameraErrorPolicy.present(CameraError.PERMISSION_DENIED)
        p.recoveryAction shouldBe RecoveryAction.OPEN_SETTINGS
        p.preservePreview.shouldBeFalse()
    }

    "missing close-range focus offers no recovery control and keeps scanning (Req 1.9)" {
        val p = CameraErrorPolicy.present(CameraError.NO_MACRO_FOCUS)
        p.recoveryAction shouldBe RecoveryAction.NONE
        p.preservePreview.shouldBeTrue()
    }

    "camera unavailable offers a retry control and has no preview to preserve (Req 1.10)" {
        val p = CameraErrorPolicy.present(CameraError.UNAVAILABLE)
        p.recoveryAction shouldBe RecoveryAction.RETRY
        p.preservePreview.shouldBeFalse()
    }

    "capture failure offers a retry control and preserves the live preview (Req 1.11)" {
        val p = CameraErrorPolicy.present(CameraError.CAPTURE_FAILED)
        p.recoveryAction shouldBe RecoveryAction.RETRY
        p.preservePreview.shouldBeTrue()
    }

    // --- Open-settings is reserved for the permission case -------------------

    "open-settings recovery is offered only for denied permission" {
        CameraError.entries
            .filter { CameraErrorPolicy.present(it).recoveryAction == RecoveryAction.OPEN_SETTINGS }
            .shouldContainExactlyInAnyOrder(listOf(CameraError.PERMISSION_DENIED))
    }

    "retry recovery is offered only for the unavailable and capture-failed cases" {
        CameraError.entries
            .filter { CameraErrorPolicy.present(it).recoveryAction == RecoveryAction.RETRY }
            .shouldContainExactlyInAnyOrder(
                listOf(CameraError.UNAVAILABLE, CameraError.CAPTURE_FAILED),
            )
    }

    "informational (no-control) errors are exactly the torch and macro-focus cases" {
        CameraError.entries
            .filter { CameraErrorPolicy.present(it).recoveryAction == RecoveryAction.NONE }
            .shouldContainExactlyInAnyOrder(
                listOf(CameraError.NO_TORCH, CameraError.NO_MACRO_FOCUS),
            )
    }

    // --- Preview-preservation matches the requirements -----------------------

    "preview is preserved exactly for the scanning-continues and capture-retry cases" {
        CameraError.entries
            .filter { CameraErrorPolicy.present(it).preservePreview }
            .shouldContainExactlyInAnyOrder(
                listOf(
                    CameraError.NO_TORCH,
                    CameraError.NO_MACRO_FOCUS,
                    CameraError.CAPTURE_FAILED,
                ),
            )
    }

    // --- CameraState.error factory stays consistent with the policy ----------

    "CameraState.error mirrors the policy presentation for every kind" {
        CameraError.entries.forEach { error ->
            val expected = CameraErrorPolicy.present(error)
            val state = CameraState.error(error)
            state.error shouldBe expected.error
            state.message shouldBe expected.message
            state.recoveryAction shouldBe expected.recoveryAction
            state.preservePreview shouldBe expected.preservePreview
        }
    }

    "CameraState.error yields a CameraState.Error carrying a non-empty message" {
        CameraError.entries.forEach { error ->
            val state: CameraState = CameraState.error(error)
            (state is CameraState.Error).shouldBeTrue()
            (state as CameraState.Error).message.shouldNotBeBlank()
        }
    }
})

package com.constella.braille.pipeline.camera

import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.runtime.preprocess.CapturedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Camera_Module: owns the CameraX lifecycle, drives the Torch and
 * close-range focus policy, exposes the low-resolution live analysis stream,
 * and performs the highest-resolution still capture.
 *
 * The interface is intentionally narrow so each behaviour can be exercised in
 * isolation. The deterministic *decisions* behind these operations (torch
 * on/off, focus-distance clamping, resolution selection) live in the pure
 * [CameraPolicy] and are unit-tested on the JVM; the device-dependent CameraX
 * wiring that applies them lives in [CameraXCameraModule].
 *
 * Responsibilities (Req 1, 12):
 *  - Start a live preview with the Torch on by default for Raking_Light
 *    (Req 1.2), keeping it on in Embossed_Mode unless toggled (Req 1.8).
 *  - Provide an Operator-facing Torch toggle (Req 1.3) via [setTorch].
 *  - Maintain focus on a document 5–25 cm from the lens (Req 1.4) using the
 *    [CameraPolicy] working-distance window.
 *  - Stream low-resolution [AnalysisFrame]s for Live_Mode and the
 *    Alignment_Guide (Req 12.2), and capture a single highest-resolution still
 *    on demand (Req 1.5).
 *  - Apply the per-[ScanningMode] torch/focus policy (Req 1.8) via
 *    [applyScanningMode].
 *
 * The typed camera/permission error states and their recovery controls
 * (Req 1.6, 1.7, 1.9, 1.10, 1.11) are layered on in task 18.2; here
 * [previewState] only carries the minimal [CameraState] surface.
 *
 * _Requirements: 1.2, 1.3, 1.4, 1.5, 1.8_
 */
interface CameraModule {

    /** The current camera lifecycle state (Starting / Previewing / Error). */
    val previewState: StateFlow<CameraState>

    /** The low-resolution live analysis frame stream (Live_Mode + alignment). */
    val analysisFrames: Flow<AnalysisFrame>

    /**
     * Toggle the Torch on or off (Req 1.3). An explicit call wins over the
     * scanning-mode default, including the Embossed_Mode "keep torch on"
     * policy, until changed again (Req 1.8).
     */
    fun setTorch(enabled: Boolean)

    /**
     * Apply the torch/focus policy for [mode] (Req 1.8). Re-evaluates the
     * effective Torch state against the Operator's current torch preference and
     * re-biases focus to the supported close-range window (Req 1.4).
     */
    fun applyScanningMode(mode: ScanningMode)

    /**
     * Capture a single still frame at the highest still resolution the active
     * camera supports (Req 1.5). Returns a [Result] so capture failures can be
     * surfaced without throwing; the typed `CaptureFailed` state and retry
     * affordance are added in task 18.2 (Req 1.11).
     */
    suspend fun captureStill(): Result<CapturedImage>
}

package com.constella.braille.pipeline.camera

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize

/**
 * The Operator's torch intent, layered on top of the per-[ScanningMode] default.
 *
 * The Camera_Module forces the Torch on by default to create Raking_Light
 * (Req 1.2) and keeps it on in Embossed_Mode (Req 1.8); the Operator may
 * override that with the torch toggle (Req 1.3). This tri-state captures that
 * relationship without the policy having to know whether a value came from the
 * mode default or an explicit tap:
 *
 *  - [DEFAULT] — the Operator has not touched the toggle; follow the mode's
 *    default torch policy.
 *  - [ON] — the Operator explicitly turned the Torch on.
 *  - [OFF] — the Operator explicitly turned the Torch off (this is the only way
 *    the Torch is off in Embossed_Mode, per Req 1.8).
 *
 * An explicit [ON]/[OFF] is intentionally sticky across scanning-mode changes:
 * once the Operator has made a choice it is respected until they change it
 * again, so switching modes never silently re-lights a Torch the Operator
 * turned off.
 */
enum class TorchPreference { DEFAULT, ON, OFF }

/**
 * Pure, framework-free camera policy: the deterministic decisions the
 * Camera_Module makes, expressed as plain functions over plain values so they
 * can be unit-tested on the JVM with no CameraX (or Android) dependency.
 *
 * Everything here is a *decision* — what the torch state should be, how a
 * working distance maps to a lens focus distance and the clamp range that keeps
 * it within the supported 5–25 cm window, and which of a set of camera-reported
 * resolutions to use for the still capture and the low-resolution analysis
 * stream. The actual CameraX wiring that *applies* these decisions lives in the
 * thin, device-dependent [CameraXCameraModule]; keeping the decisions here means
 * the policy is verifiable without a device.
 *
 * Focus-distance bounds come from [ScanConstants.Camera], the single source of
 * truth for the working-distance window (Req 1.4); this object never hard-codes
 * the 5 cm / 25 cm numbers itself.
 *
 * _Requirements: 1.2, 1.3, 1.4, 1.5, 1.8_
 */
object CameraPolicy {

    /** Nearest supported working distance, in centimeters (Req 1.4). */
    val minFocusDistanceCm: Float = ScanConstants.Camera.MIN_FOCUS_DISTANCE_CM.toFloat()

    /** Farthest supported working distance, in centimeters (Req 1.4). */
    val maxFocusDistanceCm: Float = ScanConstants.Camera.MAX_FOCUS_DISTANCE_CM.toFloat()

    /**
     * Default low-resolution analysis-stream bound: the longest side of an
     * analysis frame should not exceed this many pixels. The Live_Mode analysis
     * stream is deliberately low-resolution so the per-frame budget stays within
     * the sub-second live-update target (Req 12.2); the highest-resolution path
     * is reserved for the still capture (Req 1.5). This is a calibration default
     * (not pinned by a requirement) and is tunable against the latency budget.
     */
    const val DEFAULT_ANALYSIS_MAX_LONG_SIDE_PX: Int = 1280

    /**
     * The default Torch state for [mode] when the Operator has not overridden
     * it. The Torch is forced on by default in every mode to create the
     * Raking_Light needed for embossed-dot shadows (Req 1.2); Embossed_Mode in
     * particular keeps it on (Req 1.8).
     */
    fun defaultTorchEnabled(mode: ScanningMode): Boolean = when (mode) {
        ScanningMode.EMBOSSED -> true
        ScanningMode.HANDWRITTEN -> true
    }

    /**
     * Resolves the effective Torch state from the active [mode] and the
     * Operator's [preference]. An explicit [TorchPreference.ON] or
     * [TorchPreference.OFF] always wins over the mode default (Req 1.3);
     * [TorchPreference.DEFAULT] follows [defaultTorchEnabled], which is `true`
     * on preview start (Req 1.2) and keeps the Torch on in Embossed_Mode unless
     * the Operator turns it off (Req 1.8).
     */
    fun resolveTorchEnabled(mode: ScanningMode, preference: TorchPreference): Boolean =
        when (preference) {
            TorchPreference.ON -> true
            TorchPreference.OFF -> false
            TorchPreference.DEFAULT -> defaultTorchEnabled(mode)
        }

    /**
     * Clamps a desired working [distanceCm] into the supported `[min, max]`
     * focus window (Req 1.4) so focus is only ever requested within the range
     * the System can reliably hold a document at.
     */
    fun clampWorkingDistanceCm(distanceCm: Float): Float =
        distanceCm.coerceIn(minFocusDistanceCm, maxFocusDistanceCm)

    /**
     * True when [distanceCm] lies within the supported `[min, max]` working
     * distance window (Req 1.4), inclusive of both ends.
     */
    fun isWithinWorkingDistance(distanceCm: Float): Boolean =
        distanceCm >= minFocusDistanceCm && distanceCm <= maxFocusDistanceCm

    /**
     * Converts a working [distanceCm] to a lens focus distance in **diopters**
     * (reciprocal meters, `1/m`), the unit CameraX/Camera2 uses for manual focus
     * (`LENS_FOCUS_DISTANCE`). The input is first clamped to the supported
     * window (Req 1.4), so the returned diopter value is always within
     * `[minFocusDiopters, maxFocusDiopters]`.
     *
     * Derivation: `diopters = 1 / meters = 1 / (cm / 100) = 100 / cm`.
     */
    fun workingDistanceToDiopters(distanceCm: Float): Float =
        100f / clampWorkingDistanceCm(distanceCm)

    /**
     * Smallest focus distance in diopters, corresponding to the *farthest*
     * supported working distance ([maxFocusDistanceCm]). Larger distance ⇒
     * smaller diopters.
     */
    val minFocusDiopters: Float get() = 100f / maxFocusDistanceCm

    /**
     * Largest focus distance in diopters, corresponding to the *nearest*
     * supported working distance ([minFocusDistanceCm]). Smaller distance ⇒
     * larger diopters.
     */
    val maxFocusDiopters: Float get() = 100f / minFocusDistanceCm

    /**
     * The default working distance to request when none is otherwise specified:
     * the midpoint of the supported window. Used by the camera wiring to seed a
     * close-range manual focus that sits comfortably inside the 5–25 cm window
     * (Req 1.4).
     */
    val defaultWorkingDistanceCm: Float get() = (minFocusDistanceCm + maxFocusDistanceCm) / 2f

    /**
     * Selects the still-capture resolution: the highest-resolution option (by
     * pixel area) the active camera reports (Req 1.5). Returns `null` when
     * [available] is empty so the caller can fall back to the camera default.
     */
    fun selectStillResolution(available: List<ImageSize>): ImageSize? =
        available.maxByOrNull { it.area }

    /**
     * Selects the low-resolution analysis-stream resolution: the largest option
     * whose longer side does not exceed [maxLongSidePx], or — when every option
     * is larger than that bound — the smallest available option. This keeps the
     * Live_Mode analysis frames small for the sub-second update budget
     * (Req 12.2) while still preferring the best frame within the budget.
     * Returns `null` when [available] is empty.
     */
    fun selectAnalysisResolution(
        available: List<ImageSize>,
        maxLongSidePx: Int = DEFAULT_ANALYSIS_MAX_LONG_SIDE_PX,
    ): ImageSize? {
        if (available.isEmpty()) return null
        val withinBudget = available.filter { maxOf(it.width, it.height) <= maxLongSidePx }
        return if (withinBudget.isNotEmpty()) {
            withinBudget.maxByOrNull { it.area }
        } else {
            available.minByOrNull { it.area }
        }
    }
}

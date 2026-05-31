package com.constella.braille.domain.model

/**
 * A single piece of alignment guidance for the Operator.
 *
 * Exactly one guidance value is active at a time; the Alignment_Guide surfaces
 * the condition furthest from its threshold (task 3 implements that selection
 * and the debounced ready-to-scan state machine). This sealed type is defined
 * with the data models because [ScanStatus.LowConfidence] carries the likely
 * cause as an [AlignmentGuidance] value.
 *
 * _Requirements: 14.2_
 */
sealed interface AlignmentGuidance {
    /** Document fills too little of the frame — move the camera closer. */
    data object MoveCloser : AlignmentGuidance

    /** Document fills too much of the frame — move the camera farther away. */
    data object MoveFarther : AlignmentGuidance

    /** Too much apparent motion — hold the camera steady. */
    data object HoldSteady : AlignmentGuidance

    /** Frame is too dark — turn on the Torch or add light. */
    data object AddLight : AlignmentGuidance

    /** Document plane is too tilted — flatten it relative to the lens. */
    data object FlattenDocument : AlignmentGuidance

    /** No document detected — point the camera at a Braille document. */
    data object PointAtDocument : AlignmentGuidance

    /** All alignment conditions pass — the document is ready to scan. */
    data object ReadyToScan : AlignmentGuidance
}

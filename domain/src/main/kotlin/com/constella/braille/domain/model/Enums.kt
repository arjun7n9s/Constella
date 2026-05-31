package com.constella.braille.domain.model

/**
 * Core enums for the Braille Scanner domain.
 *
 * These are the orthogonal mode/grade settings the design keeps separate:
 *  - [ScanningMode] — the *what* (detection/preprocessing parameter set + tier).
 *  - [CaptureMode]  — the *how* (frame source and latency budget).
 *  - [Grade]        — the *resolved* translation grade actually used.
 *  - [GradeMode]    — the Operator-facing grade *setting* (which may be Auto).
 */

/**
 * The active scanning profile.
 *
 * [EMBOSSED] is the high-accuracy tier and the session default; [HANDWRITTEN]
 * is the explicitly labeled lower-confidence second tier.
 *
 * _Requirements: 9.1, 9.2, 9.3, 9.5_
 */
enum class ScanningMode {
    EMBOSSED,
    HANDWRITTEN,
}

/**
 * The capture path: continuous near-real-time [LIVE] processing versus a
 * single high-resolution [CAPTURE] still for the most reliable recognition.
 *
 * _Requirements: 12.1_
 */
enum class CaptureMode {
    LIVE,
    CAPTURE,
}

/**
 * A resolved English Braille translation grade. This is the concrete grade
 * actually handed to the Translation_Engine for a given scan (never `Auto`).
 *
 * _Requirements: 7.2, 8.1_
 */
enum class Grade {
    GRADE_1,
    GRADE_2,
}

/**
 * The Operator-facing grade setting.
 *
 * [AUTO] defers to the Grade_Detector estimate; [GRADE_1] and [GRADE_2] are
 * one-tap manual overrides. The setting defaults to [AUTO] each session.
 *
 * _Requirements: 8.1, 8.3, 8.6_
 */
enum class GradeMode {
    AUTO,
    GRADE_1,
    GRADE_2,
}

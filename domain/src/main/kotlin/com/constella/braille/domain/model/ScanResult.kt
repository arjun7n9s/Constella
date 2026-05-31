package com.constella.braille.domain.model

/**
 * The result of a completed scan: the Recognized_Text plus all provenance the
 * UI needs to display, mark, and explain the output.
 *
 * - [recognizedText] — the English text produced by the Translation_Engine.
 * - [charSpans] — per-character-span mapping back to source cells, used to mark
 *   uncertain characters (Req 10.3).
 * - [overallConfidence] — the per-scan Confidence_Score (Req 10.2, 13.9).
 * - [scanningMode] — the active scanning profile for this scan (Req 9).
 * - [resolvedGrade] — the concrete grade actually used for translation.
 * - [gradeMode] — the Operator's grade setting at scan time (Auto/G1/G2).
 * - [gradeWasAutoDetected] — whether [resolvedGrade] came from auto-detection
 *   (Req 8.2).
 * - [untranslatableCells] — indices of recognized cells liblouis could not
 *   translate (Req 7.5).
 * - [perspectiveCorrected] — whether perspective correction was applied or
 *   skipped (Req 3.5 provenance).
 * - [status] — the outcome classification of this scan.
 *
 * _Requirements: 10.2_
 */
data class ScanResult(
    val recognizedText: String,
    val charSpans: List<CharSpan>,
    val overallConfidence: Confidence,
    val scanningMode: ScanningMode,
    val resolvedGrade: Grade,
    val gradeMode: GradeMode,
    val gradeWasAutoDetected: Boolean,
    val untranslatableCells: List<Int>,
    val perspectiveCorrected: Boolean,
    val status: ScanStatus,
)

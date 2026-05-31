package com.constella.braille.domain.model

/**
 * The outcome classification of a completed (or attempted) scan.
 *
 * The pipeline reduces its result to exactly one of these states so the UI and
 * Notifier can react uniformly:
 *  - [Success] — recognition produced usable Recognized_Text.
 *  - [NoBrailleRecognized] — no usable dot candidates or no inferable
 *    Braille-like structure.
 *  - [StructureButNoCell] — Braille-like structure was inferable but no valid
 *    2x3 cell could be formed; prompt the Operator to adjust before a rescan.
 *  - [LowConfidence] — overall confidence is below the rescan-recommendation
 *    threshold; carries the [likelyCause] derived from the failed alignment
 *    condition.
 *  - [ProcessingError] — an unexpected processing error occurred; carries an
 *    explanatory [message].
 *
 * _Requirements: 4.5, 4.6, 14.1, 14.2, 14.3_
 */
sealed interface ScanStatus {
    /** Recognition succeeded and produced usable text. */
    data object Success : ScanStatus

    /** No usable dots or no inferable Braille-like structure (Req 4.6, 14.1). */
    data object NoBrailleRecognized : ScanStatus

    /** Structure inferable but no valid 2x3 cell could be formed (Req 4.5). */
    data object StructureButNoCell : ScanStatus

    /**
     * Overall scan confidence below the rescan-recommendation threshold.
     *
     * [likelyCause] is the alignment condition most likely responsible, used to
     * tell the Operator how to improve the next scan (Req 14.2).
     */
    data class LowConfidence(val likelyCause: AlignmentGuidance) : ScanStatus

    /** An unexpected processing error occurred during the scan (Req 14.3). */
    data class ProcessingError(val message: String) : ScanStatus
}

package com.constella.braille.domain.notify

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.ScanStatus

/**
 * Pure decision policy for the low-confidence / rescan-recommendation rule
 * (Req 14.2, Property 24).
 *
 * Given a completed scan's overall confidence and the alignment condition most
 * likely responsible for poor quality, this policy decides whether the scan is
 * "low confidence". When the overall confidence is **below** the centralized
 * [ConfidenceThresholds.RESCAN_RECOMMENDATION] threshold, it returns a
 * [ScanStatus.LowConfidence] carrying that failed condition as its
 * `likelyCause`; the human-readable rescan recommendation for that status is
 * produced by [ScanStatusMessages.messageFor] (Req 14.2).
 *
 * The threshold is always read from [ConfidenceThresholds] — never hard-coded —
 * so tuning the rescan-recommendation boundary stays a single-source-of-truth
 * edit. The comparison is strict (`<`): a scan whose confidence exactly equals
 * the threshold is treated as acceptable, mirroring the requirement's "below the
 * defined rescan-recommendation threshold" wording.
 *
 * This object is intentionally pure and side-effect free; it neither delivers
 * messages nor mutates state. Wiring the resulting [ScanStatus] through the
 * [Notifier] is the [ScanCoordinator]'s concern (task 21).
 *
 * _Requirements: 14.2, 14.4_
 */
object LowConfidencePolicy {

    /**
     * Decide whether [overallConfidence] is low enough to recommend a rescan.
     *
     * @param overallConfidence the scan's overall confidence as a normalized
     *   score in `[0, 1]`.
     * @return `true` when [overallConfidence] is strictly below
     *   [ConfidenceThresholds.RESCAN_RECOMMENDATION].
     */
    fun isLowConfidence(overallConfidence: Float): Boolean =
        overallConfidence < ConfidenceThresholds.RESCAN_RECOMMENDATION

    /**
     * Classify a completed scan's [overallConfidence] against the
     * rescan-recommendation threshold.
     *
     * When the confidence is below the threshold, returns
     * [ScanStatus.LowConfidence] whose `likelyCause` is
     * [failedAlignmentCondition] — the alignment condition the upstream pipeline
     * judged most responsible for the poor capture, so the operator-facing
     * message can state the likely cause (Req 14.2). When the confidence meets
     * or exceeds the threshold, returns [ScanStatus.Success]; the scan is not
     * flagged for a rescan on confidence grounds.
     *
     * @param overallConfidence the scan's overall confidence as a normalized
     *   score in `[0, 1]`.
     * @param failedAlignmentCondition the alignment condition most likely
     *   responsible for low quality, carried into [ScanStatus.LowConfidence] as
     *   its likely cause.
     * @return [ScanStatus.LowConfidence] carrying [failedAlignmentCondition]
     *   when below threshold, otherwise [ScanStatus.Success].
     */
    fun classifyConfidence(
        overallConfidence: Float,
        failedAlignmentCondition: AlignmentGuidance,
    ): ScanStatus =
        if (isLowConfidence(overallConfidence)) {
            ScanStatus.LowConfidence(likelyCause = failedAlignmentCondition)
        } else {
            ScanStatus.Success
        }
}

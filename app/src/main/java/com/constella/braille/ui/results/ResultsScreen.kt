package com.constella.braille.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.CharSpan
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.Grade
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.ScanResult
import com.constella.braille.domain.model.ScanStatus
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.ui.theme.BrailleScannerTheme
import com.constella.braille.ui.theme.BrailleTokens

/**
 * Results presentation composable (Req 10).
 *
 * Renders the Recognized_Text using **only** Design_Tokens (Req 10.5), visually
 * marks every character whose governing span confidence is below the
 * display-confidence threshold as uncertain (Req 10.3), shows the per-scan
 * Confidence_Score (Req 10.2, 13.9), and exposes a copy-to-clipboard control
 * (Req 10.4).
 *
 * This composable is intentionally **thin**: the uncertainty-segmentation rule
 * lives in [segmentByUncertainty] (pure, Compose-free, unit-tested) and the
 * confidence label in [confidencePercentLabel]. Here we only translate those
 * results into token-styled Compose nodes.
 *
 * Navigation/wiring (task 21.3) and display-unavailable retention (task 16.4)
 * are deliberately out of scope; this owns only the rendering surface.
 *
 * _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 13.9_
 */
@Composable
fun ResultsContent(
    result: ScanResult,
    modifier: Modifier = Modifier,
    clipboard: ClipboardManager = LocalClipboardManager.current,
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    // Pure mapping → token styling. The annotated string is the only place a
    // visual (the uncertain mark) is applied, and it reads its color exclusively
    // from the `uncertainMark` Design_Token (Req 10.3, 10.5). Keyed on the text,
    // spans, and token color so a theme/high-contrast swap reflows (Req 18.4).
    val styled: AnnotatedString =
        remember(result.recognizedText, result.charSpans, colors.uncertainMark) {
            buildRecognizedText(
                text = result.recognizedText,
                charSpans = result.charSpans,
                uncertainColor = colors.uncertainMark,
            )
        }

    val confidenceLabel = confidencePercentLabel(result.overallConfidence.value)

    Surface(color = colors.surface, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            // Per-scan Confidence_Score (Req 10.2, 13.9).
            Text(
                text = "Confidence: $confidenceLabel",
                style = typography.caption,
                color = colors.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "Scan confidence $confidenceLabel"
                },
            )

            // Recognized_Text with uncertain characters marked (Req 10.1, 10.3, 10.5).
            Text(
                text = styled,
                style = typography.recognizedText,
                color = colors.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Recognized text" },
            )

            Spacer(modifier = Modifier.height(spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                // Copy-to-clipboard control (Req 10.4). Primary action meets the
                // minimum touch-target size via the spacing token (Req 17.3).
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(result.recognizedText)) },
                    enabled = result.recognizedText.isNotEmpty(),
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = spacing.touchTargetMin,
                            minHeight = spacing.touchTargetMin,
                        )
                        .semantics { contentDescription = "Copy recognized text to clipboard" },
                ) {
                    Text(text = "Copy text", style = typography.label, color = colors.accent)
                }
            }
        }
    }
}

/**
 * Build the token-styled Recognized_Text: each uncertain run (per
 * [segmentByUncertainty]) is colored with the `uncertainMark` token and
 * underlined so the marking survives a grayscale / high-contrast view
 * (Req 10.3). All other runs render with the default text color.
 *
 * The [uncertainColor] is the forwarded `uncertainMark` Design_Token — the only
 * color source — so this builder introduces no hard-coded literal (Req 10.5).
 */
private fun buildRecognizedText(
    text: String,
    charSpans: List<CharSpan>,
    uncertainColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    val segments = segmentByUncertainty(text, charSpans, ConfidenceThresholds.DISPLAY_CONFIDENCE)
    for (segment in segments) {
        if (segment.isUncertain) {
            withStyle(
                SpanStyle(
                    color = uncertainColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(segment.text)
            }
        } else {
            append(segment.text)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsContentPreview() {
    val sample = ScanResult(
        recognizedText = "hello world",
        // "world" (indices 6..11) recognized with low confidence -> marked uncertain.
        charSpans = listOf(
            CharSpan(0, 6, listOf(0, 1, 2, 3, 4), Confidence(0.95f)),
            CharSpan(6, 11, listOf(5, 6, 7, 8, 9), Confidence(0.30f)),
        ),
        overallConfidence = Confidence(0.78f),
        scanningMode = ScanningMode.EMBOSSED,
        resolvedGrade = Grade.GRADE_2,
        gradeMode = GradeMode.AUTO,
        gradeWasAutoDetected = true,
        untranslatableCells = emptyList(),
        perspectiveCorrected = true,
        status = ScanStatus.Success,
    )
    BrailleScannerTheme {
        ResultsContent(result = sample)
    }
}

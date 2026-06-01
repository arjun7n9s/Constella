package com.constella.braille.ui.scanning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.ui.theme.BrailleTokens

/**
 * Scanning screen composable — the primary camera + alignment view (Req 12.1).
 *
 * Renders the camera preview area, alignment guidance overlay, processing
 * indicator, torch toggle, capture button, and mode/settings access. The
 * actual CameraX preview is injected as a composable slot ([cameraPreview])
 * since this layer is framework-free relative to the camera pipeline.
 *
 * All visuals read exclusively from Design_Tokens (Req 10.5, 18.2).
 *
 * _Requirements: 12.1, 12.5, 14.3_
 */
@Composable
fun ScanningScreen(
    guidance: AlignmentGuidance?,
    isScanning: Boolean,
    activeModeName: String,
    torchEnabled: Boolean,
    onCapture: () -> Unit,
    onTorchToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    Surface(color = colors.surface, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: mode label + settings.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.m, vertical = spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activeModeName,
                    style = typography.subtitle,
                    color = colors.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = "Active scanning mode: $activeModeName"
                    },
                )
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = spacing.touchTargetMin,
                            minHeight = spacing.touchTargetMin,
                        )
                        .semantics { contentDescription = "Open settings" },
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = colors.onSurface,
                    )
                }
            }

            // Camera preview area with guidance overlay.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(spacing.m)),
                contentAlignment = Alignment.Center,
            ) {
                // Camera preview slot.
                cameraPreview()

                // Guidance overlay.
                if (guidance != null && guidance != AlignmentGuidance.ReadyToScan) {
                    GuidanceOverlay(
                        guidance = guidance,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Ready-to-scan indicator.
                if (guidance == AlignmentGuidance.ReadyToScan) {
                    ReadyIndicator(modifier = Modifier.align(Alignment.TopCenter))
                }

                // Processing indicator (Req 12.5).
                if (isScanning) {
                    ProcessingOverlay()
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // Bottom controls: torch toggle + capture button.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl, vertical = spacing.m),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Torch toggle (Req 1.2, 1.3).
                IconButton(
                    onClick = onTorchToggle,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = spacing.touchTargetMin,
                            minHeight = spacing.touchTargetMin,
                        )
                        .semantics {
                            contentDescription = if (torchEnabled) {
                                "Turn off flashlight"
                            } else {
                                "Turn on flashlight"
                            }
                        },
                ) {
                    Icon(
                        imageVector = if (torchEnabled) {
                            Icons.Default.Add
                        } else {
                            Icons.Default.Add
                        },
                        contentDescription = null,
                        tint = if (torchEnabled) colors.accent else colors.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Capture button (primary action, Req 17.3: ≥48dp).
                Button(
                    onClick = onCapture,
                    enabled = guidance == AlignmentGuidance.ReadyToScan && !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                        disabledContainerColor = colors.outline,
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Capture Braille scan" },
                ) {
                    Text(
                        text = "Scan",
                        style = typography.label,
                    )
                }

                // Spacer for balance (torch on left, capture center, placeholder right).
                Spacer(modifier = Modifier.size(spacing.touchTargetMin))
            }

            Spacer(modifier = Modifier.height(spacing.s))
        }
    }
}

/**
 * Alignment guidance overlay displayed on top of the camera preview.
 */
@Composable
private fun GuidanceOverlay(
    guidance: AlignmentGuidance,
    modifier: Modifier = Modifier,
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    val guidanceText = when (guidance) {
        AlignmentGuidance.PointAtDocument -> "Point camera at the Braille document"
        AlignmentGuidance.MoveCloser -> "Move closer to the document"
        AlignmentGuidance.MoveFarther -> "Move farther from the document"
        AlignmentGuidance.HoldSteady -> "Hold steady"
        AlignmentGuidance.AddLight -> "Add more light"
        AlignmentGuidance.FlattenDocument -> "Flatten the document"
        AlignmentGuidance.ReadyToScan -> "" // Handled separately.
    }

    Box(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = guidanceText,
            style = typography.subtitle,
            color = colors.guidance,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(spacing.xl)
                .semantics {
                    contentDescription = "Alignment guidance: $guidanceText"
                },
        )
    }
}

/**
 * Ready-to-scan indicator displayed at the top of the preview.
 */
@Composable
private fun ReadyIndicator(modifier: Modifier = Modifier) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    Surface(
        color = colors.success.copy(alpha = 0.9f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(spacing.s),
        modifier = modifier.padding(spacing.m),
    ) {
        Text(
            text = "Ready to Scan",
            style = typography.label,
            color = colors.onSuccess,
            modifier = Modifier
                .padding(horizontal = spacing.m, vertical = spacing.s)
                .semantics { contentDescription = "Ready to scan" },
        )
    }
}

/**
 * Translucent overlay with a spinner shown while scanning is in progress
 * (Req 12.5).
 */
@Composable
private fun ProcessingOverlay() {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning…",
                style = typography.body,
                color = colors.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "Scanning in progress"
                },
            )
        }
    }
}

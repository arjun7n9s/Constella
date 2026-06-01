package com.constella.braille.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.constella.braille.domain.model.GradeMode
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.ui.theme.BrailleTokens

/**
 * Settings screen composable providing controls for scanning mode, grade mode,
 * and cloud opt-in.
 *
 * All visuals read from Design_Tokens exclusively (Req 18.2). All interactive
 * controls have descriptive accessibility labels (Req 17.2) and meet the
 * minimum touch-target size (Req 17.3).
 *
 * _Requirements: 9.1, 9.2, 8.3, 8.6, 16.3_
 */
@Composable
fun SettingsScreen(
    currentScanningMode: ScanningMode,
    currentGradeMode: GradeMode,
    cloudOptInEnabled: Boolean,
    onScanningModeSelected: (ScanningMode) -> Unit,
    onGradeModeSelected: (GradeMode) -> Unit,
    onCloudOptInToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    Surface(color = colors.surface, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            // Header.
            Text(
                text = "Settings",
                style = typography.title,
                color = colors.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "Settings screen"
                },
            )

            Spacer(modifier = Modifier.height(spacing.s))

            // --- Scanning Mode Section (Req 9.1, 9.2) ---
            SectionHeader(title = "Scanning Mode")

            ScanningModeOption(
                mode = ScanningMode.EMBOSSED,
                label = "Embossed",
                description = "High-accuracy mode for machine-embossed Braille",
                isSelected = currentScanningMode == ScanningMode.EMBOSSED,
                onSelect = { onScanningModeSelected(ScanningMode.EMBOSSED) },
            )

            ScanningModeOption(
                mode = ScanningMode.HANDWRITTEN,
                label = "Handwritten",
                description = "Lower-confidence mode for slate-and-stylus Braille",
                isSelected = currentScanningMode == ScanningMode.HANDWRITTEN,
                onSelect = { onScanningModeSelected(ScanningMode.HANDWRITTEN) },
            )

            HorizontalDivider(color = colors.outline)

            // --- Grade Mode Section (Req 8.3, 8.6) ---
            SectionHeader(title = "Braille Grade")

            GradeModeOption(
                mode = GradeMode.AUTO,
                label = "Auto",
                description = "Automatically detect Grade 1 or Grade 2",
                isSelected = currentGradeMode == GradeMode.AUTO,
                onSelect = { onGradeModeSelected(GradeMode.AUTO) },
            )

            GradeModeOption(
                mode = GradeMode.GRADE_1,
                label = "Grade 1",
                description = "Uncontracted Braille",
                isSelected = currentGradeMode == GradeMode.GRADE_1,
                onSelect = { onGradeModeSelected(GradeMode.GRADE_1) },
            )

            GradeModeOption(
                mode = GradeMode.GRADE_2,
                label = "Grade 2",
                description = "Contracted Braille",
                isSelected = currentGradeMode == GradeMode.GRADE_2,
                onSelect = { onGradeModeSelected(GradeMode.GRADE_2) },
            )

            HorizontalDivider(color = colors.outline)

            // --- Cloud Opt-In Section (Req 16.3) ---
            SectionHeader(title = "Cloud Features")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = spacing.touchTargetMin)
                    .semantics {
                        contentDescription = if (cloudOptInEnabled) {
                            "Cloud features enabled. Tap to disable."
                        } else {
                            "Cloud features disabled. Tap to enable."
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable cloud features",
                        style = typography.body,
                        color = colors.onSurface,
                    )
                    Text(
                        text = "Off by default. All scanning works offline.",
                        style = typography.caption,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = cloudOptInEnabled,
                    onCheckedChange = onCloudOptInToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.accent,
                        checkedTrackColor = colors.accent.copy(alpha = 0.3f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography

    Text(
        text = title,
        style = typography.subtitle,
        color = colors.accent,
    )
}

@Composable
private fun ScanningModeOption(
    mode: ScanningMode,
    label: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = spacing.touchTargetMin)
            .semantics {
                contentDescription = "$label scanning mode. $description. ${if (isSelected) "Selected" else "Not selected"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent),
        )
        Column(modifier = Modifier.padding(start = spacing.s)) {
            Text(
                text = label,
                style = typography.body,
                color = colors.onSurface,
            )
            Text(
                text = description,
                style = typography.caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradeModeOption(
    mode: GradeMode,
    label: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = BrailleTokens.colors
    val typography = BrailleTokens.typography
    val spacing = BrailleTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = spacing.touchTargetMin)
            .semantics {
                contentDescription = "$label grade mode. $description. ${if (isSelected) "Selected" else "Not selected"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent),
        )
        Column(modifier = Modifier.padding(start = spacing.s)) {
            Text(
                text = label,
                style = typography.body,
                color = colors.onSurface,
            )
            Text(
                text = description,
                style = typography.caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

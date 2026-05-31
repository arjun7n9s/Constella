package com.constella.braille.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Theming_Layer entry point (UI layer).
 *
 * Placeholder wrapper that establishes the `ui.theme` package boundary. The
 * full centralized Design_Token implementation (ColorTokens, TypographyTokens,
 * SpacingTokens with `touchTargetMin = 48.dp`, default + high-contrast token
 * sets, exposed via CompositionLocals) is implemented in task 2.1.
 *
 * Components must read visuals exclusively through this layer and never
 * hard-code Color(...), .sp, or .dp values outside the token definitions.
 */
@Composable
fun BrailleScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

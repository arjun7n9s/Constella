package com.constella.braille.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Theming_Layer entry point (UI layer, Req 18).
 *
 * Installs the semantic Design_Token sets ([ColorTokens], [TypographyTokens],
 * [SpacingTokens]) into the composition via their `CompositionLocal`s so every
 * descendant composable reads visuals exclusively through the layer (Req 18.1,
 * 18.2). Switching [highContrast] swaps the entire token set for the
 * low-vision palette (Req 17.7) — a pure value swap that, thanks to the static
 * `CompositionLocal`s, propagates to every consumer with no structural change
 * (Req 18.4).
 *
 * The component structure/contract (the token data classes and the
 * `CompositionLocal` accessors in `Tokens.kt`) is intentionally kept separate
 * from the token *values* (in the `*Tokens.kt` value files), so a designer can
 * later restyle by editing values alone (Req 18.5).
 *
 * A `MaterialTheme` is also provided, derived **from the same tokens**, so
 * Material3 building blocks (`Surface`, `Text`, buttons) inherit the token
 * palette/type and there is still a single source of truth.
 *
 * @param highContrast select the high-contrast token sets (Req 17.7).
 * @param colorTokens override the color token set (defaults derived from [highContrast]).
 * @param typographyTokens override the typography token set.
 * @param spacingTokens override the spacing token set.
 */
@Composable
fun BrailleScannerTheme(
    highContrast: Boolean = false,
    colorTokens: ColorTokens = if (highContrast) HighContrastColorTokens else DefaultColorTokens,
    typographyTokens: TypographyTokens = if (highContrast) HighContrastTypographyTokens else DefaultTypographyTokens,
    spacingTokens: SpacingTokens = if (highContrast) HighContrastSpacingTokens else DefaultSpacingTokens,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColorTokens provides colorTokens,
        LocalTypographyTokens provides typographyTokens,
        LocalSpacingTokens provides spacingTokens,
    ) {
        MaterialTheme(
            colorScheme = colorTokens.toMaterialColorScheme(highContrast),
            typography = typographyTokens.toMaterialTypography(),
            content = content,
        )
    }
}

/**
 * Bridge the semantic [ColorTokens] onto a Material3 `ColorScheme` so Material
 * components stay consistent with the token palette. Only role mappings live
 * here; no new color literals are introduced.
 */
private fun ColorTokens.toMaterialColorScheme(highContrast: Boolean) =
    if (highContrast) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            outline = outline,
        )
    }

/**
 * Bridge the role-based [TypographyTokens] onto Material3 `Typography` slots so
 * Material components inherit token type. Maps the closest Material role for
 * each token; no new `.sp` literals are introduced.
 */
private fun TypographyTokens.toMaterialTypography(): Typography {
    val base = Typography()
    return base.copy(
        titleLarge = title,
        titleMedium = subtitle,
        bodyLarge = body,
        bodyMedium = body,
        labelLarge = label,
        bodySmall = caption,
        labelSmall = caption,
    )
}

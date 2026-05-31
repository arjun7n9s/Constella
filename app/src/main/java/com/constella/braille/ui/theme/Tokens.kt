package com.constella.braille.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Compose `CompositionLocal` hooks that expose the Design_Tokens to the whole
 * UI tree (Req 18.1, 18.2).
 *
 * This file is the **contract** between the Theming_Layer and its consumers:
 * components read `LocalColorTokens.current`, `LocalTypographyTokens.current`,
 * and `LocalSpacingTokens.current` and nothing else. The concrete values live
 * in the token files (`ColorTokens.kt`, `TypographyTokens.kt`,
 * `SpacingTokens.kt`) and are installed by [BrailleScannerTheme], keeping the
 * structure/contract cleanly separated from the values (Req 18.5).
 *
 * Each local has **no usable default**: a consumer reached outside a
 * [BrailleScannerTheme] fails loudly rather than silently rendering an
 * unthemed value. This enforces Req 18.3 — a token must be provided by the
 * Theming_Layer before any component can consume it.
 *
 * `staticCompositionLocalOf` is used (rather than `compositionLocalOf`) because
 * token sets change rarely (only on a theme/high-contrast switch); a static
 * local recomposes the whole subtree on change, which is exactly the
 * "propagate to every consumer" behaviour Req 18.4 requires.
 */
val LocalColorTokens = staticCompositionLocalOf<ColorTokens> {
    error("ColorTokens not provided. Wrap UI in BrailleScannerTheme { } (Req 18.3).")
}

val LocalTypographyTokens = staticCompositionLocalOf<TypographyTokens> {
    error("TypographyTokens not provided. Wrap UI in BrailleScannerTheme { } (Req 18.3).")
}

val LocalSpacingTokens = staticCompositionLocalOf<SpacingTokens> {
    error("SpacingTokens not provided. Wrap UI in BrailleScannerTheme { } (Req 18.3).")
}

/**
 * Ergonomic, namespaced accessor so consumers can write
 * `BrailleTokens.colors.accent`, `BrailleTokens.typography.body`, or
 * `BrailleTokens.spacing.touchTargetMin` from any `@Composable`. This is the
 * single, discoverable entry point components use to read visuals; it adds no
 * values of its own, it only forwards to the `CompositionLocal`s above.
 */
object BrailleTokens {

    val colors: ColorTokens
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalColorTokens.current

    val typography: TypographyTokens
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalTypographyTokens.current

    val spacing: SpacingTokens
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSpacingTokens.current
}

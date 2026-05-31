package com.constella.braille.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color Design_Tokens (Req 18.1, 18.5).
 *
 * Every field is a **role**, not a literal — `accent`, `uncertainMark`,
 * `surface` rather than "teal" or "0xFF...". Components read these roles by
 * name through [LocalColorTokens]; they never reference a raw [Color] literal
 * directly (Req 18.2). Because the contract here is the *set of role names*,
 * swapping the underlying values (e.g. switching to [HighContrastColorTokens]
 * or a future designer palette) reflows every consumer with no structural
 * change (Req 18.4).
 *
 * Foreground/`on*` roles are paired with their background role and chosen so
 * the default set meets the 4.5:1 contrast bar (Req 17.4); the high-contrast
 * set (Req 17.7) pushes every pair far past it.
 */
@Immutable
data class ColorTokens(
    /** Primary screen background. */
    val surface: Color,
    /** Text/iconography drawn on [surface]. */
    val onSurface: Color,
    /** Secondary container background (cards, result panels). */
    val surfaceVariant: Color,
    /** Text/iconography drawn on [surfaceVariant]. */
    val onSurfaceVariant: Color,
    /** Primary action / emphasis color (e.g. capture button). */
    val accent: Color,
    /** Text/iconography drawn on [accent]. */
    val onAccent: Color,
    /** Failure state color (camera/permission/processing errors, Req 14). */
    val error: Color,
    /** Text/iconography drawn on [error]. */
    val onError: Color,
    /** Ready-to-scan / positive confirmation color (Req 2.7). */
    val success: Color,
    /** Text/iconography drawn on [success]. */
    val onSuccess: Color,
    /** Caution color for low-confidence and Handwritten_Mode tier labels (Req 9.5, 14.2). */
    val warning: Color,
    /** Marking applied to characters below the display-confidence threshold (Req 10.3). */
    val uncertainMark: Color,
    /** Alignment guidance indicator color (Req 2.8). */
    val guidance: Color,
    /** Hairline borders / dividers / outlines. */
    val outline: Color,
)

/**
 * Default (light) palette. Foreground roles meet ≥ 4.5:1 against their paired
 * background (Req 17.4).
 */
val DefaultColorTokens: ColorTokens = ColorTokens(
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF424242),
    accent = Color(0xFF00504D),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    success = Color(0xFF1B5E20),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF8A5000),
    uncertainMark = Color(0xFFB00020),
    guidance = Color(0xFF1565C0),
    outline = Color(0xFF595959),
)

/**
 * High-contrast palette for low-vision Operators (Req 17.7). Same component
 * contract as [DefaultColorTokens] — only the values differ — so selecting it
 * is a pure value swap (Req 18.4).
 */
val HighContrastColorTokens: ColorTokens = ColorTokens(
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFFFFFFFF),
    accent = Color(0xFFFFFF00),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF5252),
    onError = Color(0xFF000000),
    success = Color(0xFF69F0AE),
    onSuccess = Color(0xFF000000),
    warning = Color(0xFFFFD740),
    uncertainMark = Color(0xFFFF8A80),
    guidance = Color(0xFF40C4FF),
    outline = Color(0xFFFFFFFF),
)

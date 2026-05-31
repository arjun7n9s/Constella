package com.constella.braille.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Role-based typography Design_Tokens (Req 18.1, 18.5).
 *
 * Each field names a **role** in the UI (a screen title, the recognized-text
 * body, a guidance line) rather than a literal size. Components select a role
 * from [LocalTypographyTokens]; they never write a raw `.sp` literal (Req
 * 18.2). Restyling type means editing these [TextStyle] values, not the
 * composables that consume them (Req 18.4).
 */
@Immutable
data class TypographyTokens(
    /** Screen and section titles. */
    val title: TextStyle,
    /** Prominent subheadings (active mode, selected grade, Req 8.2, 9.4). */
    val subtitle: TextStyle,
    /** Default body text. */
    val body: TextStyle,
    /** Recognized_Text rendering on the results area (Req 10.1). */
    val recognizedText: TextStyle,
    /** Spoken/visual alignment guidance lines (Req 2.8). */
    val guidance: TextStyle,
    /** Action control / button labels. */
    val label: TextStyle,
    /** Small supporting text: confidence score, disclaimers, tier labels (Req 8.5, 9.5, 10.2). */
    val caption: TextStyle,
)

/**
 * Default typography scale. Uses the platform default font family so no font
 * asset is required; a designer can later swap the family/weights here without
 * touching any consumer (Req 18.4).
 */
val DefaultTypographyTokens: TypographyTokens = TypographyTokens(
    title = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    subtitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    recognizedText = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    guidance = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    label = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
)

/**
 * High-contrast typography (Req 17.7). Larger, heavier weights aid low-vision
 * Operators while preserving the exact same role set as
 * [DefaultTypographyTokens] (Req 18.4, 18.5).
 */
val HighContrastTypographyTokens: TypographyTokens = TypographyTokens(
    title = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    subtitle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    recognizedText = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    guidance = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    label = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

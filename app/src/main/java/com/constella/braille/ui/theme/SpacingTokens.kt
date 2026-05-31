package com.constella.braille.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing Design_Tokens (Req 18.1, 18.5).
 *
 * A named scale (`xs`…`xl`) plus the accessibility-critical
 * [touchTargetMin]. Components space and size themselves from
 * [LocalSpacingTokens]; they never hard-code a `.dp` literal (Req 18.2), so
 * retuning density is a value swap with no structural change (Req 18.4).
 */
@Immutable
data class SpacingTokens(
    /** Extra-small gap (tight icon/text padding). */
    val xs: Dp,
    /** Small gap. */
    val s: Dp,
    /** Medium gap (default content padding). */
    val m: Dp,
    /** Large gap (section separation). */
    val l: Dp,
    /** Extra-large gap (screen margins). */
    val xl: Dp,
    /**
     * Minimum touch-target size for primary action controls, enforced at
     * 48 dp (Req 17.3). Accessibility code reads this token rather than
     * repeating the literal so the floor lives in exactly one place.
     */
    val touchTargetMin: Dp,
)

/**
 * Default spacing scale. `touchTargetMin = 48.dp` is the Req 17.3 minimum
 * touch-target size.
 */
val DefaultSpacingTokens: SpacingTokens = SpacingTokens(
    xs = 4.dp,
    s = 8.dp,
    m = 16.dp,
    l = 24.dp,
    xl = 32.dp,
    touchTargetMin = 48.dp,
)

/**
 * High-contrast spacing (Req 17.7). Roomier targets and gaps help low-vision
 * and motor-impaired Operators; the 48 dp floor is never reduced. Same
 * contract as [DefaultSpacingTokens] — values only (Req 18.4, 18.5).
 */
val HighContrastSpacingTokens: SpacingTokens = SpacingTokens(
    xs = 6.dp,
    s = 12.dp,
    m = 20.dp,
    l = 28.dp,
    xl = 40.dp,
    touchTargetMin = 56.dp,
)

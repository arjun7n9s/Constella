package com.constella.braille.domain.preprocess

/**
 * The pixel dimensions of an image, used by the deterministic preprocessing
 * geometry to compute frame area (for the document boundary minimum-area test,
 * Req 3.1) and to size the rectified target rectangle (Req 3.2).
 *
 * Both dimensions must be positive; a zero-area frame has no meaningful area
 * fraction and no axes to align a rectified document to.
 *
 * _Requirements: 3.1, 3.2_
 */
data class ImageSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "ImageSize width must be > 0 but was $width" }
        require(height > 0) { "ImageSize height must be > 0 but was $height" }
    }

    /** Total frame area in pixels, in [Double] to avoid `Int` overflow on large frames. */
    val area: Double get() = width.toDouble() * height.toDouble()
}

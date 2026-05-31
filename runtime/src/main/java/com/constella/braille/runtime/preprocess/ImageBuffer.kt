package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.ImageSize

/**
 * A raw, in-memory single-channel (grayscale) image buffer in row-major order.
 *
 * This is the runtime-layer image container handed between the OpenCV-facing
 * boundary finder / warp and the rest of the preprocessing path. It is a plain
 * data holder (no Android `Bitmap`, no OpenCV `Mat`) so the preprocessing
 * orchestration and its tests do not depend on either framework; the
 * native-facing [OpenCvBridge] is responsible for converting to/from whatever
 * native representation it uses.
 *
 * [pixels] has exactly `width * height` entries, each an unsigned 8-bit
 * luminance value stored in a [ByteArray] (interpret with `& 0xFF`). Keeping
 * frames in memory only — never written to storage — is consistent with the
 * camera-privacy requirement (Req 16.1).
 *
 * _Requirements: 3.1, 3.2_
 */
class ImageBuffer(
    val size: ImageSize,
    val pixels: ByteArray,
) {
    init {
        val expected = size.width.toLong() * size.height.toLong()
        require(pixels.size.toLong() == expected) {
            "ImageBuffer expected ${expected} pixels for ${size.width}x${size.height} but got ${pixels.size}"
        }
    }

    val width: Int get() = size.width
    val height: Int get() = size.height

    /** Reads the luminance at `(x, y)` as an `Int` in `[0, 255]`. */
    fun luminanceAt(x: Int, y: Int): Int {
        require(x in 0 until width) { "x out of bounds: $x" }
        require(y in 0 until height) { "y out of bounds: $y" }
        return pixels[y * width + x].toInt() and 0xFF
    }
}

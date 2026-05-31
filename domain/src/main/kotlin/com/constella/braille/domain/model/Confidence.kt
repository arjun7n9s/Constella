package com.constella.braille.domain.model

/**
 * A normalized confidence score in the closed interval `[0, 1]`.
 *
 * Used everywhere the System reports estimated reliability of a recognized dot,
 * cell, character, or scan (Confidence_Score in the requirements).
 *
 * The primary constructor *validates* the range and throws
 * [IllegalArgumentException] for out-of-range or `NaN` inputs, so an invalid
 * [Confidence] can never exist. When a caller has a raw value that may fall
 * outside the range and simply wants it pinned into range, use [Confidence.of],
 * which clamps before constructing.
 *
 * Implemented as an inline value class so it carries no boxing/allocation cost
 * over a bare `Float` at runtime while still being type-safe at the API level.
 *
 * _Requirements: 4.3, 6.2, 10.2_
 */
@JvmInline
value class Confidence(val value: Float) {
    init {
        require(value in 0f..1f) { "Confidence must be in [0, 1] but was $value" }
    }

    companion object {
        /** Lowest possible confidence. */
        val ZERO: Confidence = Confidence(0f)

        /** Highest possible confidence. */
        val ONE: Confidence = Confidence(1f)

        /**
         * Build a [Confidence] from a raw value, clamping it into `[0, 1]`.
         *
         * A `NaN` input has no meaningful clamp target and is rejected by the
         * underlying constructor.
         */
        fun of(raw: Float): Confidence = Confidence(raw.coerceIn(0f, 1f))
    }
}

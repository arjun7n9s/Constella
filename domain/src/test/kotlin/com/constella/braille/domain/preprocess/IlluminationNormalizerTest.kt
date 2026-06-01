package com.constella.braille.domain.preprocess

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.math.roundToInt

/**
 * Tests for [IlluminationNormalizer] — the pure-Kotlin lighting normalization
 * behind the Image_Preprocessor (task 5.3, Req 3.3).
 *
 * Mix of example/edge-case unit checks and a generated-input (property-style)
 * implementation test that flat-field correction drives illumination variation
 * below the defined uniformity threshold for randomly generated smooth
 * gradients. This is the *implementation* test for task 5.3; the numbered
 * Correctness Property 5 is implemented separately by task 5.4.
 */
class IlluminationNormalizerTest : StringSpec({

    val threshold = IlluminationNormalizer.ILLUMINATION_UNIFORMITY_THRESHOLD

    /**
     * Builds a `w x h` luminance buffer combining a smooth linear illumination
     * gradient (slopes [gx] across the width, [gy] down the height, about a
     * mid-gray centre) with a small high-frequency texture of amplitude
     * [texture] — a stand-in for the fine dot/shadow detail normalization must
     * preserve while flattening the gradient.
     */
    fun gradientImage(w: Int, h: Int, gx: Double, gy: Double, texture: Int): ByteArray {
        val px = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val fx = if (w > 1) x.toDouble() / (w - 1) - 0.5 else 0.0
                val fy = if (h > 1) y.toDouble() / (h - 1) - 0.5 else 0.0
                val illum = 128.0 + gx * fx + gy * fy
                // High-frequency, zero-mean-ish texture independent of the gradient.
                val t = (((x * 7 + y * 13) % 5) - 2) * texture / 2.0
                px[y * w + x] = (illum + t).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return px
    }

    "a uniformly-lit image has near-zero illumination variation and stays uniform" {
        val size = ImageSize(64, 48)
        val flat = ByteArray(size.width * size.height) { 120.toByte() }

        IlluminationNormalizer.illuminationVariation(flat, size).shouldBeLessThanOrEqual(threshold)

        val normalized = IlluminationNormalizer.normalize(flat, size)
        // A flat field maps to (near) its global mean everywhere — still uniform.
        IlluminationNormalizer.maxAbsPixelDelta(flat, normalized).shouldBeLessThanOrEqual(1)
    }

    "a strong horizontal gradient is uneven before and uniform after normalization" {
        val size = ImageSize(96, 64)
        val gradient = gradientImage(size.width, size.height, gx = 240.0, gy = 0.0, texture = 0)

        val before = IlluminationNormalizer.illuminationVariation(gradient, size)
        before.shouldBeGreaterThan(0.5)

        val normalized = IlluminationNormalizer.normalize(gradient, size)
        val after = IlluminationNormalizer.illuminationVariation(normalized, size)
        after.shouldBeLessThanOrEqual(threshold)
        after.shouldBeLessThan(before)
    }

    "a strong diagonal gradient is flattened below the uniformity threshold" {
        val size = ImageSize(80, 80)
        val gradient = gradientImage(size.width, size.height, gx = 120.0, gy = 120.0, texture = 0)

        val after = IlluminationNormalizer.illuminationVariation(
            IlluminationNormalizer.normalize(gradient, size),
            size,
        )
        after.shouldBeLessThanOrEqual(threshold)
    }

    "local dot contrast survives normalization of an uneven field" {
        // A bright field with a strong illumination gradient and one dark dot.
        val size = ImageSize(64, 64)
        val px = gradientImage(size.width, size.height, gx = 200.0, gy = 0.0, texture = 0).copyOf()
        val dotX = 40
        val dotY = 32
        // Make the dot markedly darker than its local background.
        val original = px[dotY * size.width + dotX].toInt() and 0xFF
        px[dotY * size.width + dotX] = (original - 60).coerceIn(0, 255).toByte()

        val normalized = IlluminationNormalizer.normalize(px, size)

        // After flattening the gradient, the dot is still clearly darker than a
        // neighbour a few pixels away (local contrast preserved, Req 3.3).
        val dotValue = normalized[dotY * size.width + dotX].toInt() and 0xFF
        val neighbour = normalized[dotY * size.width + (dotX + 5)].toInt() and 0xFF
        (neighbour - dotValue).toDouble().shouldBeGreaterThan(20.0)
    }

    "estimateBackground of a uniform field reproduces that field" {
        val size = ImageSize(40, 40)
        val flat = ByteArray(size.width * size.height) { 90.toByte() }

        val background = IlluminationNormalizer.estimateBackground(flat, size)

        IlluminationNormalizer.maxAbsPixelDelta(flat, background).shouldBeLessThanOrEqual(1)
    }

    // Generated-input implementation test (task 5.3): for randomly generated
    // smooth illumination gradients over a textured base, normalization reduces
    // illumination variation to within the defined uniformity threshold.
    "normalization drives smooth-gradient illumination variation below the threshold" {
        checkAll(
            200,
            Arb.int(48..96),   // width
            Arb.int(48..96),   // height
            Arb.int(-140..140), // horizontal gradient amplitude
            Arb.int(-140..140), // vertical gradient amplitude
            Arb.int(0..20),     // high-frequency texture amplitude
        ) { w, h, gxi, gyi, texture ->
            val size = ImageSize(w, h)
            val image = gradientImage(w, h, gxi.toDouble(), gyi.toDouble(), texture)

            val normalized = IlluminationNormalizer.normalize(image, size)
            val after = IlluminationNormalizer.illuminationVariation(normalized, size)

            after.shouldBeLessThanOrEqual(threshold)
        }
    }
})

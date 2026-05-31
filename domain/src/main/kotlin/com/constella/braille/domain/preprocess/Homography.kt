package com.constella.braille.domain.preprocess

/**
 * A 3x3 projective transform (homography) stored in row-major order, mapping a
 * source pixel `(x, y)` to a destination pixel via homogeneous coordinates:
 *
 * ```
 * [ x' ]   [ m[0] m[1] m[2] ] [ x ]
 * [ y' ] = [ m[3] m[4] m[5] ] [ y ]
 * [ w  ]   [ m[6] m[7] m[8] ] [ 1 ]
 * ```
 *
 * with the result de-homogenized as `(x'/w, y'/w)`.
 *
 * This is the deterministic mathematical core of the perspective warp (Req
 * 3.2): the OpenCV-facing layer only needs to *apply* this transform to image
 * pixels; the transform itself is solved here in pure Kotlin so the warp
 * geometry is JVM-unit- and property-testable without OpenCV.
 *
 * Use [solve] to compute the homography from four point correspondences (the
 * document-boundary corners mapped onto the rectified target-rectangle
 * corners).
 *
 * _Requirements: 3.2_
 */
class Homography private constructor(val m: DoubleArray) {

    init {
        require(m.size == 9) { "A homography has 9 coefficients but got ${m.size}" }
    }

    /**
     * Applies this homography to [p], returning the de-homogenized destination
     * point. Throws if the mapped point is at infinity (`w == 0`), which cannot
     * occur for a valid mapping of points inside a non-degenerate quad.
     */
    fun apply(p: Point2D): Point2D {
        val x = p.x.toDouble()
        val y = p.y.toDouble()
        val dx = m[0] * x + m[1] * y + m[2]
        val dy = m[3] * x + m[4] * y + m[5]
        val dw = m[6] * x + m[7] * y + m[8]
        require(dw != 0.0) { "Homography mapped a point to infinity (w == 0)" }
        return Point2D((dx / dw).toFloat(), (dy / dw).toFloat())
    }

    /**
     * The inverse homography, mapping destination pixels back to source pixels.
     * Used by software (non-OpenCV) warp execution, which samples each
     * destination pixel from its pre-image in the source frame. Throws if this
     * homography is singular (cannot occur for a transform built from a
     * non-degenerate quad).
     */
    fun inverse(): Homography {
        val a = m
        val c0 = a[4] * a[8] - a[5] * a[7]
        val c1 = a[2] * a[7] - a[1] * a[8]
        val c2 = a[1] * a[5] - a[2] * a[4]
        val det = a[0] * c0 + a[3] * c1 + a[6] * c2
        require(kotlin.math.abs(det) > 1e-12) { "Homography is singular and cannot be inverted" }
        val invDet = 1.0 / det
        val inv = DoubleArray(9)
        inv[0] = c0 * invDet
        inv[1] = c1 * invDet
        inv[2] = c2 * invDet
        inv[3] = (a[5] * a[6] - a[3] * a[8]) * invDet
        inv[4] = (a[0] * a[8] - a[2] * a[6]) * invDet
        inv[5] = (a[2] * a[3] - a[0] * a[5]) * invDet
        inv[6] = (a[3] * a[7] - a[4] * a[6]) * invDet
        inv[7] = (a[1] * a[6] - a[0] * a[7]) * invDet
        inv[8] = (a[0] * a[4] - a[1] * a[3]) * invDet
        return Homography(inv)
    }

    companion object {
        /**
         * Solves for the homography mapping the four [src] points onto the four
         * [dst] points (in matching order). Builds the standard 8x8 linear
         * system (two rows per correspondence) with `h[8]` fixed to 1 and solves
         * it with Gaussian elimination + partial pivoting.
         *
         * Both lists must contain exactly four points, and neither the source
         * nor destination four-tuple may be degenerate (collinear / coincident),
         * which would make the system singular.
         */
        fun solve(src: List<Point2D>, dst: List<Point2D>): Homography {
            require(src.size == 4) { "solve requires exactly 4 source points but got ${src.size}" }
            require(dst.size == 4) { "solve requires exactly 4 destination points but got ${dst.size}" }

            // Build A (8x8) and b (8) for the unknowns h0..h7 (h8 == 1).
            val a = Array(8) { DoubleArray(8) }
            val b = DoubleArray(8)
            for (i in 0 until 4) {
                val sx = src[i].x.toDouble()
                val sy = src[i].y.toDouble()
                val dx = dst[i].x.toDouble()
                val dy = dst[i].y.toDouble()

                val r0 = 2 * i
                a[r0][0] = sx; a[r0][1] = sy; a[r0][2] = 1.0
                a[r0][3] = 0.0; a[r0][4] = 0.0; a[r0][5] = 0.0
                a[r0][6] = -dx * sx; a[r0][7] = -dx * sy
                b[r0] = dx

                val r1 = 2 * i + 1
                a[r1][0] = 0.0; a[r1][1] = 0.0; a[r1][2] = 0.0
                a[r1][3] = sx; a[r1][4] = sy; a[r1][5] = 1.0
                a[r1][6] = -dy * sx; a[r1][7] = -dy * sy
                b[r1] = dy
            }

            val h = gaussianSolve(a, b)
            return Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
        }

        /**
         * Solves the dense linear system `A x = b` for a square [a] using
         * Gaussian elimination with partial pivoting. Throws if [a] is singular
         * (degenerate point configuration).
         */
        private fun gaussianSolve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
            val n = b.size
            // Work on augmented copies so callers' arrays are untouched.
            val mat = Array(n) { r -> DoubleArray(n) { c -> a[r][c] } }
            val rhs = b.copyOf()

            for (col in 0 until n) {
                // Partial pivot: find the row with the largest magnitude in this column.
                var pivot = col
                var best = kotlin.math.abs(mat[col][col])
                for (r in col + 1 until n) {
                    val v = kotlin.math.abs(mat[r][col])
                    if (v > best) {
                        best = v
                        pivot = r
                    }
                }
                require(best > 1e-12) { "Singular system: degenerate point configuration" }
                if (pivot != col) {
                    val tmp = mat[col]; mat[col] = mat[pivot]; mat[pivot] = tmp
                    val tr = rhs[col]; rhs[col] = rhs[pivot]; rhs[pivot] = tr
                }

                // Eliminate below.
                for (r in col + 1 until n) {
                    val factor = mat[r][col] / mat[col][col]
                    if (factor == 0.0) continue
                    for (c in col until n) {
                        mat[r][c] -= factor * mat[col][c]
                    }
                    rhs[r] -= factor * rhs[col]
                }
            }

            // Back-substitution.
            val x = DoubleArray(n)
            for (row in n - 1 downTo 0) {
                var sum = rhs[row]
                for (c in row + 1 until n) {
                    sum -= mat[row][c] * x[c]
                }
                x[row] = sum / mat[row][row]
            }
            return x
        }
    }
}

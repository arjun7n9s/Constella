package com.constella.braille.runtime.detect

import android.content.Context
import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite implementation of [DotDetector] (Req 4.1, 4.2).
 *
 * Loads the bundled object-detection model from the application's `assets/`
 * (default [DEFAULT_MODEL_ASSET_PATH]) and runs inference **entirely on the
 * device** through the TFLite [Interpreter]. The raw interpreter output tensors
 * are handed to the pure [DotDetectionPostProcessor], which performs the
 * deterministic confidence filtering, coordinate mapping, and structure
 * decision (Req 4.3, 4.7).
 *
 * ## Required asset (not yet bundled)
 * This wrapper expects an SSD-style detection model at
 * `assets/[DEFAULT_MODEL_ASSET_PATH]`. **That `.tflite` file does not exist in
 * the repository yet** — it is dropped in by the later packaging task (task 23 /
 * see `runtime/src/main/assets/README.md`). Until then, [TfLiteDotDetector]
 * **degrades gracefully**: construction with a missing asset throws a clearly
 * described [ModelUnavailableException] rather than crashing, so the
 * ScanCoordinator can surface a recoverable error.
 *
 * ## Expected model I/O
 * - **Input:** a single `1 x [inputHeightPx] x [inputWidthPx] x 3` tensor. The
 *   detector resizes/letterboxes are out of scope here; the caller's
 *   [DetectorImage] is expected to already match (it comes from the
 *   Image_Preprocessor). Bytes are 8-bit RGB.
 * - **Output:** the standard TFLite detection post-processing tensors —
 *   normalized boxes `1 x N x 4` and scores `1 x N`. [OutputLayout] maps tensor
 *   indices; defaults match the canonical TFLite SSD export.
 *
 * The class is not thread-safe; a single instance serializes calls to one
 * [Interpreter]. Call [close] when done to release native resources.
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.7_
 */
class TfLiteDotDetector private constructor(
    private val interpreter: Interpreter,
    private val config: Config,
) : DotDetector, AutoCloseable {

    /**
     * Static configuration for the bundled model's I/O contract. Defaults match
     * the canonical TFLite SSD detection export and a 320x320 input.
     */
    data class Config(
        val inputWidthPx: Int = DEFAULT_INPUT_SIZE,
        val inputHeightPx: Int = DEFAULT_INPUT_SIZE,
        val maxDetections: Int = DEFAULT_MAX_DETECTIONS,
        val output: OutputLayout = OutputLayout(),
        val boxFormat: BoxFormat = BoxFormat.YMIN_XMIN_YMAX_XMAX,
    ) {
        init {
            require(inputWidthPx > 0 && inputHeightPx > 0) {
                "input dimensions must be > 0"
            }
            require(maxDetections > 0) { "maxDetections must be > 0" }
        }
    }

    /** Output-tensor index map for the detection post-processing outputs. */
    data class OutputLayout(
        val boxesTensorIndex: Int = 0,
        val scoresTensorIndex: Int = 2,
    )

    override suspend fun detect(image: DetectorImage): DetectorOutput {
        val raw = try {
            runInference(image)
        } catch (e: IllegalArgumentException) {
            // Interpreter rejects a tensor shape/type mismatch with IAE.
            throw ModelOutputFormatException(
                "Bundled detection model output did not match the expected layout " +
                    "(${config.output}). The packaged .tflite may be a different export.",
                e,
            )
        }
        return DotDetectionPostProcessor.process(
            raw = raw,
            imageWidthPx = image.widthPx,
            imageHeightPx = image.heightPx,
        )
    }

    /** Run the interpreter and copy its output tensors into [RawDetections]. */
    private fun runInference(image: DetectorImage): RawDetections {
        val input = image.rgb.duplicate().order(ByteOrder.nativeOrder())
        input.rewind()

        val n = config.maxDetections
        val boxes = Array(1) { Array(n) { FloatArray(RawDetections.COORDS_PER_BOX) } }
        val scores = Array(1) { FloatArray(n) }

        val outputs = HashMap<Int, Any>()
        outputs[config.output.boxesTensorIndex] = boxes
        outputs[config.output.scoresTensorIndex] = scores

        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)

        val flatBoxes = FloatArray(n * RawDetections.COORDS_PER_BOX)
        for (i in 0 until n) {
            val base = i * RawDetections.COORDS_PER_BOX
            flatBoxes[base] = boxes[0][i][0]
            flatBoxes[base + 1] = boxes[0][i][1]
            flatBoxes[base + 2] = boxes[0][i][2]
            flatBoxes[base + 3] = boxes[0][i][3]
        }
        return RawDetections(
            boxes = flatBoxes,
            scores = scores[0].copyOf(),
            format = config.boxFormat,
        )
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        /** Asset-relative path of the bundled object-detection model (Req 4.1). */
        const val DEFAULT_MODEL_ASSET_PATH: String = "models/braille_dot_detector.tflite"

        /** Default square model input size in pixels. */
        const val DEFAULT_INPUT_SIZE: Int = 320

        /** Default maximum detections the model emits per image. */
        const val DEFAULT_MAX_DETECTIONS: Int = 100

        /**
         * Create a detector by loading the bundled model from [context]'s assets.
         *
         * @throws ModelUnavailableException if the asset is missing (e.g. the
         *   `.tflite` has not been bundled yet) or cannot be loaded — a clear,
         *   recoverable failure rather than a crash.
         */
        fun fromAssets(
            context: Context,
            assetPath: String = DEFAULT_MODEL_ASSET_PATH,
            config: Config = Config(),
            options: Interpreter.Options = Interpreter.Options().apply { numThreads = 4 },
        ): TfLiteDotDetector {
            val model = loadModelFile(context.assets, assetPath)
            val interpreter = try {
                Interpreter(model, options)
            } catch (e: IllegalArgumentException) {
                throw ModelUnavailableException(
                    "Bundled detection model at asset '$assetPath' could not be " +
                        "initialized as a TFLite interpreter.",
                    e,
                )
            }
            return TfLiteDotDetector(interpreter, config)
        }

        /**
         * Memory-map the model asset so the interpreter can run without copying
         * the whole model onto the heap.
         *
         * @throws ModelUnavailableException when the asset is absent or unreadable.
         */
        private fun loadModelFile(assets: AssetManager, assetPath: String): MappedByteBuffer {
            return try {
                assets.openFd(assetPath).use { fd ->
                    fd.createInputStream().use { stream ->
                        val channel: FileChannel = stream.channel
                        channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            fd.startOffset,
                            fd.declaredLength,
                        )
                    }
                }
            } catch (e: FileNotFoundException) {
                throw ModelUnavailableException(
                    "Bundled detection model asset '$assetPath' was not found. The " +
                        ".tflite model must be packaged into runtime/src/main/assets/ " +
                        "before scanning can run (see assets/README.md).",
                    e,
                )
            } catch (e: IOException) {
                throw ModelUnavailableException(
                    "Bundled detection model asset '$assetPath' could not be read.",
                    e,
                )
            }
        }
    }
}

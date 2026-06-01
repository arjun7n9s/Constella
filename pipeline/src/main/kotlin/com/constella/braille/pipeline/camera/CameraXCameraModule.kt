package com.constella.braille.pipeline.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.runtime.preprocess.CapturedImage
import com.constella.braille.runtime.preprocess.ImageBuffer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX-backed implementation of [CameraModule].
 *
 * This is the thin, **device-dependent** wiring layer: it owns the CameraX
 * lifecycle (`ProcessCameraProvider`, `Preview`, `ImageAnalysis`,
 * `ImageCapture`) and translates the deterministic decisions made by
 * [CameraPolicy] into CameraX/Camera2 calls. The decision logic itself — torch
 * on/off, focus-distance clamping and diopter conversion, resolution selection
 * — lives in [CameraPolicy] and is unit-tested on the JVM; this class only
 * *applies* those decisions to hardware and is exercised by an instrumented
 * test on a device rather than by JVM unit tests.
 *
 * Behaviour mapped to requirements:
 *  - Binds a live [Preview] and enables the Torch by default for Raking_Light
 *    (Req 1.2), keeping it on in Embossed_Mode unless the Operator toggles it
 *    (Req 1.8).
 *  - [setTorch] is the Operator torch toggle (Req 1.3); an explicit choice is
 *    remembered as a [TorchPreference] and wins over the mode default.
 *  - Biases focus to a close-range working distance inside the supported 5–25 cm
 *    window (Req 1.4) via a manual Camera2 `LENS_FOCUS_DISTANCE`, falling back
 *    silently where manual focus is unsupported (the typed `NoMacroFocus` state
 *    and its recovery are task 18.2's responsibility).
 *  - Streams low-resolution [AnalysisFrame]s with `STRATEGY_KEEP_ONLY_LATEST`
 *    plus an in-flight guard so frames are throttled for the live budget
 *    (Req 12.2), and captures a single highest-resolution still (Req 1.5) using
 *    the highest-available resolution strategy — the CameraX equivalent of
 *    [CameraPolicy.selectStillResolution].
 *
 * Typed error states: capability/permission/capture failures are classified
 * into a [CameraError] and emitted as a [CameraState.Error] built through
 * [CameraErrorPolicy], which decides the dual-channel message, the recovery
 * control, and whether the preview is preserved (Req 1.6, 1.7, 1.9, 1.10,
 * 1.11). This class only *detects* the condition; the deterministic
 * error-to-presentation mapping lives in the pure [CameraErrorPolicy].
 *
 * @param context an application [Context] used to obtain the camera provider.
 * @param analysisExecutor executor on which analysis frames and capture
 *   callbacks are delivered; defaults to a dedicated single thread so the
 *   pipeline never blocks the main thread.
 * @param hasCameraPermission predicate used to distinguish a permission denial
 *   (Req 1.7) from a generic unavailability (Req 1.10) when binding fails;
 *   defaults to assuming the permission was granted so the caller can supply
 *   the device's real permission state.
 *
 * _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11_
 */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@android.annotation.SuppressLint("UnsafeOptInUsageError")
class CameraXCameraModule(
    private val context: Context,
    private val analysisExecutor: Executor = Executors.newSingleThreadExecutor(),
    private val hasCameraPermission: () -> Boolean = { true },
) : CameraModule {

    private val _previewState = MutableStateFlow<CameraState>(CameraState.Starting)
    override val previewState: StateFlow<CameraState> = _previewState.asStateFlow()

    private val _analysisFrames = MutableSharedFlow<AnalysisFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val analysisFrames: Flow<AnalysisFrame> = _analysisFrames.asSharedFlow()

    /** Executor that posts to the Android main thread without requiring androidx.core. */
    private val mainExecutor: Executor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null

    private var scanningMode: ScanningMode = ScanningMode.EMBOSSED
    private var torchPreference: TorchPreference = TorchPreference.DEFAULT
    private var workingDistanceCm: Float = CameraPolicy.defaultWorkingDistanceCm

    /** Guards against processing a new analysis frame while one is still in flight (Req 12.2). */
    @Volatile
    private var frameInFlight: Boolean = false

    /**
     * Binds the camera use cases to [lifecycleOwner] and starts the preview,
     * rendering into [surfaceProvider] (typically a `PreviewView` supplied by
     * the UI layer). On success the Torch is applied per the current policy
     * (Req 1.2, 1.8) and focus is biased to the close-range window (Req 1.4),
     * and [previewState] transitions to [CameraState.Previewing].
     */
    suspend fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        _previewState.value = CameraState.Starting
        try {
            val provider = awaitCameraProvider().also { cameraProvider = it }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
                this.preview = it
            }

            // Highest-resolution still capture (Req 1.5) — CameraX equivalent of
            // CameraPolicy.selectStillResolution(...).
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build(),
                )
                .build()
                .also { imageCapture = it }

            // Low-resolution, throttled analysis stream (Req 12.2) — mirrors
            // CameraPolicy.selectAnalysisResolution(...).
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(
                                    CameraPolicy.DEFAULT_ANALYSIS_MAX_LONG_SIDE_PX,
                                    CameraPolicy.DEFAULT_ANALYSIS_MAX_LONG_SIDE_PX,
                                ),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            ),
                        )
                        .build(),
                )
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::onAnalysisFrame); imageAnalysis = it }

            provider.unbindAll()
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
                analysis,
            )
            camera = bound

            applyTorch()
            applyFocus()
            _previewState.value = CameraState.Previewing(
                torchEnabled = CameraPolicy.resolveTorchEnabled(scanningMode, torchPreference),
                focusDistanceCm = CameraPolicy.clampWorkingDistanceCm(workingDistanceCm),
            )

            // Non-fatal capability errors are surfaced *after* the preview is
            // live so the preview is preserved underneath the message
            // (Req 1.6, 1.9). detectCapabilityError() returns the highest-
            // priority missing capability, or null when the camera is fully
            // capable.
            detectCapabilityError(bound)?.let { _previewState.value = CameraState.error(it) }
        } catch (t: Throwable) {
            // Distinguish a denied permission (Req 1.7, open settings) from a
            // generic non-permission unavailability (Req 1.10, retry).
            val kind = if (!hasCameraPermission()) {
                CameraError.PERMISSION_DENIED
            } else {
                CameraError.UNAVAILABLE
            }
            _previewState.value = CameraState.error(kind)
        }
    }

    /** Unbinds all use cases and releases the preview. */
    fun unbind() {
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        imageAnalysis = null
        _previewState.value = CameraState.Starting
    }

    override fun setTorch(enabled: Boolean) {
        torchPreference = if (enabled) TorchPreference.ON else TorchPreference.OFF
        applyTorch()
        refreshPreviewState()
    }

    override fun applyScanningMode(mode: ScanningMode) {
        scanningMode = mode
        // Re-evaluate torch against the (possibly sticky) Operator preference and
        // re-bias focus to the supported close-range window (Req 1.4, 1.8).
        applyTorch()
        applyFocus()
        refreshPreviewState()
    }

    /**
     * Sets the desired working distance (centimeters); the value is clamped to
     * the supported 5–25 cm window before being applied to the lens (Req 1.4).
     */
    fun setWorkingDistanceCm(distanceCm: Float) {
        workingDistanceCm = CameraPolicy.clampWorkingDistanceCm(distanceCm)
        applyFocus()
        refreshPreviewState()
    }

    override suspend fun captureStill(): Result<CapturedImage> {
        val capture = imageCapture
            ?: run {
                // No capture use case bound: treat as a capture failure, keep
                // the preview (Req 1.11), and surface the typed state.
                emitCaptureFailed()
                return Result.failure(IllegalStateException("Camera is not started"))
            }
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                analysisExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            cont.resume(Result.success(image.toCapturedImage()))
                        } catch (t: Throwable) {
                            emitCaptureFailed()
                            cont.resume(Result.failure(t))
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        // Req 1.11: inform + preserve preview + allow retry.
                        emitCaptureFailed()
                        cont.resume(Result.failure(exception))
                    }
                },
            )
        }
    }

    // --- internals -----------------------------------------------------------

    private fun onAnalysisFrame(image: ImageProxy) {
        // In-flight guard: drop the frame if the previous emission is still
        // being consumed, keeping the live loop responsive (Req 12.2).
        if (frameInFlight) {
            image.close()
            return
        }
        frameInFlight = true
        try {
            val frame = AnalysisFrame(
                buffer = image.yPlaneToLuminance(),
                rotationDegrees = image.imageInfo.rotationDegrees.normalizedRotation(),
                timestampMs = image.imageInfo.timestamp / 1_000_000L,
            )
            _analysisFrames.tryEmit(frame)
        } finally {
            image.close()
            frameInFlight = false
        }
    }

    private fun applyTorch() {
        val control = camera?.cameraControl ?: return
        control.enableTorch(CameraPolicy.resolveTorchEnabled(scanningMode, torchPreference))
    }

    private fun applyFocus() {
        val control = camera?.cameraControl ?: return
        val diopters = CameraPolicy.workingDistanceToDiopters(workingDistanceCm)
        // Best-effort manual close-range focus; devices without manual focus
        // simply ignore these options. The typed NoMacroFocus state is 18.2.
        runCatching {
            val camera2Control = Camera2CameraControl.from(control)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF,
                )
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
                .build()
            // Returns a ListenableFuture<Void>; fire-and-forget for best-effort focus.
            camera2Control.setCaptureRequestOptions(options)
        }
    }

    private fun refreshPreviewState() {
        if (_previewState.value is CameraState.Previewing) {
            _previewState.value = CameraState.Previewing(
                torchEnabled = CameraPolicy.resolveTorchEnabled(scanningMode, torchPreference),
                focusDistanceCm = CameraPolicy.clampWorkingDistanceCm(workingDistanceCm),
            )
        }
    }

    /**
     * Emits the typed [CameraError.CAPTURE_FAILED] state (Req 1.11). Because the
     * policy marks this error as preview-preserving, the existing preview
     * binding is left untouched; only the observable state changes so the UI can
     * show the message and a retry control over the still-live preview.
     */
    private fun emitCaptureFailed() {
        _previewState.value = CameraState.error(CameraError.CAPTURE_FAILED)
    }

    /**
     * Inspects a freshly bound [camera] for the non-fatal capability gaps that
     * must be surfaced while scanning continues, returning the highest-priority
     * [CameraError] or `null` when the camera is fully capable.
     *
     * Missing close-range focus (Req 1.9) is reported before a missing Torch
     * (Req 1.6): without macro focus the dots are out of focus regardless of
     * lighting, so it is the more actionable instruction. Both are
     * preview-preserving per [CameraErrorPolicy].
     */
    private fun detectCapabilityError(camera: Camera): CameraError? = when {
        !supportsCloseRangeFocus(camera) -> CameraError.NO_MACRO_FOCUS
        !camera.cameraInfo.hasFlashUnit() -> CameraError.NO_TORCH
        else -> null
    }

    /**
     * True when the active [camera] exposes a minimum focus distance near enough
     * to cover the supported close-range working window (Req 1.4). A reported
     * minimum focus distance of `0` means the lens is fixed-focus (effectively
     * focused at infinity) and cannot engage macro focus, so this returns
     * `false` and the caller surfaces [CameraError.NO_MACRO_FOCUS] (Req 1.9).
     */
    private fun supportsCloseRangeFocus(camera: Camera): Boolean {
        val minFocusDiopters = runCatching {
            Camera2CameraInfo.from(camera.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        }.getOrNull() ?: return true // Unknown: don't raise a false alarm.

        // A fixed-focus lens reports 0 (infinity only) and cannot focus close.
        if (minFocusDiopters <= 0f) return false

        // The lens can focus at least as near as our nearest working distance
        // when its largest supported diopter value reaches that point.
        return minFocusDiopters >= CameraPolicy.maxFocusDiopters
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                },
                mainExecutor,
            )
        }

    private fun ImageProxy.toCapturedImage(): CapturedImage =
        CapturedImage(
            when (format) {
                ImageFormat.JPEG -> decodeJpegToLuminance()
                else -> yPlaneToLuminance()
            },
        )

    /** Extracts the Y (luminance) plane of a YUV_420_888 frame into an [ImageBuffer]. */
    private fun ImageProxy.yPlaneToLuminance(): ImageBuffer {
        val plane = planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = width
        val h = height
        val out = ByteArray(w * h)
        for (row in 0 until h) {
            val rowStart = row * rowStride
            for (col in 0 until w) {
                out[row * w + col] = buffer.get(rowStart + col * pixelStride)
            }
        }
        return ImageBuffer(ImageSize(w, h), out)
    }

    /** Decodes a JPEG-format still into a single-channel luminance [ImageBuffer]. */
    private fun ImageProxy.decodeJpegToLuminance(): ImageBuffer {
        val plane = planes[0]
        val jpeg = plane.buffer
        val bytes = ByteArray(jpeg.remaining())
        jpeg.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Captured still could not be decoded")
        return bitmap.toLuminance().also { bitmap.recycle() }
    }

    private fun Bitmap.toLuminance(): ImageBuffer {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma.
            out[i] = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255).toByte()
        }
        return ImageBuffer(ImageSize(w, h), out)
    }

    private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360
}

package com.constella.braille.runtime.translate

/**
 * Thin JNI bridge to the bundled liblouis native library.
 *
 * liblouis is cross-compiled with the Android NDK and shipped as
 * `liblouis.so` plus a small `liblouis_jni.so` shim in
 * `runtime/src/main/jniLibs/<abi>/` (see `jniLibs/README.md`). This object
 * declares the `external` (native) methods that shim exposes and owns the
 * one-time library load.
 *
 * **Graceful degradation.** The native `.so` files and the Grade 1/Grade 2
 * tables are *not yet bundled* in this skeleton. Every entry point here is
 * guarded by [ensureLoaded], which returns `false` (instead of throwing) when
 * the libraries cannot be loaded — for example on the JVM during unit tests, or
 * on a build where the binaries have not been dropped in yet. Callers
 * ([LiblouisTranslationEngine]) translate a `false`/`null` result into a
 * `TranslationUnavailableException` so the rest of the pipeline keeps working.
 *
 * The native shim is expected to implement, on top of `lou_backTranslate`, an
 * output→input character position mapping so the Kotlin side can rebuild the
 * cell→character spans. See [NativeBackTranslation].
 *
 * _Requirements: 7.1, 7.4_
 */
internal object LiblouisBridge {

    /** Base name of the liblouis shared library (`liblouis.so`). */
    private const val LIB_LIBLOUIS = "louis"

    /** Base name of the JNI shim (`liblouis_jni.so`). */
    private const val LIB_JNI_SHIM = "louis_jni"

    @Volatile
    private var loadState: LoadState = LoadState.UNATTEMPTED

    private enum class LoadState { UNATTEMPTED, LOADED, FAILED }

    /**
     * Attempt to load the native libraries exactly once.
     *
     * @return `true` if liblouis and its JNI shim are available, `false`
     *   otherwise. Never throws: a missing/incompatible `.so` becomes `false`.
     */
    @Synchronized
    fun ensureLoaded(): Boolean {
        when (loadState) {
            LoadState.LOADED -> return true
            LoadState.FAILED -> return false
            LoadState.UNATTEMPTED -> Unit
        }
        loadState = try {
            System.loadLibrary(LIB_LIBLOUIS)
            System.loadLibrary(LIB_JNI_SHIM)
            LoadState.LOADED
        } catch (_: UnsatisfiedLinkError) {
            LoadState.FAILED
        } catch (_: SecurityException) {
            LoadState.FAILED
        }
        return loadState == LoadState.LOADED
    }

    /** `true` once the native libraries have been successfully loaded. */
    val isLoaded: Boolean
        get() = loadState == LoadState.LOADED

    /**
     * Point liblouis at the directory holding the bundled translation tables.
     * Must be a real filesystem directory (assets are copied out at startup),
     * because liblouis reads tables from disk by name.
     *
     * Native (`lou_setDataPath`). Only call when [ensureLoaded] returned `true`.
     */
    external fun nativeSetTablesDirectory(absolutePath: String)

    /**
     * Back-translate a Unicode-Braille [braille] string (one `U+28xx` character
     * per cell) to text using the comma-separated [tableList] of bundled table
     * file names.
     *
     * @return the translated text together with the per-output-character source
     *   mapping, or `null` if liblouis reported a failure.
     *
     * Native (`lou_backTranslate` + position mapping). Only call when
     * [ensureLoaded] returned `true`.
     */
    external fun nativeBackTranslate(tableList: String, braille: String): NativeBackTranslation?

    /** liblouis version string, for diagnostics. Native (`lou_version`). */
    external fun nativeVersion(): String
}

/**
 * Raw result of a native back-translation, constructed on the JNI side.
 *
 * @property text the translated output text.
 * @property inputPositions for each output character index `i`,
 *   `inputPositions[i]` is the index (into the input Braille string, i.e. the
 *   source cell index) of the cell that produced that output character. This is
 *   liblouis's `inputPos` array and is what lets the Kotlin layer rebuild
 *   cell→character spans for Grade 2 contractions. Length equals `text.length`.
 */
class NativeBackTranslation(
    @JvmField val text: String,
    @JvmField val inputPositions: IntArray,
)

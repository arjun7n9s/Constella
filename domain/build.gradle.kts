// :domain — Domain layer (pure Kotlin/JVM).
//
// This is the deterministic core of the app: ScanSession state machine,
// AlignmentEvaluator, GradeDetector, segmentation/reading-order logic,
// confidence policy, data models, and the centralized thresholds.
//
// It is a plain Kotlin/JVM module (NOT an Android module) so that it has no
// Android dependencies and can be exercised quickly by JVM property-based
// tests. It depends on nothing else in the project — the bottom of the
// downward-only dependency arrows.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Property-based + unit testing (kotest-property is the chosen PBT framework).
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)
}

// kotest uses the JUnit Platform.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

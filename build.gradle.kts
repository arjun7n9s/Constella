// Root build script. Plugins are declared here with `apply false` so the
// version catalog pins one version for the whole build; each module applies
// the plugins it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Root build: declare plugin versions once, apply in modules.
// The Kotlin JVM plugin is NOT declared here: it lives only in core/ (a separate build).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}

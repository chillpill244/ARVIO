// Root build file for the muvio KMP project (iOS + Android mobile touch apps).
// This is a SEPARATE Gradle build from the Android TV app (ARVIO/:app), which
// stays on its own pinned toolchain. Nothing here affects the TV app.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

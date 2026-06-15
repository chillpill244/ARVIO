import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )
    iosTargets.forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            // Lifecycle / ViewModel (multiplatform)
            implementation(libs.androidx.lifecycle.viewmodel)
            // Compose Multiplatform UI
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)
            // Navigation (multiplatform)
            implementation(libs.androidx.navigation.compose)
            // Compose lifecycle integration
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            // Koin compose + viewmodel
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Image loading (multiplatform)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(compose.preview)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.muvio.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
            }
        }
    }
}

android {
    namespace = "com.muvio.app"
    buildFeatures { buildConfig = true }
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.muvio.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        // Keys are empty by default; override in local.properties or CI secrets
        buildConfigField("String", "TMDB_API_KEY", "\"${project.findProperty("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${project.findProperty("TRAKT_CLIENT_ID") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

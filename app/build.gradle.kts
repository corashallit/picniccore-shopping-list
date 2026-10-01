plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin: no org.jetbrains.kotlin.android here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val jvmTargetVersion = libs.versions.jvmTarget.get()

// APK file name: picnic-list-debug.apk instead of app-debug.apk.
base {
    archivesName.set("picnic-list")
}

android {
    namespace = "earth.cora.picnic"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "earth.cora.picnic"
        minSdk {
            version = release(libs.versions.minSdk.get().toInt())
        }
        targetSdk {
            version = release(libs.versions.targetSdk.get().toInt())
        }
        // CI passes -Ppicnic.versionCode=<run number> so each CI APK installs over the last one.
        versionCode = providers.gradleProperty("picnic.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("picnic.versionName").getOrElse("0.1.0-dev")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Committed debug keystore so successive sideloaded APKs update in place.
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Robolectric on SDK 36+ touches FileDescriptor internals via jdk.internal.access.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        // targetSdk 36 is deliberate (see docs/BUILDING.md); version-freshness checks are noise in CI.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

roborazzi {
    // Goldens live in the repo so art passes can review them without an emulator.
    outputDir.set(file("src/test/screenshots"))
    // Every @Preview in earth.cora.picnic becomes a screenshot test automatically.
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    generateComposePreviewRobolectricTests {
        enable.set(true)
        packages.set(listOf("earth.cora.picnic"))
        includePrivatePreviews.set(true)
        // Values are pasted into generated Kotlin source: string values need their own quotes.
        robolectricConfig.set(mapOf("sdk" to "[36]", "qualifiers" to "\"w411dp-h891dp-night-xxhdpi\""))
    }
}

dependencies {
    implementation(libs.picnic.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)
}

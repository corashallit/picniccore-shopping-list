plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin: no org.jetbrains.kotlin.android here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val jvmTargetVersion = libs.versions.jvmTarget.get()

// Play upload key (docs/RELEASING.md). Each value comes from an environment variable (CI) or the
// Gradle property next to it (for example in ~/.gradle/gradle.properties; never in this repository).
// None set: no release signing at all, so debug and sideload builds are unchanged and
// bundleRelease/assembleRelease produce unsigned artifacts as before. The key password may be
// omitted: a PKCS12 keystore (keytool's default) uses the store password for its key.
fun uploadSetting(env: String, property: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(property)).orNull?.takeIf { it.isNotBlank() }

val uploadStoreFile = uploadSetting("PICNIC_UPLOAD_STORE_FILE", "picnic.upload.storeFile")
val uploadStorePassword = uploadSetting("PICNIC_UPLOAD_STORE_PASSWORD", "picnic.upload.storePassword")
val uploadKeyAlias = uploadSetting("PICNIC_UPLOAD_KEY_ALIAS", "picnic.upload.keyAlias")
val uploadKeyPassword = uploadSetting("PICNIC_UPLOAD_KEY_PASSWORD", "picnic.upload.keyPassword") ?: uploadStorePassword
val uploadKeystore: File? = run {
    val required = mapOf(
        "PICNIC_UPLOAD_STORE_FILE" to uploadStoreFile,
        "PICNIC_UPLOAD_STORE_PASSWORD" to uploadStorePassword,
        "PICNIC_UPLOAD_KEY_ALIAS" to uploadKeyAlias,
    )
    val missing = required.filterValues { it == null }.keys
    when {
        missing.size == required.size -> null
        // Half a configuration would silently produce an unsigned bundle: fail loudly instead.
        missing.isNotEmpty() -> throw GradleException(
            "Release signing is only partly configured; also set ${missing.joinToString()} " +
                "(or the matching picnic.upload.* Gradle properties). See docs/RELEASING.md.",
        )
        // Relative paths resolve against the repository root.
        else -> rootProject.file(uploadStoreFile!!).also {
            if (!it.isFile) throw GradleException("PICNIC_UPLOAD_STORE_FILE: no keystore file at $it")
        }
    }
}

// APK file names: picnic-list-debug.apk / picnic-list-sideload.apk instead of app-*.apk.
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
        // Play builds pass minutes since 2026-01-01 UTC instead (docs/RELEASING.md, "Version codes").
        // 2100000000 is the largest version code Play accepts.
        versionCode = providers.gradleProperty("picnic.versionCode").map(String::toInt).getOrElse(1).also {
            if (it !in 1..2_100_000_000) throw GradleException("picnic.versionCode must be 1..2100000000, got $it")
        }
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
        // Play upload key: only when configured (see the top of this file). Play re-signs what it
        // delivers with its own app signing key, so Play installs never match the debug key.
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // What goes to Google Play: `bundleRelease` (R8 on). Signed with the upload key when it is
        // configured, unsigned otherwise. Not debuggable (Play rejects debuggable builds).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
        // What friends install (CI's APK): the debug build's code and committed signing key, so it
        // installs over earlier debug APKs, but NOT debuggable. A debuggable app lets anyone with
        // brief USB access `adb shell run-as` into its files and Keystore key (the stored OpenAI key)
        // or attach a debugger. Not minified: the same bytecode the unit tests cover.
        create("sideload") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
    implementation(libs.androidx.datastore.preferences)
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

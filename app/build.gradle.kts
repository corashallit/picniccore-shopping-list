plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin: no org.jetbrains.kotlin.android here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val jvmTargetVersion = libs.versions.jvmTarget.get()

// The app's private signing key, registered for earth.cora.picnic in Android Developer Console
// (docs/RELEASING.md). Each value comes from an environment variable (CI) or the Gradle property next
// to it (for example in ~/.gradle/gradle.properties; never in this repository).
// None set: sideload builds fall back to the committed debug key, and assembleRelease/bundleRelease
// produce unsigned artifacts. The key password may be omitted: a PKCS12 keystore (keytool's default)
// uses the store password for its key.
fun signingSetting(env: String, property: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(property)).orNull?.takeIf { it.isNotBlank() }

val signingStoreFile = signingSetting("PICNIC_SIGNING_STORE_FILE", "picnic.signing.storeFile")
val signingStorePassword = signingSetting("PICNIC_SIGNING_STORE_PASSWORD", "picnic.signing.storePassword")
val signingKeyAlias = signingSetting("PICNIC_SIGNING_KEY_ALIAS", "picnic.signing.keyAlias")
val signingKeyPassword = signingSetting("PICNIC_SIGNING_KEY_PASSWORD", "picnic.signing.keyPassword") ?: signingStorePassword
val signingKeystore: File? = run {
    val required = mapOf(
        "PICNIC_SIGNING_STORE_FILE" to signingStoreFile,
        "PICNIC_SIGNING_STORE_PASSWORD" to signingStorePassword,
        "PICNIC_SIGNING_KEY_ALIAS" to signingKeyAlias,
    )
    val missing = required.filterValues { it == null }.keys
    when {
        missing.size == required.size -> null
        // Half a configuration would silently fall back to the debug key: fail loudly instead.
        missing.isNotEmpty() -> throw GradleException(
            "Signing is only partly configured; also set ${missing.joinToString()} " +
                "(or the matching picnic.signing.* Gradle properties). See docs/RELEASING.md.",
        )
        // Relative paths resolve against the repository root.
        else -> rootProject.file(signingStoreFile!!).also {
            if (!it.isFile) throw GradleException("PICNIC_SIGNING_STORE_FILE: no keystore file at $it")
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
        // 2100000000 is the largest version code Google Play accepts.
        versionCode = providers.gradleProperty("picnic.versionCode").map(String::toInt).getOrElse(1).also {
            if (it !in 1..2_100_000_000) throw GradleException("picnic.versionCode must be 1..2100000000, got $it")
        }
        versionName = providers.gradleProperty("picnic.versionName").getOrElse("0.1.0-dev")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Committed debug keystore so successive debug builds update in place. It is public:
            // never register it in Android Developer Console.
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The registered private key: only when configured (see the top of this file).
        if (signingKeystore != null) {
            create("private") {
                storeFile = signingKeystore
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // R8 build (`assembleRelease`/`bundleRelease`), not what testers get today. Signed with the
        // private key when it is configured, unsigned otherwise. Not debuggable.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("private")
        }
        // What testers install (CI's APK): the debug build's code, but NOT debuggable. A debuggable app
        // lets anyone with brief USB access `adb shell run-as` into its files and Keystore key (the
        // stored OpenAI key) or attach a debugger. Not minified: the same bytecode the unit tests cover.
        // Signed with the registered private key when it is configured (CI checks the certificate), and
        // with the debug key otherwise, which only suits your own phone over ADB.
        create("sideload") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("debug")
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

// :app:checkStickers: the generated sticker art matches the DSL (design/stickers/, docs/DESIGN_STICKER.md §18.3).
apply(from = rootProject.file("design/stickers/checkStickers.gradle.kts"))

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "earth.cora.picnic"
version = "0.1.0"

val jvmTargetVersion = libs.versions.jvmTarget.get()

java {
    // Compiled with JDK 21 (no toolchain download), bytecode + API level 17.
    sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(jvmTargetVersion.toInt())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
        // Restrict JDK API usage to 17 even though we compile on 21.
        freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Live OpenAI tests (@Tag("live")) only run with -Plive=true and OPENAI_API_KEY in the env.
    val live = providers.gradleProperty("live").map(String::toBoolean).getOrElse(false)
    useJUnitPlatform {
        if (!live) excludeTags("live")
    }
    if (live) outputs.upToDateWhen { false }
}

application {
    mainClass.set("earth.cora.picnic.core.cli.MainKt")
}

// ./gradlew -p core run --args="ingest 'ingredients for pancakes'"
// The CLI reads OPENAI_API_KEY from the inherited environment (JavaExec inherits it).
// Don't call environment("OPENAI_API_KEY", System.getenv(...)) here: that would bake the
// key into the configuration cache on disk.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

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
    if (live) {
        outputs.upToDateWhen { false }
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }
    }
}

application {
    mainClass.set("earth.cora.picnic.core.cli.MainKt")
}

// Live LLM eval (docs/PROMPTS.md §10): ./gradlew -p core liveEval -Plive=true [--args="--only=<ids> --repeat=2"]
// Calls the real API (OPENAI_API_KEY inherited from the environment, like `run`), so it is gated on
// -Plive=true and never runs as part of `build` or `test`. Reports go to core/build/eval/.
val liveEvalEnabled = providers.gradleProperty("live").map(String::toBoolean).getOrElse(false)
tasks.register<JavaExec>("liveEval") {
    group = "verification"
    description = "Runs the live LLM eval set against the real API (needs -Plive=true and OPENAI_API_KEY)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("earth.cora.picnic.core.eval.EvalMainKt")
    workingDir = projectDir
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val enabled = liveEvalEnabled
    doFirst {
        // --rescore re-scores a saved report offline; everything else calls the API.
        val offline = (this as JavaExec).args.orEmpty().any { it.startsWith("--rescore") }
        if (!enabled && !offline) throw GradleException("liveEval calls the real OpenAI API and costs money: pass -Plive=true")
    }
}

// ./gradlew -p core run --args="ingest 'ingredients for pancakes'"
// The CLI reads OPENAI_API_KEY from the inherited environment (JavaExec inherits it).
// Don't call environment("OPENAI_API_KEY", System.getenv(...)) here: that would bake the
// key into the configuration cache on disk.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

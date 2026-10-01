pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // gradle/libs.versions.toml is picked up automatically as "libs" in the root build.
}

rootProject.name = "picnic-list"

// core/ is a standalone Gradle build (plain Kotlin/JVM). Gradle substitutes
// earth.cora.picnic:core with it automatically.
includeBuild("core")
include(":app")

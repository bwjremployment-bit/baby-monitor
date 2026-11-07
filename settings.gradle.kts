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
}

rootProject.name = "babymonitor"

include(":camera", ":viewer")

// The device-independent protocol/crypto/state library is a standalone Gradle
// build (so it can be built + unit-tested without the Android SDK). It is
// consumed here as a substituted dependency (com.family.monitor:shared).
includeBuild("shared")

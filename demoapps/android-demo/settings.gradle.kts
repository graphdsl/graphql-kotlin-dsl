pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Uses the local graphdsl plugin source during development.
    // To use the published plugin instead, remove this line and add
    // version "0.1.0" to the plugin block in app/build.gradle.kts.
    includeBuild("../../gradle-plugins")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "graphdsl-android-demo"
include(":app")

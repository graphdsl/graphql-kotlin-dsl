import io.github.graphdsl.gradle.internal.includeNamed

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../build-logic")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("settings.common")
}

// Core modules - source directories are at the project root (../../)
includeNamed(":mapper", "../..")
includeNamed(":codegen", "../..")
includeNamed(":cli", "../..")
includeNamed(":schema", "../..")
includeNamed(":utils", "../..")
includeNamed(":shared", "../..")

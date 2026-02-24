import io.github.graphdsl.gradle.internal.includeNamed

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../build-logic")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("settings.common")
}

includeNamed(":common")
includeNamed(":graphdsl-plugin")

// Substitute core library modules from source so this build can be run standalone
// (e.g. via --project-dir gradle-plugins in CI) without requiring published artifacts.
includeBuild("../included-builds/core") {
    dependencySubstitution {
        substitute(module("io.github.graphdsl:cli")).using(project(":cli"))
        substitute(module("io.github.graphdsl:codegen")).using(project(":codegen"))
        substitute(module("io.github.graphdsl:mapper")).using(project(":mapper"))
        substitute(module("io.github.graphdsl:schema")).using(project(":schema"))
        substitute(module("io.github.graphdsl:shared")).using(project(":shared"))
        substitute(module("io.github.graphdsl:utils")).using(project(":utils"))
    }
}

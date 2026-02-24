pluginManagement {
    includeBuild("build-logic")
    includeBuild("gradle-plugins")
}

plugins {
    id("settings.common")
    id("settings.build-scans")
}

rootProject.name = "io.github.graphdsl"

// Core library modules (dependency substitution allows gradle-plugins to depend on them)
includeBuild("included-builds/core") {
    dependencySubstitution {
        substitute(module("io.github.graphdsl:cli")).using(project(":cli"))
        substitute(module("io.github.graphdsl:codegen")).using(project(":codegen"))
        substitute(module("io.github.graphdsl:mapper")).using(project(":mapper"))
        substitute(module("io.github.graphdsl:schema")).using(project(":schema"))
        substitute(module("io.github.graphdsl:shared")).using(project(":shared"))
        substitute(module("io.github.graphdsl:utils")).using(project(":utils"))
    }
}

// Gradle plugins (substitution for publishing)
includeBuild("gradle-plugins") {
    dependencySubstitution {
        substitute(module("io.github.graphdsl:gradle-plugins-common")).using(project(":common"))
        substitute(module("io.github.graphdsl:graphdsl-gradle-plugin")).using(project(":graphdsl-plugin"))
    }
}
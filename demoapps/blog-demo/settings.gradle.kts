pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../gradle-plugins")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Substitute core library modules so the graphdsl plugin classpath resolves locally
// (graphdsl-plugin depends on io.github.graphdsl:cli which is built here, not published)
includeBuild("../../included-builds/core") {
    dependencySubstitution {
        substitute(module("io.github.graphdsl:cli")).using(project(":cli"))
        substitute(module("io.github.graphdsl:codegen")).using(project(":codegen"))
        substitute(module("io.github.graphdsl:mapper")).using(project(":mapper"))
        substitute(module("io.github.graphdsl:schema")).using(project(":schema"))
        substitute(module("io.github.graphdsl:shared")).using(project(":shared"))
        substitute(module("io.github.graphdsl:utils")).using(project(":utils"))
    }
}

rootProject.name = "blog-demo"

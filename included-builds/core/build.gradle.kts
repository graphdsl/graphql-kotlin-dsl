plugins {
    id("conventions.graphdsl-publishing") apply false
    id("buildroot.orchestration")
    id("buildroot.versioning")
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        pluginManager.apply("conventions.graphdsl-publishing")
    }
}

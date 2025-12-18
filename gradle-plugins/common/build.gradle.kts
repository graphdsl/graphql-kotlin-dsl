plugins {
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
    id("conventions.graphdsl-publishing")
}

dependencies {
    api(gradleApi())
    implementation(libs.idea.gradle.plugin)
}

graphDslPublishing {
    artifactId.set("gradle-plugins-common")
    name.set("Common Gradle Plugin Libraries")
    description.set("Common libs used by GraphDSL Gradle plugins.")
}

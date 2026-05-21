plugins {
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.idea.gradle.plugin)
}

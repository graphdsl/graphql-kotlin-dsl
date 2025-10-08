plugins {
    id("conventions.kotlin-without-tests")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":schema"))
    implementation(project(":utils"))
    implementation(project(":mapper"))
    implementation(project(":shared"))
    implementation(libs.graphql.java)
    implementation(libs.kotlinx.metadata.jvm)
}

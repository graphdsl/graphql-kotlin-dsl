plugins {
    id("conventions.kotlin-without-tests")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.clikt.jvm)
    implementation(libs.graphql.java)
    implementation(project(":mapper"))
    implementation(project(":utils"))
    implementation(project(":codegen"))
    implementation(project(":schema"))
}

plugins {
    id("conventions.kotlin")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":schema"))
    implementation(project(":utils"))
    implementation(libs.graphql.java)
    implementation(libs.kotlinx.metadata.jvm)
}

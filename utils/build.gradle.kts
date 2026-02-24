plugins {
    id("conventions.kotlin")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.graphql.java)
    implementation(libs.kotlinx.metadata.jvm)
    implementation(libs.antlr.st4)
}

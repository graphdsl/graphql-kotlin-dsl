plugins {
    id("conventions.kotlin-without-tests")
}

dependencies {
    api(libs.javassist)
    api(libs.kotlinx.metadata.jvm)
    implementation(libs.antlr.st4)
    implementation(libs.kotlinx.coroutines.core)
}

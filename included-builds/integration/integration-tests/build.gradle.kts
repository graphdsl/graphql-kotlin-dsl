plugins {
    kotlin("jvm")
    id("io.github.graphdsl.graphdsl-gradle-plugin")
}

kotlin {
    jvmToolchain(17)
}

graphDsl {
    packageName.set("io.github.graphdsl.integration.dsl")
    schemaDir.set("src/main/graphql")
}

dependencies {
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

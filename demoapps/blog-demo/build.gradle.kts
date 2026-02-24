plugins {
    kotlin("jvm") version "1.9.24"
    id("io.github.graphdsl.graphdsl-gradle-plugin") version "0.1.0"
}

kotlin {
    jvmToolchain(17)
}

graphDsl {
    packageName.set("demo.blog.dsl")
    schemaDir.set("src/main/graphql")
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

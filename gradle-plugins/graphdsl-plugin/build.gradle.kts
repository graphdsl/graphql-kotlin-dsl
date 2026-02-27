plugins {
    `kotlin-dsl`
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
    id("com.gradle.plugin-publish") version "2.0.0"
    id("conventions.graphdsl-publishing")
}

// Declare functional test source set before gradlePlugin block references it
val functionalTest by sourceSets.creating

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":common"))

    implementation("io.github.graphdsl:cli:${project.version}")

    // Do NOT leak the Kotlin Gradle Plugin at runtime
    compileOnly(libs.kotlin.gradle.plugin)

    // Unit tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)

    // Functional (E2E) tests via Gradle TestKit
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(libs.kotlin.test)
    "functionalTestImplementation"(libs.junit)
    "functionalTestRuntimeOnly"(libs.junit.engine)
    "functionalTestRuntimeOnly"(libs.junit.launcher)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    }
}

gradlePlugin {
    website = "https://github.com/graphdsl/graphdsl"
    vcsUrl = "https://github.com/graphdsl/graphdsl"

    // Injects plugin-under-test-metadata.properties so withPluginClasspath() works
    testSourceSets(functionalTest)

    plugins {
        create("graphDsl") {
            id = "$group.graphdsl-gradle-plugin"
            implementationClass = "io.github.graphdsl.gradle.GraphDslPlugin"
            displayName = "GraphDSL :: Gradle Plugin"
            description = "Generates type-safe Kotlin DSL query builders from GraphQL schema."
            tags.set(listOf("graphql", "kotlin", "dsl", "codegen"))
        }
    }
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs E2E functional tests using Gradle TestKit."
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
}

tasks.check { dependsOn(functionalTestTask) }

graphDslPublishing {
    name.set("GraphDSL Gradle Plugin")
    description.set("Gradle plugin that generates Kotlin DSL query builders from GraphQL schema.")
    artifactId.set("graphdsl-gradle-plugin")
}

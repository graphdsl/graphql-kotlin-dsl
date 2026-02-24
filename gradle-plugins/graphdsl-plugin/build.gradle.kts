plugins {
    `kotlin-dsl`
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
    id("com.gradle.plugin-publish") version "2.0.0"
    id("conventions.graphdsl-publishing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":common"))

    implementation("io.github.graphdsl:cli:${project.version}")

    // Do NOT leak the Kotlin Gradle Plugin at runtime
    compileOnly(libs.kotlin.gradle.plugin)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)
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

graphDslPublishing {
    name.set("GraphDSL Gradle Plugin")
    description.set("Gradle plugin that generates Kotlin DSL query builders from GraphQL schema.")
    artifactId.set("graphdsl-gradle-plugin")
}

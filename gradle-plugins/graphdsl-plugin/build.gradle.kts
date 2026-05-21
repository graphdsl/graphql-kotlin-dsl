import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.plugin.compatibility.compatibility
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `kotlin-dsl`
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
    id("com.gradle.plugin-publish") version "2.1.1"
    id("conventions.graphdsl-publishing")
    id("com.gradleup.shadow") version "8.3.5"
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
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    isZip64 = true
    mergeServiceFiles()

    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
        )
    }

    // Relocate third-party deps to avoid classpath conflicts in consumer builds
    relocate("graphql.", "io.github.graphdsl.shadow.graphql.")
    relocate("org.antlr", "io.github.graphdsl.shadow.antlr")
    relocate("com.github.ajalt.clikt", "io.github.graphdsl.shadow.clikt")
    relocate("javassist", "io.github.graphdsl.shadow.javassist")

    // Kotlin stdlib is provided by Gradle — don't bundle it
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
    }
}

// Publish the shadow (fat) JAR; keep the thin jar only for local use
tasks.named<Jar>("jar") {
    archiveClassifier.set("thin")
}

// Wire shadow JAR into the variants used by Maven publishing
configurations.named("runtimeElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.named<ShadowJar>("shadowJar"))
}
configurations.named("apiElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.named<ShadowJar>("shadowJar"))
}

// Suppress the extra shadowRuntimeElements variant so it isn't published as a separate component
afterEvaluate {
    val shadowRuntimeElements = configurations.findByName("shadowRuntimeElements")
    if (shadowRuntimeElements != null) {
        (components["java"] as AdhocComponentWithVariants)
            .withVariantsFromConfiguration(shadowRuntimeElements) { skip() }
    }
}

gradlePlugin {
    website = "https://github.com/graphdsl/graphql-kotlin-dsl"
    vcsUrl = "https://github.com/graphdsl/graphql-kotlin-dsl"

    plugins {
        create("graphDsl") {
            id = "$group.graphdsl-gradle-plugin"
            implementationClass = "io.github.graphdsl.gradle.GraphDslPlugin"
            displayName = "GraphDSL :: Gradle Plugin"
            description = "Generates type-safe Kotlin DSL query builders from GraphQL schema."
            tags.set(listOf("graphql", "kotlin", "dsl", "codegen"))
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
    }
}

graphDslPublishing {
    name.set("GraphDSL Gradle Plugin")
    description.set("Gradle plugin that generates Kotlin DSL query builders from GraphQL schema.")
    artifactId.set("graphdsl-gradle-plugin")
}

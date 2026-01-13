package io.github.graphdsl.gradle

import io.github.graphdsl.gradle.task.GenerateDslTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import io.github.graphdsl.gradle.GraphDslPluginCommon
import io.github.graphdsl.gradle.GraphDslPluginCommon.configureIdeaIntegration

/**
 * Gradle plugin that generates type-safe Kotlin DSL query builders from GraphQL schema.
 *
 * Apply this plugin to any project that has GraphQL schema files and wants to
 * use a Kotlin DSL for building queries.
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     kotlin("jvm")
 *     id("io.github.graphdsl.graphdsl-gradle-plugin")
 * }
 *
 * graphDsl {
 *     packageName.set("com.example.api.dsl")
 *     schemaDir.set("src/main/graphql")
 * }
 * ```
 *
 * The plugin will:
 * 1. Scan for `.graphqls` files in the configured schema directory
 * 2. Generate Kotlin DSL source files into `build/generated-sources/graphdsl`
 * 3. Automatically add the generated sources to the Kotlin `main` source set
 */
class GraphDslPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val ext = extensions.create("graphDsl", GraphDslExtension::class.java, objects)

        val pluginClasspath = files(
            GraphDslPluginCommon.getClassPathElements(this@GraphDslPlugin::class.java)
        )

        val generateDslTask = tasks.register<GenerateDslTask>("generateGraphDsl") {
            mainClass.set(CODEGEN_MAIN_CLASS)
            classpath.setFrom(pluginClasspath)
            schemaFiles.setFrom(
                ext.schemaDir.map { dir ->
                    fileTree(layout.projectDirectory.dir(dir)) { include("**/*.graphqls") }
                }
            )
            packageName.set(ext.packageName)
            outputDirectory.set(layout.buildDirectory.dir("generated-sources/graphdsl"))
        }

        // Wire generated sources into Kotlin compilation
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinExt = extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlinExt.sourceSets.named("main") {
                kotlin.srcDir(generateDslTask.flatMap { it.outputDirectory })
            }
        }

        // Wire into Java compilation if Kotlin plugin isn't applied
        pluginManager.withPlugin("java") {
            if (!pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
                val sourceSets = extensions.getByType(
                    org.gradle.api.tasks.SourceSetContainer::class.java
                )
                sourceSets.named("main") {
                    java.srcDir(generateDslTask.flatMap { it.outputDirectory })
                }
            }
        }

        // Make compile tasks depend on generation
        tasks.matching { it.name == "compileKotlin" || it.name == "compileJava" }.configureEach {
            dependsOn(generateDslTask)
        }

        // IDE integration
        configureIdeaIntegration(generateDslTask)
    }

    companion object {
        private const val CODEGEN_MAIN_CLASS = "io.github.graphdsl.cli.KotlinDslGenerator\$Main"
    }
}

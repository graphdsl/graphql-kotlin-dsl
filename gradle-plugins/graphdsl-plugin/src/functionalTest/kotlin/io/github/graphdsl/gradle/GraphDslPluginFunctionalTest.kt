package io.github.graphdsl.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphDslPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun writeSettings(rootName: String = "test-project") {
        projectDir.resolve("settings.gradle.kts")
            .writeText("""rootProject.name = "$rootName"""")
    }

    private fun writeBuild(packageName: String = "com.example.dsl") {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.graphdsl.graphdsl-gradle-plugin")
            }
            graphDsl {
                packageName.set("$packageName")
            }
            """.trimIndent()
        )
    }

    private fun writeSchema(content: String) {
        val dir = projectDir.resolve("src/main/graphql").also { it.mkdirs() }
        dir.resolve("schema.graphqls").writeText(content.trimIndent())
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args)
            .withPluginClasspath()

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    fun `generateGraphDsl task succeeds with a simple Query type`() {
        writeSettings()
        writeBuild()
        writeSchema(
            """
            type Query {
                hello: String
                user(id: ID!): User
            }
            type User {
                id: ID!
                name: String!
            }
            """
        )

        val result = runner("generateGraphDsl", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateGraphDsl")?.outcome)
        val outputDir = projectDir.resolve("build/generated-sources/graphdsl")
        assertTrue(outputDir.exists(), "Output directory should be created")
        assertTrue(
            outputDir.walkTopDown().any { it.extension == "kt" },
            "Expected at least one generated .kt file"
        )
    }

    @Test
    fun `generateGraphDsl task is UP-TO-DATE on second run`() {
        writeSettings()
        writeBuild()
        writeSchema(
            """
            type Query {
                hello: String
            }
            """
        )

        runner("generateGraphDsl").build()
        val result = runner("generateGraphDsl").build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateGraphDsl")?.outcome)
    }

    @Test
    fun `generated files declare the configured package name`() {
        writeSettings()
        writeBuild(packageName = "com.acme.graphql")
        writeSchema(
            """
            type Query {
                hello: String
            }
            """
        )

        runner("generateGraphDsl").build()

        val generatedFiles = projectDir.resolve("build/generated-sources/graphdsl")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

        assertTrue(generatedFiles.isNotEmpty(), "Expected at least one generated .kt file")
        assertTrue(
            generatedFiles.all { it.readText().contains("package com.acme.graphql") },
            "All generated files should declare 'package com.acme.graphql'"
        )
    }

    @Test
    fun `generateGraphDsl skips gracefully when no schema files exist`() {
        writeSettings()
        // No graphDsl block needed — defaults are fine, no schema dir created
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.graphdsl.graphdsl-gradle-plugin")
            }
            """.trimIndent()
        )

        val result = runner("generateGraphDsl").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateGraphDsl")?.outcome)
        assertTrue(
            result.output.contains("No .graphqls schema files found"),
            "Expected warning about missing schema files in build output"
        )
    }
}

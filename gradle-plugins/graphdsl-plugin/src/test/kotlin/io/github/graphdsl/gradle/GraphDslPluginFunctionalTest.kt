package io.github.graphdsl.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphDslPluginFunctionalTest {
    private fun withProjectDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("graphdsl-functional").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.writeSettings(name: String = "test-project") {
        resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "$name"
            """.trimIndent(),
        )
    }

    private fun File.writeBuildFile(
        pkg: String = "com.example.dsl",
        schemaDir: String = "src/main/graphql",
    ) {
        resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.graphdsl.graphdsl-gradle-plugin")
            }

            graphDsl {
                packageName.set("$pkg")
                schemaDir.set("$schemaDir")
            }
            """.trimIndent(),
        )
    }

    private fun File.writeSchema(
        sdl: String,
        dir: String = "src/main/graphql",
    ) {
        val schemaDir = resolve(dir).also { it.mkdirs() }
        File(schemaDir, "schema.graphqls").writeText(sdl.trimIndent())
    }

    private fun runner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()

    // =========================================================================
    // Task registration
    // =========================================================================

    @Test
    fun `generateGraphDsl task is registered after plugin is applied`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile()

            val result =
                runner(dir)
                    .withArguments("tasks", "--group=graphdsl")
                    .build()

            assertTrue(result.output.contains("generateGraphDsl"))
        }
    }

    // =========================================================================
    // Code generation
    // =========================================================================

    @Test
    fun `generateGraphDsl task succeeds with minimal schema`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile()
            dir.writeSchema("type Query { hello: String }")

            val result =
                runner(dir)
                    .withArguments("generateGraphDsl", "--stacktrace")
                    .build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":generateGraphDsl")?.outcome)
        }
    }

    @Test
    fun `generates QueryDsl file for schema with Query type`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile(pkg = "com.example.dsl")
            dir.writeSchema("type Query { hello: String }")

            runner(dir).withArguments("generateGraphDsl").build()

            assertTrue(
                File(dir, "build/generated-sources/graphdsl/com/example/dsl/QueryDsl.kt").exists(),
            )
        }
    }

    @Test
    fun `generates object builder for types referenced from query`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile(pkg = "com.example.dsl")
            dir.writeSchema(
                """
                type Query { user: User }
                type User { name: String! }
                """,
            )

            runner(dir).withArguments("generateGraphDsl").build()

            assertTrue(
                File(dir, "build/generated-sources/graphdsl/com/example/dsl/UserDslBuilder.kt").exists(),
            )
        }
    }

    @Test
    fun `task is UP-TO-DATE on second run with unchanged inputs`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile()
            dir.writeSchema("type Query { hello: String }")

            runner(dir).withArguments("generateGraphDsl").build()
            val second = runner(dir).withArguments("generateGraphDsl").build()

            assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateGraphDsl")?.outcome)
        }
    }

    @Test
    fun `task succeeds when schema directory does not exist`() {
        withProjectDir { dir ->
            dir.writeSettings()
            dir.writeBuildFile()
            // No schema directory created

            val result = runner(dir).withArguments("generateGraphDsl").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":generateGraphDsl")?.outcome)
        }
    }
}

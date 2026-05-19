package io.github.graphdsl.codegen

import graphql.schema.idl.SchemaParser
import io.github.graphdsl.mapper.GJSchemaRaw
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslFilesBuilderTest {

    private fun schemaFrom(sdl: String): GJSchemaRaw =
        GJSchemaRaw.fromRegistry(SchemaParser().parse(sdl))

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("graphdsl-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun build(
        schema: GJSchemaRaw,
        pkg: String = "com.example",
        outDir: File,
    ) = DslFilesBuilder(pkg, outDir, GraphDSLBaseTypeMapper(schema)).generate(schema)

    @Test
    fun `generates QueryDsl file for schema with Query type`() {
        withTempDir { dir ->
            val schema = schemaFrom("type Query { hello: String }")
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/QueryDsl.kt").exists())
        }
    }

    @Test
    fun `generates MutationDsl file when Mutation type is present`() {
        withTempDir { dir ->
            val schema = schemaFrom(
                """
                type Query { hello: String }
                type Mutation { createUser: Boolean }
                """,
            )
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/MutationDsl.kt").exists())
        }
    }

    @Test
    fun `does not generate MutationDsl when no Mutation type`() {
        withTempDir { dir ->
            val schema = schemaFrom("type Query { hello: String }")
            build(schema, outDir = dir)
            assertFalse(File(dir, "com/example/MutationDsl.kt").exists())
        }
    }

    @Test
    fun `generates object builder for types referenced from Query`() {
        withTempDir { dir ->
            val schema = schemaFrom(
                """
                type Query { user: User }
                type User { name: String! }
                """,
            )
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/UserDslBuilder.kt").exists())
        }
    }

    @Test
    fun `generates input builder for input types used as field args`() {
        withTempDir { dir ->
            val schema = schemaFrom(
                """
                type Query { users(filter: UserFilter!): User }
                type User { name: String! }
                input UserFilter { name: String }
                """,
            )
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/UserFilterBuilder.kt").exists())
        }
    }

    @Test
    fun `creates nested package directory structure`() {
        withTempDir { dir ->
            val schema = schemaFrom("type Query { hello: String }")
            build(schema, pkg = "io.example.api.dsl", outDir = dir)
            assertTrue(File(dir, "io/example/api/dsl").isDirectory)
        }
    }

    @Test
    fun `generated QueryDsl file contains valid kotlin header`() {
        withTempDir { dir ->
            val schema = schemaFrom("type Query { greeting: String }")
            build(schema, outDir = dir)
            val content = File(dir, "com/example/QueryDsl.kt").readText()
            assertTrue(content.contains("package com.example"))
            assertTrue(content.contains("fun query("))
            assertTrue(content.contains("QueryDslBuilder"))
        }
    }

    @Test
    fun `generates interface builder for interface types`() {
        withTempDir { dir ->
            val schema = schemaFrom(
                """
                interface Node { id: ID! }
                type User implements Node { id: ID!, name: String! }
                type Query { node(id: ID!): Node }
                """,
            )
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/NodeDslBuilder.kt").exists())
        }
    }

    @Test
    fun `does not generate root type as object builder`() {
        withTempDir { dir ->
            val schema = schemaFrom("type Query { hello: String }")
            build(schema, outDir = dir)
            assertFalse(File(dir, "com/example/QueryDslBuilder.kt").exists())
        }
    }

    @Test
    fun `generates builders for deeply nested types`() {
        withTempDir { dir ->
            val schema = schemaFrom(
                """
                type Query { user: User }
                type User { profile: Profile }
                type Profile { avatar: Image }
                type Image { url: String! }
                """,
            )
            build(schema, outDir = dir)
            assertTrue(File(dir, "com/example/UserDslBuilder.kt").exists())
            assertTrue(File(dir, "com/example/ProfileDslBuilder.kt").exists())
            assertTrue(File(dir, "com/example/ImageDslBuilder.kt").exists())
        }
    }
}

package io.github.graphdsl.codegen

import graphql.schema.idl.SchemaParser
import io.github.graphdsl.mapper.GJSchemaRaw
import io.github.graphdsl.schema.GraphDslSchema
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class QueryDslGenTest {

    private fun schemaFrom(sdl: String): GJSchemaRaw =
        GJSchemaRaw.fromRegistry(SchemaParser().parse(sdl))

    private fun generate(sdl: String): String {
        val schema = schemaFrom(sdl)
        val query = schema.queryTypeDef as GraphDslSchema.Object
        return queryDslGen("com.example", query, GraphDSLBaseTypeMapper(schema)).toString()
    }

    @Test
    fun `generated code declares correct package`() {
        val output = generate("type Query { hello: String }")
        assertContains(output, "package com.example")
    }

    @Test
    fun `generates top-level query function`() {
        val output = generate("type Query { hello: String }")
        assertContains(output, "fun query(")
    }

    @Test
    fun `generates QueryDslBuilder class`() {
        val output = generate("type Query { hello: String }")
        assertContains(output, "class QueryDslBuilder")
    }

    @Test
    fun `scalar field with no args generates val property`() {
        val output = generate("type Query { greeting: String }")
        assertContains(output, "val greeting")
    }

    @Test
    fun `multiple scalar fields each generate a val property`() {
        val output = generate("type Query { name: String, age: Int, active: Boolean }")
        assertContains(output, "val name")
        assertContains(output, "val age")
        assertContains(output, "val active")
    }

    @Test
    fun `complex return type generates fun function`() {
        val output = generate(
            """
            type Query { user: User }
            type User { name: String! }
            """,
        )
        assertContains(output, "fun user(")
    }

    @Test
    fun `complex return type references correct builder type`() {
        val output = generate(
            """
            type Query { user: User }
            type User { name: String! }
            """,
        )
        assertContains(output, "UserDslBuilder")
    }

    @Test
    fun `scalar field with arg is generated as fun not val`() {
        val output = generate("type Query { echo(msg: String!): String }")
        assertContains(output, "fun echo(")
        assertFalse(output.contains("val echo"))
    }

    @Test
    fun `field argument appears in parameter signature`() {
        val output = generate(
            """
            type Query { user(id: ID!): User }
            type User { name: String! }
            """,
        )
        assertContains(output, "id:")
    }

    @Test
    fun `generated code contains build function`() {
        val output = generate("type Query { hello: String }")
        assertContains(output, "fun build()")
    }

    @Test
    fun `query function wraps output in query braces`() {
        val output = generate("type Query { hello: String }")
        assertContains(output, "query")
        assertContains(output, "builder.build()")
    }

    @Test
    fun `custom package name appears in generated code`() {
        val schema = schemaFrom("type Query { hello: String }")
        val query = schema.queryTypeDef as GraphDslSchema.Object
        val output = queryDslGen("io.company.api.dsl", query, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "package io.company.api.dsl")
    }
}

package io.github.graphdsl.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadTypesFromFilesTest {

    private fun tempSchema(content: String): File =
        File.createTempFile("schema", ".graphqls").also {
            it.writeText(content.trimIndent())
            it.deleteOnExit()
        }

    @Test
    fun `parses a simple schema with a single type`() {
        val schema = tempSchema("""
            type Query {
                hello: String
            }
        """)
        val registry = readTypesFromFiles(listOf(schema))
        assertNotNull(registry)
        assertTrue(registry.getType("Query").isPresent)
    }

    @Test
    fun `parses multiple schema files merged into one registry`() {
        val schema1 = tempSchema("""
            type Query {
                user: User
            }
        """)
        val schema2 = tempSchema("""
            type User {
                id: ID!
                name: String!
            }
        """)
        val registry = readTypesFromFiles(listOf(schema1, schema2))
        assertTrue(registry.getType("Query").isPresent)
        assertTrue(registry.getType("User").isPresent)
    }

    @Test
    fun `parses schema with input types and enums`() {
        val schema = tempSchema("""
            enum Role { ADMIN USER }

            input CreateUserInput {
                name: String!
                role: Role!
            }

            type Mutation {
                createUser(input: CreateUserInput!): Boolean
            }

            type Query {
                hello: String
            }
        """)
        val registry = readTypesFromFiles(listOf(schema))
        assertTrue(registry.getType("Role").isPresent)
        assertTrue(registry.getType("CreateUserInput").isPresent)
        assertTrue(registry.getType("Mutation").isPresent)
    }

    @Test
    fun `parses schema with interfaces`() {
        val schema = tempSchema("""
            interface Node {
                id: ID!
            }

            type User implements Node {
                id: ID!
                name: String!
            }

            type Query {
                node(id: ID!): Node
            }
        """)
        val registry = readTypesFromFiles(listOf(schema))
        assertTrue(registry.getType("Node").isPresent)
        assertTrue(registry.getType("User").isPresent)
    }
}

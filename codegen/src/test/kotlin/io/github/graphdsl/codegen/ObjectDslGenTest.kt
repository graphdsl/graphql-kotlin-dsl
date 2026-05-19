package io.github.graphdsl.codegen

import graphql.schema.idl.SchemaParser
import io.github.graphdsl.mapper.GJSchemaRaw
import io.github.graphdsl.schema.GraphDslSchema
import kotlin.test.Test
import kotlin.test.assertContains

class ObjectDslGenTest {

    private fun schemaFrom(sdl: String): GJSchemaRaw =
        GJSchemaRaw.fromRegistry(SchemaParser().parse(sdl))

    @Test
    fun `generated builder has correct class name`() {
        val schema = schemaFrom(
            """
            type Query { user: User }
            type User { name: String! }
            """,
        )
        val user = schema.types["User"] as GraphDslSchema.Object
        val output = objectDslGen("com.example", user, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "class UserDslBuilder")
    }

    @Test
    fun `declares correct package`() {
        val schema = schemaFrom(
            """
            type Query { user: User }
            type User { name: String! }
            """,
        )
        val user = schema.types["User"] as GraphDslSchema.Object
        val output = objectDslGen("io.example.api", user, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "package io.example.api")
    }

    @Test
    fun `scalar fields become val properties`() {
        val schema = schemaFrom(
            """
            type Query { user: User }
            type User { name: String!, age: Int! }
            """,
        )
        val user = schema.types["User"] as GraphDslSchema.Object
        val output = objectDslGen("com.example", user, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "val name")
        assertContains(output, "val age")
    }

    @Test
    fun `nested object field becomes fun function`() {
        val schema = schemaFrom(
            """
            type Query { user: User }
            type User { address: Address }
            type Address { street: String! }
            """,
        )
        val user = schema.types["User"] as GraphDslSchema.Object
        val output = objectDslGen("com.example", user, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "fun address(")
        assertContains(output, "AddressDslBuilder")
    }

    @Test
    fun `builder contains build function`() {
        val schema = schemaFrom(
            """
            type Query { user: User }
            type User { name: String! }
            """,
        )
        val user = schema.types["User"] as GraphDslSchema.Object
        val output = objectDslGen("com.example", user, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "fun build()")
    }

    @Test
    fun `field with argument generates parameter in function signature`() {
        val schema = schemaFrom(
            """
            type Query { post: Post }
            type Post { comments(limit: Int!): Comment }
            type Comment { text: String! }
            """,
        )
        val post = schema.types["Post"] as GraphDslSchema.Object
        val output = objectDslGen("com.example", post, GraphDSLBaseTypeMapper(schema)).toString()
        assertContains(output, "fun comments(")
        assertContains(output, "limit:")
    }
}

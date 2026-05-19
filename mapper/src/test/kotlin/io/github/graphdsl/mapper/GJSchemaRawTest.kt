package io.github.graphdsl.mapper

import graphql.schema.idl.SchemaParser
import io.github.graphdsl.schema.GraphDslSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GJSchemaRawTest {

    private fun parse(sdl: String): GJSchemaRaw =
        GJSchemaRaw.fromRegistry(SchemaParser().parse(sdl))

    // =========================================================================
    // Object types
    // =========================================================================

    @Test
    fun `parses object type with scalar fields`() {
        val schema = parse(
            """
            type Query {
                id: ID!
                name: String
                age: Int!
                active: Boolean
            }
            """,
        )
        val query = assertIs<GraphDslSchema.Object>(schema.queryTypeDef)
        assertEquals("Query", query.name)
        assertEquals(4, query.fields.size)
        assertFalse(assertNotNull(query.field("id")).type.baseTypeNullable)
        assertTrue(assertNotNull(query.field("name")).type.baseTypeNullable)
        assertFalse(assertNotNull(query.field("age")).type.baseTypeNullable)
    }

    @Test
    fun `parses nested object type`() {
        val schema = parse(
            """
            type Query { user: User }
            type User { id: ID!, name: String! }
            """,
        )
        val user = assertIs<GraphDslSchema.Object>(schema.types["User"])
        assertEquals(2, user.fields.size)
        assertEquals("ID", assertNotNull(user.field("id")).type.baseTypeDef.name)
        assertEquals("String", assertNotNull(user.field("name")).type.baseTypeDef.name)
    }

    @Test
    fun `parses field arguments`() {
        val schema = parse(
            """
            type Query { user(id: ID!, active: Boolean): User }
            type User { name: String! }
            """,
        )
        val userField = assertNotNull(schema.queryTypeDef!!.field("user"))
        assertEquals(2, userField.args.size)
        val idArg = assertNotNull(userField.args.find { it.name == "id" })
        assertFalse(idArg.type.baseTypeNullable)
        val activeArg = assertNotNull(userField.args.find { it.name == "active" })
        assertTrue(activeArg.type.baseTypeNullable)
    }

    // =========================================================================
    // Enum types
    // =========================================================================

    @Test
    fun `parses enum type with all values`() {
        val schema = parse(
            """
            enum Status { ACTIVE INACTIVE PENDING }
            type Query { status: Status }
            """,
        )
        val status = assertIs<GraphDslSchema.Enum>(schema.types["Status"])
        assertEquals(3, status.values.size)
        val names = status.values.map { it.name }
        assertTrue("ACTIVE" in names)
        assertTrue("INACTIVE" in names)
        assertTrue("PENDING" in names)
    }

    @Test
    fun `enum value lookup by name`() {
        val schema = parse(
            """
            enum Role { ADMIN USER GUEST }
            type Query { role: Role }
            """,
        )
        val role = assertIs<GraphDslSchema.Enum>(schema.types["Role"])
        assertNotNull(role.value("ADMIN"))
        assertNotNull(role.value("USER"))
        assertNull(role.value("SUPERUSER"))
    }

    // =========================================================================
    // Interface types
    // =========================================================================

    @Test
    fun `parses interface with implementing types`() {
        val schema = parse(
            """
            interface Node { id: ID! }
            type User implements Node { id: ID!, name: String! }
            type Post implements Node { id: ID!, title: String! }
            type Query { node(id: ID!): Node }
            """,
        )
        val node = assertIs<GraphDslSchema.Interface>(schema.types["Node"])
        assertEquals(2, node.possibleObjectTypes.size)
        val implementorNames = node.possibleObjectTypes.map { it.name }.toSet()
        assertEquals(setOf("User", "Post"), implementorNames)
    }

    @Test
    fun `implementing type references its interface as super`() {
        val schema = parse(
            """
            interface Node { id: ID! }
            type User implements Node { id: ID!, name: String! }
            type Query { user: User }
            """,
        )
        val user = assertIs<GraphDslSchema.Object>(schema.types["User"])
        assertEquals(1, user.supers.size)
        assertEquals("Node", user.supers.first().name)
    }

    // =========================================================================
    // Input types
    // =========================================================================

    @Test
    fun `parses input type with required fields`() {
        val schema = parse(
            """
            input CreateUserInput { name: String!, email: String! }
            type Query { hello: String }
            """,
        )
        val input = assertIs<GraphDslSchema.Input>(schema.types["CreateUserInput"])
        assertEquals(2, input.fields.size)
        assertNotNull(input.field("name"))
        assertNotNull(input.field("email"))
    }

    @Test
    fun `input field with default value`() {
        val schema = parse(
            """
            input SearchInput { page: Int = 0 }
            type Query { hello: String }
            """,
        )
        val input = assertIs<GraphDslSchema.Input>(schema.types["SearchInput"])
        val pageField = assertNotNull(input.field("page"))
        assertTrue(pageField.hasDefault)
        assertNotNull(pageField.defaultValue) // value is a graphql-java IntValue, not a Kotlin Int
    }

    @Test
    fun `input field without default has no default`() {
        val schema = parse(
            """
            input SearchInput { query: String! }
            type Query { hello: String }
            """,
        )
        val input = assertIs<GraphDslSchema.Input>(schema.types["SearchInput"])
        assertFalse(assertNotNull(input.field("query")).hasDefault)
    }

    // =========================================================================
    // Union types
    // =========================================================================

    @Test
    fun `parses union with all member types`() {
        val schema = parse(
            """
            type Cat { meow: String! }
            type Dog { bark: String! }
            union Pet = Cat | Dog
            type Query { pet: Pet }
            """,
        )
        val union = assertIs<GraphDslSchema.Union>(schema.types["Pet"])
        val memberNames = union.possibleObjectTypes.map { it.name }.toSet()
        assertEquals(setOf("Cat", "Dog"), memberNames)
    }

    // =========================================================================
    // Type nullability and list wrapping
    // =========================================================================

    @Test
    fun `non-null type has baseTypeNullable false`() {
        val schema = parse("type Query { name: String! }")
        assertFalse(schema.queryTypeDef!!.field("name")!!.type.baseTypeNullable)
    }

    @Test
    fun `nullable type has baseTypeNullable true`() {
        val schema = parse("type Query { name: String }")
        assertTrue(schema.queryTypeDef!!.field("name")!!.type.baseTypeNullable)
    }

    @Test
    fun `list type is detected`() {
        val schema = parse("type Query { tags: [String!]! }")
        val field = assertNotNull(schema.queryTypeDef!!.field("tags"))
        assertTrue(field.type.isList)
    }

    @Test
    fun `non-null inner list type has baseTypeNullable false`() {
        val schema = parse("type Query { items: [String!]! }")
        val field = assertNotNull(schema.queryTypeDef!!.field("items"))
        assertFalse(field.type.baseTypeNullable)
    }

    // =========================================================================
    // Root type detection
    // =========================================================================

    @Test
    fun `detects Query by convention when no schema block`() {
        val schema = parse("type Query { hello: String }")
        assertNotNull(schema.queryTypeDef)
        assertEquals("Query", schema.queryTypeDef!!.name)
    }

    @Test
    fun `no Mutation type gives null mutationTypeDef`() {
        val schema = parse("type Query { hello: String }")
        assertNull(schema.mutationTypeDef)
    }

    @Test
    fun `detects Mutation by convention`() {
        val schema = parse(
            """
            type Query { hello: String }
            type Mutation { createUser: Boolean }
            """,
        )
        assertNotNull(schema.mutationTypeDef)
        assertEquals("Mutation", schema.mutationTypeDef!!.name)
    }

    @Test
    fun `detects root types from schema definition block`() {
        val schema = parse(
            """
            schema { query: MyQuery mutation: MyMutation }
            type MyQuery { hello: String }
            type MyMutation { doThing: Boolean }
            """,
        )
        assertEquals("MyQuery", schema.queryTypeDef!!.name)
        assertEquals("MyMutation", schema.mutationTypeDef!!.name)
    }

    // =========================================================================
    // Custom scalars
    // =========================================================================

    @Test
    fun `parses custom scalar`() {
        val schema = parse(
            """
            scalar DateTime
            type Query { createdAt: DateTime! }
            """,
        )
        val scalar = assertIs<GraphDslSchema.Scalar>(schema.types["DateTime"])
        assertEquals("DateTime", scalar.name)
    }

    @Test
    fun `custom scalar field type resolves correctly`() {
        val schema = parse(
            """
            scalar UUID
            type Query { id: UUID! }
            """,
        )
        val field = assertNotNull(schema.queryTypeDef!!.field("id"))
        assertEquals("UUID", field.type.baseTypeDef.name)
        assertFalse(field.type.baseTypeNullable)
    }
}

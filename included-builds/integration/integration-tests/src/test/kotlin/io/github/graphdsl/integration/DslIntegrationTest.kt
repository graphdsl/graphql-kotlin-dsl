package io.github.graphdsl.integration

import io.github.graphdsl.integration.dsl.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslIntegrationTest {

    // =========================================================================
    // Query – scalar fields
    // =========================================================================

    @Test
    fun `scalar field produces correct query`() {
        val q = query { greeting }
        assertEquals("query { greeting }", q)
    }

    @Test
    fun `named query includes operation name`() {
        val q = query("GetGreeting") { greeting }
        assertEquals("query GetGreeting { greeting }", q)
    }

    // =========================================================================
    // Query – complex fields with arguments
    // =========================================================================

    @Test
    fun `complex field with id argument`() {
        val q = query {
            product(id = "1") {
                id
                name
                available
            }
        }
        assertTrue(q.contains("""product(id: "1")"""))
        assertTrue(q.contains("name"))
        assertTrue(q.contains("available"))
    }

    @Test
    fun `nested object field`() {
        val q = query {
            product(id = "42") {
                category {
                    id
                    name
                }
            }
        }
        assertTrue(q.contains("""product(id: "42")"""))
        assertTrue(q.contains("category"))
        assertTrue(q.contains("name"))
    }

    // =========================================================================
    // Query – fields with input-type arguments
    // =========================================================================

    @Test
    fun `field with input type boolean filter`() {
        val q = query {
            products {
                filter {
                    available = true
                }
                id
                name
            }
        }
        assertTrue(q.contains("products"))
        assertTrue(q.contains("available: true"))
        assertTrue(q.contains("id"))
        assertTrue(q.contains("name"))
    }

    @Test
    fun `field with input type id filter`() {
        val q = query("FilterByCategory") {
            products {
                filter {
                    categoryId = "cat-99"
                }
                id
                name
                available
            }
        }
        assertTrue(q.startsWith("query FilterByCategory {"))
        assertTrue(q.contains("""categoryId: "cat-99""""))
        assertTrue(q.contains("available"))
    }

    // =========================================================================
    // Query – multiple fields
    // =========================================================================

    @Test
    fun `multiple top-level fields in one query`() {
        val q = query("Dashboard") {
            greeting
            products {
                filter {
                    available = true
                }
                id
                name
            }
        }
        assertTrue(q.startsWith("query Dashboard {"))
        assertTrue(q.contains("greeting"))
        assertTrue(q.contains("products"))
    }

    // =========================================================================
    // Mutation – scalar return
    // =========================================================================

    @Test
    fun `named mutation with scalar return`() {
        val m = mutation("DeleteProduct") {
            deleteProduct(id = "99")
        }
        assertTrue(m.startsWith("mutation DeleteProduct {"))
        assertTrue(m.contains("""deleteProduct(id: "99")"""))
    }

    @Test
    fun `unnamed mutation produces valid string`() {
        val m = mutation {
            deleteProduct(id = "1")
        }
        assertTrue(m.startsWith("mutation {"))
        assertTrue(m.contains("deleteProduct"))
    }

    // =========================================================================
    // Mutation – complex fields with input argument
    // =========================================================================

    @Test
    fun `mutation with input type and field selection`() {
        val m = mutation("CreateProduct") {
            createProduct {
                input {
                    name = "Widget"
                    categoryId = "cat-1"
                }
                id
                name
                available
            }
        }
        assertTrue(m.startsWith("mutation CreateProduct {"))
        assertTrue(m.contains("createProduct"))
        assertTrue(m.contains("""name: "Widget""""))
        assertTrue(m.contains("""categoryId: "cat-1""""))
        assertTrue(m.contains("id"))
        assertTrue(m.contains("available"))
    }

    @Test
    fun `mutation with nested category selection`() {
        val m = mutation {
            createProduct {
                input {
                    name = "Gadget"
                    categoryId = "cat-2"
                }
                id
                name
                category {
                    id
                    name
                }
            }
        }
        assertTrue(m.contains("createProduct"))
        assertTrue(m.contains("""name: "Gadget""""))
        assertTrue(m.contains("category"))
    }
}

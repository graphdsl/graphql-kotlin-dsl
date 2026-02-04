package demo.blog

import demo.blog.dsl.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlogDslTest {

    // =========================================================================
    // Query – scalar fields (accessed as properties)
    // =========================================================================

    @Test
    fun `scalar field produces correct query`() {
        val q = query {
            greeting
        }
        assertEquals("query { greeting }", q)
    }

    @Test
    fun `named query wraps operation name correctly`() {
        val q = query("SayHello") {
            greeting
        }
        assertEquals("query SayHello { greeting }", q)
    }

    // =========================================================================
    // Query – complex fields with scalar arguments
    // =========================================================================

    @Test
    fun `author field with id argument serializes correctly`() {
        mutation { createPost {
            input {
                title = ""
            }
        } }
        val q = query("GetAuthor") {
            author(id = "42") {
                id
                name
                email
            }
        }
        assertTrue(q.startsWith("query GetAuthor {"))
        assertTrue(q.contains("""author(id: "42")"""))
        assertTrue(q.contains("id"))
        assertTrue(q.contains("name"))
        assertTrue(q.contains("email"))
    }

    @Test
    fun `post field with id argument and nested author`() {
        val q = query {
            post(id = "7") {
                id
                title
                content
                published
                author {
                    name
                    email
                }
            }
        }
        assertTrue(q.contains("""post(id: "7")"""))
        assertTrue(q.contains("title"))
        assertTrue(q.contains("content"))
        assertTrue(q.contains("published"))
        assertTrue(q.contains("author"))
        assertTrue(q.contains("name"))
        assertTrue(q.contains("email"))
    }

    // =========================================================================
    // Query – complex fields with no arguments
    // =========================================================================

    @Test
    fun `list field without arguments builds selection`() {
        val q = query {
            authors {
                id
                name
            }
        }
        assertTrue(q.contains("authors"))
        assertTrue(q.contains("id"))
        assertTrue(q.contains("name"))
    }

    // =========================================================================
    // Query – fields with input-type arguments (combined builder pattern)
    // =========================================================================

    @Test
    fun `search posts with boolean filter`() {
        val q = query("SearchPublished") {
            searchPosts {
                filter {
                    published = true
                }
                id
                title
            }
        }
        assertTrue(q.startsWith("query SearchPublished {"))
        assertTrue(q.contains("searchPosts"))
        assertTrue(q.contains("published: true"))
        assertTrue(q.contains("id"))
        assertTrue(q.contains("title"))
    }

    @Test
    fun `search posts with string filter`() {
        val q = query {
            searchPosts {
                filter {
                    titleContains = "GraphQL"
                }
                id
                title
                author {
                    name
                }
            }
        }
        assertTrue(q.contains("""titleContains: "GraphQL""""))
        assertTrue(q.contains("author"))
        assertTrue(q.contains("name"))
    }

    @Test
    fun `multiple fields in single query`() {
        val q = query("Dashboard") {
            greeting
            authors {
                id
                name
            }
        }
        assertTrue(q.startsWith("query Dashboard {"))
        assertTrue(q.contains("greeting"))
        assertTrue(q.contains("authors"))
    }

    // =========================================================================
    // Mutation – scalar return fields (function calls)
    // =========================================================================

    @Test
    fun `delete post mutation with id argument`() {
        val m = mutation("DeletePost") {
            deletePost(id = "99")
        }
        assertTrue(m.startsWith("mutation DeletePost {"))
        assertTrue(m.contains("""deletePost(id: "99")"""))
    }

    @Test
    fun `unnamed mutation still produces valid string`() {
        val m = mutation {
            deletePost(id = "1")
        }
        assertTrue(m.startsWith("mutation {"))
        assertTrue(m.contains("deletePost"))
    }

    // =========================================================================
    // Mutation – complex fields with input-type arguments
    // =========================================================================

    @Test
    fun `create post mutation with input and field selection`() {
        val m = mutation("CreatePost") {
            createPost {
                input {
                    title = "Hello World"
                    content = "My first blog post"
                    authorId = "1"
                }
                id
                title
                published
            }
        }
        assertTrue(m.startsWith("mutation CreatePost {"))
        assertTrue(m.contains("createPost"))
        assertTrue(m.contains("""title: "Hello World""""))
        assertTrue(m.contains("""content: "My first blog post""""))
        assertTrue(m.contains("""authorId: "1""""))
        assertTrue(m.contains("id"))
        assertTrue(m.contains("published"))
    }

    @Test
    fun `create post mutation with nested author selection`() {
        val m = mutation {
            createPost {
                input {
                    title = "Kotlin DSLs"
                    content = "Type-safe and elegant"
                    authorId = "5"
                }
                id
                title
                author {
                    name
                    email
                }
            }
        }
        assertTrue(m.contains("createPost"))
        assertTrue(m.contains("""title: "Kotlin DSLs""""))
        assertTrue(m.contains("author"))
        assertTrue(m.contains("name"))
        assertTrue(m.contains("email"))
    }
}

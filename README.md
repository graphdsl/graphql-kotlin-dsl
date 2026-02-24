# GraphQL Kotlin DSL

Generates type-safe Kotlin DSL query builders from GraphQL schema files. Instead of building queries as strings, you get idiomatic Kotlin code with compile-time safety.

```kotlin
// Write this:
val q = query("GetAuthor") {
    author(id = "42") {
        id
        name
        email
    }
}

// Produces: query GetAuthor { author(id: "42") { id name email } }
```

## Getting started

Apply the Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("io.github.graphdsl.graphdsl-gradle-plugin") version "0.1.0"
}
```

Configure it (optional — defaults shown):

```kotlin
graphDsl {
    schemaDir.set("src/main/graphql")       // where your .graphqls files live
    packageName.set("io.github.graphdsl.dsl") // package for generated code
}
```

Place your schema in `src/main/graphql/`, then run:

```bash
./gradlew generateGraphDsl
```

Generated Kotlin sources land in `build/generated-sources/graphdsl/` and are automatically wired into compilation — no extra `sourceSets` config needed.

## Example

Given this schema:

```graphql
type Query {
    author(id: ID!): Author
    posts: [Post]
}

type Mutation {
    createPost(input: PostInput!): Post
}

type Author { id: ID!, name: String!, email: String }
type Post   { id: ID!, title: String!, published: Boolean }

input PostInput { title: String!, content: String!, authorId: ID! }
```

The plugin generates builders for every type, query field, and mutation field:

```kotlin
// Queries
val getAuthor = query("GetAuthor") {
    author(id = "42") {
        id
        name
        email
    }
}

val allPosts = query {
    posts {
        id
        title
        published
    }
}

// Mutations
val create = mutation("CreatePost") {
    createPost {
        input {
            title = "Hello World"
            content = "My first post"
            authorId = "1"
        }
        id
        title
    }
}
```

## CLI

The plugin invokes the CLI internally, but you can also call it directly:

```bash
java -cp <classpath> io.github.graphdsl.cli.KotlinDslGenerator \
  --schema_files schema.graphqls \
  --pkg_for_generated_classes com.example.dsl \
  --generated_directory build/generated-sources
```

| Flag | Description | Required |
|------|-------------|----------|
| `--schema_files` | Comma-separated list of `.graphqls` files | Yes |
| `--generated_directory` | Output directory for generated Kotlin | Yes |
| `--pkg_for_generated_classes` | Package name for generated code | No (default: `io.github.graphdsl.dsl`) |
| `--output_archive` | Zip the output and delete the directory | No |

## Modules

| Module | Description |
|--------|-------------|
| `cli` | CLI entry point, wires together schema parsing and codegen |
| `codegen` | Core generation engine — queries, mutations, inputs, objects |
| `mapper` | Maps graphql-java schema types to internal model |
| `schema` | Internal schema representation |
| `shared` | String template utilities and Kotlin metadata helpers |
| `utils` | Schema file parsing utilities |
| `gradle-plugins/graphdsl-plugin` | Gradle plugin — `generateGraphDsl` task + IDE integration |
| `gradle-plugins/common` | Shared Gradle plugin utilities |

## Building from source

Requires JDK 17+.

```bash
git clone https://github.com/graphdsl/graphql-kotlin-dsl.git
cd graphql-kotlin-dsl
./gradlew build
```

To run the blog demo:

```bash
cd demoapps/blog-demo
./gradlew test
```

## License

MIT

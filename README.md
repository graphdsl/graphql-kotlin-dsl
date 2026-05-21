# GraphQL Kotlin DSL

Generates type-safe Kotlin DSL query builders from GraphQL schema files.

```kotlin
val q = query("GetAuthor") {
    author(id = "42") {
        name
        email
    }
}
// → query GetAuthor { author(id: "42") { name email } }
```

`query {}` and `mutation {}` return a `String` directly — no extra serialization step.

## Quick start

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("io.github.graphdsl.graphdsl-gradle-plugin") version "0.1.4"
}
```

Place your `.graphqls` schema in `src/main/graphql/`, then build you project.

Generated sources land in `build/generated-sources/graphdsl/` and are wired into compilation automatically.

## Documentation

Full docs — configuration reference, CLI usage, and module overview — are at [graphdsl.github.io](https://graphdsl.github.io).

## License

Apache 2.0 — see [LICENSE](LICENSE).

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Please read it before contributing.

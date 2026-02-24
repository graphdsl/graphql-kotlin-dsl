# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

GraphQL Kotlin DSL generates type-safe Kotlin DSL query builders from GraphQL schema files. The Gradle plugin (`io.github.graphdsl.graphdsl-gradle-plugin`) reads `.graphqls` files and generates Kotlin source that lets users write `query { author(id = "42") { name } }` instead of raw strings. `query {}` and `mutation {}` return a `String` directly.

## Composite build structure

This is a **Gradle composite build** with three separate builds. This is the most important architectural fact — running tasks from the wrong directory is a common mistake.

| Build root | Contains | Run tasks with |
|---|---|---|
| `/` (root) | Orchestration only — no library code | `./gradlew <task>` |
| `included-builds/core` | Library modules: `schema`, `utils`, `mapper`, `shared`, `codegen`, `cli` | `./gradlew --project-dir included-builds/core :<module>:<task>` |
| `gradle-plugins` | Gradle plugin: `graphdsl-plugin`, `common` | `./gradlew --project-dir gradle-plugins :<module>:<task>` |

The root `build.gradle.kts` just applies `buildroot.orchestration` and lists `participatingIncludedBuilds = ["core", "gradle-plugins"]`. Root-level `build`, `test`, `check`, `clean`, and `publishToMavenCentral` tasks fan out across all participating builds.

## Common commands

```bash
# Build everything (all included builds)
./gradlew build

# Run all tests across all builds
./gradlew test

# Run tests for a specific core module
./gradlew --project-dir included-builds/core :utils:test
./gradlew --project-dir included-builds/core :codegen:test

# Run tests for the Gradle plugin
./gradlew --project-dir gradle-plugins :graphdsl-plugin:test

# Run the blog demo tests (exercises the full codegen pipeline end-to-end)
cd demoapps/blog-demo && ./gradlew test

# Publish everything to local Maven repo
./gradlew publishToMavenLocal

# Print / bump version
./gradlew printVersion
./gradlew bumpVersion -PnewVersion=0.2.0
```

## Codegen pipeline

The full pipeline from schema → generated Kotlin:

```
.graphqls files
    → utils      (SchemaParser → TypeDefinitionRegistry via graphql-java)
    → mapper     (TypeDefinitionRegistry → GraphDslSchema internal model)
    → codegen    (GraphDslSchema → Kotlin source via ST4 StringTemplate)
    → cli        (KotlinDslGenerator.Main — orchestrates everything above)
    → gradle-plugins/graphdsl-plugin  (runs cli as forked JVM process)
```

Key details:
- `GraphDslSchema` (in `schema` module) is the central internal IR. Everything in `codegen` operates on it.
- `codegen` uses ANTLR StringTemplate 4 (ST4) for all source generation — templates are inline Kotlin strings, not `.stg` files.
- `GraphDslPlugin` runs codegen via `GraphDslPluginCommon.getClassPathElements()` — the plugin executes its own classpath so all codegen deps are bundled inside the plugin JAR.
- The plugin only auto-wires generated sources into `kotlin.jvm` source sets. Android (`kotlin.android`) requires manual wiring.
- All GraphQL `ID` scalars map to `String` in generated code.

## Convention plugins (build-logic)

All build conventions live in `build-logic/src/main/kotlin/`. The most important ones:

- `conventions.kotlin` — standard Kotlin JVM module with JUnit 5 + JaCoCo
- `conventions.kotlin-without-tests` — Kotlin JVM without test support (used for modules that don't yet have tests)
- `conventions.graphdsl-publishing` — wires `com.vanniktech.maven.publish` for Maven Central, configures POM metadata and GPG signing. Each module using this must configure the `graphDslPublishing { }` extension.
- `buildroot.orchestration` — creates the cross-build `build`/`test`/`check`/`publish` aggregate tasks
- `buildroot.versioning` — reads version from the `VERSION` file at repo root; sets `group = "io.github.graphdsl"` and `version` on all projects

## Versioning and release

- Version is stored in the `VERSION` file at the repo root (e.g. `0.1.1`).
- Release is triggered by pushing a branch named `release/X.Y.Z` — the GitHub Actions workflow (`.github/workflows/release.yml`) extracts the version, runs `bumpVersion`, publishes to Maven Central and Gradle Plugin Portal, commits the version bump, and creates a GitHub Release draft.
- The `gradle-plugins` build is published separately with `./gradlew --project-dir gradle-plugins publishPlugins` for the Gradle Plugin Portal.

## Publishing / signing setup

GPG signing uses in-memory keys via Gradle project properties:
- `signingKeyId` — short 8-char key ID
- `signingKey` — full ASCII-armored private key block
- `signingPassword` — passphrase (empty string if none)

In CI these are set via `ORG_GRADLE_PROJECT_signing*` environment variables mapped from GitHub secrets. The public key must be uploaded to `keyserver.ubuntu.com`, `keys.openpgp.org`, and `pgp.mit.edu` for Sonatype validation.

## Demo apps

- `demoapps/blog-demo` — JVM demo, exercises the full codegen pipeline. Standalone Gradle project.
- `demoapps/android-demo` — Android/Compose demo using the Countries GraphQL API. Uses the published plugin version; requires manual source wiring since the plugin only auto-wires for `kotlin.jvm`.

Both demo apps reference the published plugin version (`0.1.1`) and are fully independent from the library source — they use Maven Central/Gradle Plugin Portal only.

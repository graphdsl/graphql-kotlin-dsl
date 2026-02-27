package io.github.graphdsl.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Extension for configuring the GraphDSL code generation plugin.
 *
 * Usage in build.gradle.kts:
 * ```kotlin
 * graphDsl {
 *     packageName.set("com.example.api.dsl")
 * }
 * ```
 */
open class GraphDslExtension(objects: ObjectFactory) {
    /**
     * The Kotlin package name for generated DSL classes.
     * Defaults to "io.github.graphdsl.dsl".
     */
    val packageName: Property<String> = objects.property(String::class.java)
        .convention("io.github.graphdsl.dsl")

    /**
     * The directory containing GraphQL schema files (.graphqls).
     * Defaults to "src/main/graphql".
     */
    val schemaDir: Property<String> = objects.property(String::class.java)
        .convention("src/main/graphql")

    /**
     * Whether to generate `.class` bytecode files directly instead of `.kt` source files.
     * Defaults to false (source generation via StringTemplate).
     */
    val useBytecode: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)
}

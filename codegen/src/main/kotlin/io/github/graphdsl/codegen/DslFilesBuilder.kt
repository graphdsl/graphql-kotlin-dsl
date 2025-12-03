/**
 * DSL Files Builder - Main Orchestrator for GraphQL DSL Code Generation.
 *
 * This class coordinates the generation of all DSL files from a GraphQL schema.
 * It serves as the entry point for the DSL code generation pipeline.
 *
 * ## Overview
 *
 * The DSL generation system creates type-safe Kotlin builders for GraphQL operations.
 * Given a GraphQL schema, it produces:
 *
 * 1. **QueryDsl.kt** - Builder for GraphQL queries
 * 2. **MutationDsl.kt** - Builder for GraphQL mutations (if schema has mutations)
 * 3. **{ObjectType}DslBuilder.kt** - Builders for each object type used in selections
 * 4. **{Interface}DslBuilder.kt** - Builders for interface types with fragment support
 * 5. **{InputType}Builder.kt** - Builders for input types with DSL syntax
 * 6. **{MutationField}MutationBuilder.kt** - Specialized builders for mutation fields
 *
 * Input types are accessed via DSL functions within mutation builders.
 *
 * ## Output Directory Structure
 *
 * ```
 * outputDir/
 * └── {pkg}/
 *     ├── QueryDsl.kt
 *     ├── MutationDsl.kt
 *     ├── UserDslBuilder.kt
 *     ├── PostDslBuilder.kt
 *     └── NodeDslBuilder.kt
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * val builder = DslFilesBuilder(
 *     pkg = "com.example.api.dsl",
 *     outputDir = File("generated"),
 *     baseTypeMapper = GraphDSLBaseTypeMapper(schema)
 * )
 * builder.generate(schema)
 * ```
 */
package io.github.graphdsl.codegen

import io.github.graphdsl.schema.GraphDslSchema
import java.io.File

/**
 * Orchestrator for generating GraphQL DSL files from a schema.
 *
 * @param pkg The target package name for generated DSL classes
 * @param outputDir The root output directory for generated files
 * @param baseTypeMapper The type mapper for GraphQL to Kotlin type conversion
 */
class DslFilesBuilder(
    private val pkg: String,
    private val outputDir: File,
    private val baseTypeMapper: BaseTypeMapper
) {
    /** Directory for DSL builder files */
    private val packageDir: File = File(outputDir, pkg.replace('.', '/'))

    /**
     * Generates all DSL files from the provided GraphQL schema.
     *
     * This method orchestrates the complete code generation process:
     * 1. Creates output directory
     * 2. Generates Query DSL (if schema has Query type)
     * 3. Generates Mutation DSL (if schema has Mutation type)
     * 4. Generates Object DSL builders for all referenced types
     * 5. Generates Interface DSL builders with fragment support
     * 6. Generates Input DSL builders for all input types
     * 7. Generates Mutation Field builders for mutations with input args
     *
     * @param schema The GraphQL schema to generate DSL for
     */
    fun generate(schema: GraphDslSchema) {
        packageDir.mkdirs()

        val objectTypesNeedingBuilders = mutableSetOf<String>()
        val inputTypesNeedingBuilders = mutableSetOf<String>()
        val queryFieldsNeedingBuilders = mutableListOf<GraphDslSchema.Field>()
        val mutationFieldsNeedingBuilders = mutableListOf<GraphDslSchema.Field>()

        // Generate Query DSL and collect needed object builders
        schema.types[QUERY_TYPE]?.let { queryType ->
            if (queryType is GraphDslSchema.Object) {
                generateQueryDsl(queryType)
                collectNeededBuilders(queryType, objectTypesNeedingBuilders, inputTypesNeedingBuilders, schema)
                // Collect query fields that need specialized builders
                collectQueryFieldsWithInputs(queryType, queryFieldsNeedingBuilders)
            }
        }

        // Generate Mutation DSL and collect needed object/input builders
        schema.types[MUTATION_TYPE]?.let { mutationType ->
            if (mutationType is GraphDslSchema.Object) {
                generateMutationDsl(mutationType)
                collectNeededBuilders(mutationType, objectTypesNeedingBuilders, inputTypesNeedingBuilders, schema)
                // Collect mutation fields that need specialized builders
                collectMutationFieldsWithInputs(mutationType, mutationFieldsNeedingBuilders)
            }
        }

        // Generate Object DSL builders for all collected types
        generateObjectBuilders(objectTypesNeedingBuilders, schema)

        // Generate Interface DSL builders with fragment methods
        generateInterfaceBuilders(schema, objectTypesNeedingBuilders)

        // Generate Input DSL builders for all input types
        generateInputBuilders(inputTypesNeedingBuilders, schema)

        // Generate specialized query field builders
        generateQueryFieldBuilders(queryFieldsNeedingBuilders)

        // Generate specialized mutation field builders
        generateMutationFieldBuilders(mutationFieldsNeedingBuilders)
    }

    // =========================================================================
    // Individual Generator Methods
    // =========================================================================

    /**
     * Generates QueryDsl.kt for the GraphQL Query type.
     */
    private fun generateQueryDsl(queryType: GraphDslSchema.Object) {
        val destination = File(packageDir, "QueryDsl.kt")
        queryDslGen(pkg, queryType, baseTypeMapper).write(destination)
    }

    /**
     * Generates MutationDsl.kt for the GraphQL Mutation type.
     */
    private fun generateMutationDsl(mutationType: GraphDslSchema.Object) {
        val destination = File(packageDir, "MutationDsl.kt")
        mutationDslGen(pkg, mutationType, baseTypeMapper).write(destination)
    }

    /**
     * Generates a DSL builder for a specific GraphQL Object type.
     */
    private fun generateObjectDsl(objectType: GraphDslSchema.Object) {
        val destination = File(packageDir, "${objectType.name}DslBuilder.kt")
        objectDslGen(pkg, objectType, baseTypeMapper).write(destination)
    }

    /**
     * Generates a DSL builder for a specific GraphQL Input type.
     */
    private fun generateInputDsl(inputType: GraphDslSchema.Input) {
        val destination = File(packageDir, "${inputType.name}Builder.kt")
        inputDslGen(pkg, inputType, baseTypeMapper).write(destination)
    }

    /**
     * Generates a specialized builder for a mutation field with input arguments.
     */
    private fun generateMutationFieldDsl(field: GraphDslSchema.Field, returnType: GraphDslSchema.TypeDef) {
        val builderName = getMutationFieldBuilderName(field.name)
        val destination = File(packageDir, "$builderName.kt")
        mutationFieldDslGen(pkg, field, returnType, baseTypeMapper).write(destination)
    }

    /**
     * Generates a specialized builder for a query field with input arguments.
     */
    private fun generateQueryFieldDsl(field: GraphDslSchema.Field, returnType: GraphDslSchema.TypeDef) {
        val builderName = getQueryFieldBuilderName(field.name)
        val destination = File(packageDir, "$builderName.kt")
        queryFieldDslGen(pkg, field, returnType, baseTypeMapper).write(destination)
    }

    // =========================================================================
    // Batch Generation Methods
    // =========================================================================

    /**
     * Generates Object DSL builders for all types in the collection.
     *
     * Excludes root types (Query, Mutation, Subscription) as they have
     * their own dedicated generators.
     */
    private fun generateObjectBuilders(
        typeNames: Set<String>,
        schema: GraphDslSchema
    ) {
        for (typeName in typeNames) {
            val typeDef = schema.types[typeName]
            if (typeDef is GraphDslSchema.Object && !isRootType(typeName)) {
                generateObjectDsl(typeDef)
            }
        }
    }

    /**
     * Generates Interface DSL builders for all interfaces in the schema.
     *
     * Also adds implementing types to the needed builders set since
     * fragment methods reference their builders.
     */
    private fun generateInterfaceBuilders(
        schema: GraphDslSchema,
        objectTypesNeedingBuilders: MutableSet<String>
    ) {
        for ((typeName, typeDef) in schema.types) {
            if (typeDef is GraphDslSchema.Interface) {
                val implementingTypes = findImplementingTypes(schema, typeName)

                if (implementingTypes.isNotEmpty()) {
                    val destination = File(packageDir, "${typeName}DslBuilder.kt")
                    nodeInterfaceDslGen(pkg, typeDef, implementingTypes).write(destination)

                    // Ensure builders exist for all implementing types
                    implementingTypes.forEach { objectTypesNeedingBuilders.add(it.name) }
                }
            }
        }
    }

    /**
     * Generates Input DSL builders for all input types in the collection.
     */
    private fun generateInputBuilders(
        inputTypeNames: Set<String>,
        schema: GraphDslSchema
    ) {
        for (typeName in inputTypeNames) {
            val typeDef = schema.types[typeName]
            if (typeDef is GraphDslSchema.Input) {
                generateInputDsl(typeDef)
            }
        }
    }

    /**
     * Generates specialized query field builders for fields with input arguments.
     */
    private fun generateQueryFieldBuilders(queryFields: List<GraphDslSchema.Field>) {
        for (field in queryFields) {
            val returnType = field.type.baseTypeDef
            if (returnType.requiresSelectionSet()) {
                generateQueryFieldDsl(field, returnType)
            }
        }
    }

    /**
     * Generates specialized mutation field builders for fields with input arguments.
     */
    private fun generateMutationFieldBuilders(mutationFields: List<GraphDslSchema.Field>) {
        for (field in mutationFields) {
            val returnType = field.type.baseTypeDef
            if (returnType.requiresSelectionSet()) {
                generateMutationFieldDsl(field, returnType)
            }
        }
    }

    // =========================================================================
    // Type Discovery Methods
    // =========================================================================

    /**
     * Recursively collects all object and input types that need DSL builders.
     *
     * Starting from a root type (Query or Mutation), traverses all fields
     * to find Object, Interface, Union, and Input types that will need builders.
     *
     * @param typeDef The type to scan for referenced types
     * @param objectCollectors The set to add discovered object type names to
     * @param inputCollectors The set to add discovered input type names to
     * @param schema The schema for looking up type definitions
     */
    private fun collectNeededBuilders(
        typeDef: GraphDslSchema.Object,
        objectCollectors: MutableSet<String>,
        inputCollectors: MutableSet<String>,
        schema: GraphDslSchema
    ) {
        for (field in typeDef.fields) {
            // Collect input types from field arguments
            for (arg in field.args) {
                collectInputTypes(arg.type.baseTypeDef, inputCollectors, schema)
            }

            // Collect object types from return types
            when (val returnType = field.type.baseTypeDef) {
                is GraphDslSchema.Object -> {
                    // Recursively collect from Object types, avoiding cycles
                    if (!isRootType(returnType.name) && objectCollectors.add(returnType.name)) {
                        collectNeededBuilders(returnType, objectCollectors, inputCollectors, schema)
                    }
                }
                is GraphDslSchema.Interface, is GraphDslSchema.Union -> {
                    // Interface and Union types need builders but don't recurse
                    objectCollectors.add(returnType.name)
                }
            }
        }
    }

    /**
     * Recursively collects all input types referenced by a type definition.
     */
    private fun collectInputTypes(
        typeDef: GraphDslSchema.TypeDef,
        inputCollectors: MutableSet<String>,
        schema: GraphDslSchema
    ) {
        if (typeDef is GraphDslSchema.Input) {
            if (inputCollectors.add(typeDef.name)) {
                // Recursively collect nested input types
                for (field in typeDef.fields) {
                    collectInputTypes(field.type.baseTypeDef, inputCollectors, schema)
                }
            }
        }
    }

    /**
     * Collects query fields that have input type arguments and need specialized builders.
     */
    private fun collectQueryFieldsWithInputs(
        queryType: GraphDslSchema.Object,
        queryFields: MutableList<GraphDslSchema.Field>
    ) {
        for (field in queryType.fields) {
            val hasInputArgs = field.args.any { it.type.baseTypeDef is GraphDslSchema.Input }
            val hasComplexReturnType = field.type.baseTypeDef.requiresSelectionSet()
            if (hasInputArgs && hasComplexReturnType) {
                queryFields.add(field)
            }
        }
    }

    /**
     * Collects mutation fields that have input type arguments and need specialized builders.
     */
    private fun collectMutationFieldsWithInputs(
        mutationType: GraphDslSchema.Object,
        mutationFields: MutableList<GraphDslSchema.Field>
    ) {
        for (field in mutationType.fields) {
            val hasInputArgs = field.args.any { it.type.baseTypeDef is GraphDslSchema.Input }
            val hasComplexReturnType = field.type.baseTypeDef.requiresSelectionSet()
            if (hasInputArgs && hasComplexReturnType) {
                mutationFields.add(field)
            }
        }
    }

    /**
     * Finds all Object types that implement a given interface.
     *
     * @param schema The schema to search
     * @param interfaceName The interface name to find implementations for
     * @return List of Object types implementing the interface
     */
    private fun findImplementingTypes(
        schema: GraphDslSchema,
        interfaceName: String
    ): List<GraphDslSchema.Object> =
        schema.types.values
            .filterIsInstance<GraphDslSchema.Object>()
            .filter { obj -> obj.supers.any { it.name == interfaceName } }

    /**
     * Checks if a type name is a GraphQL root operation type.
     *
     * Root types (Query, Mutation, Subscription) have dedicated generators
     * and should not get generic Object DSL builders.
     */
    private fun isRootType(name: String): Boolean = name in ROOT_TYPES

    companion object {
        /** GraphQL Query root type name */
        private const val QUERY_TYPE = "Query"

        /** GraphQL Mutation root type name */
        private const val MUTATION_TYPE = "Mutation"

        /** Set of GraphQL root operation type names */
        private val ROOT_TYPES = setOf("Query", "Mutation", "Subscription")
    }
}
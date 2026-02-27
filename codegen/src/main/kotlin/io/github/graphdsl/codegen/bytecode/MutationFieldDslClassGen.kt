package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.schema.GraphDslSchema

/**
 * Generates a `{FieldName}MutationBuilder` bytecode class for a mutation field
 * that has Input-type arguments.
 *
 * This is the bytecode equivalent of `mutationFieldDslGen.kt`.
 *
 * The generated class extends `{ReturnType}DslBuilder` and adds argument setters.
 * See [operationFieldDslClassGen] for the shared implementation.
 */
fun mutationFieldDslClassGen(
    pkg: String,
    field: GraphDslSchema.Field,
    returnType: GraphDslSchema.TypeDef,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder =
    operationFieldDslClassGen(pkg, field, returnType, OperationKind.MUTATION, baseTypeMapper)

/**
 * Returns the builder class name for a mutation field with input args.
 */
fun mutationFieldBuilderName(fieldName: String): String =
    "${fieldName.replaceFirstChar { it.uppercase() }}MutationBuilder"

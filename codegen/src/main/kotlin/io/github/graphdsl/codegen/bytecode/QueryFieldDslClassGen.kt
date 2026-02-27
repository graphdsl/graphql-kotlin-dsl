package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.schema.GraphDslSchema

/**
 * Generates a `{FieldName}QueryBuilder` bytecode class for a query field
 * that has Input-type arguments.
 *
 * This is the bytecode equivalent of `queryFieldDslGen.kt`.
 *
 * The generated class extends `{ReturnType}DslBuilder` and adds argument setters.
 * See [operationFieldDslClassGen] for the shared implementation.
 */
fun queryFieldDslClassGen(
    pkg: String,
    field: GraphDslSchema.Field,
    returnType: GraphDslSchema.TypeDef,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder =
    operationFieldDslClassGen(pkg, field, returnType, OperationKind.QUERY, baseTypeMapper)

/**
 * Returns the builder class name for a query field with input args.
 */
fun queryFieldBuilderName(fieldName: String): String =
    "${fieldName.replaceFirstChar { it.uppercase() }}QueryBuilder"

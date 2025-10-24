/**
 * Shared utilities for DSL code generation.
 *
 * This file contains common classes, functions, and type conversions used across
 * the various DSL generators (Query, Mutation, Object, NodeInterface).
 */
package io.github.graphdsl.codegen

import io.github.graphdsl.codegen.km.kotlinTypeString
import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.codegen.JavaName

/**
 * Represents a parameter (argument) for a GraphQL field in the generated DSL.
 *
 * Input types are converted to `Map<String, Any?>` for idiomatic Kotlin DSL usage.
 *
 * @property escapedName The Kotlin-safe name (escaped if it's a reserved keyword)
 * @property argName The original GraphQL argument name
 * @property kotlinType The Kotlin type string for this parameter
 */
class FieldParameterModel(
    arg: GraphDslSchema.HasDefaultValue,
    pkg: String,
    baseTypeMapper: BaseTypeMapper
) {
    val escapedName: String = getEscapedFieldName(arg.name)
    val argName: String = arg.name

    val kotlinType: String = run {
        val baseType = arg.type.baseTypeDef
        val isNullable = arg.type.isNullable

        // Input types become Map<String, Any?>
        if (baseType is GraphDslSchema.Input) {
            if (isNullable) "Map<String, Any?>?" else "Map<String, Any?>"
        } else {
            // For scalars and enums, use the standard type mapping
            arg.kmType(JavaName(pkg).asKmName, baseTypeMapper)
                .kotlinTypeString
                .replaceGlobalIdWithString()
                .simplifyKotlinType()
        }
    }
}

/**
 * Replaces GlobalID<T> types with String in type signatures.
 */
fun String.replaceGlobalIdWithString(): String {
    val globalIdPattern = Regex("""graphdsl\.api\.globalid\.GlobalID<[^>]+>""")
    return this.replace(globalIdPattern, "String")
}

/**
 * Simplifies Kotlin type names by removing common package prefixes.
 * Handles both "kotlin." and "kotlin.collections." prefixes.
 */
fun String.simplifyKotlinType(): String = this
    .replace("kotlin.collections.", "")
    .replace("kotlin.", "")

/**
 * Checks if a GraphQL type definition is a scalar or enum type.
 */
fun GraphDslSchema.TypeDef.isScalarOrEnum(): Boolean =
    this is GraphDslSchema.Scalar || this is GraphDslSchema.Enum

/**
 * Checks if a GraphQL type definition requires a selection set.
 */
fun GraphDslSchema.TypeDef.requiresSelectionSet(): Boolean =
    this is GraphDslSchema.Object ||
        this is GraphDslSchema.Interface ||
        this is GraphDslSchema.Union

/**
 * Builds a Kotlin function parameter signature string from a list of parameters.
 */
fun buildParameterSignature(parameters: List<FieldParameterModel>, includeAlias: Boolean = false): String {
    val paramStr = parameters.joinToString(", ") { "${it.escapedName}: ${it.kotlinType}" }
    return if (includeAlias) {
        if (paramStr.isNotEmpty()) "$paramStr, alias: String? = null" else "alias: String? = null"
    } else {
        paramStr
    }
}

/**
 * Builds the argument serialization expression for StringTemplate.
 */
fun buildParameterSerializers(parameters: List<FieldParameterModel>): String =
    parameters.joinToString(", ") { "\"${it.argName}: \" + serializeValue(${it.escapedName})" }
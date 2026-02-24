/**
 * Input DSL Generator.
 *
 * Generates type-safe Kotlin DSL builder classes for GraphQL input types.
 * These builders allow setting input fields using a clean DSL syntax.
 *
 * ## Example Usage
 *
 * For an input type like:
 * ```graphql
 * input CreateCharacterInput {
 *     name: String!
 *     age: Int
 * }
 * ```
 *
 * The generated builder allows:
 * ```kotlin
 * input {
 *     name = "Luke"
 *     age = 19
 * }
 * ```
 *
 */
package io.github.graphdsl.codegen

import io.github.graphdsl.codegen.km.kotlinTypeString
import io.github.graphdsl.codegen.st.STContents
import io.github.graphdsl.codegen.st.stTemplate
import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.codegen.JavaName

// =============================================================================
// Public API
// =============================================================================

/**
 * Generates the Input DSL builder code for a GraphQL Input type.
 *
 * @param pkg The target package name for the generated DSL classes
 * @param inputDef The GraphQL Input type definition
 * @param baseTypeMapper The type mapper for GraphQL to Kotlin type conversion
 * @return STContents ready to be written to a file
 */
fun inputDslGen(
    pkg: String,
    inputDef: GraphDslSchema.Input,
    baseTypeMapper: BaseTypeMapper
): STContents = STContents(
    INPUT_DSL_TEMPLATE,
    InputDslModelImpl(pkg, inputDef, baseTypeMapper)
)

// =============================================================================
// Template Model Interface
// =============================================================================

private interface InputDslModel {
    val pkg: String
    val inputName: String
    val escapedInputName: String
    val builderName: String
    val scalarFields: List<ScalarInputFieldModel>
    val nestedInputFields: List<NestedInputFieldModel>
}

// =============================================================================
// Field Model Classes
// =============================================================================

/**
 * Model for scalar fields in input types (String, Int, Boolean, Enum, etc.)
 */
@Suppress("UNUSED")
private class ScalarInputFieldModel(
    fieldDef: GraphDslSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper
) {
    val escapedName: String = getEscapedFieldName(fieldDef.name)
    val fieldName: String = fieldDef.name
    val graphqlType: String = fieldDef.type.toString()

    val kotlinType: String = run {
        fieldDef.kmType(JavaName(pkg).asKmName, baseTypeMapper)
            .kotlinTypeString
            .simplifyKotlinType()
    }
}

/**
 * Model for nested input type fields (fields that reference another Input type)
 */
@Suppress("UNUSED")
private class NestedInputFieldModel(
    fieldDef: GraphDslSchema.Field
) {
    val escapedName: String = getEscapedFieldName(fieldDef.name)
    val fieldName: String = fieldDef.name
    val graphqlType: String = fieldDef.type.toString()

    private val inputTypeDef: GraphDslSchema.Input = fieldDef.type.baseTypeDef as GraphDslSchema.Input
    val nestedBuilderType: String = "${inputTypeDef.name}Builder"
}

// =============================================================================
// Template Model Implementation
// =============================================================================

private class InputDslModelImpl(
    override val pkg: String,
    inputDef: GraphDslSchema.Input,
    baseTypeMapper: BaseTypeMapper
) : InputDslModel {

    override val inputName: String = inputDef.name
    override val escapedInputName: String = getEscapedFieldName(inputDef.name.replaceFirstChar { it.lowercase() })
    override val builderName: String = "${inputDef.name}Builder"

    override val scalarFields: List<ScalarInputFieldModel>
    override val nestedInputFields: List<NestedInputFieldModel>

    init {
        val scalars = mutableListOf<ScalarInputFieldModel>()
        val nested = mutableListOf<NestedInputFieldModel>()

        for (field in inputDef.fields) {
            val fieldType = field.type.baseTypeDef

            if (fieldType is GraphDslSchema.Input) {
                nested.add(NestedInputFieldModel(field))
            } else {
                scalars.add(ScalarInputFieldModel(field, pkg, baseTypeMapper))
            }
        }

        scalarFields = scalars
        nestedInputFields = nested
    }
}

// =============================================================================
// StringTemplate Definition
// =============================================================================

private val INPUT_DSL_TEMPLATE = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

/**
 * DSL builder for the `<mdl.inputName>` GraphQL input type.
 */
class <mdl.builderName> internal constructor() {
    private val values = mutableMapOf\<String, Any?>()

<mdl.scalarFields: { f |
    var <f.escapedName>: <f.kotlinType>
        get() = values["<f.fieldName>"] as <f.kotlinType>
        set(value) {
            values["<f.fieldName>"] = value
        \}
}; separator="\n">

<mdl.nestedInputFields: { f |
    fun <f.escapedName>(block: <f.nestedBuilderType>.() -> Unit) {
        val nestedBuilder = <f.nestedBuilderType>()
        nestedBuilder.block()
        values["<f.fieldName>"] = nestedBuilder.build()
    \}
}; separator="\n">

    internal fun build(): Map\<String, Any?> = values.toMap()
}
"""
)
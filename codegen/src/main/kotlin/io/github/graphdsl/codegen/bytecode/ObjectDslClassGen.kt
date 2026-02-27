package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.km.KmPropertyBuilder
import io.github.graphdsl.codegen.isScalarOrEnum
import io.github.graphdsl.codegen.requiresSelectionSet
import io.github.graphdsl.codegen.utils.JavaIdName
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Generates a `{TypeName}DslBuilder` bytecode class for a GraphQL Object type.
 *
 * This is the bytecode equivalent of `objectDslGen.kt`.
 */
fun objectDslClassGen(
    pkg: String,
    objectDef: GraphDslSchema.Object,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder {
    val kmName = "${objectDef.name}DslBuilder".toPkgKmName(pkg)
    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = true, isNested = false)

    // Add standard DSL infrastructure: constructor, fields, addField, build, serializeValue
    builder.addDslBuilderInfrastructure()

    // Add fields
    for (field in objectDef.fields) {
        val returnType = field.type.baseTypeDef
        val isSimpleScalar = returnType.isScalarOrEnum() && field.args.isEmpty()

        if (isSimpleScalar) {
            builder.addScalarFieldProperty(field)
        } else {
            builder.addComplexFieldFunction(field, pkg, baseTypeMapper)
        }
    }

    return builder
}

// =========================================================================
// Internal helpers
// =========================================================================

/**
 * Adds a `val fieldName: Unit` computed property that calls `addField("fieldName")`.
 */
internal fun CustomClassBuilder.addScalarFieldProperty(field: GraphDslSchema.Field) {
    val propBuilder = KmPropertyBuilder(
        name = JavaIdName(field.name),
        type = KM_UNIT_TYPE,
        inputType = KM_UNIT_TYPE,
        isVariable = false,
        constructorProperty = false,
    )
        .getterVisibility(Visibility.PUBLIC)
        .getterBody("{ addField(\"${field.name}\"); }")
    addProperty(propBuilder.build())
}

/**
 * Adds a function for a complex field (one that needs a selection block and/or has args).
 *
 * All args are included as parameters:
 *   - Input types → `Map<String, Any?>` (serialized via serializeValue)
 *   - Other types → proper Kotlin type via [BaseTypeMapper]
 *
 * Generated function signature mirrors the ST template:
 *   `fun fieldName(arg1: Type1, ..., block: ReturnTypeDslBuilder.() -> Unit)`
 */
internal fun CustomClassBuilder.addComplexFieldFunction(
    field: GraphDslSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper,
) {
    val returnTypeDef = field.type.baseTypeDef
    val needsSelection = returnTypeDef.requiresSelectionSet()
    val allArgs = field.args

    // Build parameter list: all args + optional block param
    val kmFn = KmFunction(field.name).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.OPEN
        f.returnType = KM_UNIT_TYPE

        // Add all arg parameters (Input → Map<String,Any?>, other → actual type)
        for (arg in allArgs) {
            f.valueParameters.add(KmValueParameter(arg.name).also { p ->
                p.type = argKmType(arg, pkg, baseTypeMapper)
            })
        }

        // Add block parameter if the return type needs a selection set
        if (needsSelection) {
            val builderKmName = returnTypeDef.toDslBuilderKmName(pkg)
            val blockType = kmFunctionType(builderKmName.asType())
            f.valueParameters.add(KmValueParameter("block").also { p ->
                p.type = blockType
            })
        }
    }

    val body = buildComplexFieldBody(field, pkg, needsSelection, allArgs)
    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

/**
 * Generates the Javassist method body for a complex field function.
 */
internal fun buildComplexFieldBody(
    field: GraphDslSchema.Field,
    pkg: String,
    needsSelection: Boolean,
    allArgs: Collection<GraphDslSchema.FieldArg>,
): String {
    val returnTypeDef = field.type.baseTypeDef
    val fieldName = field.name
    val hasArgs = allArgs.isNotEmpty()

    // $1, $2, ... for args; last param is block if needsSelection
    val blockParamIndex = allArgs.size + 1  // 1-based

    return buildString {
        append("{\n")

        if (needsSelection) {
            val builderClassName = "${pkg}.${returnTypeDef.name}DslBuilder"
            append("    $builderClassName nestedBuilder = new $builderClassName();\n")
            append("    ((kotlin.jvm.functions.Function1)\$${blockParamIndex}).invoke(nestedBuilder);\n")

            if (hasArgs) {
                val argsExpr = buildArgsExpression(allArgs, startIdx = 1)
                append("    String args = $argsExpr;\n")
                append("    addField(\"$fieldName(\" + args + \") { \" + nestedBuilder.build() + \" }\");\n")
            } else {
                append("    addField(\"$fieldName { \" + nestedBuilder.build() + \" }\");\n")
            }
        } else {
            // Scalar/enum with args
            if (hasArgs) {
                val argsExpr = buildArgsExpression(allArgs, startIdx = 1)
                append("    String args = $argsExpr;\n")
                append("    addField(\"$fieldName(\" + args + \")\");\n")
            } else {
                append("    addField(\"$fieldName\");\n")
            }
        }

        append("}")
    }
}

/**
 * Builds a Java expression that serializes a list of args into a GraphQL argument string.
 * E.g., for args [id, name] → `"id: " + serializeValue($1) + ", " + "name: " + serializeValue($2)`
 */
internal fun buildArgsExpression(
    args: Collection<GraphDslSchema.FieldArg>,
    startIdx: Int,
): String =
    args.mapIndexed { i, arg ->
        "\"${arg.name}: \" + serializeValue(\$${startIdx + i})"
    }.joinToString(" + \", \" + ")

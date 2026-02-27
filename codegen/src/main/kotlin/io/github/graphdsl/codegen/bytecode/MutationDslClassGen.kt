package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.isScalarOrEnum
import io.github.graphdsl.codegen.requiresSelectionSet
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Generates a `MutationDslBuilder` bytecode class for the GraphQL Mutation type.
 *
 * This is the bytecode equivalent of `mutationDslGen.kt`.
 *
 * Key difference from Query DSL: ALL mutation fields are functions (not properties),
 * including simple scalar fields without arguments. This reflects that mutations
 * are operations with side effects.
 *
 * The `mutation()` top-level function (file facade) is NOT generated here —
 * it is handled separately in [DslClassesBuilder] as a raw file facade class.
 */
fun mutationDslClassGen(
    pkg: String,
    mutationDef: GraphDslSchema.Object,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder {
    val kmName = "MutationDslBuilder".toPkgKmName(pkg)
    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = true, isNested = false)

    builder.addDslBuilderInfrastructure()

    for (field in mutationDef.fields) {
        val returnType = field.type.baseTypeDef
        val hasInputArgs = field.args.any { it.type.baseTypeDef is GraphDslSchema.Input }

        when {
            // Scalar fields with input args — treat as scalar function with input builder
            returnType.isScalarOrEnum() && hasInputArgs -> {
                // In practice rare, fall back to scalar function with args
                builder.addScalarMutationFunction(field, pkg, baseTypeMapper)
            }
            // Scalar fields without args — simple fun that calls addField
            returnType.isScalarOrEnum() -> {
                builder.addScalarMutationFunction(field, pkg, baseTypeMapper)
            }
            // Complex fields with input args — use specialized mutation builder
            hasInputArgs -> {
                builder.addInputArgFieldFunction(field, pkg, OperationKind.MUTATION)
            }
            // Complex fields without input args — standard selection builder
            else -> {
                builder.addComplexFieldFunction(field, pkg, baseTypeMapper)
            }
        }
    }

    return builder
}

/**
 * Adds a function for a scalar mutation field (or scalar with args).
 *
 * All mutation fields are functions, unlike query DSL where scalar fields
 * without args become properties.
 *
 * Generated signature: `fun fieldName(arg1: Type1, ...): Unit`
 */
private fun CustomClassBuilder.addScalarMutationFunction(
    field: GraphDslSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper,
) {
    val fieldName = field.name
    val allArgs = field.args

    val kmFn = KmFunction(fieldName).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.OPEN
        f.returnType = KM_UNIT_TYPE

        for (arg in allArgs) {
            f.valueParameters.add(KmValueParameter(arg.name).also { p ->
                p.type = argKmType(arg, pkg, baseTypeMapper)
            })
        }
    }

    val body = buildScalarMutationBody(fieldName, allArgs)
    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

private fun buildScalarMutationBody(
    fieldName: String,
    allArgs: Collection<GraphDslSchema.FieldArg>,
): String {
    val hasArgs = allArgs.isNotEmpty()
    return buildString {
        append("{\n")
        if (hasArgs) {
            val argsExpr = buildArgsExpression(allArgs, startIdx = 1)
            append("    String args = $argsExpr;\n")
            append("    addField(\"$fieldName(\" + args + \")\");\n")
        } else {
            append("    addField(\"$fieldName\");\n")
        }
        append("}")
    }
}

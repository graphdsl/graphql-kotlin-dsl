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
 * Generates a `QueryDslBuilder` bytecode class for the GraphQL Query type.
 *
 * This is the bytecode equivalent of `queryDslGen.kt`.
 *
 * The `query()` top-level function (file facade) is NOT generated here —
 * it is handled separately in [DslClassesBuilder] as a raw file facade class.
 */
fun queryDslClassGen(
    pkg: String,
    queryDef: GraphDslSchema.Object,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder {
    val kmName = "QueryDslBuilder".toPkgKmName(pkg)
    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = true, isNested = false)

    builder.addDslBuilderInfrastructure()

    for (field in queryDef.fields) {
        val returnType = field.type.baseTypeDef
        val isSimpleScalar = returnType.isScalarOrEnum() && field.args.isEmpty()
        val hasInputArgs = field.args.any { it.type.baseTypeDef is GraphDslSchema.Input }

        when {
            isSimpleScalar -> builder.addScalarFieldProperty(field)
            hasInputArgs -> builder.addInputArgFieldFunction(field, pkg, OperationKind.QUERY)
            else -> builder.addComplexFieldFunction(field, pkg, baseTypeMapper)
        }
    }

    return builder
}

/**
 * Operation kind (Query vs Mutation) — determines the specialized builder suffix.
 */
enum class OperationKind(val builderSuffix: String) {
    QUERY("QueryBuilder"),
    MUTATION("MutationBuilder"),
}

/**
 * Adds a function for a field that has at least one Input-type argument.
 *
 * Instead of taking Input args as flat parameters (which would be `Map<String, Any?>`),
 * this uses a specialized `{FieldName}QueryBuilder` or `{FieldName}MutationBuilder` class
 * that combines arg setters with the selection set.
 *
 * Generated signature: `fun fieldName(block: {FieldName}QueryBuilder.() -> Unit): Unit`
 *
 * The body creates a specialized builder, invokes the block, serializes the args map,
 * and appends the full field expression to the selection set.
 */
internal fun CustomClassBuilder.addInputArgFieldFunction(
    field: GraphDslSchema.Field,
    pkg: String,
    kind: OperationKind,
) {
    val fieldName = field.name
    val returnTypeDef = field.type.baseTypeDef
    val needsSelection = returnTypeDef.requiresSelectionSet()
    val specializedBuilderName = fieldName.replaceFirstChar { it.uppercase() } + kind.builderSuffix
    val specializedBuilderJavaName = "$pkg.$specializedBuilderName"
    val builderKmName = specializedBuilderName.toPkgKmName(pkg)

    val kmFn = KmFunction(fieldName).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.OPEN
        f.returnType = KM_UNIT_TYPE

        val blockType = kmFunctionType(builderKmName.asType())
        f.valueParameters.add(KmValueParameter("block").also { p ->
            p.type = blockType
        })
    }

    val body = buildInputArgFieldBody(fieldName, specializedBuilderJavaName, needsSelection)
    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

/**
 * Generates the Javassist method body for a field with input args.
 *
 * The body:
 * 1. Creates the specialized builder (e.g. `SearchCharacterQueryBuilder`)
 * 2. Invokes the user's DSL block
 * 3. Serializes the args map from `buildArgs()`
 * 4. Appends the complete field expression to the selection set
 */
private fun buildInputArgFieldBody(
    fieldName: String,
    specializedBuilderJavaName: String,
    needsSelection: Boolean,
): String = buildString {
    append("{\n")
    append("    $specializedBuilderJavaName nestedBuilder = new $specializedBuilderJavaName();\n")
    append("    ((kotlin.jvm.functions.Function1)\$1).invoke(nestedBuilder);\n")

    if (needsSelection) {
        append("    java.util.Map argsMap = nestedBuilder.buildArgs();\n")
        append("    java.util.StringJoiner argsSj = new java.util.StringJoiner(\", \");\n")
        append("    java.util.Iterator it = argsMap.entrySet().iterator();\n")
        append("    while (it.hasNext()) {\n")
        append("        java.util.Map\${'$'}Entry entry = (java.util.Map\${'$'}Entry)it.next();\n")
        append("        argsSj.add(entry.getKey().toString() + \": \" + serializeValue(entry.getValue()));\n")
        append("    }\n")
        append("    String argsStr = argsSj.toString();\n")
        append("    String argsSection = argsStr.length() > 0 ? \"(\" + argsStr + \")\" : \"\";\n")
        append("    addField(\"$fieldName\" + argsSection + \" { \" + nestedBuilder.build() + \" }\");\n")
    } else {
        // Input args on a scalar field — unusual but handle gracefully
        append("    addField(\"$fieldName\");\n")
    }

    append("}")
}

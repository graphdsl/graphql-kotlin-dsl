package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.isScalarOrEnum
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Generates a `{InterfaceName}DslBuilder` bytecode class for a GraphQL Interface type.
 *
 * This is the bytecode equivalent of `nodeInterfaceDslGen.kt`.
 *
 * Generated class structure:
 * ```
 * open class {Interface}DslBuilder internal constructor() {
 *     // common scalar fields as properties
 *     val id: Unit get() { addField("id") }
 *
 *     // inline fragment methods for each implementing type
 *     fun onUser(block: UserDslBuilder.() -> Unit) {
 *         val nestedBuilder = UserDslBuilder()
 *         block(nestedBuilder)
 *         addField("... on User { ${nestedBuilder.build()} }")
 *     }
 * }
 * ```
 */
fun nodeInterfaceDslClassGen(
    pkg: String,
    interfaceDef: GraphDslSchema.Interface,
    implementingTypes: List<GraphDslSchema.Object>,
): CustomClassBuilder {
    val kmName = "${interfaceDef.name}DslBuilder".toPkgKmName(pkg)
    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = true, isNested = false)

    builder.addDslBuilderInfrastructure()

    // Add common scalar fields (no args, scalar/enum return)
    for (field in interfaceDef.fields) {
        val returnType = field.type.baseTypeDef
        if (returnType.isScalarOrEnum() && field.args.isEmpty()) {
            builder.addScalarFieldProperty(field)
        }
        // Complex interface fields and fields with args are only accessible via type fragments
    }

    // Add inline fragment functions for each implementing type
    for (implementingType in implementingTypes) {
        builder.addFragmentFunction(implementingType, pkg)
    }

    return builder
}

/**
 * Adds an `on{TypeName}` function for an inline GraphQL fragment.
 *
 * Generated: `fun on{TypeName}(block: {TypeName}DslBuilder.() -> Unit): Unit`
 * Body creates a {TypeName}DslBuilder, invokes the block, and appends
 * `... on TypeName { ... }` to the selection set.
 */
private fun CustomClassBuilder.addFragmentFunction(
    implementingType: GraphDslSchema.Object,
    pkg: String,
) {
    val typeName = implementingType.name
    val functionName = "on$typeName"
    val builderClassName = "${typeName}DslBuilder"
    val builderJavaName = "$pkg.$builderClassName"
    val builderKmName = builderClassName.toPkgKmName(pkg)
    val blockType = kmFunctionType(builderKmName.asType())

    val kmFn = KmFunction(functionName).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.OPEN
        f.returnType = KM_UNIT_TYPE
        f.valueParameters.add(KmValueParameter("block").also { p ->
            p.type = blockType
        })
    }

    val body = buildString {
        append("{\n")
        append("    $builderJavaName nestedBuilder = new $builderJavaName();\n")
        append("    ((kotlin.jvm.functions.Function1)\$1).invoke(nestedBuilder);\n")
        append("    addField(\"... on $typeName { \" + nestedBuilder.build() + \" }\");\n")
        append("}")
    }

    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

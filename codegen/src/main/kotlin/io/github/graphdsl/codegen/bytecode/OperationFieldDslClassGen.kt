package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.ct.KmConstructorWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.km.KmPropertyBuilder
import io.github.graphdsl.codegen.utils.JavaIdName
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.name
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.KmVariance
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Generates a specialized `{FieldName}QueryBuilder` or `{FieldName}MutationBuilder` class
 * for a query/mutation field that has Input-type arguments.
 *
 * This is the bytecode equivalent of `OperationType.kt`'s `operationFieldDslGen`.
 *
 * The generated class:
 * - Extends `{ReturnType}DslBuilder` (inheriting selection-set fields)
 * - Has a private `argValues: MutableMap<String, Any?>` backing field
 * - For each scalar arg: adds a `var argName: Type?` property
 * - For each Input arg: adds a `fun argName(block: InputBuilder.() -> Unit)` function
 * - Adds `internal fun buildArgs(): Map<String, Any?>`
 */
fun operationFieldDslClassGen(
    pkg: String,
    field: GraphDslSchema.Field,
    returnType: GraphDslSchema.TypeDef,
    kind: OperationKind,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder {
    val fieldName = field.name
    val builderClassName = "${fieldName.replaceFirstChar { it.uppercase() }}${kind.builderSuffix}"
    val kmName = builderClassName.toPkgKmName(pkg)

    // Parent class: {ReturnType}DslBuilder
    val parentBuilderKmName = "${returnType.name}DslBuilder".toPkgKmName(pkg)
    val parentBuilderJavaName = "$pkg.${returnType.name}DslBuilder"

    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = false, isNested = false)
    builder.addSupertype(parentBuilderKmName.asType())

    // Constructor: calls super() and initializes argValues
    val ctor = KmConstructorWrapper(
        constructor = KmConstructor().also { c ->
            c.visibility = Visibility.INTERNAL
        },
        body = "{ super(); this.argValues = new java.util.LinkedHashMap(); }"
    )
    builder.addConstructor(ctor)

    // `private val argValues: MutableMap<String, Any?>`
    val argValuesType = mutableMapStringAnyNullableType()
    val argValuesProp = KmPropertyBuilder(
        name = JavaIdName("argValues"),
        type = argValuesType,
        inputType = argValuesType,
        isVariable = false,
        constructorProperty = false,
    )
        .getterVisibility(Visibility.PRIVATE)
        .build()
    builder.addProperty(argValuesProp)

    // Add arg properties/functions
    for (arg in field.args) {
        val argTypeDef = arg.type.baseTypeDef
        if (argTypeDef is GraphDslSchema.Input) {
            builder.addInputArgFunction(arg, pkg)
        } else {
            builder.addScalarArgProperty(arg, pkg, baseTypeMapper)
        }
    }

    // `internal fun buildArgs(): Map<String, Any?>`
    val buildArgsFn = KmFunctionWrapper(
        fn = KmFunction("buildArgs").also { f ->
            f.visibility = Visibility.INTERNAL
            f.modality = Modality.FINAL
            f.returnType = mapStringAnyNullableType()
        },
        body = "{ return this.argValues; }"
    )
    builder.addFunction(buildArgsFn)

    return builder
}

// =========================================================================
// Internal helpers
// =========================================================================

/**
 * Adds a `var argName: ArgType?` property that reads/writes from the `argValues` map.
 *
 * Type is forced nullable to simplify getter casting (no JVM primitive unboxing needed).
 */
private fun CustomClassBuilder.addScalarArgProperty(
    arg: GraphDslSchema.HasDefaultValue,
    pkg: String,
    baseTypeMapper: BaseTypeMapper,
) {
    val argName = arg.name
    val rawType = argKmType(arg, pkg, baseTypeMapper)
    // Force nullable to simplify JVM getter body (avoids primitive unboxing)
    val nullableType = rawType.name.asNullableType()
    val jvmCastType = operationFieldJvmCastType(nullableType)

    val getterBody = "{ return ($jvmCastType)this.argValues.get(\"$argName\"); }"
    val setterBody = "{ this.argValues.put(\"$argName\", \$1); }"

    val propBuilder = KmPropertyBuilder(
        name = JavaIdName(argName),
        type = nullableType,
        inputType = nullableType,
        isVariable = true,
        constructorProperty = false,
    )
        .getterVisibility(Visibility.PUBLIC)
        .getterBody(getterBody)
        .setterVisibility(Visibility.PUBLIC)
        .setterBody(setterBody)
    addProperty(propBuilder.build())
}

/**
 * Adds a `fun argName(block: {Input}Builder.() -> Unit)` function that builds
 * the input using the dedicated builder and stores the result in `argValues`.
 */
private fun CustomClassBuilder.addInputArgFunction(
    arg: GraphDslSchema.HasDefaultValue,
    pkg: String,
) {
    val argName = arg.name
    val inputTypeDef = arg.type.baseTypeDef as GraphDslSchema.Input
    val inputBuilderClassName = "${inputTypeDef.name}Builder"
    val inputBuilderJavaName = "$pkg.$inputBuilderClassName"
    val builderKmName = inputBuilderClassName.toPkgKmName(pkg)
    val blockType = kmFunctionType(builderKmName.asType())

    val kmFn = KmFunction(argName).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.FINAL
        f.returnType = KM_UNIT_TYPE
        f.valueParameters.add(KmValueParameter("block").also { p ->
            p.type = blockType
        })
    }

    val body = buildString {
        append("{\n")
        append("    $inputBuilderJavaName inputBuilder = new $inputBuilderJavaName();\n")
        append("    ((kotlin.jvm.functions.Function1)\$1).invoke(inputBuilder);\n")
        append("    this.argValues.put(\"$argName\", inputBuilder.build());\n")
        append("}")
    }

    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

/**
 * Returns the JVM boxed type name for use in a cast expression when reading
 * from a `Map<String, Any?>`. Uses boxed reference types for all values.
 */
private fun operationFieldJvmCastType(type: KmType): String = when (type.name) {
    Km.STRING -> "java.lang.String"
    Km.INT -> "java.lang.Integer"
    Km.LONG -> "java.lang.Long"
    Km.DOUBLE -> "java.lang.Double"
    Km.FLOAT -> "java.lang.Float"
    Km.BOOLEAN -> "java.lang.Boolean"
    Km.CHAR -> "java.lang.Character"
    Km.ANY -> "java.lang.Object"
    else -> type.name.toString().replace('/', '.')
}

/**
 * Returns a KmType for MutableMap<String, Any?>.
 */
private fun mutableMapStringAnyNullableType(): KmType =
    KmType().also { t ->
        t.classifier = KmClassifier.Class(Km.MUTABLE_MAP.toString())
        t.arguments.add(KmTypeProjection(KmVariance.INVARIANT, Km.STRING.asType()))
        t.arguments.add(KmTypeProjection(KmVariance.OUT, Km.ANY.asNullableType()))
    }

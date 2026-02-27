package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.km.KmPropertyBuilder
import io.github.graphdsl.codegen.utils.JavaIdName
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.KmName
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import io.github.graphdsl.codegen.ct.KmConstructorWrapper
import io.github.graphdsl.codegen.utils.name
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Generates an `{InputName}Builder` bytecode class for a GraphQL Input type.
 *
 * This is the bytecode equivalent of `inputDslGen.kt`.
 *
 * Generated class structure:
 * ```
 * class {Input}Builder internal constructor() {
 *     private val values = LinkedHashMap<String, Any?>()
 *
 *     var fieldName: FieldType?  // for scalar fields
 *         get() = values["fieldName"] as FieldType?
 *         set(value) { values["fieldName"] = value }
 *
 *     fun nestedField(block: NestedInputBuilder.() -> Unit)  // for nested input fields
 *
 *     internal fun build(): Map<String, Any?> = values.toMap()
 * }
 * ```
 *
 * Note: All scalar field types are made nullable to simplify JVM getter/setter implementation.
 */
fun inputDslClassGen(
    pkg: String,
    inputDef: GraphDslSchema.Input,
    baseTypeMapper: BaseTypeMapper,
): CustomClassBuilder {
    val kmName = "${inputDef.name}Builder".toPkgKmName(pkg)
    val builder = CustomClassBuilder.classBuilder(kmName, isOpen = false, isNested = false)

    // Constructor: `internal constructor() { super(); this.values = new java.util.LinkedHashMap(); }`
    val ctor = KmConstructorWrapper(
        constructor = KmConstructor().also { c ->
            c.visibility = Visibility.INTERNAL
        },
        body = "{ super(); this.values = new java.util.LinkedHashMap(); }"
    )
    builder.addConstructor(ctor)

    // `private val values: MutableMap<String, Any?>`
    val valuesType = mutableMapStringAnyNullableType()
    val valuesProp = KmPropertyBuilder(
        name = JavaIdName("values"),
        type = valuesType,
        inputType = valuesType,
        isVariable = false,
        constructorProperty = false,
    )
        .getterVisibility(Visibility.PRIVATE)
        .build()
    builder.addProperty(valuesProp)

    // Add fields
    for (field in inputDef.fields) {
        val fieldTypeDef = field.type.baseTypeDef
        if (fieldTypeDef is GraphDslSchema.Input) {
            builder.addNestedInputFieldFunction(field, pkg)
        } else {
            builder.addScalarInputFieldProperty(field, pkg, baseTypeMapper)
        }
    }

    // `internal fun build(): Map<String, Any?>`
    val buildFn = KmFunctionWrapper(
        fn = KmFunction("build").also { f ->
            f.visibility = Visibility.INTERNAL
            f.modality = Modality.FINAL
            f.returnType = mapStringAnyNullableType()
        },
        body = "{ return this.values; }"
    )
    builder.addFunction(buildFn)

    return builder
}

// =========================================================================
// Internal helpers
// =========================================================================

/**
 * Adds a `var fieldName: FieldType?` property for a scalar input field.
 *
 * The property uses a nullable type (even if the schema says non-nullable)
 * to simplify JVM getter body generation. This trades some compile-time
 * type safety for implementation simplicity.
 *
 * Getter: reads from the `values` map with a cast
 * Setter: stores in the `values` map
 */
private fun CustomClassBuilder.addScalarInputFieldProperty(
    field: GraphDslSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper,
) {
    val fieldName = field.name
    // Use nullable version to simplify getter (no unboxing needed)
    val rawType = argKmType(field, pkg, baseTypeMapper)
    val nullableType = rawType.name.asNullableType()
    val jvmCastType = nullableType.inputFieldJvmCastType()

    val getterBody = "{ return ($jvmCastType)this.values.get(\"$fieldName\"); }"
    val setterBody = "{ this.values.put(\"$fieldName\", \$1); }"

    val propBuilder = KmPropertyBuilder(
        name = JavaIdName(fieldName),
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
 * Adds a function for a nested input field.
 *
 * Generated: `fun fieldName(block: NestedInputBuilder.() -> Unit): Unit`
 * Body creates a nested builder, invokes the block, and stores the result.
 */
private fun CustomClassBuilder.addNestedInputFieldFunction(
    field: GraphDslSchema.Field,
    pkg: String,
) {
    val fieldName = field.name
    val inputTypeDef = field.type.baseTypeDef as GraphDslSchema.Input
    val nestedBuilderName = "${inputTypeDef.name}Builder"
    val nestedBuilderJavaName = "$pkg.$nestedBuilderName"
    val builderKmName = nestedBuilderName.toPkgKmName(pkg)
    val blockType = kmFunctionType(builderKmName.asType())

    val kmFn = KmFunction(fieldName).also { f ->
        f.visibility = Visibility.PUBLIC
        f.modality = Modality.FINAL
        f.returnType = KM_UNIT_TYPE
        f.valueParameters.add(KmValueParameter("block").also { p ->
            p.type = blockType
        })
    }

    val body = buildString {
        append("{\n")
        append("    $nestedBuilderJavaName nestedBuilder = new $nestedBuilderJavaName();\n")
        append("    ((kotlin.jvm.functions.Function1)\$1).invoke(nestedBuilder);\n")
        append("    this.values.put(\"$fieldName\", nestedBuilder.build());\n")
        append("}")
    }

    addFunction(KmFunctionWrapper(fn = kmFn, body = body))
}

/**
 * Returns the JVM class name to use in a cast expression for reading from
 * a `Map<String, Any?>`. Always uses boxed types.
 */
private fun KmType.inputFieldJvmCastType(): String = when (name) {
    Km.STRING -> "java.lang.String"
    Km.INT -> "java.lang.Integer"
    Km.LONG -> "java.lang.Long"
    Km.DOUBLE -> "java.lang.Double"
    Km.FLOAT -> "java.lang.Float"
    Km.BOOLEAN -> "java.lang.Boolean"
    Km.CHAR -> "java.lang.Character"
    Km.ANY -> "java.lang.Object"
    else -> name.toString().replace('/', '.')
}

/**
 * Returns a KmType for MutableMap<String, Any?> (used for the `values` backing field).
 */
private fun mutableMapStringAnyNullableType(): KmType =
    KmType().also { t ->
        t.classifier = KmClassifier.Class(Km.MUTABLE_MAP.toString())
        t.arguments.add(kotlinx.metadata.KmTypeProjection(kotlinx.metadata.KmVariance.INVARIANT, Km.STRING.asType()))
        t.arguments.add(kotlinx.metadata.KmTypeProjection(kotlinx.metadata.KmVariance.OUT, Km.ANY.asNullableType()))
    }

package io.github.graphdsl.codegen

import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.codegen.KmName
import io.github.graphdsl.utils.codegen.Km
import io.github.graphdsl.utils.codegen.name
import kotlinx.metadata.*

fun GraphDslSchema.HasDefaultValue.kmType(
    pkg: KmName,
    baseTypeMapper: BaseTypeMapper,
    isInput: Boolean = false,
    useSchemaValueType: Boolean = false,
): KmType = type.kmType(pkg, baseTypeMapper, this, isInput, useSchemaValueType)

fun GraphDslSchema.TypeExpr.kmType(
    pkg: KmName,
    baseTypeMapper: BaseTypeMapper,
    field: GraphDslSchema.HasDefaultValue?,
    isInput: Boolean,
    useSchemaValueType: Boolean,
): KmType {
    var result = this.baseTypeKmType(pkg, baseTypeMapper, field, isInput)

    if (useSchemaValueType) {
        val baseType = this.baseTypeDef
        if (!this.isList &&
            baseType is GraphDslSchema.Object &&
            !baseType.isConnection &&
            !baseType.isNode &&
            !graphqlScalarTypeToKmName.containsKey(baseType.name)
        ) {
            val baseTypeName = "$pkg/${baseType.name}"
            return KmType().also {
                it.classifier = KmClassifier.Class("$baseTypeName.Value")
                it.isNullable = this.baseTypeNullable
            }
        }
    }

    // For input types, the variance of subclassable GRTs is OUT, the rest are INVARIANT
    var variance =
        if (!isInput) {
            KmVariance.INVARIANT
        } else {
            when (this.baseTypeDef.kind) {
                GraphDslSchema.TypeDefKind.OBJECT -> {
                    baseTypeMapper.getInputVarianceForObject() ?: KmVariance.INVARIANT
                }

                GraphDslSchema.TypeDefKind.ENUM, GraphDslSchema.TypeDefKind.INTERFACE, GraphDslSchema.TypeDefKind.UNION,
                -> KmVariance.OUT

                GraphDslSchema.TypeDefKind.SCALAR -> {
                    if (result.name == Km.ANY) {
                        // JSON types map to kotlin/Any
                        KmVariance.OUT
                    } else {
                        KmVariance.INVARIANT
                    }
                }

                else -> KmVariance.INVARIANT
            }
        }
    for (i in (this.listDepth - 1) downTo 0) {
        result = KmType().also {
            it.classifier = KmClassifier.Class(Km.LIST.toString())
            it.arguments.add(KmTypeProjection(variance, result))
            it.isNullable = this.nullableAtDepth(i)
        }
        // For input types, all contained-lists have (upper-bounded) wildcard types
        variance = if (isInput) KmVariance.OUT else KmVariance.INVARIANT
    }
    return result
}

fun GraphDslSchema.TypeExpr.baseTypeKmType(
    pkg: KmName,
    baseTypeMapper: BaseTypeMapper,
    field: GraphDslSchema.HasDefaultValue?,
    isInput: Boolean = false,
): KmType {
    // Check if mapper wants to handle this type
    baseTypeMapper.mapBaseType(this, pkg, field, isInput)?.let { return it }

    // Default case - create standard KmType
    val kmName = graphqlScalarTypeToKmName[this.baseTypeDef.name]
        ?: KmName("$pkg/${this.baseTypeDef.name}")

    return KmType().also {
        it.classifier = KmClassifier.Class(kmName.toString())
        it.isNullable = this.baseTypeNullable
    }
}

val GraphDslSchema.TypeDef.isNode: Boolean
    get() = (name == "Node" && this is GraphDslSchema.Interface) ||
            (this is GraphDslSchema.Record && supers.any { it.isNode })

val GraphDslSchema.TypeDef.isConnection: Boolean
    get() = (name == "PagedConnection" && this is GraphDslSchema.Interface) ||
            (this is GraphDslSchema.Interface && supers.any { it.isConnection }) ||
            (this is GraphDslSchema.Object && supers.any { it.isConnection })


private val graphqlScalarTypeToKmName: Map<String, KmName> =
    baseGraphqlScalarTypeMapping.mapValues { KmName(it.value.qualifiedName!!.replace(".", "/")) }


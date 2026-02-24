package io.github.graphdsl.codegen

import io.github.graphdsl.mapper.backingDataType
import io.github.graphdsl.mapper.isBackingDataType
import io.github.graphdsl.mapper.isID
import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.codegen.Km
import io.github.graphdsl.utils.codegen.KmName
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmVariance
import kotlinx.metadata.isNullable

/**
 * Interface for handling base type mapping in GraphDslSchemaExtensions.
 */
interface BaseTypeMapper {
    fun mapBaseType(
        type: GraphDslSchema.TypeExpr,
        pkg: KmName,
        field: GraphDslSchema.HasDefaultValue? = null,
        isInput: Boolean = false,
    ): KmType?

    /**
     * Determines variance for input types based on TypeDef kind.
     * Returns null if default OSS behavior should be used.
     */
    fun getInputVarianceForObject(): KmVariance?
}

/**
 * GraphDSL implementation of BaseTypeMapper that handles standard cases.
 */
class GraphDSLBaseTypeMapper(
    val schema: GraphDslSchema,
) : BaseTypeMapper {
    override fun mapBaseType(
        type: GraphDslSchema.TypeExpr,
        pkg: KmName,
        field: GraphDslSchema.HasDefaultValue?,
        isInput: Boolean,
    ): KmType? {
        val baseTypeDef = type.baseTypeDef

        if (baseTypeDef.isBackingDataType) {
            return type.backingDataType()
        } else if (baseTypeDef.isID) {
            // GraphQL ID scalars always map to String
            return KmType().also {
                it.classifier = KmClassifier.Class(Km.STRING.toString())
                it.isNullable = type.baseTypeNullable
            }
        }

        return null // Let extension function handle default case
    }

    override fun getInputVarianceForObject(): KmVariance {
        return KmVariance.INVARIANT
    }
}
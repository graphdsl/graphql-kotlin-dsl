package io.github.graphdsl.codegen

import io.github.graphdsl.mapper.backingDataType
import io.github.graphdsl.mapper.grtNameForIdParam
import io.github.graphdsl.mapper.isBackingDataType
import io.github.graphdsl.mapper.isID
import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.codegen.JavaBinaryName
import io.github.graphdsl.utils.codegen.Km
import io.github.graphdsl.utils.codegen.KmName
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmVariance
import kotlinx.metadata.isNullable

/**
 * Interface for handling base type mapping in GraphqDslSchemaExtensions.
 * This allows AirBnB-specific type mapping logic to be plugged in.
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

    fun getGlobalIdType(): JavaBinaryName
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
            return type.idKmType(pkg, field, isInput)
        }

        return null // Let extension function handle default case
    }

    override fun getInputVarianceForObject(): KmVariance {
        return KmVariance.INVARIANT
    }



    override fun getGlobalIdType(): JavaBinaryName {
        return JavaBinaryName("graphdsl.api.globalid.GlobalID")
    }

    /**
     * Constructs a KmType for a TypeExpr representing an ID scalar.
     * For `Foo` a GraphQL node-composite-output type, when:
     *   ID is not the `id` of a node-type and has no @idOf on it -> String
     *   isInput == false || Foo is Object -> graphdsl.api.globalid.GlobalID<Foo>
     *   else -> graphdsl.api.globalid.GlobalID<out Foo>
     *
     * See the `learnings.md` section on "input types" for more information.
     *
     * @param pkg containing generated GRTs
     * @param field field this type-expr is from, it that's where it's from
     * @param isInput type is being used in an input context, e.g., as
     *   setter for a field or the continuation param of a suspend fun
     */
    private fun GraphDslSchema.TypeExpr.idKmType(
        pkg: KmName,
        field: GraphDslSchema.HasDefaultValue?,
        isInput: Boolean = false,
    ): KmType {
        val idTypeName = this@GraphDSLBaseTypeMapper.getGlobalIdType().asKmName // The "GlobalID" in GlobalID<Foo>
        val grtTypeName = field?.grtNameForIdParam() // The "Foo" in GlobalID<Foo>
        val grtBaseTypeDef = grtTypeName?.let { schema.types[it] }

        if (grtTypeName == null || grtBaseTypeDef == null) {
            return KmType().also {
                it.classifier = KmClassifier.Class(Km.STRING.toString())
                it.isNullable = this.baseTypeNullable
            }
        }

        val notGraphQLObjectType = (grtBaseTypeDef.kind != GraphDslSchema.TypeDefKind.OBJECT)
        val variance = if (isInput && notGraphQLObjectType) {
            KmVariance.OUT
        } else {
            KmVariance.INVARIANT
        }

        return idTypeName.asType().also {
            it.arguments += KmTypeProjection(variance, pkg.append("/$grtTypeName").asType())
            it.isNullable = this.baseTypeNullable
        }
    }
}
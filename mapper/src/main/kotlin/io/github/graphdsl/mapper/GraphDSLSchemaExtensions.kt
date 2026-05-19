package io.github.graphdsl.mapper

import io.github.graphdsl.utils.codegen.Km
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmType
import kotlinx.metadata.isNullable


val GraphDslSchema.TypeDef.isBackingDataType: Boolean get() =
    kind == GraphDslSchema.TypeDefKind.SCALAR && name == "BackingData"

val GraphDslSchema.TypeDef.isID: Boolean
    get() = kind == GraphDslSchema.TypeDefKind.SCALAR && name == "ID"

/** create a KmType describing the customized BackingData */
fun GraphDslSchema.TypeExpr.backingDataType(): KmType {
    return Km.ANY.asType().apply { isNullable = baseTypeNullable }
}


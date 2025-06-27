package io.github.graphdsl.mapper

import io.github.graphdsl.utils.codegen.Km
import io.github.graphdsl.mapper.IdOf.Companion.idOf
import io.github.graphdsl.schema.GraphDslSchema
import graphql.language.StringValue
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

fun GraphDslSchema.HasDefaultValue.grtNameForIdParam(): String? {
    val isNodeIdField = isNodeIdField(this)
    val idOf = this.appliedDirectives.idOf

    return if (isNodeIdField) {
        require(idOf == null) {
            "@idOf may not be used on the `id` field of a Node implementation"
        }
        this.containingDef.name
    } else idOf?.type
}
private fun isNodeIdField(field: GraphDslSchema.HasDefaultValue): Boolean {
    val containerType = field.containingDef as? GraphDslSchema.TypeDef
    return field.name == "id" && containerType?.isNode == true
}

data class IdOf(val type: String) {
    companion object {
        private val name: String = "idOf"

        private fun parse(dir: GraphDslSchema.AppliedDirective): IdOf {
            require(dir.name == name)
            return IdOf((dir.arguments["type"] as StringValue).value!!)
        }

        val Iterable<GraphDslSchema.AppliedDirective>.idOf: IdOf?
            get() = firstNotNullOfOrNull { if (it.name == name) parse(it) else null }
    }
}

val GraphDslSchema.TypeDef.isNode: Boolean
    get() = (name == "Node" && this is GraphDslSchema.Interface) ||
            (this is GraphDslSchema.Record && supers.any { it.isNode })


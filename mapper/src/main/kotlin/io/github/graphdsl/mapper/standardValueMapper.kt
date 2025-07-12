package io.github.graphdsl.mapper

import graphdsl.schema.GraphDslSchema
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetTime

/**
 *  A ValueMapper suitable converting GJ literal values, represented by a
 *  [graphql.language.Value], into a value suitable for use in Kotlin.
 */
internal val standardValueMapper = object : ValueMapper<Value<*>, Any> {
    override fun invoke(
        type: GraphDslSchema.TypeExpr,
        value: Value<*>?
    ): Any? = value?.let { map(type, it, 0) }

    private fun map(
        type: GraphDslSchema.TypeExpr,
        value: Value<*>,
        listDepth: Int
    ): Any? {
        if (value is NullValue) {
            if (!type.nullableAtDepth(listDepth)) {
                throw IllegalArgumentException("$type not nullable at depth $listDepth")
            }
            return null
        }

        if (listDepth < type.listDepth) {
            if (value !is ArrayValue) {
                throw IllegalArgumentException("$type not ArrayValue at depth $listDepth ($value)")
            }
            return value.values.map { map(type, it, listDepth + 1) }
        }

        return when (val bt = type.baseTypeDef) {
            is GraphDslSchema.Input -> {
                (value as ObjectValue).objectFields.associate {
                    it.name to map(bt.field(it.name)!!.type, it.value, 0)
                } + ("__typename" to bt.name)
            }
            is GraphDslSchema.Enum -> {
                if (value !is EnumValue) {
                    throw IllegalArgumentException("Base value of $type not EnumValue ($value)")
                }
                bt.values.find { it.name == value.name }
                    ?: throw IllegalArgumentException("Enum value ${value.name} not in basetype of $type")
            }
            is GraphDslSchema.Scalar -> when (bt.name) {
                "Boolean" -> (value as BooleanValue).isValue
                "Date" -> (value as StringValue).value?.let { LocalDate.parse(it) }
                "DateTime" -> (value as StringValue).value?.let { Instant.parse(it) }
                "Float", "Int", "Long", "Short" -> {
                    val v = when (value) {
                        is IntValue -> BigDecimal(value.value)
                        else -> (value as FloatValue).value
                    }
                    when (bt.name) {
                        "Float" -> v.toDouble()
                        "Int" -> v.toInt()
                        "Long" -> v.toLong()
                        "Short" -> v.toShort()
                        else -> throw IllegalStateException("Will never happen (${bt.name}).")
                    }
                }
                "ID" -> (value as StringValue).value
                "String" -> (value as StringValue).value
                "Time" -> OffsetTime.parse((value as StringValue).value)
                else -> throw IllegalArgumentException("Can't convert $value to $type")
            }
            else -> throw IllegalArgumentException("Can't convert $value to $type")
        }
    }
}
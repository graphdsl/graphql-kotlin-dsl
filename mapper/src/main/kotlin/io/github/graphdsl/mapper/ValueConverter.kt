package io.github.graphdsl.mapper

import io.github.graphdsl.schema.GraphDslSchema
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.IntValue
import graphql.language.ObjectValue
import graphql.language.ScalarValue
import graphql.language.StringValue
import graphql.language.Value

/**
 *  Used to convert the default values of fields and arguments and
 *  applied-directive argument values found in the graphql-java
 *  schema (i.e., [graphql.language.Value] objects) into whatever
 *  universe of values the consumer of the schema would like.  The
 *  resulting values must implement "value type" semantics, meaning
 *  [equals] and [hashCode] are based on value equality, not on
 *  reference equality.
 */
interface ValueConverter {

    /** Convert a value that was parsed as a literal [graphql.language.Value] */
    fun convert(
        type: GraphDslSchema.TypeExpr,
        value: Value<*>
    ): Any?

    fun javaClassFor(type: GraphDslSchema.TypeExpr): Class<*>? = null

    companion object {
        val default =
            object : ValueConverter {
                override fun convert(
                    type: GraphDslSchema.TypeExpr,
                    value: Value<*>
                ): Any? = defaultValueMapper(type, value)

                override fun javaClassFor(type: GraphDslSchema.TypeExpr): Class<*> =
                    when {
                        type.isList -> ArrayValue::class.java
                        type.baseTypeDef is GraphDslSchema.Enum -> EnumValue::class.java
                        type.baseTypeDef is GraphDslSchema.Input -> ObjectValue::class.java
                        type.baseTypeDef.name == "String" -> StringValue::class.java
                        type.baseTypeDef.name == "Int" -> IntValue::class.java
                        type.baseTypeDef.name == "Float" -> ScalarValue::class.java // Cheat...
                        type.baseTypeDef.name == "Boolean" -> BooleanValue::class.java
                        type.baseTypeDef.name == "ID" -> StringValue::class.java
                        else -> throw IllegalArgumentException("Bad type for default (${type.baseTypeDef}).")
                    }
            }
    }
}
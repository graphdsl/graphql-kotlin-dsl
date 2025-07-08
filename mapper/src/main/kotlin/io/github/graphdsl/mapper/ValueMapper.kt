package io.github.graphdsl.mapper

import io.github.graphdsl.schema.GraphDslSchema
import graphql.language.NullValue
import graphql.language.Value

/** Implements a 1-way mapping of a value from a `From` type to a `To` type */
interface ValueMapper<A, B> : ((GraphDslSchema.TypeExpr, A?) -> B?)


/** A pass-thru ValueMapper for GJ literals */
internal val defaultValueMapper =
    object : ValueMapper<Value<*>, Value<*>> {
        override fun invoke(
            type: GraphDslSchema.TypeExpr,
            value: Value<*>?
        ): Value<*>? =
            if (value is NullValue) {
                null
            } else {
                value
            }
    }

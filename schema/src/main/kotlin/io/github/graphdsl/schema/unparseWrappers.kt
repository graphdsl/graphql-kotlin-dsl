package io.github.graphdsl.schema

/** Unparses wrappers into an internal syntax.  This syntax
 *  uses an explicit '?' to represent a nullable type, and
 *  explicit '!' a not-null type.  A wrapper always has at
 *  least one of these characters indicating nullability
 *  for the base type.  It will have an additional such
 *  indicator for every depth of list.  Read from
 *  left-to-right, the left-most character indicates the
 *  nullability of the outermost value, the right-most of the
 *  base type.  Thus, for example, "?" by itself means the
 *  type has no list-wrappers, and that the value can be
 *  either null, or a value of the base-type, where "!?!"
 *  means a not-nullable list of nullable elements, each
 *  element, if not null, is itself a list of non-null
 *  elements having the bast-type as their type. */
fun GraphDslSchema.TypeExpr.unparseWrappers(): String {
    val result = StringBuilder()
    for (i in 0 until listDepth) {
        result.append(if (nullableAtDepth(i)) '?' else '!')
    }
    result.append(if (baseTypeNullable) '?' else '!')
    return result.toString()
}
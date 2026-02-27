package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.ct.KM_UNIT_TYPE
import io.github.graphdsl.codegen.ct.KmConstructorWrapper
import io.github.graphdsl.codegen.ct.KmFunctionWrapper
import io.github.graphdsl.codegen.km.CustomClassBuilder
import io.github.graphdsl.codegen.km.KmPropertyBuilder
import io.github.graphdsl.codegen.utils.JavaIdName
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

/**
 * Adds the four standard DSL builder members to a [CustomClassBuilder]:
 *
 * 1. `private val fields: MutableList<String>` — backing field, initialized in the constructor
 * 2. `protected fun addField(name: String): Unit`
 * 3. `internal open fun build(): String`
 * 4. `private fun serializeValue(value: Any?): String`
 *
 * Also adds an `internal constructor()` that calls `super()` and initializes `fields`.
 */
fun CustomClassBuilder.addDslBuilderInfrastructure() {
    // -- Constructor: internal constructor() { super(); this.fields = new java.util.ArrayList(); }
    val ctor = KmConstructorWrapper(
        constructor = KmConstructor().also { c ->
            c.visibility = Visibility.INTERNAL
        },
        body = "{ super(); this.fields = new java.util.ArrayList(); }"
    )
    addConstructor(ctor)

    // -- private val fields: MutableList<String>
    val fieldsType = mutableListOfStringType()
    val fieldsProp = KmPropertyBuilder(
        name = JavaIdName("fields"),
        type = fieldsType,
        inputType = fieldsType,
        isVariable = false,
        constructorProperty = false,
    )
        .getterVisibility(Visibility.PRIVATE)
        .build()
    addProperty(fieldsProp)

    // -- protected fun addField(name: String): Unit
    val addFieldFn = KmFunctionWrapper(
        fn = KmFunction("addField").also { f ->
            f.visibility = Visibility.PROTECTED
            f.modality = Modality.FINAL
            f.returnType = KM_UNIT_TYPE
            f.valueParameters.add(
                KmValueParameter("name").also { p -> p.type = stringType() }
            )
        },
        body = "{ this.fields.add(\$1); }"
    )
    addFunction(addFieldFn)

    // -- internal open fun build(): String
    val buildFn = KmFunctionWrapper(
        fn = KmFunction("build").also { f ->
            f.visibility = Visibility.INTERNAL
            f.modality = Modality.OPEN
            f.returnType = stringType()
        },
        body = buildMethodBody()
    )
    addFunction(buildFn)

    // -- private fun serializeValue(value: Any?): String
    val serializeValueFn = KmFunctionWrapper(
        fn = KmFunction("serializeValue").also { f ->
            f.visibility = Visibility.PRIVATE
            f.modality = Modality.FINAL
            f.returnType = stringType()
            f.valueParameters.add(
                KmValueParameter("value").also { p -> p.type = nullableAnyType() }
            )
        },
        body = serializeValueMethodBody()
    )
    addFunction(serializeValueFn)
}

// =========================================================================
// Method body generators
// =========================================================================

private fun buildMethodBody(): String = """
    {
        java.util.StringJoiner joiner = new java.util.StringJoiner(" ");
        java.util.Iterator it = this.fields.iterator();
        while (it.hasNext()) {
            joiner.add((String)it.next());
        }
        return joiner.toString();
    }
""".trimIndent()

private fun serializeValueMethodBody(): String = """
    {
        if ($1 == null) return "null";
        if ($1 instanceof java.lang.String) {
            java.lang.String s = (java.lang.String)$1;
            s = s.replace("\\", "\\\\");
            s = s.replace("\"", "\\\"");
            return "\"" + s + "\"";
        }
        if ($1 instanceof java.lang.Boolean) return $1.toString();
        if ($1 instanceof java.lang.Number) return $1.toString();
        if ($1 instanceof java.lang.Enum) return ((java.lang.Enum)$1).name();
        if ($1 instanceof java.util.Map) {
            java.util.StringJoiner sj = new java.util.StringJoiner(", ", "{", "}");
            java.util.Iterator it = ((java.util.Map)$1).entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map${'$'}Entry entry = (java.util.Map${'$'}Entry)it.next();
                sj.add(entry.getKey().toString() + ": " + serializeValue(entry.getValue()));
            }
            return sj.toString();
        }
        if ($1 instanceof java.util.List) {
            java.util.StringJoiner sj = new java.util.StringJoiner(", ", "[", "]");
            java.util.Iterator it = ((java.util.List)$1).iterator();
            while (it.hasNext()) {
                sj.add(serializeValue(it.next()));
            }
            return sj.toString();
        }
        return $1.toString();
    }
""".trimIndent()

// =========================================================================
// Type helpers used internally
// =========================================================================

private fun nullableAnyType() = io.github.graphdsl.codegen.utils.Km.ANY.asNullableType()

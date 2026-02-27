package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.kmType
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.KmName
import io.github.graphdsl.schema.GraphDslSchema
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmVariance
import kotlinx.metadata.jvm.annotations

/**
 * Shared KmName constants for DSL bytecode generation.
 */
object DslKmNames {
    val FUNCTION1 = KmName("kotlin/Function1")
    val UNIT = Km.UNIT
    val STRING = Km.STRING
    val MUTABLE_LIST = Km.MUTABLE_LIST
    val ANY = Km.ANY
}

/**
 * Builds a `Function1<T, Unit>` KmType representing a Kotlin receiver lambda `(T.() -> Unit)`.
 *
 * At the JVM level, `(T.() -> Unit)` becomes `Function1<T, Unit>` where T is the receiver.
 * The `@kotlin.ExtensionFunctionType` annotation on the type tells the Kotlin compiler to treat
 * this as an extension receiver lambda, so callers can write `builder { field }` instead of
 * `{ builder -> builder.field }`.
 */
fun kmFunctionType(receiverType: KmType): KmType =
    KmType().also { t ->
        t.classifier = KmClassifier.Class(DslKmNames.FUNCTION1.toString())
        t.arguments.add(KmTypeProjection(KmVariance.INVARIANT, receiverType))
        t.arguments.add(KmTypeProjection(KmVariance.INVARIANT, DslKmNames.UNIT.asType()))
        // Mark as extension function type (T.() -> Unit not (T) -> Unit).
        // The JVM-level representation is the same (Function1<T, Unit>), but Kotlin metadata
        // encodes the distinction via this type annotation, which the compiler reads to inject
        // the receiver into the lambda scope.
        t.annotations.add(KmAnnotation("kotlin/ExtensionFunctionType", emptyMap()))
    }

/**
 * Converts a simple class name to a KmName in the given package.
 *
 * Example: `"Foo".toPkgKmName("com.example")` → `KmName("com/example/Foo")`
 */
fun String.toPkgKmName(pkg: String): KmName =
    KmName("${pkg.replace('.', '/')}/$this")

/**
 * Converts a KmName to a JVM class descriptor.
 *
 * Example: `KmName("com/example/Foo")` → `"Lcom/example/Foo;"`
 */
fun KmName.asJvmDesc(): String = "L${this.toString().replace('.', '$')};"

/**
 * Maps a GraphDslSchema TypeDef to the KmName of its DSL builder class.
 */
fun GraphDslSchema.TypeDef.toDslBuilderKmName(pkg: String): KmName =
    "${name}DslBuilder".toPkgKmName(pkg)

/**
 * Returns a nullable KmType for kotlin.String (i.e., `String?`).
 */
fun nullableStringType(): KmType = Km.STRING.asNullableType()

/**
 * Returns a non-nullable KmType for kotlin.String.
 */
fun stringType(): KmType = Km.STRING.asType()

/**
 * Returns a KmType for MutableList<String>.
 */
fun mutableListOfStringType(): KmType =
    KmType().also { t ->
        t.classifier = KmClassifier.Class(Km.MUTABLE_LIST.toString())
        t.arguments.add(KmTypeProjection(KmVariance.INVARIANT, Km.STRING.asType()))
    }

/**
 * Returns a KmType for Map<String, Any?>.
 */
fun mapStringAnyNullableType(): KmType =
    KmType().also { t ->
        t.classifier = KmClassifier.Class(Km.MAP.toString())
        t.arguments.add(KmTypeProjection(KmVariance.INVARIANT, Km.STRING.asType()))
        t.arguments.add(KmTypeProjection(KmVariance.OUT, Km.ANY.asNullableType()))
    }

/**
 * Returns the fully-qualified JVM class name for a generated DSL builder.
 * (dot-separated, for use in Javassist method bodies)
 */
fun dslBuilderJavaName(pkg: String, typeName: String): String = "$pkg.${typeName}DslBuilder"

/**
 * Returns the fully-qualified JVM class name for a generated Input builder.
 */
fun inputBuilderJavaName(pkg: String, typeName: String): String = "$pkg.${typeName}Builder"

/**
 * Returns the KmType for a field argument, respecting Input type mapping.
 *
 * - Input types → `Map<String, Any?>`
 * - Other types → proper Kotlin type via [BaseTypeMapper]
 */
fun argKmType(
    arg: GraphDslSchema.HasDefaultValue,
    pkg: String,
    baseTypeMapper: BaseTypeMapper,
): KmType {
    val baseType = arg.type.baseTypeDef
    if (baseType is GraphDslSchema.Input) {
        return mapStringAnyNullableType()
    }
    return arg.kmType(io.github.graphdsl.utils.codegen.JavaName(pkg).asKmName, baseTypeMapper)
}

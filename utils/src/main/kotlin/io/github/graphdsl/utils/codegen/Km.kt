package io.github.graphdsl.utils.codegen

import kotlinx.metadata.*

// KmType extensions and related utilities
object Km {
    val ANY = KmName("kotlin/Any")
    val LIST = KmName("kotlin/collections/List")
    val STRING = KmName("kotlin/String")
}

val KmType.name: KmName
    get() =
    when (classifier) {
        is KmClassifier.Class -> KmName((classifier as KmClassifier.Class).name)
        else -> throw IllegalArgumentException("Can't handle $this")
    }



val KmType.refs: Set<KmName> get() = setOf(this.name) + arguments.flatMap { it.refs }
val KmTypeProjection.refs: Set<KmName> get() = type?.refs ?: emptySet()

val KmAnnotationArgument.refs: Set<KmName> get() = when (this) {
    is KmAnnotationArgument.AnnotationValue -> this.annotation.refs
    is KmAnnotationArgument.KClassValue -> setOf(KmName(this.className))
    is KmAnnotationArgument.ArrayKClassValue -> setOf(KmName(this.className))
    is KmAnnotationArgument.ArrayValue -> this.elements.flatMap { it.refs }.toSet()
    is KmAnnotationArgument.EnumValue -> setOf(KmName(this.enumClassName))
    else -> emptySet()
}

val KmAnnotation.refs: Set<KmName> get() =
    this.arguments.flatMap { it.value.refs }.toSet() + KmName(this.className)
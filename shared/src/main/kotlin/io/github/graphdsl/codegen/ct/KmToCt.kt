@file:Suppress("UNUSED_PARAMETER")

package io.github.graphdsl.codegen.ct

import javassist.ClassPool
import javassist.CtClass
import javassist.bytecode.AccessFlag
import javassist.bytecode.InnerClassesAttribute
import kotlinx.metadata.ClassKind
import kotlinx.metadata.KmClass
import kotlinx.metadata.isInner
import kotlinx.metadata.jvm.JvmMetadataVersion
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.kind
import io.github.graphdsl.codegen.utils.DEFAULT_IMPLS
import io.github.graphdsl.codegen.utils.INVISIBLE
import io.github.graphdsl.codegen.utils.JavaBinaryName
import io.github.graphdsl.codegen.utils.JavaName
import io.github.graphdsl.codegen.utils.KmName
import io.github.graphdsl.codegen.utils.VISIBLE

fun buildCtClasses(
    pool: ClassPool,
    kmClassTrees: Iterable<KmClassTree>,
    externWrappers: Iterable<ExternalClassWrapper>,
    importedClasses: Iterable<JavaName>,
    classFileMajorVersion: Int?,
): Iterable<CtClass> {
    // We first "prime" the class pool by making all the classes we'll either
    // need to generate, or the external references that will be referenced
    // by those.  We start with the external ones, then do the outer ones,
    // then the nested ones.
    for (wrapper in externWrappers) {
        primeExternalClass(pool, wrapper)
    }
    for (tree in kmClassTrees) {
        primeClassTree(pool, tree)
    }

    // Imports for Javassist's built-in compiler
    for (importedClass in importedClasses) {
        pool.importPackage(importedClass.toString())
    }

    // Now generate actual bytecode
    val ctCtx = CtGenContext(pool)
    val results = mutableMapOf<KmName, Pair<KmClassWrapper, CtClass>>()

    kmClassTrees.mapWithOuter { tree, outer ->
        if (tree.cls.tier == 0) {
            val built = ctCtx.kmToCt(tree.cls, outer?.cls)
            results[tree.cls.kmClass.kmName] = tree.cls to built
        }
    }

    kmClassTrees.mapWithOuter { tree, outer ->
        if (tree.cls.tier == 1
        ) {
            val built = ctCtx.kmToCt(tree.cls, outer?.cls)
            results[tree.cls.kmClass.kmName] = tree.cls to built
        }
    }

    ctCtx.compileCompilables()

    // updating InnerClassesAttribute depends on having compiled method bodies
    updateAttributes(pool, results)

    return kmClassTrees
        .flatten()
        .flatMap {
            val className = KmName(it.kmClass.name).asJavaBinaryName
            val c = ctCtx.getClass(className)
            if (c.isInterface) {
                // DefaultImpls classes are created on the fly, and don't have an associated KmClassWrapper
                val defaultImpls = ctCtx.getClassOrNull(JavaBinaryName("$className$$DEFAULT_IMPLS"))
                if (defaultImpls != null) return@flatMap listOf(c, defaultImpls)
            }
            listOf(c)
        }.also { it ->
            if (classFileMajorVersion != null) {
                it.forEach { it.classFile.majorVersion = classFileMajorVersion }
            }
        }
}

private fun primeExternalClass(
    pool: ClassPool,
    wrapper: ExternalClassWrapper,
) {
    val cls = if (wrapper.isInterface) {
        pool.makeInterface(wrapper.name.toString())
    } else {
        pool.makeClass(wrapper.name.toString())
    }

    fun buildNested(
        nesteds: List<ExternalClassWrapper.Nested>,
        outer: CtClass,
    ) {
        nesteds.forEach { n ->
            val nc = outer.makeNestedClassFixed(n.nestedName.toString(), n.flags)
            buildNested(n.nested, nc)
        }
    }

    buildNested(wrapper.nested, cls)
}

private fun primeClassTree(
    pool: ClassPool,
    tree: KmClassTree,
) {
    // build nested class hierarchies from the outside in
    fun build(
        trees: Iterable<KmClassTree>,
        outer: CtClass?,
    ) {
        trees.forEach { tree ->
            val newOuter = makeClass(pool, tree.cls.kmClass, outer)
            build(tree.nested, newOuter)
        }
    }

    build(listOf(tree), null)
}

// Internal implementation

private fun makeClass(
    pool: ClassPool,
    kmClass: KmClass,
    ctOuter: CtClass?,
): CtClass {
    if (kmClass.isInner) {
        val n = "${ctOuter?.name}$${kmClass.simpleName}"
        throw IllegalArgumentException("Only static nested classes are supported ($n).")
    }
    val result: CtClass =
        if (kmClass.kind == ClassKind.INTERFACE) {
            if (ctOuter != null) {
                throw IllegalArgumentException("Can't handle nested interfaces (${kmClass.name} in ${ctOuter.name}).")
            }
            pool.makeInterface(kmClass.kmName.asCtName.toString())
        } else ctOuter?.makeNestedClassFixed(kmClass.simpleName, kmClass.jvmAccessFlags)
            ?: pool.makeClass(kmClass.kmName.asCtName.toString())

    result.classFile.removeAttribute("SourceFile")
    return result
}

internal fun CtClass.makeNestedClassFixed(
    name: String,
    accFlags: Int,
    superclass: CtClass? = null,
): CtClass {
    val cf = classFile
    val ica = innerClassesAttribute ?: InnerClassesAttribute(cf.constPool).also(cf::addAttribute)

    val nestedClass = classPool.makeClass("${this.name}$$name", superclass)
    //  ^^ Javassist calls ClassPool.makeNestedClass, but this call is equivalent
    val cf2 = nestedClass.classFile
    val ica2 = InnerClassesAttribute(cf2.constPool).also(cf2::addAttribute)

    cf2.accessFlags = cf2.accessFlags or accFlags or AccessFlag.STATIC
    //  ^ cf2.accessFlags has ACC_SUPER on by default.  Javassist seems to turn that off for nested classes
    //  but looking the Kotlin compiler sets this flag, so we will too

    // write edge to both classes
    val edge = NestEdge(nestedClass, this, name, Ct.STATIC_PUBLIC_FINAL)
    edge.write(ica)
    edge.write(ica2)

    return nestedClass
}

private fun CtGenContext.kmToCt(
    kmClassWrapper: KmClassWrapper,
    outer: KmClassWrapper?,
): CtClass =
    withContext(kmClassWrapper.kmClass.name) {
        val result =
            when (kmClassWrapper.kmClass.kind) {
                ClassKind.INTERFACE -> kmToCtInterface(kmClassWrapper, outer)
                ClassKind.ENUM_CLASS -> kmToCtEnum(kmClassWrapper, outer)
                ClassKind.CLASS -> kmToCtClass(kmClassWrapper)
                ClassKind.OBJECT -> kmToCtObject(kmClassWrapper)
                else -> throw IllegalArgumentException("Can't handle $kmClassWrapper")
            }
        result
    }

private fun updateAttributes(
    pool: ClassPool,
    results: Map<KmName, Pair<KmClassWrapper, CtClass>>,
) {
    results.values.forEach { (wrapper, cls) ->
        val kmMetadataAnnotation =
            cls.asCtAnnotation(
                KotlinClassMetadata.Class(wrapper.kmClass, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0).write()
            )
        val cp = cls.classFile.constPool

        cls.classFile.addAttribute(
            wrapper
                .annotationsAttribute(
                    cp,
                    VISIBLE,
                    notNull = true
                )!!
                .also { it.addAnnotation(kmMetadataAnnotation) }
        )
        wrapper.annotationsAttribute(cp, INVISIBLE)?.let { cls.classFile.addAttribute(it) }
    }

    rebuildInnerClassesAttribute(pool, results)
}

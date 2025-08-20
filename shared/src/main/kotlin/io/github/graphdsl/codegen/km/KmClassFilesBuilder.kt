package io.github.graphdsl.codegen.km

import java.io.File
import javassist.ClassPool
import javassist.CtClass
import kotlinx.metadata.ClassKind
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmType
import kotlinx.metadata.kind
import graphdsl.codegen.ct.ExternalClassWrapper
import graphdsl.codegen.ct.KmClassTree
import graphdsl.codegen.ct.buildCtClasses
import graphdsl.codegen.ct.flatten
import graphdsl.codegen.ct.mapWithOuter
import graphdsl.codegen.utils.JavaIdName
import graphdsl.codegen.utils.JavaName
import graphdsl.codegen.utils.Km
import graphdsl.codegen.utils.KmName
import graphdsl.codegen.utils.name

/** This class is the entry point into the code generator.  Through an instance
 *  of	this class you get access	to `Builders` of various kinds,	which allow
 *  you	to build up data structures that can be	converted (by calling
 *  [buildClassfiles]) into class files.
 */
class KmClassFilesBuilder(
    val classFileMajorVersion: Int? = null,
) {
    /**
     * If the generated code only references a class by name (no member access) and we want to avoid having to
     * pull it in as a dependency when generating the Java class, add it here.
     */
    fun addExternalClassReference(
        kmName: KmName,
        isInterface: Boolean = false,
        nested: List<JavaIdName> = emptyList(),
    ) {
        externalClassWrappers.put(
            kmName,
            ExternalClassWrapper(
                kmName.asJavaBinaryName,
                isInterface,
                nested.map { ExternalClassWrapper.Nested(it) }
            )
        )
    }

    private val externalClassWrappers: MutableMap<KmName, ExternalClassWrapper> = mutableMapOf()

    /**
     * Add additional imports to include here, e.g. java.util.Arrays
     */
    fun addImportedClass(import: JavaName) = importedClasses.add(import)

    private val importedClasses = mutableSetOf<JavaName>()

    // *** The public functions below are the main API. *** //

    private val classBuilders = mutableListOf<ClassBuilder>()
    private val classTrees: List<KmClassTree> by lazy {
        classBuilders.map { it.build() }
    }

    fun addBuilder(builder: ClassBuilder) {
        classBuilders.add(builder)
    }

    fun enumClassBuilder(
        kmName: KmName,
        values: List<String>,
        tier: Int = 1,
    ) = EnumClassBuilder(kmName, values, tier).also {
        classBuilders.add(it)
    }

    fun dataClassBuilder(
        kmName: KmName,
        tier: Int = 1,
    ) = DataClassBuilder(kmName, tier).also {
        classBuilders.add(it)
    }

    fun customClassBuilder(
        kmKind: ClassKind,
        kmName: KmName,
        annotations: Set<Pair<KmAnnotation, Boolean>> = emptySet(),
        tier: Int = 1,
    ) = CustomClassBuilder(kmKind, kmName, annotations, tier = tier).also {
        classBuilders.add(it)
    }

    private fun buildBytecode(pool: ClassPool): Iterable<CtClass> =
        buildCtClasses(
            pool,
            classTrees,
            externalClassWrappers.values,
            importedClasses,
            classFileMajorVersion,
        )

    /** Internal for testing. */
    internal lateinit var classPool: ClassPool

    fun buildClassfiles(outputRoot: File) {
        classPool = ClassPool(true)
        for (ctClass in buildBytecode(classPool)) {
            try {
                ctClass.writeFile(outputRoot.toString())
            } catch (e: Exception) {
                throw RuntimeException("Error writing class file for ${ctClass.name}", e)
            }
        }
    }

    fun buildClassLoader(): ClassLoader {
        classPool = ClassPool(true)
        val ctClasses = buildBytecode(classPool)
        return object : ClassLoader(getSystemClassLoader()) {
            override fun findClass(name: String?): Class<*> {
                // Set `this` as the classloader, otherwise nested classes are not properly loaded
                return ctClasses.firstOrNull { it.name == name }?.toClass(this, null)
                    ?: throw ClassNotFoundException(name)
            }
        }
    }

    companion object {
        val EMPTY_KMTYPES = listOf<KmType>()
    }
}

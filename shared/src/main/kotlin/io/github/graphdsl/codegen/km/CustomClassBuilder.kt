package io.github.graphdsl.codegen.km

import io.github.graphdsl.codegen.ct.*
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.KmName
import kotlinx.metadata.*

class CustomClassBuilder constructor(
    private val kmKind: ClassKind,
    val kmName: KmName,
    private val classAnnotations: Set<Pair<KmAnnotation, Boolean>> = emptySet(),
    private val isNested: Boolean = false,
    private val isDataClass: Boolean = false,
    private val isOpen: Boolean = false,
    private val tier: Int = 1
) : ClassBuilder() {
    val kmType = kmName.asType()

    private val supertypes = mutableListOf<KmType>()
    private val constructors = mutableListOf<KmConstructorWrapper>()
    private val functions = mutableListOf<KmFunctionWrapper>()
    private val properties = mutableListOf<KmPropertyWrapper>()
    private val nestedClasses = mutableListOf<CustomClassBuilder>()
    private val enumEntries = mutableListOf<String>()

    init {
        if (tier != 0 && tier != 1) {
            throw IllegalArgumentException("Only tiers 0 and 1 are supported ($kmName: $tier)")
        }
        if (kmKind == ClassKind.OBJECT) {
            addObjectBoilerplate()
        }
    }

    // =========================================================================
    // Public fluent add* methods
    // =========================================================================

    fun addSupertype(type: KmType): CustomClassBuilder {
        supertypes.add(type)
        return this
    }

    fun addConstructor(wrapper: KmConstructorWrapper): CustomClassBuilder {
        constructors.add(wrapper)
        return this
    }

    fun addFunction(wrapper: KmFunctionWrapper): CustomClassBuilder {
        functions.add(wrapper)
        return this
    }

    fun addProperty(wrapper: KmPropertyWrapper): CustomClassBuilder {
        properties.add(wrapper)
        return this
    }

    fun addNestedClass(builder: CustomClassBuilder): CustomClassBuilder {
        nestedClasses.add(builder)
        return this
    }

    fun addEnumEntry(name: String): CustomClassBuilder {
        enumEntries.add(name)
        return this
    }

    // =========================================================================
    // Build
    // =========================================================================

    override fun build(): KmClassTree = buildInternal(null)

    private fun buildInternal(containingClass: KmClassWrapper?): KmClassTree {
        if (isNested && containingClass == null) {
            throw IllegalArgumentException("containingClass expected for nested class $kmName")
        } else if (!isNested && containingClass != null) {
            throw IllegalArgumentException(
                "Unexpected containingClass ${containingClass.kmClass.name} received for non-nested class $kmName"
            )
        }

        val kmClass =
            KmClass().also { it ->
                it.name = kmName.toString()
                it.visibility = Visibility.PUBLIC
                it.modality = when {
                    kmKind == ClassKind.INTERFACE -> Modality.ABSTRACT
                    isOpen -> Modality.OPEN
                    else -> Modality.FINAL
                }
                it.kind = kmKind
                it.hasAnnotations = classAnnotations.isNotEmpty()
                it.isData = isDataClass
                kmType.arguments.forEachIndexed { i, proj ->
                    val param = KmTypeParameter("T$i", i, proj.variance ?: KmVariance.INVARIANT).also {
                        proj.type?.let { t ->
                            it.upperBounds += t
                        }
                    }
                    it.typeParameters += param
                }
                if (supertypes.isEmpty()) {
                    it.supertypes.add(Km.ANY.asType())
                } else {
                    it.supertypes.addAll(supertypes)
                }
            }

        constructors.forEach {
            kmClass.constructors.add(it.constructor)
        }
        functions.forEach {
            kmClass.functions.add(it.function)
        }
        properties.forEach {
            kmClass.properties.add(it.property)
        }
        if (enumEntries.isNotEmpty()) {
            kmClass.enumEntries.addAll(enumEntries)
        }

        val cls =
            KmClassWrapper(
                kmClass,
                constructors,
                functions,
                properties,
                classAnnotations,
                tier
            )

        val nested =
            nestedClasses
                .map { it ->
                    it.buildInternal(cls).also {
                        cls.kmClass.nestedClasses.add(it.cls.kmClass.simpleName)
                    }
                }
        return KmClassTree(cls, nested)
    }

    private fun addObjectBoilerplate() {
        // add private constructor
        constructors.add(
            KmConstructorWrapper(
                KmConstructor().also {
                    it.visibility = Visibility.PRIVATE
                },
                body = "{}"
            )
        )
    }

    // =========================================================================
    // Companion factory methods
    // =========================================================================

    companion object {
        /**
         * Creates a builder for a regular Kotlin class.
         *
         * @param kmName The KmName of the class
         * @param isOpen True to make the class open (extensible), false for final
         * @param isNested True if this is a nested class inside another class
         */
        fun classBuilder(
            kmName: KmName,
            isOpen: Boolean = false,
            isNested: Boolean = false,
        ): CustomClassBuilder = CustomClassBuilder(
            kmKind = ClassKind.CLASS,
            kmName = kmName,
            isNested = isNested,
            isOpen = isOpen,
        )

        /**
         * Creates a builder for a Kotlin object (singleton).
         */
        fun objectBuilder(kmName: KmName): CustomClassBuilder = CustomClassBuilder(
            kmKind = ClassKind.OBJECT,
            kmName = kmName,
        )

        /**
         * Creates a builder for a Kotlin interface.
         */
        fun interfaceBuilder(kmName: KmName): CustomClassBuilder = CustomClassBuilder(
            kmKind = ClassKind.INTERFACE,
            kmName = kmName,
        )
    }
}

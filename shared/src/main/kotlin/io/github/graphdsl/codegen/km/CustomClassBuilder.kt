package io.github.graphdsl.codegen.km

import io.github.graphdsl.codegen.ct.*
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.KmName
import kotlinx.metadata.*

class CustomClassBuilder internal constructor(
    private val kmKind: ClassKind,
    val kmName: KmName,
    private val classAnnotations: Set<Pair<KmAnnotation, Boolean>> = emptySet(),
    private val isNested: Boolean = false,
    private val isDataClass: Boolean = false,
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
                it.modality = if (kmKind == ClassKind.INTERFACE) Modality.ABSTRACT else Modality.FINAL
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
}

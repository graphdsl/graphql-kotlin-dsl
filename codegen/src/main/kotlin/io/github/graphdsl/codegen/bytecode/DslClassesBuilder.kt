package io.github.graphdsl.codegen.bytecode

import io.github.graphdsl.codegen.BaseTypeMapper
import io.github.graphdsl.codegen.ct.asCtAnnotation
import io.github.graphdsl.codegen.ct.buildCtClasses
import io.github.graphdsl.codegen.requiresSelectionSet
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.schema.GraphDslSchema
import javassist.ClassPool
import javassist.CtClass
import javassist.CtNewMethod
import javassist.LoaderClassPath
import javassist.bytecode.AnnotationsAttribute
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.Modality
import kotlinx.metadata.Visibility
import kotlinx.metadata.declaresDefaultValue
import kotlinx.metadata.jvm.JvmMetadataVersion
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KmPackageParts
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.modality
import kotlinx.metadata.visibility
import java.io.File

/**
 * Orchestrator for generating GraphQL DSL `.class` bytecode files from a schema.
 *
 * This is the bytecode-generation parallel to [io.github.graphdsl.codegen.DslFilesBuilder].
 * Instead of generating `.kt` source files via ST4, it generates `.class` files directly
 * using kotlinx.metadata + Javassist.
 *
 * @param pkg The target package name for generated DSL classes
 * @param outputDir The root output directory where `.class` files are written
 * @param baseTypeMapper The type mapper for GraphQL to Kotlin type conversion
 */
class DslClassesBuilder(
    private val pkg: String,
    private val outputDir: File,
    private val baseTypeMapper: BaseTypeMapper,
) {
    /**
     * Generates all DSL bytecode classes from the provided GraphQL schema.
     */
    fun generate(schema: GraphDslSchema) {
        outputDir.mkdirs()

        // =========================================================================
        // Step 1: Collect all types that need builders (same logic as DslFilesBuilder)
        // =========================================================================

        val objectTypesNeedingBuilders = mutableSetOf<String>()
        val inputTypesNeedingBuilders = mutableSetOf<String>()
        val queryFieldsNeedingBuilders = mutableListOf<GraphDslSchema.Field>()
        val mutationFieldsNeedingBuilders = mutableListOf<GraphDslSchema.Field>()

        val queryType = schema.types[QUERY_TYPE]?.takeIf { it is GraphDslSchema.Object } as? GraphDslSchema.Object
        val mutationType = schema.types[MUTATION_TYPE]?.takeIf { it is GraphDslSchema.Object } as? GraphDslSchema.Object

        queryType?.let {
            collectNeededBuilders(it, objectTypesNeedingBuilders, inputTypesNeedingBuilders, schema)
            collectOperationFieldsWithInputs(it, queryFieldsNeedingBuilders)
        }
        mutationType?.let {
            collectNeededBuilders(it, objectTypesNeedingBuilders, inputTypesNeedingBuilders, schema)
            collectOperationFieldsWithInputs(it, mutationFieldsNeedingBuilders)
        }

        // =========================================================================
        // Step 2: Build CustomClassBuilder instances for each type
        // =============
        // ============================================================

        val trees = mutableListOf<io.github.graphdsl.codegen.ct.KmClassTree>()

        queryType?.let {
            trees.add(queryDslClassGen(pkg, it, baseTypeMapper).build())
        }
        mutationType?.let {
            trees.add(mutationDslClassGen(pkg, it, baseTypeMapper).build())
        }

        for (typeName in objectTypesNeedingBuilders) {
            val typeDef = schema.types[typeName]
            if (typeDef is GraphDslSchema.Object && typeName !in ROOT_TYPES) {
                trees.add(objectDslClassGen(pkg, typeDef, baseTypeMapper).build())
            }
        }

        for ((_, typeDef) in schema.types) {
            if (typeDef is GraphDslSchema.Interface) {
                val implementors = findImplementingTypes(schema, typeDef.name)
                if (implementors.isNotEmpty()) {
                    trees.add(nodeInterfaceDslClassGen(pkg, typeDef, implementors).build())
                    implementors.forEach { objectTypesNeedingBuilders.add(it.name) }
                }
            }
        }

        for (typeName in inputTypesNeedingBuilders) {
            val typeDef = schema.types[typeName]
            if (typeDef is GraphDslSchema.Input) {
                trees.add(inputDslClassGen(pkg, typeDef, baseTypeMapper).build())
            }
        }

        for (field in queryFieldsNeedingBuilders) {
            val returnType = field.type.baseTypeDef
            if (returnType.requiresSelectionSet()) {
                trees.add(queryFieldDslClassGen(pkg, field, returnType, baseTypeMapper).build())
            }
        }

        for (field in mutationFieldsNeedingBuilders) {
            val returnType = field.type.baseTypeDef
            if (returnType.requiresSelectionSet()) {
                trees.add(mutationFieldDslClassGen(pkg, field, returnType, baseTypeMapper).build())
            }
        }

        // =========================================================================
        // Step 3: Compile all class trees into CtClass objects
        // =========================================================================

        val pool = ClassPool(true)
        pool.appendClassPath(LoaderClassPath(Thread.currentThread().contextClassLoader))

        val ctClasses = buildCtClasses(
            pool = pool,
            kmClassTrees = trees,
            externWrappers = emptyList(),
            importedClasses = emptyList(),
            classFileMajorVersion = null,
        ).toMutableList()

        // =========================================================================
        // Step 4: Create file facade classes for query/mutation top-level functions
        // =========================================================================

        queryType?.let {
            ctClasses.add(createFileFacadeClass(pool, "QueryDslKt", "query", "QueryDslBuilder", pkg))
        }
        mutationType?.let {
            ctClasses.add(createFileFacadeClass(pool, "MutationDslKt", "mutation", "MutationDslBuilder", pkg))
        }

        // =========================================================================
        // Step 5: Write all CtClass files to the output directory
        // =========================================================================

        for (ctClass in ctClasses) {
            ctClass.writeFile(outputDir.absolutePath)
        }

        // =========================================================================
        // Step 6: Write META-INF/<name>.kotlin_module so the Kotlin compiler can
        // discover top-level functions from FileFacade (k=2) classes in the JAR.
        // Without this file, `import io.example.pkg.query` resolves to nothing even
        // though QueryDslKt.class with @Metadata(k=2) is on the compile classpath.
        // =========================================================================

        val facadeClassNames = mutableListOf<String>()
        val packageInternalName = pkg.replace('.', '/')
        queryType?.let { facadeClassNames.add("$packageInternalName/QueryDslKt") }
        mutationType?.let { facadeClassNames.add("$packageInternalName/MutationDslKt") }

        if (facadeClassNames.isNotEmpty()) {
            val kmModule = KmModule()
            val packageFqName = pkg
            kmModule.packageParts[packageFqName] = KmPackageParts(facadeClassNames, mutableMapOf())
            val moduleBytes = KotlinModuleMetadata(kmModule, JvmMetadataVersion.LATEST_STABLE_SUPPORTED).write()
            val metaInfDir = File(outputDir, "META-INF")
            metaInfDir.mkdirs()
            File(metaInfDir, "graphdsl-generated.kotlin_module").writeBytes(moduleBytes)
        }
    }

    // =========================================================================
    // File facade creation
    // =========================================================================

    /**
     * Creates a Kotlin file facade CtClass with a single top-level function.
     *
     * For example, `QueryDslKt` contains `fun query(name: String?, block: QueryDslBuilder.() -> Unit): String`.
     *
     * The generated static method body:
     * 1. Creates the DSL builder
     * 2. Invokes the user's block
     * 3. Returns the formatted operation string
     */
    private fun createFileFacadeClass(
        pool: ClassPool,
        facadeClassName: String,
        operationName: String,
        builderClassName: String,
        pkg: String,
    ): CtClass {
        val fqFacadeClass = "$pkg.$facadeClassName"
        val fqBuilderClass = "$pkg.$builderClassName"

        val ctClass = pool.makeClass(fqFacadeClass)
        ctClass.classFile.removeAttribute("SourceFile")

        // Add the static method: public static String query(String name, Function1 block)
        // Note: CtNewMethod.make uses declared parameter names, not $N indices.
        val methodBody = buildString {
            append("{\n")
            append("    $fqBuilderClass builder = new $fqBuilderClass();\n")
            append("    ((kotlin.jvm.functions.Function1)block).invoke(builder);\n")
            append("    String opNameSuffix = (name != null) ? \" \" + name : \"\";\n")
            append("    return \"$operationName\" + opNameSuffix + \" { \" + builder.build() + \" }\";\n")
            append("}")
        }

        val method = CtNewMethod.make(
            "public static String $operationName(String name, kotlin.jvm.functions.Function1 block) $methodBody",
            ctClass
        )
        ctClass.addMethod(method)

        // Generate the Kotlin $default synthetic method so callers can write query { } without
        // passing name explicitly, matching the source-generated fun query(name: String? = null, ...).
        // Kotlin's default-parameter convention: $default(p0, p1, ..., int mask, Object marker)
        // where bit N of mask means "parameter N was omitted, use its default value".
        val defaultMethodBody = buildString {
            append("{\n")
            append("    if ((mask & 1) != 0) { name = null; }\n")
            append("    return $operationName(name, block);\n")
            append("}")
        }
        val defaultMethod = CtNewMethod.make(
            "public static String ${operationName}\$default(String name, kotlin.jvm.functions.Function1 block, int mask, Object marker) $defaultMethodBody",
            ctClass
        )
        ctClass.addMethod(defaultMethod)

        // Build KmPackage metadata for the FileFacade annotation
        val kmPkg = KmPackage()
        val kmFn = KmFunction(operationName).also { f ->
            f.visibility = Visibility.PUBLIC
            f.modality = Modality.FINAL
            f.returnType = Km.STRING.asType()
            f.valueParameters.add(KmValueParameter("name").also { p ->
                p.type = Km.STRING.asNullableType()
                p.declaresDefaultValue = true
            })
            f.valueParameters.add(KmValueParameter("block").also { p ->
                p.type = kmFunctionType(builderClassName.toPkgKmName(pkg).asType())
            })
            // JVM signature for the static method
            f.signature = JvmMethodSignature(
                operationName,
                "(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)Ljava/lang/String;"
            )
        }
        kmPkg.functions.add(kmFn)

        // Write KotlinClassMetadata.FileFacade annotation
        val metadata = KotlinClassMetadata.FileFacade(
            kmPkg,
            JvmMetadataVersion.LATEST_STABLE_SUPPORTED,
            0
        ).write()

        val ctAnnotation = ctClass.asCtAnnotation(metadata)
        val annotationsAttr = AnnotationsAttribute(
            ctClass.classFile.constPool,
            AnnotationsAttribute.visibleTag
        )
        annotationsAttr.addAnnotation(ctAnnotation)
        ctClass.classFile.addAttribute(annotationsAttr)

        return ctClass
    }

    // =========================================================================
    // Type collection helpers (mirrored from DslFilesBuilder)
    // =========================================================================

    private fun collectNeededBuilders(
        typeDef: GraphDslSchema.Object,
        objectCollectors: MutableSet<String>,
        inputCollectors: MutableSet<String>,
        schema: GraphDslSchema,
    ) {
        for (field in typeDef.fields) {
            for (arg in field.args) {
                collectInputTypes(arg.type.baseTypeDef, inputCollectors, schema)
            }
            when (val returnType = field.type.baseTypeDef) {
                is GraphDslSchema.Object -> {
                    if (returnType.name !in ROOT_TYPES && objectCollectors.add(returnType.name)) {
                        collectNeededBuilders(returnType, objectCollectors, inputCollectors, schema)
                    }
                }
                is GraphDslSchema.Interface, is GraphDslSchema.Union -> {
                    objectCollectors.add(returnType.name)
                }
                else -> {}
            }
        }
    }

    private fun collectInputTypes(
        typeDef: GraphDslSchema.TypeDef,
        inputCollectors: MutableSet<String>,
        schema: GraphDslSchema,
    ) {
        if (typeDef is GraphDslSchema.Input && inputCollectors.add(typeDef.name)) {
            for (field in typeDef.fields) {
                collectInputTypes(field.type.baseTypeDef, inputCollectors, schema)
            }
        }
    }

    private fun collectOperationFieldsWithInputs(
        opType: GraphDslSchema.Object,
        fields: MutableList<GraphDslSchema.Field>,
    ) {
        for (field in opType.fields) {
            val hasInputArgs = field.args.any { it.type.baseTypeDef is GraphDslSchema.Input }
            val hasComplexReturn = field.type.baseTypeDef.requiresSelectionSet()
            if (hasInputArgs && hasComplexReturn) {
                fields.add(field)
            }
        }
    }

    private fun findImplementingTypes(
        schema: GraphDslSchema,
        interfaceName: String,
    ): List<GraphDslSchema.Object> =
        schema.types.values
            .filterIsInstance<GraphDslSchema.Object>()
            .filter { obj -> obj.supers.any { it.name == interfaceName } }

    companion object {
        private const val QUERY_TYPE = "Query"
        private const val MUTATION_TYPE = "Mutation"
        private val ROOT_TYPES = setOf("Query", "Mutation", "Subscription")
    }
}

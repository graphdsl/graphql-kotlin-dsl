package io.github.graphdsl.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import io.github.graphdsl.codegen.DslFilesBuilder
import io.github.graphdsl.codegen.ZipUtil.zipAndWriteDirectories
import io.github.graphdsl.mapper.GJSchemaRaw
import io.github.graphdsl.codegen.GraphDSLBaseTypeMapper
import io.github.graphdsl.utils.readTypesFromFiles
import java.io.File

/**
 * CLI command for generating Kotlin DSL query builders from GraphQL schema.
 *
 * This command generates type-safe Kotlin DSL code that allows users to build
 * GraphQL queries programmatically without string manipulation.
 *
 * Example generated usage:
 * ```kotlin
 * val query = query(name = "GetUser") {
 *     greeting
 *     author
 *     user(id = "123") {
 *         name
 *         email
 *     }
 * }
 * ```
 *
 * This is primarily designed to be invoked by the GraphDSL Gradle plugin.
 */
class KotlinDslGenerator : CliktCommand() {
    private val outputArchive: File? by option("--output_archive")
        .file(mustExist = false, canBeDir = false)

    private val generatedDir: File by option("--generated_directory")
        .file(mustExist = false, canBeFile = false).required()

    private val schemaFiles: List<File> by option("--schema_files")
        .file(mustExist = true, canBeDir = false).split(",").required()

    private val pkgForGeneratedClasses: String by option("--pkg_for_generated_classes")
        .default("io.github.graphdsl.dsl")

    override fun run() {
        if (generatedDir.exists()) generatedDir.deleteRecursively()
        generatedDir.mkdirs()


        val typeDefRegistry = readTypesFromFiles(schemaFiles)
        val schema = GJSchemaRaw.fromRegistry(typeDefRegistry)


        val baseTypeMapper = GraphDSLBaseTypeMapper(schema)

        val dslBuilder = DslFilesBuilder(
            pkg = pkgForGeneratedClasses,
            outputDir = generatedDir,
            baseTypeMapper = baseTypeMapper
        )

        dslBuilder.generate(schema)

        outputArchive?.let {
            it.zipAndWriteDirectories(generatedDir)
            generatedDir.deleteRecursively()
        }
    }

    object Main {
        @JvmStatic
        fun main(args: Array<String>) = KotlinDslGenerator().main(args)
    }
}
package io.github.graphdsl.gradle.task

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Gradle task that generates Kotlin DSL query builders from GraphQL schema files.
 *
 * Invokes [graphdsl.cli.KotlinDslGenerator] via JavaExec with the plugin's classpath.
 */
@CacheableTask
abstract class GenerateDslTask
@Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    init {
        group = "graphdsl"
        description = "Generate Kotlin DSL query builders from GraphQL schema."
    }

    /** Main class to invoke for code generation. */
    @get:Input
    abstract val mainClass: Property<String>

    /** Classpath containing the codegen CLI and its dependencies. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpath: ConfigurableFileCollection

    /** GraphQL schema files (.graphqls) to generate DSL from. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaFiles: ConfigurableFileCollection

    /** Kotlin package name for the generated DSL classes. */
    @get:Input
    abstract val packageName: Property<String>

    /** Output directory for generated Kotlin source files. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val schemaFilesList = schemaFiles.files.filter { it.exists() && it.extension == "graphqls" }
        if (schemaFilesList.isEmpty()) {
            logger.warn("No .graphqls schema files found. Skipping DSL generation.")
            return
        }

        execOperations.javaexec {
            classpath = this@GenerateDslTask.classpath
            mainClass.set(this@GenerateDslTask.mainClass.get())
            argumentProviders.add {
                listOf(
                    "--schema_files",
                    schemaFilesList.map(File::getAbsolutePath).sorted().joinToString(","),
                    "--pkg_for_generated_classes",
                    this@GenerateDslTask.packageName.get(),
                    "--generated_directory",
                    outputDirectory.get().asFile.absolutePath
                )
            }
        }
    }
}

package io.github.graphdsl.gradle

import java.io.File
import java.net.URLClassLoader
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

object GraphDslPluginCommon {

    /**
     * Extracts the classpath elements from the classloader of the given anchor class.
     * Used to pass the plugin's classpath to JavaExec tasks for codegen.
     */
    fun getClassPathElements(anchor: Class<*>): List<File> =
        (anchor.classLoader as? URLClassLoader)
            ?.urLs
            ?.mapNotNull { url -> runCatching { File(url.toURI()) }.getOrNull() }
            .orEmpty()

    fun Project.configureIdeaIntegration(generateTask: TaskProvider<*>) {
        pluginManager.apply("org.jetbrains.gradle.plugin.idea-ext")

        pluginManager.withPlugin("org.jetbrains.gradle.plugin.idea-ext") {
            val ideaExtension = extensions.findByType(IdeaModel::class.java)
            ideaExtension?.project?.settings {
                taskTriggers {
                    beforeSync(generateTask)
                }
            }
        }
    }
}

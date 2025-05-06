package conventions

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.*
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.vanniktech.maven.publish")
    signing
}

abstract class GraphDslPublishingExtension @Inject constructor(objects: ObjectFactory) {
    val artifactId: Property<String> = objects.property(String::class.java).convention("")
    val name: Property<String> = objects.property(String::class.java).convention("")
    val description: Property<String> = objects.property(String::class.java).convention("")
}

val graphDslPublishing = extensions.create<GraphDslPublishingExtension>("graphDslPublishing")

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    when {
        plugins.hasPlugin("java-platform") -> configure(JavaPlatform())
        plugins.hasPlugin("com.gradle.plugin-publish") -> configure(GradlePublishPlugin())
        else -> configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    }
}

// 🔑 Defer coordinates() until after the consumer has configured graphDslPublishing { ... }.
afterEvaluate {
    // Resolve lazily here (now it's safe to .get()).
    val resolvedArtifactId = graphDslPublishing.artifactId.get().ifBlank { project.name }
    val resolvedName = graphDslPublishing.name.get().ifBlank { project.name }.let { "GraphDSL :: $it" }
    val resolvedDescription = graphDslPublishing.description.get().ifBlank { "" }

    extensions.configure<MavenPublishBaseExtension> {
        coordinates(project.group.toString(), resolvedArtifactId, project.version.toString())

        pom {
            name.set(resolvedName)
            if (resolvedDescription.isNotBlank()) description.set(resolvedDescription) else description.set("GraphDSL library $resolvedArtifactId")

            url.set("https://github.com/graphdsl/graphdsl")
            licenses { license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }}
            developers { developer {
                id.set("graphdsl"); name.set("GraphDSL")
                email.set("mkristileka@gmail.com")
            }}
            scm {
                connection.set("scm:git:git://github.com/graphdsl/graphdsl.git")
                developerConnection.set("scm:git:ssh://github.com/graphdsl/graphdsl.git")
                url.set("https://github.com/graphdsl/graphdsl")
            }
        }
    }

    signing {
        val signingKeyId: String? by project
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        setRequired {
            gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
        }
        project.logger.lifecycle(publishing.publications.toString())
        publishing.publications.forEach { project.logger.lifecycle("Publication: ${it.name}") }
        publishing.publications.forEach { sign(it) }
    }
}
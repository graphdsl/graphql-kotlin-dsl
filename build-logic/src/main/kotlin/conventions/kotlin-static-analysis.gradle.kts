package conventions

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}


val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")


ktlint {
    version.set(libs.findVersion("ktlintVersion").get().requiredVersion)
    enableExperimentalRules.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(true)

    filter {
        exclude { element ->
            element.file.path.contains("/generated-sources/") ||
                    element.file.path.contains("/build/generated/") ||
                    element.file.name.contains("SchemaObjects")
        }
    }
}

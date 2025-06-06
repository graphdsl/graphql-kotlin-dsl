package io.github.graphdsl.utils

import graphql.parser.MultiSourceReader
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import java.io.File
import java.io.InputStreamReader
import java.io.Reader


fun readTypesFromFiles(inputFiles: List<File>): TypeDefinitionRegistry =
    readTypes(
        inputFiles,
        { file -> InputStreamReader(file.inputStream()) },
        { file -> file.path }
    )

private fun <T> readTypes(
    inputFiles: List<T>,
    toReader: (T) -> Reader,
    toPath: (T) -> String
): TypeDefinitionRegistry {
    val reader =
        MultiSourceReader
            .newMultiSourceReader()
            .apply {
                inputFiles.forEach {
                    this.reader(toReader(it), toPath(it))
                }
            }.trackData(true)
            .build()
    return SchemaParser().parse(reader)
}
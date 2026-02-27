package io.github.graphdsl.codegen.km

import io.github.graphdsl.codegen.ct.KmClassTree

/** The external API to this package returns these ClassBuilders to
 *  clients.  Subclasses allow those clients to populate a ClassBuilder
 *  but not to actually build one themselves - that's done internally. */
abstract class ClassBuilder {
    abstract fun build(): KmClassTree
}

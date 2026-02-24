package io.github.graphdsl.utils.codegen

import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmVariance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KmConstantsTest {

    @Test
    fun `STRING is kotlin-String`() {
        assertEquals("kotlin/String", Km.STRING.toString())
    }

    @Test
    fun `ANY is kotlin-Any`() {
        assertEquals("kotlin/Any", Km.ANY.toString())
    }

    @Test
    fun `LIST is kotlin-collections-List`() {
        assertEquals("kotlin/collections/List", Km.LIST.toString())
    }
}

class KmTypeExtensionsTest {

    private fun kmType(className: String) = KmType().also {
        it.classifier = KmClassifier.Class(className)
    }

    @Test
    fun `name extracts class name from KmType`() {
        val type = kmType("kotlin/String")
        assertEquals(KmName("kotlin/String"), type.name)
    }

    @Test
    fun `name throws for non-class classifier`() {
        val type = KmType().also { it.classifier = KmClassifier.TypeParameter(0) }
        assertFailsWith<IllegalArgumentException> { type.name }
    }

    @Test
    fun `refs on simple type contains only itself`() {
        val type = kmType("kotlin/String")
        assertEquals(setOf(KmName("kotlin/String")), type.refs)
    }

    @Test
    fun `refs on parameterized type includes outer and all type arguments`() {
        val inner = kmType("kotlin/String")
        val outer = kmType("kotlin/collections/List").also {
            it.arguments.add(KmTypeProjection(KmVariance.INVARIANT, inner))
        }
        assertEquals(
            setOf(KmName("kotlin/collections/List"), KmName("kotlin/String")),
            outer.refs
        )
    }

    @Test
    fun `refs on nested parameterized type includes all transitively`() {
        val innermost = kmType("kotlin/Int")
        val middle = kmType("kotlin/collections/List").also {
            it.arguments.add(KmTypeProjection(KmVariance.INVARIANT, innermost))
        }
        val outer = kmType("kotlin/collections/Map").also {
            it.arguments.add(KmTypeProjection(KmVariance.INVARIANT, middle))
        }
        assertTrue(KmName("kotlin/collections/Map") in outer.refs)
        assertTrue(KmName("kotlin/collections/List") in outer.refs)
        assertTrue(KmName("kotlin/Int") in outer.refs)
    }

    @Test
    fun `KmTypeProjection refs delegates to its type`() {
        val type = kmType("kotlin/String")
        val projection = KmTypeProjection(KmVariance.INVARIANT, type)
        assertEquals(setOf(KmName("kotlin/String")), projection.refs)
    }

    @Test
    fun `KmTypeProjection with null type returns empty refs`() {
        val projection = KmTypeProjection.STAR
        assertEquals(emptySet(), projection.refs)
    }
}

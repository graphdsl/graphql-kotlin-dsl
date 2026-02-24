package io.github.graphdsl.utils.codegen

import kotlinx.metadata.KmClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaNameTest {

    @Test
    fun `simple class name is valid`() {
        assertEquals("MyClass", JavaName("MyClass").toString())
    }

    @Test
    fun `fully qualified name is valid`() {
        assertEquals("com.example.MyClass", JavaName("com.example.MyClass").toString())
    }

    @Test
    fun `asKmName converts dots to slashes`() {
        assertEquals("com/example/MyClass", JavaName("com.example.MyClass").asKmName.toString())
    }

    @Test
    fun `single segment asKmName is unchanged`() {
        assertEquals("MyClass", JavaName("MyClass").asKmName.toString())
    }

    @Test
    fun `empty string is rejected`() {
        assertFailsWith<IllegalArgumentException> { JavaName("") }
    }

    @Test
    fun `name with hyphen is rejected`() {
        assertFailsWith<IllegalArgumentException> { JavaName("my-class") }
    }

    @Test
    fun `name starting with digit is rejected`() {
        assertFailsWith<IllegalArgumentException> { JavaName("1BadName") }
    }
}

class JavaBinaryNameTest {

    @Test
    fun `top-level class name is valid`() {
        assertEquals("com.example.MyClass", JavaBinaryName("com.example.MyClass").toString())
    }

    @Test
    fun `inner class with dollar sign is valid`() {
        val name = JavaBinaryName("com.example.Outer\$Inner")
        assertEquals("com.example.Outer\$Inner", name.toString())
    }

    @Test
    fun `asKmName converts dots to slashes and dollar to dot`() {
        assertEquals(
            "com/example/Outer.Inner",
            JavaBinaryName("com.example.Outer\$Inner").asKmName.toString()
        )
    }

    @Test
    fun `asKmName for top-level class converts dots to slashes`() {
        assertEquals("com/example/MyClass", JavaBinaryName("com.example.MyClass").asKmName.toString())
    }

    @Test
    fun `empty string is rejected`() {
        assertFailsWith<IllegalArgumentException> { JavaBinaryName("") }
    }

    @Test
    fun `name with hyphen is rejected`() {
        assertFailsWith<IllegalArgumentException> { JavaBinaryName("my-class") }
    }
}

class KmNameTest {

    @Test
    fun `simple name is valid`() {
        assertEquals("MyClass", KmName("MyClass").toString())
    }

    @Test
    fun `slash-separated package name is valid`() {
        assertEquals("com/example/MyClass", KmName("com/example/MyClass").toString())
    }

    @Test
    fun `inner class with dot separator is valid`() {
        assertEquals("com/example/Outer.Inner", KmName("com/example/Outer.Inner").toString())
    }

    @Test
    fun `append adds suffix to name`() {
        assertEquals("com/example/MyClass", KmName("com/example").append("/MyClass").toString())
    }

    @Test
    fun `asType creates KmType with correct class classifier`() {
        val type = KmName("kotlin/String").asType()
        val classifier = type.classifier as KmClassifier.Class
        assertEquals("kotlin/String", classifier.name)
    }

    @Test
    fun `empty string is rejected`() {
        assertFailsWith<IllegalArgumentException> { KmName("") }
    }

    @Test
    fun `name with hyphen is rejected`() {
        assertFailsWith<IllegalArgumentException> { KmName("my-class") }
    }
}

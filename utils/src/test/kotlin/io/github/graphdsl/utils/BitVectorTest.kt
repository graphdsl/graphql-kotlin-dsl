package io.github.graphdsl.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BitVectorTest {

    // =========================================================================
    // Construction
    // =========================================================================

    @Test
    fun `positive size initializes all bits to false`() {
        for (size in listOf(1, 63, 64, 65, 128)) {
            val bv = BitVector(size)
            assertEquals(size, bv.size)
            for (i in 0 until size) {
                assertFalse(bv.get(i), "Expected bit $i to be false for size $size")
            }
        }
    }

    @Test
    fun `negative size initializes all bits to true`() {
        for (size in listOf(1, 63, 64, 65, 128)) {
            val bv = BitVector(-size)
            assertEquals(size, bv.size)
            for (i in 0 until size) {
                assertTrue(bv.get(i), "Expected bit $i to be true for size $size")
            }
        }
    }

    @Test
    fun `size zero creates empty vector`() {
        val bv = BitVector(0)
        assertEquals(0, bv.size)
    }

    // =========================================================================
    // get(idx) and set(idx)
    // =========================================================================

    @Test
    fun `set and get single bit within first word`() {
        val bv = BitVector(64)
        bv.set(0)
        assertTrue(bv.get(0))
        assertFalse(bv.get(1))

        bv.set(63)
        assertTrue(bv.get(63))
    }

    @Test
    fun `set and get single bit beyond first word`() {
        val bv = BitVector(128)
        bv.set(64)
        assertTrue(bv.get(64))
        assertFalse(bv.get(63))
        assertFalse(bv.get(65))

        bv.set(127)
        assertTrue(bv.get(127))
    }

    @Test
    fun `get out of bounds throws IndexOutOfBoundsException`() {
        val bv = BitVector(4)
        assertFailsWith<IndexOutOfBoundsException> { bv.get(-1) }
        assertFailsWith<IndexOutOfBoundsException> { bv.get(4) }
    }

    @Test
    fun `set is fluent and returns the same instance`() {
        val bv = BitVector(10)
        val result = bv.set(3)
        assertTrue(result === bv)
    }

    // =========================================================================
    // get(idx, count) multi-bit
    // =========================================================================

    @Test
    fun `get zero bits always returns zero`() {
        val bv = BitVector(-64)
        assertEquals(0L, bv.get(0, 0))
        assertEquals(0L, bv.get(32, 0))
    }

    @Test
    fun `get all bits from all-true vector`() {
        val bv = BitVector(-64)
        assertEquals(-1L, bv.get(0, 64))
        assertEquals(0b111L, bv.get(0, 3))
    }

    @Test
    fun `get bits spanning word boundary`() {
        val bv = BitVector(128)
        bv.set(63)
        bv.set(64)
        val result = bv.get(63, 2)
        assertEquals(0b11L, result)
    }

    @Test
    fun `get with count exceeding remaining bits throws IndexOutOfBoundsException`() {
        val bv = BitVector(64)
        assertFailsWith<IndexOutOfBoundsException> { bv.get(0, 65) }
    }

    @Test
    fun `get with negative count throws IllegalArgumentException`() {
        val bv = BitVector(64)
        assertFailsWith<IllegalArgumentException> { bv.get(0, -1) }
    }

    // =========================================================================
    // lsr()
    // =========================================================================

    @Test
    fun `lsr decreases size by one`() {
        val bv = BitVector(8)
        bv.set(3)
        val shifted = bv.lsr()
        assertEquals(7, shifted.size)
    }

    @Test
    fun `lsr shifts bit at position 1 to position 0`() {
        val bv = BitVector(4)
        bv.set(1)
        val shifted = bv.lsr()
        assertEquals(3, shifted.size)
        assertTrue(shifted.get(0))
        assertFalse(shifted.get(1))
    }

    @Test
    fun `lsr on size zero returns same instance`() {
        val bv = BitVector(0)
        val result = bv.lsr()
        assertEquals(0, result.size)
    }

    @Test
    fun `lsr carries bit from second word into first word`() {
        val bv = BitVector(128)
        bv.set(64)
        val shifted = bv.lsr()
        assertEquals(127, shifted.size)
        assertTrue(shifted.get(63))
        assertFalse(shifted.get(62))
        assertFalse(shifted.get(64))
    }

    // =========================================================================
    // equals / hashCode / toString
    // =========================================================================

    @Test
    fun `vectors with same content are equal and have same hashCode`() {
        val a = BitVector(10).set(3).set(7)
        val b = BitVector(10).set(3).set(7)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `vectors with different sizes are not equal`() {
        assertNotEquals(BitVector(10), BitVector(11))
    }

    @Test
    fun `vectors with same size but different bits are not equal`() {
        assertNotEquals(BitVector(10).set(0), BitVector(10).set(1))
    }

    @Test
    fun `toString returns binary string with MSB first`() {
        val bv = BitVector(4)
        bv.set(0)
        assertEquals("0001", bv.toString())

        bv.set(3)
        assertEquals("1001", bv.toString())
    }

    @Test
    fun `toString for all-false vector is all zeros`() {
        assertEquals("0000", BitVector(4).toString())
    }

    @Test
    fun `toString for all-true vector is all ones`() {
        assertEquals("1111", BitVector(-4).toString())
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @Test
    fun `builder creates correct vector from added bits`() {
        val bv = BitVector.Builder()
            .add(0b101L, 3)
            .build()
        assertEquals(3, bv.size)
        assertTrue(bv.get(0))
        assertFalse(bv.get(1))
        assertTrue(bv.get(2))
    }

    @Test
    fun `builder handles adding bits across word boundary`() {
        val builder = BitVector.Builder()
        builder.add(-1L, 64)
        builder.add(1L, 1)
        val bv = builder.build()
        assertEquals(65, bv.size)
        for (i in 0 until 64) assertTrue(bv.get(i), "Expected bit $i to be true")
        assertTrue(bv.get(64))
    }

    @Test
    fun `builder add with zero count is a no-op`() {
        val bv = BitVector.Builder().add(0b111L, 0).build()
        assertEquals(0, bv.size)
    }

    @Test
    fun `builder cannot be reused after build`() {
        val builder = BitVector.Builder()
        builder.add(1L, 1)
        builder.build()
        assertFailsWith<IllegalStateException> { builder.build() }
    }

    @Test
    fun `builder result matches direct construction`() {
        val direct = BitVector(-3)
        val built = BitVector.Builder().add(0b111L, 3).build()
        assertEquals(direct, built)
    }
}

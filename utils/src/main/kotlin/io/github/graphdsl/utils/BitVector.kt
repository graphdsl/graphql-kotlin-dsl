package io.github.graphdsl.utils

import kotlin.math.min

class BitVector private constructor(
    val size: Int,
    private var bits: Long, // re-assigned in invert function
    // May be larger that (size+63)/64!!  so don't use its length
    private var extraBits: LongArray? = null
) {
    /**
     * Create a bit vector whose length is equal to the absolute value of the size parameter. If size
     * is positive, then the vector is initialized to all false values; if negative, then initialized
     * to all true.
     */
    constructor(vSize: Int) : this((if (0 <= vSize) vSize else -vSize), fragment(vSize)) {
        var size = vSize
        val len = (if (0 <= size) size else -size)
        if (len <= 64) {
            this.extraBits = null
        } else {
            val l = (len - 1) / 64
            this.extraBits = LongArray(l)
            if (size < 0) {
                for (i in 0 until l - 1) {
                    extraBits!![i] = -1L
                    size += 64
                }
                extraBits!![l - 1] = fragment(size + 64)
            }
        }
    }

    companion object {
        private fun fragment(size: Int): Long {
            if (0 <= size) return 0L
            if (size <= -64) return -1L
            return -1L and ((1L shl -size) - 1)
        }
    }

    private fun check(idx: Int) {
        if (idx !in 0 until size) throw IndexOutOfBoundsException("" + idx)
    }

    private fun check(
        idx: Int,
        count: Int
    ) {
        if (idx < 0) throw IndexOutOfBoundsException("" + idx)
        if (count < 0) throw IllegalArgumentException("" + size)
        if (size < idx + count) throw IndexOutOfBoundsException("$idx+$count")
        require(count in 0..64) { "" + count }
    }

    fun get(idx: Int): Boolean {
        check(idx)
        val result = (if (idx < 64) bits else extraBits!![(idx shr 6) - 1])
        return 0L != (result and (1L shl (idx and 63)))
    }

    fun get(
        idx: Int,
        count: Int
    ): Long {
        check(idx, count)
        if (count == 0) return 0L

        // Set bits in low word first
        var result = (if (idx < 64) bits else extraBits!![(idx shr 6) - 1])
        val startingBitInLo = (idx and 63)
        val mask = (if (count == 64) -1L else ((1L shl count) - 1))
        result = (result ushr startingBitInLo) and mask

        // Set bits in the high word if necessary
        if (64 < count + (idx and 63)) {
            val hi = ((idx - 64) + count - 1) shr 6
            val countInLo = 64 - startingBitInLo
            val countInHi = count - countInLo
            // Note: countInHi can never be 64 given the constraints (count ≤ 64, countInLo ≥ 1)
            // but we add a guard for defensive programming similar to line 72
            val hiMask = (if (countInHi == 64) -1L else ((1L shl countInHi) - 1))
            result = result or ((extraBits!![hi] and hiMask) shl countInLo)
        }

        return result
    }

    fun set(idx: Int): BitVector {
        check(idx)
        if (idx < 64) {
            bits = bits or (1L shl (idx and 63))
        } else {
            extraBits!![(idx shr 6) - 1] = extraBits!![(idx shr 6) - 1] or (1L shl (idx and 63))
        }
        return this
    }

    fun lsr(): BitVector {
        if (size == 0) return this
        var newBits = (bits ushr 1)
        if (64 < size) newBits = newBits or (extraBits!![0] shl 63)
        var newExtraBits: LongArray? = null
        val extraBitsLen = (size - 2) / 64
        if (0 < extraBitsLen) {
            newExtraBits = LongArray(extraBitsLen)
            for (i in 0 until extraBitsLen) {
                newExtraBits[i] = (extraBits!![i] ushr 1)
                if (i + 1 < extraBits!!.size) newExtraBits[i] = newExtraBits[i] or (extraBits!![i + 1] shl 63)
            }
        }
        return BitVector(size - 1, newBits, newExtraBits)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitVector) return false
        if (size != other.size) return false
        if (bits != other.bits) return false
        if (64 < size) {
            val len = (size - 1) / 64
            for (i in 0 until len) if (extraBits!![i] != other.extraBits!![i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 31 + (bits xor (bits ushr 32)).toInt()
        if (64 < size) {
            val len = (size - 1) / 64
            for (i in 0 until len) {
                val element = extraBits!![i]
                val elementHash = (element xor (element ushr 32)).toInt()
                result = 31 * result + elementHash
            }
        }
        return result
    }

    override fun toString(): String {
        val result = StringBuilder()
        var i = size - 1
        while (0 <= i) {
            result.append(if (get(i)) '1' else '0')
            i--
        }
        return result.toString()
    }

    class Builder {
        private var inserted: LongArray? = LongArray(4) // Initial capacity
        private var insertedSize: Int = 0 // Number of elements actually used
        private var buffer = 0L
        private var bitsInBuffer = 0

        private fun pushBuffer() {
            // Grow array if needed using exponential growth
            if (insertedSize == inserted!!.size) {
                val newCapacity = maxOf(8, inserted!!.size * 2)
                val tmp = LongArray(newCapacity)
                System.arraycopy(inserted!!, 0, tmp, 0, insertedSize)
                inserted = tmp
            }
            inserted!![insertedSize] = buffer
            insertedSize++
            buffer = 0L
            bitsInBuffer = 0
        }

        fun add(
            bits: Long,
            count: Int
        ): Builder {
            checkNotNull(inserted) { "Builder has already been built." }
            require(count in 0..64) { "Bad count arg ($count)." }
            if (count == 0) return this
            val firstInsertCount = min((64 - bitsInBuffer).toDouble(), count.toDouble()).toInt()
            val mask = (if (firstInsertCount == 64) -1L else (1L shl firstInsertCount) - 1)
            buffer = buffer or ((bits and mask) shl bitsInBuffer)
            bitsInBuffer += firstInsertCount
            if (bitsInBuffer == 64) pushBuffer()
            if (firstInsertCount < count) {
                val secondInsertCount = (count - firstInsertCount)
                buffer = buffer or ((bits ushr firstInsertCount) and ((1L shl secondInsertCount) - 1))
                bitsInBuffer += secondInsertCount
            }
            return this
        }

        fun build(): BitVector {
            checkNotNull(inserted) { "Builder has already been built." }
            val size = bitsInBuffer + 64 * insertedSize
            val bits: Long
            val extraBits: LongArray?
            if (size <= 64) {
                bits = (if (size < 64) buffer else inserted!![0])
                extraBits = null
            } else {
                // Trim array to actual size and rearrange
                bits = inserted!![0]
                val finalSize = if (bitsInBuffer > 0) insertedSize else insertedSize - 1
                val result = LongArray(finalSize)
                System.arraycopy(inserted!!, 1, result, 0, insertedSize - 1)
                if (bitsInBuffer > 0) {
                    result[finalSize - 1] = buffer
                }
                extraBits = result
            }
            inserted = null // prevent reuse
            return BitVector(size, bits, extraBits)
        }
    }

}
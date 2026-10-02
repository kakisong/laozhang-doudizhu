package com.kaynzhang.doudizhu.engine.ai

/**
 * Open-addressing Long → Int hash map without boxing. Key 0 is reserved as the empty marker.
 * Not thread-safe; every analyzer or search worker owns its own map.
 */
internal class LongIntMap(initialCapacity: Int = 1 shl 12) {
    private var keys = LongArray(capacityFor(initialCapacity))
    private var values = IntArray(keys.size)
    private var mask = keys.size - 1
    var size = 0
        private set

    fun get(key: Long, missing: Int): Int {
        var i = index(key)
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == 0L) return missing
            i = (i + 1) and mask
        }
    }

    fun put(key: Long, value: Int) {
        require(key != 0L)
        if ((size + 1) * 2 > keys.size) grow()
        var i = index(key)
        while (true) {
            val k = keys[i]
            if (k == key) {
                values[i] = value
                return
            }
            if (k == 0L) {
                keys[i] = key
                values[i] = value
                size++
                return
            }
            i = (i + 1) and mask
        }
    }

    fun clear() {
        keys.fill(0L)
        size = 0
    }

    private fun index(key: Long): Int = ((key * -0x61c8864680b583ebL) ushr 32).toInt() and mask

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size * 2)
        values = IntArray(keys.size)
        mask = keys.size - 1
        size = 0
        for (i in oldKeys.indices) if (oldKeys[i] != 0L) put(oldKeys[i], oldValues[i])
    }

    private companion object {
        fun capacityFor(n: Int): Int = Integer.highestOneBit(maxOf(16, n - 1)) shl 1
    }
}

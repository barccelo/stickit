package com.barccelo.stickit

/**
 * Reads raw GIF graphics-control delays without interpreting playback policy.
 * Some GIF encoders store 0 or 10 ms delays; viewers can silently clamp them
 * to 100 ms, explaining why the same GIF changes speed across applications.
 */
object GifFrameTiming {
    fun delaysCentiseconds(bytes: ByteArray): List<Int> {
        if (bytes.size < 14) return emptyList()
        val signature = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        if (signature != "GIF87a" && signature != "GIF89a") return emptyList()
        fun u(position: Int): Int = bytes[position].toInt() and 0xFF
        fun blocks(start: Int): Int {
            var p = start
            while (p < bytes.size) {
                val length = u(p++)
                if (length == 0) return p
                if (length > bytes.size - p) return -1
                p += length
            }
            return -1
        }
        fun colorTableSize(packed: Int): Int =
            if ((packed and 0x80) == 0) 0 else 3 * (1 shl ((packed and 7) + 1))

        var p = 13 + colorTableSize(u(10))
        if (p > bytes.size) return emptyList()
        val delays = ArrayList<Int>()
        var pending = 10
        while (p < bytes.size) {
            when (u(p)) {
                0x3B -> break
                0x21 -> {
                    if (p + 1 >= bytes.size) return emptyList()
                    val label = u(p + 1)
                    if (label == 0xF9) {
                        if (p + 7 >= bytes.size || u(p + 2) != 4 || u(p + 7) != 0) return emptyList()
                        pending = u(p + 4) or (u(p + 5) shl 8)
                        p += 8
                    } else {
                        p = blocks(p + 2)
                        if (p < 0) return emptyList()
                    }
                }
                0x2C -> {
                    if (p + 9 >= bytes.size) return emptyList()
                    val packed = u(p + 9)
                    p += 10 + colorTableSize(packed)
                    if (p >= bytes.size) return emptyList()
                    p = blocks(p + 1) // LZW minimum code size, then sub-blocks
                    if (p < 0) return emptyList()
                    delays.add(pending)
                    pending = 10
                }
                else -> return emptyList()
            }
        }
        return delays
    }

    /** Whichever app consumes the exported GIF must not clamp short frames. */
    fun effectiveDurationMs(rawCentiseconds: List<Int>): Int =
        rawCentiseconds.fold(0L) { total, cs -> total + cs.coerceAtLeast(2) * 10L }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    data class Sample(val timeMs: Int, val delayCentiseconds: Int)

    /**
     * Resample a variable-delay source to uniformly spaced display intervals,
     * keeping the total duration within one 10 ms GIF time unit.
     * Max 25 FPS; extended clips use fewer frames to bound work.
     */
    fun samples(durationMs: Int, frameCap: Int = 150): List<Sample> {
        require(durationMs > 0 && frameCap >= 2)
        val totalCs = ((durationMs + 5L) / 10L).toInt().coerceAtLeast(4)
        val count = ((totalCs + 3) / 4).coerceIn(2, minOf(frameCap, totalCs / 2))
        return List(count) { i ->
            val startCs = ((i.toLong() * totalCs) / count).toInt()
            val endCs = (((i + 1L) * totalCs) / count).toInt()
            Sample((i.toLong() * durationMs / count).toInt(),
                (endCs - startCs).coerceAtLeast(2))
        }
    }
}

package faith.mimikyumochi.core

import faith.mimikyumochi.core.rand.Rand

object Utils {
    fun lerp(delta: Double, start: Double, end: Double): Double {
        return start + delta * (end - start)
    }

    fun lerp2(deltaX: Double, deltaY: Double, val00: Double, val10: Double, val01: Double, val11: Double): Double {
        return lerp(deltaY,
            lerp(deltaX, val00, val10),
            lerp(deltaX, val01, val11)
        )
    }

    fun lerp3(deltaX: Double, deltaY: Double, deltaZ: Double, val000: Double, val100: Double, val010: Double,
              val110: Double, val001: Double, val101: Double, val011: Double, val111: Double): Double {
        return lerp(deltaZ,
            lerp2(deltaX, deltaY, val000, val100, val010, val110),
            lerp2(deltaX, deltaY, val001, val101, val011, val111))
    }

    fun clampedLerp(first: Double, second: Double, delta: Double): Double {
        if (delta < 0.0) return first
        if (delta > 1.0) return second
        return lerp(delta, first, second)
    }

    fun maintainPrecision(d: Double): Double {
        return d - lfloor(d / 3.3554432E7 + 0.5).toDouble() * 3.3554432E7
    }

    fun lfloor(d: Double): Long {
        val l = d.toLong()
        return if (d < l.toDouble()) l - 1L else l
    }
    fun grad(hash: Int, x: Double, y: Double, z: Double): Double {
        when (hash and 0xF) {
            0x0 -> return x + y
            0x1 -> return -x + y
            0x2 -> return x - y
            0x3 -> return -x - y
            0x4 -> return x + z
            0x5 -> return -x + z
            0x6 -> return x - z
            0x7 -> return -x - z
            0x8 -> return y + z
            0x9, 0xD -> return -y + z
            0xA -> return y - z
            0xB, 0xF -> return -y - z
            0xC -> return y + x
            0xE -> return y - x
            else -> return 0.0
        }
    }
    private val grad3 = arrayOf(
        1.0 to 1.0,  -1.0 to 1.0,  1.0 to -1.0, -1.0 to -1.0,
        1.0 to 0.0,  -1.0 to 0.0,  1.0 to 0.0,  -1.0 to 0.0,
        0.0 to 1.0,   0.0 to -1.0, 0.0 to 1.0,   0.0 to -1.0
    )
    fun grad2(hash: Int, x: Double, y: Double): Double {
        val h = Math.floorMod(hash, 12)
        return grad3[h].first * x + grad3[h].second * y
    }

    fun smoothStep(d: Double): Double {
        return d * d * d * (d * (d * 6.0 - 15.0) + 10.0)
    }

    fun toShuffledList(range: IntRange, rand: Rand): List<Int> {
        val result = range.toList().toIntArray()

        for (i in result.size downTo 2) {
            val swapTo = rand.nextInt(i)
            val temp = result[i - 1]
            result[i - 1] = result[swapTo]
            result[swapTo] = temp
        }

        return result.toList()
    }
}

package faith.mimikyumochi.core.terrain

import faith.mimikyumochi.core.noise.FloatMth

abstract class FloatTerrain(private val cellXZ: Int, protected val cellY: Int, protected val cellsY: Int) {
    private val invXZ = 1.0f / cellXZ
    private val invY = 1.0f / cellY

    protected abstract fun computeCornerColumn(x: Int, z: Int): FloatArray

    protected fun yGradient(y: Int, from: Int, to: Int, fromValue: Float, toValue: Float): Float {
        val factor = (toValue - fromValue) / (to - from).toFloat()
        return fromValue + (Math.min(Math.max(y, from), to) - from).toFloat() * factor
    }

    protected fun lerpConst(alpha: Float, first: Float, second: Float): Float = when (alpha) {
        0.0f -> first
        1.0f -> second
        else -> FloatMth.lerp(alpha, first, second)
    }

    private fun squeeze(v: Float): Float {
        val c = FloatMth.clamp(v, -1.0f, 1.0f)
        return c / 2.0f - c * c * c / 24.0f
    }

    private val cornerColumns = object : LinkedHashMap<Long, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FloatArray>) = size > 4096
    }

    private fun cornerColumn(cellX: Int, cellZ: Int): FloatArray {
        val key = (cellX.toLong() shl 32) or (cellZ.toLong() and 0xFFFFFFFFL)
        synchronized(cornerColumns) { cornerColumns[key] }?.let { return it }
        val column = computeCornerColumn(cellX * cellXZ, cellZ * cellXZ)
        synchronized(cornerColumns) { cornerColumns[key] = column }
        return column
    }

    fun densityColumn(x: Int, z: Int): FloatArray {
        val cellX = Math.floorDiv(x, cellXZ)
        val cellZ = Math.floorDiv(z, cellXZ)
        val localX = Math.floorMod(x, cellXZ)
        val localZ = Math.floorMod(z, cellXZ)

        val c00 = cornerColumn(cellX, cellZ)
        val c10 = cornerColumn(cellX + 1, cellZ)
        val c01 = cornerColumn(cellX, cellZ + 1)
        val c11 = cornerColumn(cellX + 1, cellZ + 1)

        val tz = localZ.toFloat() * invXZ
        val tx = localX.toFloat() * invXZ
        val out = FloatArray(cellsY * cellY)

        for (cy in 0 until cellsY) {
            val a = FloatMth.lerp(tz, c00[cy], c01[cy])
            val b = FloatMth.lerp(tz, c00[cy + 1], c01[cy + 1])
            val c = FloatMth.lerp(tz, c10[cy], c11[cy])
            val d = FloatMth.lerp(tz, c10[cy + 1], c11[cy + 1])
            val lo = FloatMth.lerp(tx, a, c)
            val hi = FloatMth.lerp(tx, b, d)
            val step = (hi - lo) * invY
            var v = lo + step * 0.0f
            for (ly in 0 until cellY) {
                out[cy * cellY + ly] = squeeze(v)
                v += step
            }
        }
        return out
    }
}

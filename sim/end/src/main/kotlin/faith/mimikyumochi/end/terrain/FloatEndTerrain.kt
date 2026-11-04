package faith.mimikyumochi.end.terrain

import faith.mimikyumochi.core.noise.BlendedNoise
import faith.mimikyumochi.core.noise.FloatMth
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.end.biome.BiomeSource

class FloatEndTerrain(private val biomeSource: BiomeSource) {
    private val base3d: BlendedNoise

    init {
        val worldSeed = biomeSource.worldSeed
        base3d = BlendedNoise(Rand().apply { setSeed(worldSeed) }, 0.25, 0.25, 80.0, 160.0, 4.0)
    }

    private fun yGradient(y: Int, from: Int, to: Int, fromValue: Float, toValue: Float): Float {
        val factor = (toValue - fromValue) / (to - from).toFloat()
        return fromValue + (Math.min(Math.max(y, from), to) - from).toFloat() * factor
    }

    private fun lerpConst(alpha: Float, first: Float, second: Float): Float = when (alpha) {
        0.0f -> first
        1.0f -> second
        else -> FloatMth.lerp(alpha, first, second)
    }

    private fun cornerDensity(x: Int, y: Int, z: Int, islands: Float): Float {
        val slopedCheese = islands + base3d.sample(x, y, z)
        val bottom = lerpConst(yGradient(y, 56, 312, 1.0f, 0.0f), -23.4375f, slopedCheese)
        val top = lerpConst(yGradient(y, 4, 32, 0.0f, 1.0f), -0.234375f, bottom)
        return top * 0.64f
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
        val x = cellX * CELL_XZ
        val z = cellZ * CELL_XZ
        val islands = biomeSource.islandDensity(x, z)
        val column = FloatArray(CELLS_Y + 1) { cy -> cornerDensity(x, cy * CELL_Y, z, islands) }
        synchronized(cornerColumns) { cornerColumns[key] = column }
        return column
    }

    fun densityColumn(x: Int, z: Int): FloatArray {
        val cellX = Math.floorDiv(x, CELL_XZ)
        val cellZ = Math.floorDiv(z, CELL_XZ)
        val localX = Math.floorMod(x, CELL_XZ)
        val localZ = Math.floorMod(z, CELL_XZ)

        val c00 = cornerColumn(cellX, cellZ)
        val c10 = cornerColumn(cellX + 1, cellZ)
        val c01 = cornerColumn(cellX, cellZ + 1)
        val c11 = cornerColumn(cellX + 1, cellZ + 1)

        val tz = localZ.toFloat() * INV_XZ
        val tx = localX.toFloat() * INV_XZ
        val out = FloatArray(CELLS_Y * CELL_Y)

        for (cy in 0 until CELLS_Y) {
            val a = FloatMth.lerp(tz, c00[cy], c01[cy])
            val b = FloatMth.lerp(tz, c00[cy + 1], c01[cy + 1])
            val c = FloatMth.lerp(tz, c10[cy], c11[cy])
            val d = FloatMth.lerp(tz, c10[cy + 1], c11[cy + 1])
            val lo = FloatMth.lerp(tx, a, c)
            val hi = FloatMth.lerp(tx, b, d)
            val step = (hi - lo) * INV_Y
            var v = lo + step * 0.0f
            for (ly in 0 until CELL_Y) {
                out[cy * CELL_Y + ly] = squeeze(v)
                v += step
            }
        }
        return out
    }

    private companion object {
        const val CELL_XZ = 8
        const val CELL_Y = 4
        const val CELLS_Y = 32
        const val INV_XZ = 1.0f / 8
        const val INV_Y = 1.0f / 4
    }
}

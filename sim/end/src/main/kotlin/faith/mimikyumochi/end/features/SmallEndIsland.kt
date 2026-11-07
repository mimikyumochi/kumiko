package faith.mimikyumochi.end.structures

import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.rand.Xoroshiro128PlusPlus
import faith.mimikyumochi.core.version.MCVersion

class SmallEndIsland(val version: MCVersion = MCVersion.LATEST) {
    data class Island(val x: Int, val y: Int, val z: Int, val radius: Int, val layers: List<Float>) {
        fun isBlock(dx: Int, dy: Int, dz: Int): Boolean {
            val f = layers.getOrNull(-dy) ?: return false
            val lo = kotlin.math.floor(-f).toInt()
            val hi = kotlin.math.ceil(f).toInt()
            if (dx !in lo..hi || dz !in lo..hi) return false
            return (dx * dx + dz * dz).toFloat() <= (f + 1f) * (f + 1f)
        }

        val reach: Int get() = kotlin.math.ceil(layers.firstOrNull() ?: 0f).toInt()
    }

    val salt = 0
    val chance = 14

    fun getPlacedIslands(chunkX: Int, chunkZ: Int, biomeSource: EndBiomeSource): List<Island> {
        if (version < MCVersion.V1_18) {
            val biome = biomeSource.sampleBiome((chunkX shl 2) + 2, (chunkZ shl 2) + 2)
            return if (biome == BIOME) getIslands(chunkX, chunkZ, biomeSource.worldSeed) else emptyList()
        }
        return getIslands(chunkX, chunkZ, biomeSource.worldSeed) { x, y, z -> biomeSource.biomeAt(x, y, z) == BIOME }
    }

    fun getIslands(chunkX: Int, chunkZ: Int, worldSeed: Long, placed: ((Int, Int, Int) -> Boolean)? = null): List<Island> {
        val blockX = chunkX * 16
        val blockZ = chunkZ * 16

        if (version >= MCVersion.V1_18) {
            val xr = Xoroshiro128PlusPlus()
            xr.setDecoratorSeed(blockX, blockZ, worldSeed, salt)
            if (xr.nextFloat() >= 1f / chance) return emptyList()

            val count = if (xr.nextIntJ(4) == 3) 2 else 1
            val islands = ArrayList<Island>(count)
            repeat(count) {
                val x = blockX + xr.nextIntJ(16)
                val z = blockZ + xr.nextIntJ(16)
                val y = xr.nextIntJ(16) + 55
                if (placed == null || placed(x, y, z)) {
                    val r = xr.nextIntJ(3) + 4
                    islands += Island(x, y, z, r, shape(r) { xr.nextIntJ(2) })
                }
            }
            return islands
        }

        val rand = Rand()
        rand.setDecoratorSeed(worldSeed, blockX, blockZ, salt)

        if (version >= MCVersion.V1_17) {
            if (rand.nextFloat() >= 1f / chance) return emptyList()
            val second = rand.nextInt(4) == 0
            val x0 = blockX + rand.nextInt(16)
            val z0 = blockZ + rand.nextInt(16)
            val y0 = rand.nextInt(16) + 55
            val r0 = rand.nextInt(3) + 4
            val island0 = Island(x0, y0, z0, r0, shape(r0) { rand.nextInt(2) })
            if (!second) return listOf(island0)
            val x1 = blockX + rand.nextInt(16)
            val z1 = blockZ + rand.nextInt(16)
            val y1 = rand.nextInt(16) + 55
            val r1 = rand.nextInt(3) + 4
            return listOf(island0, Island(x1, y1, z1, r1, shape(r1) { rand.nextInt(2) }))
        }

        if (rand.nextInt(chance) != 0) return emptyList()
        val x0 = blockX + rand.nextInt(16)
        val y0 = rand.nextInt(16) + 55
        val z0 = blockZ + rand.nextInt(16)
        if (rand.nextInt(4) != 0) {
            val r = rand.nextInt(3) + 4
            return listOf(Island(x0, y0, z0, r, shape(r) { rand.nextInt(2) }))
        }
        val x1 = blockX + rand.nextInt(16)
        val y1 = rand.nextInt(16) + 55
        val z1 = blockZ + rand.nextInt(16)
        val r0 = rand.nextInt(3) + 4
        val island0 = Island(x0, y0, z0, r0, shape(r0) { rand.nextInt(2) })
        val r1 = rand.nextInt(3) + 4
        return listOf(island0, Island(x1, y1, z1, r1, shape(r1) { rand.nextInt(2) }))
    }

    companion object {
        const val BIOME = "small_end_islands"

        fun placedWith(x: Int, y: Int, z: Int, rand: Rand): Island {
            val r = rand.nextInt(3) + 4
            val layers = ArrayList<Float>()
            var f = r.toFloat()
            while (f > 0.5f) {
                layers += f
                f -= rand.nextInt(2) + 0.5f
            }
            return Island(x, y, z, r, layers)
        }
    }

    private inline fun shape(radius: Int, nextInt2: () -> Int): List<Float> {
        val layers = ArrayList<Float>()
        var r = radius.toFloat()
        while (r > 0.5f) {
            layers += r
            r -= nextInt2() + 0.5f
        }
        return layers
    }

    fun canStart(chunkX: Int, chunkZ: Int, biomeSource: EndBiomeSource): Boolean =
        getPlacedIslands(chunkX, chunkZ, biomeSource).isNotEmpty()
}

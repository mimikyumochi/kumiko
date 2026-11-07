package faith.mimikyumochi.nether.terrain

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.core.noise.FloatMth
import faith.mimikyumochi.core.terrain.NoiseSampling
import faith.mimikyumochi.core.terrain.TerrainGenerator
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.nether.biome.NetherBiomeSource

class NetherTerrainGenerator(
    override val biomeSource: NetherBiomeSource,
    version: MCVersion = biomeSource.version,
) : TerrainGenerator(
    worldSeed = biomeSource.worldSeed,
    version = version,
    sampling = NoiseSampling(xzScale = 1.0, yScale = 3.0, xzFactor = 80.0, yFactor = 60.0),
    worldHeight = 128,
    chunkWidth = 4,
    chunkHeight = 8,
) {
    private val floatTerrain: FloatNetherTerrain? =
        if (version >= MCVersion.V26_3) FloatNetherTerrain(worldSeed) else null

    private val caves = NetherCaves(worldSeed, version)

    private val densityBias = if (version >= MCVersion.V1_18) 0.0 else legacyDensityBias()

    private fun legacyDensityBias(): Double {
        var scaleSum = 0.0f
        var depthSum = 0.0f
        var weightSum = 0.0f
        for (dx in -2..2) for (dz in -2..2) {
            val weight = 10.0f / FloatMth.sqrt((dx * dx + dz * dz).toFloat() + 0.2f) / (BIOME_DEPTH + 2.0f)
            scaleSum += BIOME_SCALE * weight
            depthSum += BIOME_DEPTH * weight
            weightSum += weight
        }
        val depth = (depthSum / weightSum * 0.5f - 0.125f).toDouble() * 0.265625
        val scale = 96.0 / (scaleSum / weightSum * 0.9f + 0.1f).toDouble()
        return (DENSITY_OFFSET + depth) * scale
    }

    override fun computeNoiseColumn(x: Int, z: Int): DoubleArray = DoubleArray(noiseSizeY + 1) { y ->
        var noise = sampleNoise(x, y, z)
        noise += if (densityBias > 0.0) densityBias * 4.0 else densityBias
        noise = Utils.clampedLerp(120.0, noise, (noiseSizeY - y).toDouble() / 3.0)
        Utils.clampedLerp(320.0, noise, (y.toDouble() + 1.0) / 4.0)
    }

    fun baseColumn(x: Int, z: Int): List<Int> {
        val blocks = floatTerrain?.densityColumn(x, z)?.map { if (it > 0.0f) defaultBlock else defaultFluid }
            ?: super.generateColumn(x, z)
        return blocks.mapIndexed { y, block -> if (block == defaultFluid && y < SEA_LEVEL) LAVA else block }
    }

    override fun generateColumn(x: Int, z: Int): List<Int> {
        val mask = caves.carved(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
        return baseColumn(x, z).mapIndexed { y, block ->
            when {
                block != defaultBlock || !caves.isCarved(mask, x, y, z) -> block
                y < SEA_LEVEL -> LAVA
                else -> defaultFluid
            }
        }
    }

    companion object {
        const val LAVA = 2
        const val SEA_LEVEL = 32

        private const val BIOME_DEPTH = 0.1f
        private const val BIOME_SCALE = 0.2f
        private const val DENSITY_OFFSET = 0.019921875
    }
}

package faith.mimikyumochi.nether.biome

import faith.mimikyumochi.core.biome.BiomeSource
import faith.mimikyumochi.core.biome.ClimateBiome
import faith.mimikyumochi.core.noise.DoublePerlinNoise
import faith.mimikyumochi.core.noise.FloatNormalNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.version.MCVersion

class NetherBiomeSource(worldSeed: Long, version: MCVersion = MCVersion.LATEST) : BiomeSource(worldSeed, version) {
    init {
        require(version >= MCVersion.V1_16) { "nether biomes start at 1.16" }
    }

    private val temperature = climateNoise(worldSeed)
    private val humidity = climateNoise(worldSeed + 1L)

    private fun climateNoise(seed: Long): (Double, Double) -> Float {
        val rand = Rand().apply { setSeed(seed) }
        if (version >= MCVersion.V26_3) {
            val noise = FloatNormalNoise(rand, -7, 2, 0.9494731054427981)
            return { x, z -> noise.sample(x, z) }
        }
        val noise = DoublePerlinNoise(rand, listOf(-7, -6))
        return { x, z -> noise.sample(x, 0.0, z).toFloat() }
    }

    override fun sampleBiome(x: Int, y: Int, z: Int): String {
        val t = temperature(x.toDouble(), z.toDouble())
        val h = humidity(x.toDouble(), z.toDouble())
        val biome = if (version >= MCVersion.V1_18) BIOMES.minBy { it.quantizedFitness(t, h) } else BIOMES.minBy { it.fitness(t, h) }
        return biome.name
    }

    private companion object {
        val BIOMES = listOf(
            ClimateBiome("nether_wastes", 0.0f, 0.0f, 0.0f),
            ClimateBiome("soul_sand_valley", 0.0f, -0.5f, 0.0f),
            ClimateBiome("crimson_forest", 0.4f, 0.0f, 0.0f),
            ClimateBiome("warped_forest", 0.0f, 0.5f, 0.375f),
            ClimateBiome("basalt_deltas", -0.5f, 0.0f, 0.175f),
        )
    }
}

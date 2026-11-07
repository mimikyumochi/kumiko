package faith.mimikyumochi.end.terrain

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.core.terrain.NoiseSampling
import faith.mimikyumochi.core.terrain.TerrainGenerator
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.EndBiomeSource

class EndTerrainGenerator(
    override val biomeSource: EndBiomeSource,
    version: MCVersion = biomeSource.version,
) : TerrainGenerator(
    worldSeed = biomeSource.worldSeed,
    version = version,
    sampling = NoiseSampling(xzScale = 2.0, yScale = 1.0, xzFactor = 80.0, yFactor = 160.0),
    worldHeight = 128,
    chunkWidth = 8,
    chunkHeight = 4,
) {
    private val floatTerrain: FloatEndTerrain? =
        if (version >= MCVersion.V26_3) FloatEndTerrain(biomeSource) else null

    fun getDepthAndScale(x: Int, z: Int): DoubleArray {
        val height = biomeSource.getHeight(x, z)

        val depth = height - 8.0
        val scale = if (depth > 0.0) 0.25 else 1.0

        return doubleArrayOf(depth, scale)
    }

    override fun computeNoiseColumn(x: Int, z: Int): DoubleArray {
        val buffer = DoubleArray(noiseSizeY + 1)
        val (depth, scale) = getDepthAndScale(x, z)
        val fallOff = depth * scale

        for (y in 0..noiseSizeY) {
            var noise = sampleNoise(x, y, z)

            noise += if (fallOff > 0.0) fallOff * 4.0 else fallOff

            noise = Utils.clampedLerp(-3000.0, noise, ((noiseSizeY - y).toDouble() - -46.0) / 64.0)
            noise = Utils.clampedLerp(-30.0, noise, (y.toDouble() - 1.0) / 7.0)
            buffer[y] = noise
        }
        return buffer
    }

    override fun generateColumn(x: Int, z: Int): List<Int> {
        floatTerrain?.let { terrain ->
            return terrain.densityColumn(x, z).map { if (it > 0.0f) defaultBlock else defaultFluid }
        }
        return super.generateColumn(x, z)
    }
}

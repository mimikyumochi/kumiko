package faith.mimikyumochi.end.terrain

import faith.mimikyumochi.core.noise.BlendedNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.terrain.FloatTerrain
import faith.mimikyumochi.end.biome.EndBiomeSource

class FloatEndTerrain(private val biomeSource: EndBiomeSource) : FloatTerrain(cellXZ = 8, cellY = 4, cellsY = 32) {
    private val base3d = BlendedNoise(Rand().apply { setSeed(biomeSource.worldSeed) }, 0.25, 0.25, 80.0, 160.0, 4.0)

    private fun cornerDensity(x: Int, y: Int, z: Int, islands: Float): Float {
        val slopedCheese = islands + base3d.sample(x, y, z)
        val bottom = lerpConst(yGradient(y, 56, 312, 1.0f, 0.0f), -23.4375f, slopedCheese)
        val top = lerpConst(yGradient(y, 4, 32, 0.0f, 1.0f), -0.234375f, bottom)
        return top * 0.64f
    }

    override fun computeCornerColumn(x: Int, z: Int): FloatArray {
        val islands = biomeSource.islandDensity(x, z)
        return FloatArray(cellsY + 1) { cy -> cornerDensity(x, cy * cellY, z, islands) }
    }
}

package faith.mimikyumochi.nether.terrain

import faith.mimikyumochi.core.noise.BlendedNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.terrain.FloatTerrain

class FloatNetherTerrain(worldSeed: Long) : FloatTerrain(cellXZ = 4, cellY = 8, cellsY = 16) {
    private val base3d = BlendedNoise(Rand().apply { setSeed(worldSeed) }, 0.25, 0.375, 80.0, 60.0, 8.0)

    private fun cornerDensity(x: Int, y: Int, z: Int): Float {
        val top = lerpConst(yGradient(y, 104, 128, 1.0f, 0.0f), 0.9375f, base3d.sample(x, y, z))
        val bottom = lerpConst(yGradient(y, -8, 24, 0.0f, 1.0f), 2.5f, top)
        return bottom * 0.64f
    }

    override fun computeCornerColumn(x: Int, z: Int): FloatArray =
        FloatArray(cellsY + 1) { cy -> cornerDensity(x, cy * cellY, z) }
}

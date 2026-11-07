package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.rand.Rand

class DoublePerlinNoise(rand: Rand, octaves: List<Int>) {
    private val firstSampler = OctavePerlinNoise(rand, octaves)
    private val secondSampler = OctavePerlinNoise(rand, octaves)
    private val amplitude = 0.16666666666666666 / expectedDeviation(octaves.max() - octaves.min())

    private fun expectedDeviation(octaves: Int) = 0.1 * (1.0 + 1.0 / (octaves + 1).toDouble())

    fun sample(x: Double, y: Double, z: Double): Double {
        val d = x * INPUT_FACTOR
        val e = y * INPUT_FACTOR
        val f = z * INPUT_FACTOR
        return (firstSampler.sample(x, y, z, 0.0, 0.0, false) +
                secondSampler.sample(d, e, f, 0.0, 0.0, false)) * amplitude
    }

    companion object {
        const val INPUT_FACTOR = 1.0181268882175227
    }
}

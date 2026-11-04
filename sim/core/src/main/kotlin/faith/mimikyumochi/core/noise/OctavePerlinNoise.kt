package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.Utils.maintainPrecision
import faith.mimikyumochi.core.rand.Rand

class OctavePerlinNoise(rand: Rand, octaves: Int) {
    var lacunarity: Double = 0.0
    var persistence: Double = 0.0
    private var octaveSamplers: Array<PerlinNoise?>
    private var amplitudes: MutableList<Double?>? = null

    init {
        this.amplitudes = null
        this.octaveSamplers = arrayOfNulls(octaves)
        for (i in 0..<octaves) {
            this.octaveSamplers[i] = PerlinNoise(rand)
        }
        this.lacunarity = 1.0
        this.persistence = 1.0
    }

    fun sample(x: Double, y: Double, z: Double, yAmplification: Double, minY: Double, useDefaultY: Boolean): Double {
        var noise = 0.0
        var persistence = this.persistence
        var lacunarity = this.lacunarity

        for ((idx, element) in this.octaveSamplers.withIndex()) {
            val sampler: PerlinNoise? = element
            if (sampler != null) {
                val sample = sampler.sample(
                    maintainPrecision(x * persistence),
                    if (useDefaultY) -sampler.originY else maintainPrecision(y * persistence),
                    maintainPrecision(z * persistence),
                    yAmplification * persistence,
                    minY * persistence
                ) * lacunarity
                noise += (if (amplitudes != null) amplitudes!![idx] else 1.0)!! * sample
            }
            persistence /= 2.0
            lacunarity *= 2.0
        }

        return noise
    }

    fun getOctave(octave: Int): PerlinNoise {
        return this.octaveSamplers[octave]!!
    }
}

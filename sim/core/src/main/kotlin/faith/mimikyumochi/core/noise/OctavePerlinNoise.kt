package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.Utils.maintainPrecision
import faith.mimikyumochi.core.rand.Rand

class OctavePerlinNoise {
    var lacunarity: Double = 0.0
    var persistence: Double = 0.0
    private var octaveSamplers: Array<PerlinNoise?>
    private var amplitudes: MutableList<Double?>? = null

    constructor(rand: Rand, octaves: Int) {
        this.octaveSamplers = arrayOfNulls(octaves)
        for (i in 0..<octaves) {
            this.octaveSamplers[i] = PerlinNoise(rand)
        }
        this.lacunarity = 1.0
        this.persistence = 1.0
    }

    constructor(rand: Rand, octaves: List<Int>) {
        val sorted = octaves.sorted()
        val start = -sorted.first()
        val end = sorted.last()
        val length = start + end + 1
        require(length >= 1) { "total number of octaves needs to be >= 1" }

        val perlin = PerlinNoise(rand)
        octaveSamplers = arrayOfNulls(length)

        if (end in 0 until length && sorted.contains(0)) {
            octaveSamplers[end] = perlin
        }

        for (idx in end + 1 until length) {
            if (idx >= 0 && sorted.contains(end - idx)) {
                octaveSamplers[idx] = PerlinNoise(rand)
            } else {
                rand.advance(262)
            }
        }

        if (end > 0) {
            val noiseSeed = (perlin.sample(0.0, 0.0, 0.0, 0.0, 0.0) * 9.223372036854776E18).toLong()
            rand.setSeed(noiseSeed)
            for (index in end - 1 downTo 0) {
                if (index < length && sorted.contains(end - index)) {
                    octaveSamplers[index] = PerlinNoise(rand)
                } else {
                    rand.advance(262)
                }
            }
        }

        persistence = Math.pow(2.0, end.toDouble())
        lacunarity = 1.0 / (Math.pow(2.0, length.toDouble()) - 1.0)
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

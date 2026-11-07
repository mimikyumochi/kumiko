package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.rand.Rand
import kotlin.math.pow
import kotlin.math.sqrt

class FloatNormalNoise(rand: Rand, firstOctave: Int, octaveCount: Int, baseAmplitude: Double) {
    private class Layer(val noise: SmearedPerlinNoise, val frequency: Double, val amplitude: Float)

    private val layers: List<Layer>

    init {
        val stackAmplitude = (normalizationFactor(octaveCount, baseAmplitude) * baseAmplitude).toFloat()
        val first = fbm(rand, firstOctave, octaveCount)
        val second = fbm(rand, firstOctave, octaveCount)
        layers = first.map { Layer(it.noise, it.frequency * 1.0, it.amplitude * stackAmplitude) } +
            second.map { Layer(it.noise, it.frequency * DoublePerlinNoise.INPUT_FACTOR, it.amplitude * stackAmplitude) }
    }

    private fun fbm(rand: Rand, firstOctave: Int, octaveCount: Int): List<Layer> {
        val zeroOctave = -firstOctave
        val noises = arrayOfNulls<SmearedPerlinNoise>(octaveCount)
        val zero = SmearedPerlinNoise(rand, 1.0)
        if (zeroOctave in 0 until octaveCount) noises[zeroOctave] = zero
        for (octave in zeroOctave - 1 downTo 0) {
            if (octave < octaveCount) noises[octave] = SmearedPerlinNoise(rand, 1.0) else rand.advance(262)
        }
        var frequency = 2.0.pow(-zeroOctave)
        var value = 2.0.pow(octaveCount - 1) / (2.0.pow(octaveCount) - 1.0)
        return noises.map { noise ->
            Layer(noise!!, frequency, value.toFloat()).also {
                frequency *= 2.0
                value /= 2.0
            }
        }
    }

    private fun normalizationFactor(octaveCount: Int, baseAmplitude: Double): Double {
        var amplitude = baseAmplitude * (0.5.pow(-(octaveCount - 1)) / (0.5.pow(-octaveCount) - 1.0))
        var sum = 0.0
        var variance = 0.0
        repeat(octaveCount) {
            val deviation = 0.2702247831245211 * amplitude
            sum += amplitude
            variance += deviation * deviation
            amplitude *= 0.5
        }
        return sum * 0.3333333333333333 / (sqrt(variance) * sqrt(2.0))
    }

    fun sample(x: Double, z: Double): Float {
        var total = 0.0f
        for (layer in layers) {
            total += layer.amplitude * layer.noise.sample(x * layer.frequency, 0.0, z * layer.frequency)
        }
        return total
    }
}

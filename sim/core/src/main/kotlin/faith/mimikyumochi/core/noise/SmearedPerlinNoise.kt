package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.noise.FloatMth.floorToInt
import faith.mimikyumochi.core.noise.FloatMth.lerp3
import faith.mimikyumochi.core.noise.FloatMth.smoothstep
import faith.mimikyumochi.core.noise.FloatMth.wrap
import faith.mimikyumochi.core.rand.Rand

class SmearedPerlinNoise(rand: Rand, private val fudgeYScale: Double) {
    private val offsetX = rand.nextDouble() * 256.0
    private val offsetY = rand.nextDouble() * 256.0
    private val offsetZ = rand.nextDouble() * 256.0
    private val perms = ByteArray(256) { it.toByte() }

    init {
        for (i in 0 until 256) {
            val j = rand.nextInt(256 - i)
            val tmp = perms[i]
            perms[i] = perms[i + j]
            perms[i + j] = tmp
        }
    }

    private fun permute(i: Int): Int = perms[i and 255].toInt() and 255

    private fun computeFudgeY(yRaw: Double, frac: Double): Double {
        val t = if (yRaw >= 0.0 && yRaw < frac) yRaw else frac
        return floorToInt(t / fudgeYScale + 1.0000000116860974E-7).toDouble() * fudgeYScale
    }

    fun sample(xScaled: Double, yScaled: Double, zScaled: Double): Float {
        val xd = wrap(xScaled) + offsetX
        val zd = wrap(zScaled) + offsetZ
        val yd = wrap(yScaled) + offsetY

        val ix = floorToInt(xd)
        val iy = floorToInt(yd)
        val iz = floorToInt(zd)

        val fx = (xd - ix.toDouble()).toFloat()
        val fz = (zd - iz.toDouble()).toFloat()
        val fyD = yd - iy.toDouble()

        val sx = smoothstep(fx)
        val sz = smoothstep(fz)
        val sy = smoothstep(fyD.toFloat())
        val fy = (fyD - computeFudgeY(yScaled, fyD)).toFloat()

        val px0 = permute(ix)
        val px1 = permute(ix + 1)
        val h00 = permute(px0 + iy)
        val h01 = permute(px0 + iy + 1)
        val h10 = permute(px1 + iy)
        val h11 = permute(px1 + iy + 1)

        val fx1 = fx - 1.0f
        val fz1 = fz - 1.0f
        val fy1 = fy - 1.0f

        return lerp3(
            sx, sy, sz,
            corner(h00 + iz, fx, fz, fy),
            corner(h10 + iz, fx1, fz, fy),
            corner(h01 + iz, fx, fz, fy1),
            corner(h11 + iz, fx1, fz, fy1),
            corner(h00 + iz + 1, fx, fz1, fy),
            corner(h10 + iz + 1, fx1, fz1, fy),
            corner(h01 + iz + 1, fx, fz1, fy1),
            corner(h11 + iz + 1, fx1, fz1, fy1),
        )
    }

    private fun corner(hash: Int, x: Float, z: Float, y: Float): Float {
        val g = permute(hash) and 15
        return (GX[g].toFloat() * x + GZ[g].toFloat() * z) + GY[g].toFloat() * y
    }

    private companion object {
        val GX = intArrayOf(1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0)
        val GY = intArrayOf(1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1)
        val GZ = intArrayOf(0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1)
    }
}

class NoiseStack(private val noises: List<SmearedPerlinNoise>, private val frequencies: DoubleArray, private val amplitudes: FloatArray) {
    fun sample(blockX: Int, blockY: Int, blockZ: Int, xzScale: Double, yScale: Double): Float {
        var total = 0.0f
        for (i in noises.indices) {
            val f = frequencies[i]
            val xz = xzScale * f
            val y = yScale * f
            total += amplitudes[i] * noises[i].sample(blockX.toDouble() * xz, blockY.toDouble() * y, blockZ.toDouble() * xz)
        }
        return total
    }

    companion object {
        fun createFbm(rand: Rand, firstOctave: Int, smearScale: Double, amplitudeSum: Double): NoiseStack {
            require(firstOctave <= 0)
            val count = -firstOctave + 1
            var frequency = 1.0
            var amplitude = amplitudeSum / (Math.pow(2.0, count.toDouble()) - 1.0)
            val noises = ArrayList<SmearedPerlinNoise>(count)
            val frequencies = DoubleArray(count)
            val amplitudes = FloatArray(count)
            for (i in 0 until count) {
                noises += SmearedPerlinNoise(rand, smearScale * frequency)
                frequencies[i] = frequency
                amplitudes[i] = amplitude.toFloat()
                frequency /= 2.0
                amplitude *= 2.0
            }
            return NoiseStack(noises, frequencies, amplitudes)
        }
    }
}

class BlendedNoise(
    rand: Rand,
    xzScale: Double, yScale: Double,
    private val xzFactor: Double, private val yFactor: Double,
    smearScaleMultiplier: Double,
) {
    private val xzMultiplier = 684.412 * xzScale
    private val yMultiplier = 684.412 * yScale
    private val minLimit: NoiseStack
    private val maxLimit: NoiseStack
    private val main: NoiseStack

    init {
        val smear = yMultiplier * smearScaleMultiplier
        val mainSmear = smear / yFactor
        minLimit = NoiseStack.createFbm(rand, -15, smear, 0.9999847412109375)
        maxLimit = NoiseStack.createFbm(rand, -15, smear, 0.9999847412109375)
        main = NoiseStack.createFbm(rand, -7, mainSmear, 12.75)
    }

    fun sample(blockX: Int, blockY: Int, blockZ: Int): Float {
        val mainValue = main.sample(blockX, blockY, blockZ, xzMultiplier / xzFactor, yMultiplier / yFactor)
        val alpha = FloatMth.clamp(mainValue + 0.5f, 0.0f, 1.0f)
        if (alpha == 0.0f) return minLimit.sample(blockX, blockY, blockZ, xzMultiplier, yMultiplier)
        if (alpha == 1.0f) return maxLimit.sample(blockX, blockY, blockZ, xzMultiplier, yMultiplier)
        return FloatMth.lerp(
            alpha,
            minLimit.sample(blockX, blockY, blockZ, xzMultiplier, yMultiplier),
            maxLimit.sample(blockX, blockY, blockZ, xzMultiplier, yMultiplier),
        )
    }
}

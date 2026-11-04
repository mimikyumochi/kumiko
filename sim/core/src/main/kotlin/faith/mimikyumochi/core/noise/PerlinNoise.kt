package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.core.rand.Rand
import kotlin.math.floor

class PerlinNoise(rand: Rand) {
    val originX: Double = rand.nextDouble() * 256.0
    val originY: Double = rand.nextDouble() * 256.0
    val originZ: Double = rand.nextDouble() * 256.0

    private val permutations = ByteArray(256)

    init {
        for (i in 0 until 256) {
            permutations[i] = i.toByte()
        }

        for (i in 0 until 256) {
            val j = i + rand.nextInt(256 - i)

            val tmp = permutations[i]
            permutations[i] = permutations[j]
            permutations[j] = tmp
        }
    }

    fun lookup(hash: Int): Int {
        return permutations[hash and 0xFF].toInt() and 0xFF
    }
    fun sample(x: Double, y: Double, z: Double, yAmplification: Double, minY: Double): Double {
        val args: Triple<IntArray, DoubleArray, DoubleArray> = getArgs(x, y, z, yAmplification, minY)
        val section: IntArray = args.first
        val local: DoubleArray = args.second
        val fading: DoubleArray = args.third
        val perms = samplePermutations(section, local)
        return Utils.lerp3(
            fading[0],
            fading[1],
            fading[2],
            perms[0],
            perms[1],
            perms[2],
            perms[3],
            perms[4],
            perms[5],
            perms[6],
            perms[7]
        )
    }

    fun getArgs(
        x: Double,
        y: Double,
        z: Double,
        yAmplification: Double,
        minY: Double
    ): Triple<IntArray, DoubleArray, DoubleArray> {
        val offsetX: Double = x + originX
        val offsetY: Double = y + originY
        val offsetZ: Double = z + originZ

        val sectionX = floor(offsetX).toInt()
        val sectionY = floor(offsetY).toInt()
        val sectionZ = floor(offsetZ).toInt()

        val localX = offsetX - sectionX.toDouble()
        var localY = offsetY - sectionY.toDouble()
        val localZ = offsetZ - sectionZ.toDouble()

        val fadeLocalX = Utils.smoothStep(localX)
        val fadeLocalY = Utils.smoothStep(localY)
        val fadeLocalZ = Utils.smoothStep(localZ)

        if (yAmplification != 0.0) {
            val yFloor = if (minY >= 0.0 && minY < localY) minY else localY
            localY -= floor(yFloor / yAmplification + 1.0000000116860974E-7) * yAmplification
        }
        return Triple(
            intArrayOf(sectionX, sectionY, sectionZ),
            doubleArrayOf(localX, localY, localZ),
            doubleArrayOf(fadeLocalX, fadeLocalY, fadeLocalZ)
        )
    }

    fun samplePermutations(section: IntArray, local: DoubleArray): DoubleArray {
        val pXY = lookup(section[0]) + section[1]
        val pX1Y = lookup(section[0] + 1) + section[1]

        val ppXYZ = lookup(pXY) + section[2]
        val ppX1YZ = lookup(pX1Y) + section[2]

        val ppXY1Z = lookup(pXY + 1) + section[2]
        val ppX1Y1Z = lookup(pX1Y + 1) + section[2]

        val x1 = Utils.grad(lookup(ppXYZ), local[0], local[1], local[2])
        val x2 = Utils.grad(lookup(ppX1YZ), local[0] - 1.0, local[1], local[2])
        val x3 = Utils.grad(lookup(ppXY1Z), local[0], local[1] - 1.0, local[2])
        val x4 = Utils.grad(lookup(ppX1Y1Z), local[0] - 1.0, local[1] - 1.0, local[2])
        val x5 = Utils.grad(lookup(ppXYZ + 1), local[0], local[1], local[2] - 1.0)
        val x6 = Utils.grad(lookup(ppX1YZ + 1), local[0] - 1.0, local[1], local[2] - 1.0)
        val x7 = Utils.grad(lookup(ppXY1Z + 1), local[0], local[1] - 1.0, local[2] - 1.0)
        val x8 = Utils.grad(lookup(ppX1Y1Z + 1), local[0] - 1.0, local[1] - 1.0, local[2] - 1.0)

        return doubleArrayOf(x1, x2, x3, x4, x5, x6, x7, x8)
    }
}

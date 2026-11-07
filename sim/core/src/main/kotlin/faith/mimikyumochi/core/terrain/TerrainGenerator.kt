package faith.mimikyumochi.core.terrain

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.core.biome.BiomeSource
import faith.mimikyumochi.core.noise.OctavePerlinNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.version.MCVersion

data class NoiseSampling(val xzScale: Double, val yScale: Double, val xzFactor: Double, val yFactor: Double)

abstract class TerrainGenerator(
    val worldSeed: Long,
    val version: MCVersion,
    sampling: NoiseSampling,
    val worldHeight: Int,
    val chunkWidth: Int,
    val chunkHeight: Int,
) {
    abstract val biomeSource: BiomeSource

    val defaultBlock = 1
    val defaultFluid = 0

    val noiseSizeY = worldHeight / chunkHeight

    private val xzScale = 684.412 * sampling.xzScale
    private val yScale = 684.412 * sampling.yScale
    private val xzStep = xzScale / sampling.xzFactor
    private val yStep = yScale / sampling.yFactor

    private var minLimitPerlinNoise: OctavePerlinNoise
    private var maxLimitPerlinNoise: OctavePerlinNoise
    private var mainPerlinNoise: OctavePerlinNoise

    init {
        val rand = Rand(0).apply { setSeed(worldSeed) }

        minLimitPerlinNoise = OctavePerlinNoise(rand, 16)
        maxLimitPerlinNoise = OctavePerlinNoise(rand, 16)
        mainPerlinNoise     = OctavePerlinNoise(rand, 8)
    }

    fun sampleNoise(x: Int, y: Int, z: Int): Double {
        var minNoise = 0.0
        var maxNoise = 0.0
        var mainNoise = 0.0
        var persistence = 1.0

        for (octave in 0 until 16) {
            val cellX = Utils.maintainPrecision(x * xzScale * persistence)
            val cellY = Utils.maintainPrecision(y * yScale * persistence)
            val cellZ = Utils.maintainPrecision(z * xzScale * persistence)

            val sy = yScale * persistence

            minNoise += minLimitPerlinNoise.getOctave(octave)
                .sample(cellX, cellY, cellZ, sy, y * sy) / persistence

            maxNoise += maxLimitPerlinNoise.getOctave(octave)
                .sample(cellX, cellY, cellZ, sy, y * sy) / persistence

            if (octave < 8) {
                mainNoise += mainPerlinNoise.getOctave(octave).sample(
                    Utils.maintainPrecision(x * xzStep * persistence),
                    Utils.maintainPrecision(y * yStep * persistence),
                    Utils.maintainPrecision(z * xzStep * persistence),
                    yStep * persistence,
                    y * yStep * persistence
                ) / persistence
            }

            persistence *= 0.5
        }

        return Utils.clampedLerp(
            minNoise / 512.0,
            maxNoise / 512.0,
            (mainNoise / 10.0 + 1.0) / 2.0
        )
    }

    protected abstract fun computeNoiseColumn(x: Int, z: Int): DoubleArray

    private val noiseColumnCache = object : LinkedHashMap<Long, DoubleArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, DoubleArray>) = size > 4096
    }

    fun sampleNoiseColumn(x: Int, z: Int): DoubleArray {
        val key = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
        synchronized(noiseColumnCache) { noiseColumnCache[key] }?.let { return it }

        val column = computeNoiseColumn(x, z)
        synchronized(noiseColumnCache) { noiseColumnCache[key] = column }
        return column
    }

    fun getBlockFromNoise(noise: Double): Int {
        return if (noise > 0.0) defaultBlock else defaultFluid
    }

    open fun generateColumn(x: Int, z: Int): List<Int> {
        val blocks = MutableList(worldHeight) { defaultFluid }

        val cellX = Math.floorDiv(x, chunkWidth)
        val cellZ = Math.floorDiv(z, chunkWidth)

        val posX = Math.floorMod(x, chunkWidth)
        val posZ = Math.floorMod(z, chunkWidth)

        val percentX = posX / chunkWidth.toDouble()
        val percentZ = posZ / chunkWidth.toDouble()

        val ds = listOf(
            sampleNoiseColumn(cellX, cellZ),
            sampleNoiseColumn(cellX, cellZ + 1),
            sampleNoiseColumn(cellX + 1, cellZ),
            sampleNoiseColumn(cellX + 1, cellZ + 1)
        )

        for (cellY in noiseSizeY - 1 downTo 0) {
            val xyz = ds[0][cellY]
            val xyz1 = ds[1][cellY]
            val x1yz = ds[2][cellY]
            val x1yz1 = ds[3][cellY]

            val xy1z = ds[0][cellY + 1]
            val xy1z1 = ds[1][cellY + 1]
            val x1y1z = ds[2][cellY + 1]
            val x1y1z1 = ds[3][cellY + 1]

            for (posY in chunkHeight - 1 downTo 0) {
                val percentY = posY.toDouble() / chunkHeight

                val noise = Utils.lerp3(
                    percentY,
                    percentX,
                    percentZ,
                    xyz, xy1z, x1yz, x1y1z,
                    xyz1, xy1z1, x1yz1, x1y1z1
                )

                val y = cellY * chunkHeight + posY
                blocks[y] = getBlockFromNoise(noise)
            }
        }

        return blocks
    }

    fun getHeight(x: Int, z: Int): Int {
        val col = generateColumn(x, z)

        return when (val topY = col.lastIndexOf(defaultBlock)) {
            -1 -> 0
            else -> topY
        }
    }

    fun isChunkEmpty(chunkX: Int, chunkZ: Int): Boolean {
        for (dx in 0 until 16) {
            for (dz in 0 until 16) {
                val x = chunkX * 16 + dx
                val z = chunkZ * 16 + dz

                if (getHeight(x, z) > 0) {
                    return false
                }
            }
        }
        return true
    }
}

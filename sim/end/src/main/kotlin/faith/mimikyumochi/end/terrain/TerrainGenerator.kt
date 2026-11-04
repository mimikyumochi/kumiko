package faith.mimikyumochi.end.terrain

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.end.biome.BiomeSource
import faith.mimikyumochi.core.noise.OctavePerlinNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.version.MCVersion

class TerrainGenerator(val biomeSource: BiomeSource, val version: MCVersion = biomeSource.version) {
    val worldSeed = biomeSource.worldSeed

    val defaultBlock = 1
    val defaultFluid = 0
    val worldHeight = 128

    val chunkWidth = 8
    val chunkHeight = 4

    val noiseSizeY = 32

    private var minLimitPerlinNoise: OctavePerlinNoise
    private var maxLimitPerlinNoise: OctavePerlinNoise
    private var mainPerlinNoise: OctavePerlinNoise

    init {
        val rand = Rand(0).apply { setSeed(worldSeed) }

        minLimitPerlinNoise = OctavePerlinNoise(rand, 16)
        maxLimitPerlinNoise = OctavePerlinNoise(rand, 16)
        mainPerlinNoise     = OctavePerlinNoise(rand, 8)
    }

    fun getDepthAndScale(x: Int, z: Int): DoubleArray {
        val height = biomeSource.getHeight(x, z)

        val depth = height - 8.0
        val scale = if (depth > 0.0) 0.25 else 1.0

        return doubleArrayOf(depth, scale)
    }

    fun sampleNoise(x: Int, y: Int, z: Int): Double {
        val xzScale = 684.412 * 2.0
        val yScale  = 684.412 * 1.0

        val xzStep = xzScale / 80.0
        val yStep  = yScale / 160.0

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

    private val noiseColumnCache = object : LinkedHashMap<Long, DoubleArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, DoubleArray>) = size > 4096
    }

    fun sampleNoiseColumn(x: Int, z: Int): DoubleArray {
        val key = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
        synchronized(noiseColumnCache) { noiseColumnCache[key] }?.let { return it }

        val column = run {
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
            buffer
        }
        synchronized(noiseColumnCache) { noiseColumnCache[key] = column }
        return column
    }

    fun getBlockFromNoise(noise: Double): Int {
        return if (noise > 0.0) defaultBlock else defaultFluid
    }

    private val floatTerrain: FloatEndTerrain? =
        if (version >= MCVersion.V26_3) FloatEndTerrain(biomeSource) else null

    fun generateColumn(x: Int, z: Int): List<Int> {
        floatTerrain?.let { terrain ->
            return terrain.densityColumn(x, z).map { if (it > 0.0f) defaultBlock else defaultFluid }
        }
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

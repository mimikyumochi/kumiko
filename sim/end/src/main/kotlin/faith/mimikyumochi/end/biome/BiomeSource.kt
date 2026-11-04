package faith.mimikyumochi.end.biome

import faith.mimikyumochi.core.noise.SimplexNoise
import faith.mimikyumochi.core.noise.FloatMth
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.version.MCVersion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class BiomeSource(val worldSeed: Long, val version: MCVersion = MCVersion.LATEST) {
    private val simplex: SimplexNoise

    init {
        val rand = Rand().apply { setSeed(worldSeed) }
        rand.advance(17292)
        simplex = SimplexNoise(rand)
    }

    fun sampleSimplex(x: Int, z: Int): Boolean {
        return simplex.sample2D(x.toDouble(), z.toDouble()) < (-0.9f).toDouble()
    }

    fun clamp(v: Float): Float {
        if (v < -100.0f) return -100.0f
        return v.coerceAtMost(80.0f)
    }

    fun getHeight(x: Int, z: Int): Float {
        val scaledX = x / 2
        val scaledZ = z / 2
        val oddX = x % 2
        val oddZ = z % 2

        var height: Float = 100.0f - sqrt(((x * x + z * z).toFloat()).toDouble()).toFloat() * 8.0f

        height = clamp(height)

        for (rx in -12..12) {
            for (rz in -12..12) {
                val shiftedX = (scaledX + rx)
                val shiftedZ = (scaledZ + rz)

                if (shiftedX.toLong() * shiftedX + shiftedZ.toLong() * shiftedZ > 4096L
                    && sampleSimplex(shiftedX, shiftedZ)) {
                    val elevation = (abs(shiftedX.toFloat()) * 3439.0f + abs(shiftedZ.toFloat()) * 147.0f) % 13.0f + 9.0f
                    val smoothX = (oddX - rx * 2).toFloat()
                    val smoothZ = (oddZ - rz * 2).toFloat()
                    val noise = clamp(100.0f - sqrt(smoothX * smoothX + smoothZ * smoothZ) * elevation)
                    height = max(height, noise)
                }
            }
        }
        return height
    }

    fun islandDensity(blockX: Int, blockZ: Int): Float {
        val dx = (0.0 - blockX).toFloat()
        val dy = (0.0 - 0).toFloat()
        val dz = (0.0 - blockZ).toFloat()
        val distance = FloatMth.sqrt(dx * dx + dy * dy + dz * dz)
        val main = (FloatMth.clamp(100.0f - distance, -100.0f, 80.0f) - 8.0f) * 0.0078125f
        val outer = (outerIslandHeight(blockX / 8, blockZ / 8) - 8.0f) / 128.0f
        return if (outer > main) outer else main
    }

    private fun outerIslandHeight(x: Int, z: Int): Float {
        val sx = x / 2
        val sz = z / 2
        val ox = x % 2
        val oz = z % 2
        var height = -100.0f
        for (rx in -12..12) {
            for (rz in -12..12) {
                val lx = (sx + rx).toLong()
                val lz = (sz + rz).toLong()
                if (lx * lx + lz * lz > 4096L && simplex.sample2D(lx.toDouble(), lz.toDouble()).toFloat() < -0.9f) {
                    val elevation = (abs(lx.toFloat()) * 3439.0f + abs(lz.toFloat()) * 147.0f) % 13.0f + 9.0f
                    val fx = (ox - rx * 2).toFloat()
                    val fz = (oz - rz * 2).toFloat()
                    val h = FloatMth.clamp(100.0f - FloatMth.sqrt(fx * fx + fz * fz) * elevation, -100.0f, 80.0f)
                    height = Math.max(height, h)
                }
            }
        }
        return height
    }

    private val zoomSeed: Long = run {
        val bytes = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(worldSeed).array()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        java.nio.ByteBuffer.wrap(hash, 0, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
    }

    fun biomeAt(x: Int, y: Int, z: Int): String {
        val i = x - 2
        val j = y - 2
        val k = z - 2
        val qx = i shr 2
        val qy = j shr 2
        val qz = k shr 2
        val fx = (i and 3) / 4.0
        val fy = (j and 3) / 4.0
        val fz = (k and 3) / 4.0
        var best = 0
        var bestDist = Double.POSITIVE_INFINITY
        for (corner in 0 until 8) {
            val lowX = (corner and 4) == 0
            val lowY = (corner and 2) == 0
            val lowZ = (corner and 1) == 0
            val dist = fiddledDistance(
                if (lowX) qx else qx + 1, if (lowY) qy else qy + 1, if (lowZ) qz else qz + 1,
                if (lowX) fx else fx - 1.0, if (lowY) fy else fy - 1.0, if (lowZ) fz else fz - 1.0,
            )
            if (bestDist > dist) { best = corner; bestDist = dist }
        }
        val bx = if ((best and 4) == 0) qx else qx + 1
        val bz = if ((best and 1) == 0) qz else qz + 1
        return sampleBiome(bx, bz)
    }

    private fun fiddledDistance(x: Int, y: Int, z: Int, fx: Double, fy: Double, fz: Double): Double {
        var h = zoomSeed
        h = lcg(h, x.toLong()); h = lcg(h, y.toLong()); h = lcg(h, z.toLong())
        h = lcg(h, x.toLong()); h = lcg(h, y.toLong()); h = lcg(h, z.toLong())
        val dx = fiddle(h)
        h = lcg(h, zoomSeed)
        val dy = fiddle(h)
        h = lcg(h, zoomSeed)
        val dz = fiddle(h)
        return (fz + dz) * (fz + dz) + (fy + dy) * (fy + dy) + (fx + dx) * (fx + dx)
    }

    private fun lcg(seed: Long, add: Long): Long = seed * (seed * 6364136223846793005L + 1442695040888963407L) + add

    private fun fiddle(h: Long): Double = (Math.floorMod(h shr 24, 1024).toDouble() / 1024.0 - 0.5) * 0.9

    fun sampleBiome(_x: Int, _z: Int): String {
        val x = _x shr 2
        val z = _z shr 2

        if (x.toLong() * x.toLong() + z.toLong() * z.toLong() <= 4096) {
            return "the_end"
        }

        if (version >= MCVersion.V26_3) {
            val e = islandDensity((x * 2 + 1) * 8, (z * 2 + 1) * 8).toDouble()
            return when {
                e > 0.25 -> "end_highlands"
                e >= -0.0625 -> "end_midlands"
                e < -0.21875 -> "small_end_islands"
                else -> "end_barrens"
            }
        }

        val height = getHeight(x * 2 + 1, z * 2 + 1)
        if (height > 40.0f) {
            return "end_highlands"
        } else if (height >= 0.0f) {
            return "end_midlands"
        }
        return if (height < -20.0f) "small_end_islands" else "end_barrens"
    }
}

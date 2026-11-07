package faith.mimikyumochi.core.biome

import faith.mimikyumochi.core.version.MCVersion

abstract class BiomeSource(val worldSeed: Long, val version: MCVersion = MCVersion.LATEST) {
    private val zoomSeed: Long = run {
        val bytes = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(worldSeed).array()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        java.nio.ByteBuffer.wrap(hash, 0, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
    }

    abstract fun sampleBiome(x: Int, y: Int, z: Int): String

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
        val by = if ((best and 2) == 0) qy else qy + 1
        val bz = if ((best and 1) == 0) qz else qz + 1
        return sampleBiome(bx, by, bz)
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
}

package faith.mimikyumochi.nether.terrain

import faith.mimikyumochi.core.noise.FloatMth
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.version.MCVersion
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.floor

class NetherCaves(private val worldSeed: Long, version: MCVersion) {
    private class Ellipsoid(val x: Double, val y: Double, val z: Double, val horizontal: Double, val vertical: Double)

    private class Step(val index: Int, val ellipsoid: Ellipsoid)

    private class Tunnel(val total: Int, val thickness: Float, val steps: List<Step>, val branches: List<Tunnel>)

    private class Start(val rooms: List<Ellipsoid>, val tunnels: List<Tunnel>)

    private val floatTrig = version < MCVersion.V26_3
    private val startHeight = if (version >= MCVersion.V1_18) 127 else 128

    private val starts = lru<Start>(16384)
    private val masks = lru<BitSet>(4096)

    private fun <T> lru(capacity: Int) = object : LinkedHashMap<Long, T>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, T>) = size > capacity
    }

    private fun sin(v: Float): Float =
        if (floatTrig) FloatMth.SIN[(v * 10430.378f).toInt() and 65535]
        else FloatMth.SIN[((v.toDouble() * 10430.378350470453).toLong() and 65535L).toInt()]

    private fun cos(v: Float): Float =
        if (floatTrig) FloatMth.SIN[(v * 10430.378f + 16384.0f).toInt() and 65535]
        else FloatMth.SIN[((v.toDouble() * 10430.378350470453 + 16384.0).toLong() and 65535L).toInt()]

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun start(chunkX: Int, chunkZ: Int): Start {
        val key = key(chunkX, chunkZ)
        synchronized(starts) { starts[key] }?.let { return it }
        val start = planStart(chunkX, chunkZ)
        synchronized(starts) { starts[key] = start }
        return start
    }

    private fun planStart(chunkX: Int, chunkZ: Int): Start {
        val rand = Rand()
        rand.setCarverSeed(worldSeed, chunkX, chunkZ)
        val rooms = ArrayList<Ellipsoid>()
        val tunnels = ArrayList<Tunnel>()
        if (rand.nextFloat() > PROBABILITY) return Start(rooms, tunnels)

        val count = rand.nextInt(rand.nextInt(rand.nextInt(MAX_COUNT + 1) + 1) + 1)
        repeat(count) {
            val x = (chunkX * 16 + rand.nextInt(16)).toDouble()
            val y = rand.nextInt(startHeight).toDouble()
            val z = (chunkZ * 16 + rand.nextInt(16)).toDouble()
            var branches = 1
            if (rand.nextInt(4) == 0) {
                val radius = 1.0f + rand.nextFloat() * 6.0f
                val horizontal = 1.5 + (sin(1.5707964f) * radius).toDouble()
                rooms += Ellipsoid(x + 1.0, y, z, horizontal, horizontal * ROOM_VERTICAL)
                branches += rand.nextInt(4)
            }
            repeat(branches) {
                val yaw = rand.nextFloat() * 6.2831855f
                val pitch = (rand.nextFloat() - 0.5f) / 4.0f
                val thickness = rand.nextFloat() * 4.0f + rand.nextFloat() * 2.0f
                val total = RANGE - rand.nextInt(RANGE / 4)
                tunnels += planTunnel(rand.nextLong(), x, y, z, thickness, yaw, pitch, 0, total, START_VERTICAL)
            }
        }
        return Start(rooms, tunnels)
    }

    private fun planTunnel(
        seed: Long, startX: Double, startY: Double, startZ: Double,
        thickness: Float, startYaw: Float, startPitch: Float, first: Int, total: Int, yScale: Double,
    ): Tunnel {
        val rand = Rand(seed)
        val branchAt = rand.nextInt(total / 2) + total / 4
        val steep = rand.nextInt(6) == 0
        var x = startX
        var y = startY
        var z = startZ
        var yaw = startYaw
        var pitch = startPitch
        var yawDrift = 0.0f
        var pitchDrift = 0.0f
        val steps = ArrayList<Step>()

        for (i in first until total) {
            val horizontal = 1.5 + (sin(3.1415927f * i.toFloat() / total.toFloat()) * thickness).toDouble()
            val vertical = horizontal * yScale
            val cosPitch = cos(pitch)
            x += (cos(yaw) * cosPitch).toDouble()
            y += sin(pitch).toDouble()
            z += (sin(yaw) * cosPitch).toDouble()
            pitch *= if (steep) 0.92f else 0.7f
            pitch += pitchDrift * 0.1f
            yaw += yawDrift * 0.1f
            pitchDrift *= 0.9f
            yawDrift *= 0.75f
            pitchDrift += (rand.nextFloat() - rand.nextFloat()) * rand.nextFloat() * 2.0f
            yawDrift += (rand.nextFloat() - rand.nextFloat()) * rand.nextFloat() * 4.0f

            if (i == branchAt && thickness > 1.0f) {
                val left = planTunnel(rand.nextLong(), x, y, z, rand.nextFloat() * 0.5f + 0.5f, yaw - 1.5707964f, pitch / 3.0f, i, total, 1.0)
                val right = planTunnel(rand.nextLong(), x, y, z, rand.nextFloat() * 0.5f + 0.5f, yaw + 1.5707964f, pitch / 3.0f, i, total, 1.0)
                return Tunnel(total, thickness, steps, listOf(left, right))
            }
            if (rand.nextInt(4) == 0) continue
            steps += Step(i, Ellipsoid(x, y, z, horizontal, vertical))
        }
        return Tunnel(total, thickness, steps, emptyList())
    }

    private fun canReach(chunkX: Int, chunkZ: Int, tunnel: Tunnel, step: Step): Boolean {
        val dx = step.ellipsoid.x - (chunkX * 16 + 8).toDouble()
        val dz = step.ellipsoid.z - (chunkZ * 16 + 8).toDouble()
        val remaining = (tunnel.total - step.index).toDouble()
        val reach = (tunnel.thickness + 2.0f + 16.0f).toDouble()
        return dx * dx + dz * dz - remaining * remaining <= reach * reach
    }

    private fun walk(tunnel: Tunnel, chunkX: Int, chunkZ: Int, mask: BitSet) {
        for (step in tunnel.steps) {
            if (!canReach(chunkX, chunkZ, tunnel, step)) return
            carve(step.ellipsoid, chunkX, chunkZ, mask)
        }
        for (branch in tunnel.branches) walk(branch, chunkX, chunkZ, mask)
    }

    private fun carve(e: Ellipsoid, chunkX: Int, chunkZ: Int, mask: BitSet) {
        val blockX = chunkX * 16
        val blockZ = chunkZ * 16
        val limit = 16.0 + e.horizontal * 2.0
        if (abs(e.x - (blockX + 8).toDouble()) > limit || abs(e.z - (blockZ + 8).toDouble()) > limit) return

        val minX = maxOf(floor(e.x - e.horizontal).toInt() - blockX - 1, 0)
        val maxX = minOf(floor(e.x + e.horizontal).toInt() - blockX, 15)
        val minY = maxOf(floor(e.y - e.vertical).toInt() - 1, MIN_Y)
        val maxY = minOf(floor(e.y + e.vertical).toInt() + 1, MAX_Y)
        val minZ = maxOf(floor(e.z - e.horizontal).toInt() - blockZ - 1, 0)
        val maxZ = minOf(floor(e.z + e.horizontal).toInt() - blockZ, 15)

        for (x in minX..maxX) {
            val nx = ((blockX + x).toDouble() + 0.5 - e.x) / e.horizontal
            for (z in minZ..maxZ) {
                val nz = ((blockZ + z).toDouble() + 0.5 - e.z) / e.horizontal
                if (nx * nx + nz * nz >= 1.0) continue
                var y = maxY
                while (y > minY) {
                    val ny = (y.toDouble() - 0.5 - e.y) / e.vertical
                    if (ny > FLOOR_LEVEL && nx * nx + ny * ny + nz * nz < 1.0) mask.set(index(x, y, z))
                    y--
                }
            }
        }
    }

    private fun index(x: Int, y: Int, z: Int) = (x * 16 + z) * HEIGHT + y

    fun carved(chunkX: Int, chunkZ: Int): BitSet {
        val key = key(chunkX, chunkZ)
        synchronized(masks) { masks[key] }?.let { return it }
        val mask = BitSet(16 * 16 * HEIGHT)
        for (cx in chunkX - REACH..chunkX + REACH) for (cz in chunkZ - REACH..chunkZ + REACH) {
            val start = start(cx, cz)
            for (room in start.rooms) carve(room, chunkX, chunkZ, mask)
            for (tunnel in start.tunnels) walk(tunnel, chunkX, chunkZ, mask)
        }
        synchronized(masks) { masks[key] = mask }
        return mask
    }

    fun isCarved(mask: BitSet, x: Int, y: Int, z: Int): Boolean = mask.get(index(x and 15, y, z and 15))

    private companion object {
        const val PROBABILITY = 0.2f
        const val MAX_COUNT = 9
        const val RANGE = 112
        const val REACH = 8
        const val HEIGHT = 128
        const val MIN_Y = 1
        const val MAX_Y = 120
        const val ROOM_VERTICAL = 0.5f.toDouble()
        const val START_VERTICAL = 5.0
        const val FLOOR_LEVEL = (-0.7f).toDouble()
    }
}

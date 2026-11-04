package faith.mimikyumochi.end.features

import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.end.structures.SmallEndIsland
import faith.mimikyumochi.end.terrain.TerrainGenerator
import kotlin.math.floor
import kotlin.math.sqrt

class GatewayExit(private val terrain: TerrainGenerator) {
    data class Exit(val pos: BPos, val island: SmallEndIsland.Island?)

    private val islands = SmallEndIsland(terrain.version)

    private val columns = HashMap<Long, BooleanArray>()
    private val chunkIslands = HashMap<Long, List<SmallEndIsland.Island>>()

    private var spawned: SmallEndIsland.Island? = null

    fun find(gatewayX: Int, gatewayZ: Int): Exit {
        spawned = null
        val (x, z) = tentativeXZ(gatewayX, gatewayZ)
        val chunkX = floor(x / 16.0).toInt()
        val chunkZ = floor(z / 16.0).toInt()

        var spot = validSpawnInChunk(chunkX, chunkZ)
        var island: SmallEndIsland.Island? = null
        if (spot == null) {
            spot = BPos(floor(x + 0.5).toInt(), 75, floor(z + 0.5).toInt())
            island = SmallEndIsland.placedWith(spot.x, spot.y, spot.z, Rand(asLong(spot.x, spot.y, spot.z)))
            spawned = island
        }
        val top = tallestBlock(spot, 16, true)
        spawned = null
        return Exit(BPos(top.x, top.y + GATEWAY_HEIGHT_ABOVE_SURFACE, top.z), island)
    }

    private fun tentativeXZ(gatewayX: Int, gatewayZ: Int): Pair<Double, Double> {
        val length = sqrt(gatewayX.toDouble() * gatewayX + 0.0 * 0.0 + gatewayZ.toDouble() * gatewayZ)
        val (dirX, dirZ) = if (length < 1.0E-5f.toDouble()) 0.0 to 0.0 else gatewayX / length to gatewayZ / length

        var x = dirX * 1024.0
        var z = dirZ * 1024.0
        var limit = 16
        while (!isChunkEmpty(x, z) && limit-- > 0) {
            x += dirX * -16.0
            z += dirZ * -16.0
        }
        limit = 16
        while (isChunkEmpty(x, z) && limit-- > 0) {
            x += dirX * 16.0
            z += dirZ * 16.0
        }
        return x to z
    }

    private fun isChunkEmpty(x: Double, z: Double): Boolean = highestBlockY(floor(x / 16.0).toInt(), floor(z / 16.0).toInt()) < 0

    private fun highestBlockY(chunkX: Int, chunkZ: Int): Int {
        var highest = -1
        for (dx in 0 until 16) for (dz in 0 until 16) {
            val x = chunkX * 16 + dx
            val z = chunkZ * 16 + dz
            for (y in TOP downTo highest + 1) {
                if (isBlock(x, y, z)) { highest = y; break }
            }
        }
        return highest
    }

    private fun validSpawnInChunk(chunkX: Int, chunkZ: Int): BPos? {
        val highest = highestBlockY(chunkX, chunkZ)
        val sectionTop = (if (highest < 0) 0 else Math.floorDiv(highest, 16) * 16) + 16 - 1
        val minY = minOf(30, sectionTop)
        val maxY = maxOf(30, sectionTop)
        for (z in chunkZ * 16..chunkZ * 16 + 15) for (y in minY..maxY) for (x in chunkX * 16..chunkX * 16 + 15) {
            if (isBlock(x, y, z) && !isBlock(x, y + 1, z) && !isBlock(x, y + 2, z)) {
                return BPos(chunkX * 16 + 15, maxY, chunkZ * 16 + 15)
            }
        }
        return null
    }

    private fun tallestBlock(pos: BPos, radius: Int, includeCentre: Boolean): BPos {
        var best: BPos? = null
        for (dx in -radius..radius) for (dz in -radius..radius) {
            if (dx == 0 && dz == 0 && !includeCentre) continue
            var y = MAX_Y
            while (y > (best?.y ?: MIN_Y)) {
                if (isBlock(pos.x + dx, y, pos.z + dz)) {
                    best = BPos(pos.x + dx, y, pos.z + dz)
                    break
                }
                y--
            }
        }
        return best ?: pos
    }

    private fun isBlock(x: Int, y: Int, z: Int): Boolean {
        if (y !in 0..TOP) return false
        if (column(x, z)[y]) return true
        val chunkX = Math.floorDiv(x, 16)
        val chunkZ = Math.floorDiv(z, 16)
        for (cx in chunkX - 1..chunkX + 1) for (cz in chunkZ - 1..chunkZ + 1) {
            for (island in islandsIn(cx, cz)) if (island.isBlock(x - island.x, y - island.y, z - island.z)) return true
        }
        return spawned?.let { it.isBlock(x - it.x, y - it.y, z - it.z) } ?: false
    }

    private fun column(x: Int, z: Int): BooleanArray = columns.getOrPut(key(x, z)) {
        val blocks = terrain.generateColumn(x, z)
        BooleanArray(blocks.size) { blocks[it] == terrain.defaultBlock }
    }

    private fun islandsIn(chunkX: Int, chunkZ: Int): List<SmallEndIsland.Island> = chunkIslands.getOrPut(key(chunkX, chunkZ)) {
        if (islands.getIslands(chunkX, chunkZ, terrain.worldSeed).isEmpty()) emptyList()
        else islands.getPlacedIslands(chunkX, chunkZ, terrain.biomeSource)
    }

    private companion object {
        const val GATEWAY_HEIGHT_ABOVE_SURFACE = 10

        const val MIN_Y = 0
        const val MAX_Y = 255

        const val TOP = 127

        fun key(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

        fun asLong(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
    }
}

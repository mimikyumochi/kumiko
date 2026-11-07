package faith.mimikyumochi.end.structures

import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.rand.Xoroshiro128PlusPlus
import faith.mimikyumochi.core.version.MCVersion

class EndGateway(val version: MCVersion = MCVersion.LATEST) {
    data class Pos(val x: Int, val offsetY: Int, val z: Int)

    val linkedGateways =  arrayOf(
        BPos(96, 0, 0), BPos(91, 0, 29), BPos(77, 0, 56), BPos(56, 0, 77), BPos(29, 0, 91),
        BPos(-1, 0, 96), BPos(-30, 0, 91), BPos(-57, 0, 77), BPos(-78, 0, 56), BPos(-92, 0, 29),
        BPos(-96, 0, -1), BPos(-92, 0, -30), BPos(-78, 0, -57), BPos(-57, 0, -78), BPos(-30, 0, -92),
        BPos(0, 0, -96), BPos(29, 0, -92), BPos(56, 0, -78), BPos(77, 0, -57), BPos(91, 0, -30)
    )

    val salt = when {
        version >= MCVersion.V1_18 -> 40000
        version >= MCVersion.V1_16 -> 40013
        else -> 30000
    }
    val chance = 700

    fun getEndIslandGatewayOrder(biomeSource: EndBiomeSource): IntArray {
        val order = IntArray(linkedGateways.size) { it }

        val rand = Rand(biomeSource.worldSeed)

        for (i in order.size downTo 2) {
            val j = rand.nextInt(i)
            val tmp = order[i - 1]
            order[i - 1] = order[j]
            order[j] = tmp
        }

        order.reverse()
        return order
    }

    fun getPos(chunkX: Int, chunkZ: Int, worldSeed: Long): Pos? {
        val blockX = chunkX * 16
        val blockZ = chunkZ * 16

        if (version >= MCVersion.V1_18) {
            val xr = Xoroshiro128PlusPlus()
            xr.setDecoratorSeed(blockX, blockZ, worldSeed, salt)

            if (xr.nextFloat() >= 1f / chance) return null

            val x = blockX + xr.nextIntJ(16)
            val z = blockZ + xr.nextIntJ(16)
            val offsetY = xr.nextIntJ(7) + 3
            return Pos(x, offsetY, z)
        }

        val rand = Rand()
        rand.setDecoratorSeed(worldSeed, blockX, blockZ, salt)

        if (version >= MCVersion.V1_17) {
            if (rand.nextFloat() >= 1f / chance) return null
        } else {
            if (rand.nextInt(chance) != 0) return null
        }

        val x = blockX + rand.nextInt(16)
        val z = blockZ + rand.nextInt(16)
        val offsetY = rand.nextInt(7) + 3
        return Pos(x, offsetY, z)
    }

    fun isPlacedAt(x: Int, y: Int, z: Int, biomeSource: EndBiomeSource): Boolean =
        if (version >= MCVersion.V1_18) biomeSource.biomeAt(x, y, z) == "end_highlands"
        else biomeSource.sampleBiome((x shr 4 shl 2) + 2, (z shr 4 shl 2) + 2) == "end_highlands"

    fun canStart(chunkX: Int, chunkZ: Int, biomeSource: EndBiomeSource): Boolean {
        getPos(chunkX, chunkZ, biomeSource.worldSeed) ?: return false

        return biomeSource.sampleBiome((chunkX shl 2) + 2, (chunkZ shl 2) + 2) == "end_highlands"
    }
}

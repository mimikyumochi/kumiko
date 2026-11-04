package faith.mimikyumochi.end.structures

import faith.mimikyumochi.end.biome.BiomeSource
import faith.mimikyumochi.core.block.BlockRotation
import faith.mimikyumochi.core.block.CPos
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.end.terrain.TerrainGenerator

class EndCity {
    val spacing = 20
    val separation = 11
    val peak = spacing - separation

    val salt = 10387313
    val decorationSalt = 40010

    companion object {
        fun getLegacyHeightRotation(chunkX: Int, chunkZ: Int): BlockRotation =
            BlockRotation.getRandom(Rand((chunkX + chunkZ * 10387313).toLong()))

        fun getLowestYIn5by5BoxOffset7Blocks(terrainGenerator: TerrainGenerator,
                                             chunkX: Int,
                                             chunkZ: Int,
                                             rotation: BlockRotation
        ): Int {
            var offsetX = 5
            var offsetZ = 5

            when (rotation) {
                BlockRotation.CLOCKWISE_90 -> offsetX = -5
                BlockRotation.CLOCKWISE_180 -> {
                    offsetX = -5
                    offsetZ = -5
                }
                BlockRotation.CLOCKWISE_270 -> offsetZ = -5
                else -> {}
            }

            val blockX = (chunkX shl 4) + 7
            val blockZ = (chunkZ shl 4) + 7

            val center = terrainGenerator.getHeight(blockX, blockZ)
            val south = terrainGenerator.getHeight(blockX, blockZ + offsetZ)
            val east = terrainGenerator.getHeight(blockX + offsetX, blockZ)
            val southEast = terrainGenerator.getHeight(blockX + offsetX, blockZ + offsetZ)
            return minOf(center, south, east, southEast)
        }
    }

    fun isValidBiome(biome: String): Boolean {
        return biome == "end_midlands" || biome == "end_highlands"
    }

    fun canSpawn(chunkX: Int, chunkZ: Int, source: BiomeSource): Boolean {
        return isValidBiome(source.sampleBiome((chunkX shl 2) + 2, (chunkZ shl 2) + 2))
    }

    fun getInRegion(regionX: Int, regionZ: Int, structureSeed: Long): CPos {
        val rand = Rand()

        val baseRegionSeed = rand.setRegionSeed(0L, regionX, regionZ, salt)
        rand.setSeed(baseRegionSeed + structureSeed)

        val offsetX = (rand.nextInt(peak) + rand.nextInt(peak)) / 2
        val offsetZ = (rand.nextInt(peak) + rand.nextInt(peak)) / 2

        return CPos(regionX * spacing + offsetX, regionZ * spacing + offsetZ)
    }
}

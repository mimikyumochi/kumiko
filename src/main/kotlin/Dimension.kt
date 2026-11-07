package faith.mimikyumochi

import faith.mimikyumochi.core.block.CPos
import faith.mimikyumochi.core.terrain.TerrainGenerator
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.end.terrain.EndTerrainGenerator
import faith.mimikyumochi.nether.biome.NetherBiomeSource
import faith.mimikyumochi.nether.terrain.NetherTerrainGenerator

enum class Dimension(val label: String, val id: String, val firstVersion: MCVersion) {
    END("The End", "the_end", MCVersion.V1_13),
    NETHER("The Nether", "the_nether", MCVersion.V1_16);

    fun terrain(seed: Long, version: MCVersion): TerrainGenerator = when (this) {
        END -> EndTerrainGenerator(EndBiomeSource(seed, version))
        NETHER -> NetherTerrainGenerator(NetherBiomeSource(seed, version))
    }
}

fun generateNetherChunk(chunkX: Int, chunkZ: Int, terrain: NetherTerrainGenerator): Chunk {
    val originX = chunkX * CHUNK_WIDTH - 1
    val originZ = chunkZ * CHUNK_WIDTH - 1
    val blocks = Array(CHUNK_WIDTH + 2) { x ->
        Array(CHUNK_WIDTH + 2) { z ->
            val column = terrain.generateColumn(originX + x, originZ + z)
            IntArray(WORLD_HEIGHT) { y ->
                when (column.getOrNull(y)) {
                    terrain.defaultBlock -> NETHERRACK
                    NetherTerrainGenerator.LAVA -> LAVA
                    else -> AIR
                }
            }
        }
    }
    return Chunk(CPos(chunkX, chunkZ), blocks)
}

fun netherOpening(terrain: NetherTerrainGenerator): Pair<Int, Int> {
    for (step in 0 until NETHER_SEARCH_STEPS) {
        val x = step * CHUNK_WIDTH
        val column = terrain.generateColumn(x, 0)
        val floor = (NetherTerrainGenerator.SEA_LEVEL until terrain.worldHeight - NETHER_HEADROOM).firstOrNull { y ->
            (y until y + NETHER_HEADROOM).all { column[it] == terrain.defaultFluid }
        }
        if (floor != null) return x to floor
    }
    return 0 to NetherTerrainGenerator.SEA_LEVEL
}

private const val NETHER_SEARCH_STEPS = 256
private const val NETHER_HEADROOM = 8

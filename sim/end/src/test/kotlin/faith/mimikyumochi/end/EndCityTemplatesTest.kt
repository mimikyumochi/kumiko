package faith.mimikyumochi.end

import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.end.structures.EndCity
import faith.mimikyumochi.end.structures.EndCityGenerator
import faith.mimikyumochi.end.structures.EndCityTemplates
import faith.mimikyumochi.end.terrain.EndTerrainGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndCityTemplatesTest {
    @Test
    fun sizesMatchTheGenerator() {
        for ((name, size) in EndCityGenerator.STRUCTURE_SIZE) {
            assertEquals(size, EndCityTemplates[name].size, name)
        }
    }

    @Test
    fun markersMatchTheTemplates() {
        var chests = 0
        for (name in EndCityGenerator.STRUCTURE_SIZE.keys) {
            val template = EndCityTemplates[name]
            val blocks = template.blocks.associate { it.pos to it.state.name }
            for (marker in template.markers.filter { it.metadata.startsWith("Chest") }) {
                chests++
                val below = marker.pos.let { faith.mimikyumochi.core.block.BPos(it.x, it.y - 1, it.z) }
                assertEquals("minecraft:chest", blocks[below], "$name: ${marker.metadata} at ${marker.pos}")
            }
        }
        assertEquals(5, chests, "fat_tower_top ×2, ship ×2, third_floor_2")
        val elytras = EndCityGenerator.STRUCTURE_SIZE.keys.flatMap { name ->
            EndCityTemplates[name].markers.filter { it.metadata.startsWith("Elytra") }.map { name to it.pos }
        }
        assertEquals(listOf("ship" to faith.mimikyumochi.core.block.BPos(6, 5, 7)), elytras)
    }

    @Test
    fun blocksStayInsideTheirPieces() {
        val seed = 1L
        val biomes = EndBiomeSource(seed, MCVersion.V26_3)
        val terrain = EndTerrainGenerator(biomes)
        val city = EndCity()
        var cities = 0
        val rotations = HashSet<Any>()
        for (rx in -8..8) for (rz in -8..8) {
            if (cities >= 12) break
            val chunk = city.getInRegion(rx, rz, seed)
            if (!city.canSpawn(chunk.x, chunk.z, biomes)) continue
            val generator = EndCityGenerator()
            if (!generator.generate(terrain, chunk, MCVersion.V26_3)) continue
            cities++
            for (piece in generator.pieces) {
                rotations += piece.rotation
                val box = piece.box
                for (block in piece.blocks()) {
                    val p = block.pos
                    assertTrue(
                        p.x in box.minX..box.maxX && p.y in box.minY..box.maxY && p.z in box.minZ..box.maxZ,
                        "${piece.name} ${piece.rotation}: $p outside $box",
                    )
                }
            }
        }
        assertTrue(cities > 0, "no cities to check")
        assertEquals(4, rotations.size, "not every rotation was checked")
    }
}

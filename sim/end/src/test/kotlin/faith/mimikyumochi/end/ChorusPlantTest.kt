package faith.mimikyumochi.end

import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.structures.ChorusPlant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChorusPlantTest {
    private val flat = object : ChorusPlant.World {
        override fun isAir(x: Int, y: Int, z: Int) = y >= SURFACE
        override fun isEndStone(x: Int, y: Int, z: Int) = y in 0 until SURFACE
        override fun surface(x: Int, z: Int) = SURFACE
    }

    private val chorus = ChorusPlant(MCVersion.V26_3)

    private fun grow(chunkX: Int, chunkZ: Int) = chorus.generate(chunkX, chunkZ, 1L, flat) { _, _, _ -> ChorusPlant.BIOME }

    @Test
    fun plantsHaveTheVanillaShape() {
        var plants = 0
        for (cx in 0 until 20) for (cz in 0 until 20) {
            val blocks = grow(cx, cz)
            val at = blocks.associateBy { Triple(it.x, it.y, it.z) }
            fun part(x: Int, y: Int, z: Int) = at[Triple(x, y, z)]?.part
            val bases = blocks.filter { it.y == SURFACE }
            plants += bases.size
            for (base in bases) assertEquals(ChorusPlant.Part.PLANT, base.part)
            for (b in blocks) {
                val held = b.y == SURFACE || part(b.x, b.y - 1, b.z) == ChorusPlant.Part.PLANT ||
                    listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) -> part(b.x + dx, b.y, b.z + dz) == ChorusPlant.Part.PLANT }
                assertTrue(held, "floating chorus at ${b.x} ${b.y} ${b.z}")
                assertTrue(bases.any { abs(it.x - b.x) < ChorusPlant.MAX_SPREAD && abs(it.z - b.z) < ChorusPlant.MAX_SPREAD })
                assertTrue(part(b.x, b.y - 1, b.z) != ChorusPlant.Part.FLOWER)
            }
            if (bases.isNotEmpty()) assertTrue(blocks.any { it.part == ChorusPlant.Part.FLOWER })
            assertEquals(blocks, grow(cx, cz), "not deterministic")
        }
        assertTrue(plants in 600..900, "$plants plants in 400 chunks")
    }

    @Test
    fun noPlantsOutsideHighlands() {
        for (cx in 0 until 10) for (cz in 0 until 10) {
            assertTrue(chorus.generate(cx, cz, 1L, flat) { _, _, _ -> "end_midlands" }.isEmpty())
        }
    }

    private companion object {
        const val SURFACE = 60
    }
}

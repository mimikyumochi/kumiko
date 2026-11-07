package faith.mimikyumochi.nether

import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.nether.biome.NetherBiomeSource
import faith.mimikyumochi.nether.terrain.NetherTerrainGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetherTest {
    private val versions = listOf(MCVersion.V1_16, MCVersion.V1_17, MCVersion.V1_18, MCVersion.V1_19, MCVersion.V26_3)

    @Test
    fun biomesMatchNetherSimulator() {
        val expected = listOf(
            Triple(0, 0, "nether_wastes"),
            Triple(-200, -200, "nether_wastes"),
            Triple(100, -150, "crimson_forest"),
            Triple(-150, 100, "crimson_forest"),
            Triple(60, 180, "warped_forest"),
            Triple(220, 200, "basalt_deltas"),
            Triple(-240, 240, "nether_wastes"),
            Triple(170, -240, "soul_sand_valley"),
            Triple(-60, -40, "crimson_forest"),
            Triple(40, 90, "nether_wastes"),
        )
        for (version in versions) {
            val source = NetherBiomeSource(5, version)
            for ((x, z, biome) in expected) assertEquals(biome, source.sampleBiome(x, 0, z), "$version at $x $z")
        }
    }

    @Test
    fun biomesIgnoreHeight() {
        val source = NetherBiomeSource(5)
        assertEquals(source.sampleBiome(10, 0, 10), source.sampleBiome(10, 20, 10))
    }

    @Test
    fun netherBiomesStartAt116() {
        assertFailsWith<IllegalArgumentException> { NetherBiomeSource(5, MCVersion.V1_13) }
    }

    @Test
    fun columnsHaveFloorCeilingAndLavaSea() {
        for (version in versions) {
            val terrain = NetherTerrainGenerator(NetherBiomeSource(5, version))
            for (x in -32 until 32 step 7) for (z in -32 until 32 step 7) {
                val column = terrain.generateColumn(x, z)
                assertEquals(128, column.size)
                assertEquals(terrain.defaultBlock, column.first(), "$version floor at $x $z")
                assertEquals(terrain.defaultBlock, column.last(), "$version ceiling at $x $z")
                column.forEachIndexed { y, block ->
                    if (y < NetherTerrainGenerator.SEA_LEVEL) assertTrue(block != terrain.defaultFluid, "$version air at $x $y $z")
                    else assertTrue(block != NetherTerrainGenerator.LAVA, "$version lava at $x $y $z")
                }
            }
        }
    }

    @Test
    fun terrainAgreesAcrossNoisePaths() {
        val legacy = NetherTerrainGenerator(NetherBiomeSource(5, MCVersion.V1_16))
        val router = NetherTerrainGenerator(NetherBiomeSource(5, MCVersion.V1_19))
        val float = NetherTerrainGenerator(NetherBiomeSource(5, MCVersion.V26_3))
        for (x in -48 until 48 step 3) for (z in -48 until 48 step 3) {
            val column = router.baseColumn(x, z)
            assertEquals(column, legacy.baseColumn(x, z), "1.16 at $x $z")
            assertEquals(column, float.baseColumn(x, z), "26.3 at $x $z")
        }
    }

    @Test
    fun cavesCarveOnlyTheRangeTheGameCarves() {
        for (version in versions) {
            val terrain = NetherTerrainGenerator(NetherBiomeSource(5, version))
            var removed = 0
            for (x in -64 until 64) for (z in -64 until 64) {
                val base = terrain.baseColumn(x, z)
                val carved = terrain.generateColumn(x, z)
                for (y in base.indices) {
                    if (base[y] == carved[y]) continue
                    removed++
                    assertEquals(terrain.defaultBlock, base[y], "$version only solid blocks carve at $x $y $z")
                    assertTrue(y in 2..120, "$version carved outside the range at $x $y $z")
                    assertEquals(if (y < NetherTerrainGenerator.SEA_LEVEL) NetherTerrainGenerator.LAVA else terrain.defaultFluid, carved[y])
                }
            }
            assertTrue(removed > 1000, "$version carved only $removed blocks")
        }
    }

    @Test
    fun cavesDoNotDependOnGenerationOrder() {
        val first = NetherTerrainGenerator(NetherBiomeSource(5))
        val second = NetherTerrainGenerator(NetherBiomeSource(5))
        val points = (-40 until 40 step 9).flatMap { x -> (-40 until 40 step 9).map { z -> x to z } }
        val forwards = points.map { (x, z) -> first.generateColumn(x, z) }
        val backwards = points.reversed().map { (x, z) -> second.generateColumn(x, z) }.reversed()
        assertEquals(forwards, backwards)
    }
}

package faith.mimikyumochi.end

import faith.mimikyumochi.core.testkit.FixtureSuite
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.end.features.EndPillars
import faith.mimikyumochi.end.features.GatewayExit
import faith.mimikyumochi.end.structures.EndCity
import faith.mimikyumochi.end.structures.EndCityGenerator
import faith.mimikyumochi.end.structures.EndGateway
import faith.mimikyumochi.end.structures.SmallEndIsland
import faith.mimikyumochi.end.terrain.EndTerrainGenerator
import kotlin.test.Test

class EndFixtures : FixtureSuite("end") {
    private val biomes = memo { key: Pair<Long, MCVersion> -> EndBiomeSource(key.first, key.second) }
    private val terrain = memo { key: Pair<Long, MCVersion> -> EndTerrainGenerator(biomes(key)) }

    init {
        check("biomes") { row ->
            mapOf("biome" to biomes(row.long("seed") to row.version()).sampleBiome(row.int("qx"), row.int("qz")))
        }

        check("heights") { row ->
            mapOf("height" to terrain(row.long("seed") to MCVersion.V1_19).getHeight(row.int("x"), row.int("z")))
        }

        check("blocks") { row ->
            val column = terrain(row.long("seed") to row.version()).generateColumn(row.int("x"), row.int("z"))
            mapOf("block" to if (column[row.int("y")] == 1) "end_stone" else "air")
        }

        check("gateways") { row ->
            val pos = EndGateway(row.version()).getPos(row.int("chunkX"), row.int("chunkZ"), row.long("seed"))
            mapOf("x" to pos?.x, "z" to pos?.z, "offsetY" to pos?.offsetY)
        }

        check("gateway_order") { row ->
            mapOf("order" to EndGateway().getEndIslandGatewayOrder(biomes(row.long("seed") to MCVersion.V1_19)))
        }

        check("end_cities") { row ->
            val seed = row.long("seed")
            val city = EndCity()
            val chunk = city.getInRegion(row.int("regionX"), row.int("regionZ"), seed)
            val biome = city.canSpawn(chunk.x, chunk.z, biomes(seed to row.version()))

            val generator = EndCityGenerator()
            val terrainOk = if (biome) generator.generate(terrain(seed to row.version()), chunk, row.version()) else null
            val ship = if (terrainOk == true) generator.hasShip() else null

            mapOf("chunkX" to chunk.x, "chunkZ" to chunk.z, "biome" to biome, "terrain" to terrainOk, "ship" to ship)
        }

        check("end_city_blocks") { row ->
            val seed = row.long("seed")
            val key = seed to row.version()
            val (x, y, z) = Triple(row.int("x"), row.int("y"), row.int("z"))
            val city = EndCity()
            val regionX = Math.floorDiv(x shr 4, city.spacing)
            val regionZ = Math.floorDiv(z shr 4, city.spacing)
            var block = "none"
            for (rx in regionX - 1..regionX + 1) for (rz in regionZ - 1..regionZ + 1) {
                val chunk = city.getInRegion(rx, rz, seed)
                if (!city.canSpawn(chunk.x, chunk.z, biomes(key))) continue
                val generator = EndCityGenerator()
                if (!generator.generate(terrain(key), chunk, row.version())) continue
                for (piece in generator.pieces) for (b in piece.blocks()) {
                    if (b.pos.x == x && b.pos.y == y && b.pos.z == z) block = b.state.name
                }
            }
            mapOf("block" to block)
        }

        check("small_islands") { row ->
            val islands = SmallEndIsland(row.version()).getIslands(row.int("chunkX"), row.int("chunkZ"), row.long("seed"))
            mapOf("islands" to islands.map { "${it.x}:${it.y}:${it.z}:${it.radius}" })
        }

        check("zoomed_biomes") { row ->
            mapOf("biome" to biomes(row.long("seed") to row.version()).biomeAt(row.int("x"), row.int("y"), row.int("z")))
        }

        check("placed_islands") { row ->
            val islands = SmallEndIsland(row.version())
                .getPlacedIslands(row.int("chunkX"), row.int("chunkZ"), biomes(row.long("seed") to row.version()))
            mapOf("islands" to islands.map { "${it.x}:${it.y}:${it.z}:${it.radius}" })
        }

        check("pillars") { row ->
            val pillar = EndPillars.getPillarsForSeed(row.long("seed"))[row.int("i")]
            mapOf(
                "x" to pillar.x, "z" to pillar.z, "radius" to pillar.radius,
                "height" to pillar.height, "guarded" to pillar.guarded,
            )
        }

        check("gateway_exits") { row ->
            val key = row.long("seed") to row.version()
            val gateways = EndGateway()
            val gateway = gateways.linkedGateways[gateways.getEndIslandGatewayOrder(biomes(key))[row.int("n") - 1]]
            val exit = GatewayExit(terrain(key)).find(gateway.x, gateway.z)
            val island = exit.island?.let { "${it.x}:${it.y}:${it.z}:${it.radius}" } ?: "none"
            mapOf("x" to exit.pos.x, "y" to exit.pos.y, "z" to exit.pos.z, "island" to island)
        }
    }

    @Test
    fun fixtures() = runAll()
}

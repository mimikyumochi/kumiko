package faith.mimikyumochi.core

import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.noise.SimplexNoise
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.rand.Xoroshiro128PlusPlus
import faith.mimikyumochi.core.testkit.FixtureSuite
import kotlin.test.Test

class CoreFixtures : FixtureSuite("core") {
    init {
        check("java_random") { row ->
            val rand = Rand(row.long("seed"))
            val bound = row.int("bound")
            val call: () -> Any = when (val method = row.string("method")) {
                "nextInt" -> if (bound == 0) ({ rand.nextInt() }) else ({ rand.nextInt(bound) })
                "nextLong" -> rand::nextLong
                "nextFloat" -> rand::nextFloat
                "nextDouble" -> rand::nextDouble
                "nextBoolean" -> rand::nextBoolean
                else -> error("unknown method $method")
            }
            repeat(row.int("skip")) { call() }
            mapOf("value" to call())
        }

        check("xoroshiro") { row ->
            val rand = Xoroshiro128PlusPlus(row.long("seed"))
            val bound = row.int("bound")
            val call: () -> Any = when (val method = row.string("method")) {
                "nextLong" -> rand::nextLong
                "nextInt" -> ({ rand.nextInt(bound) })
                "nextIntJ" -> ({ rand.nextIntJ(bound) })
                "nextFloat" -> rand::nextFloat
                else -> error("unknown method $method")
            }
            repeat(row.int("skip")) { call() }
            mapOf("value" to call())
        }

        check("block_to_chunk") { row ->
            val chunk = BPos(row.int("blockX"), 0, row.int("blockZ")).toChunkPos()
            mapOf("chunkX" to chunk.x, "chunkZ" to chunk.z)
        }

        val simplex = memo { seed: Long -> SimplexNoise(Rand(seed)) }
        check("simplex") { row ->
            mapOf("value" to simplex(row.long("seed")).sample3D(row.double("x"), row.double("y"), row.double("z")))
        }
    }

    @Test
    fun fixtures() = runAll()
}

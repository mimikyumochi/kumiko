package faith.mimikyumochi.end.structures

import faith.mimikyumochi.core.rand.Xoroshiro128PlusPlus
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.BiomeSource
import kotlin.math.abs

class ChorusPlant(val version: MCVersion = MCVersion.LATEST) {
    interface World {
        fun isAir(x: Int, y: Int, z: Int): Boolean

        fun isEndStone(x: Int, y: Int, z: Int): Boolean

        fun surface(x: Int, z: Int): Int
    }

    enum class Part { PLANT, FLOWER }

    data class Block(val x: Int, val y: Int, val z: Int, val part: Part)

    val isSupported: Boolean get() = version >= MCVersion.V1_18

    fun generate(chunkX: Int, chunkZ: Int, worldSeed: Long, biomeSource: BiomeSource, world: World): List<Block> =
        generate(chunkX, chunkZ, worldSeed, world) { x, y, z -> biomeSource.biomeAt(x, y, z) }

    fun generate(chunkX: Int, chunkZ: Int, worldSeed: Long, world: World, biomeAt: (Int, Int, Int) -> String): List<Block> {
        if (!isSupported) return emptyList()
        val blockX = chunkX * 16
        val blockZ = chunkZ * 16
        val random = Xoroshiro128PlusPlus()
        random.setDecoratorSeed(blockX, blockZ, worldSeed, SALT)

        val count = random.nextIntJ(5)
        if (count == 0) return emptyList()
        val grower = Grower(world, random)
        repeat(count) {
            val x = blockX + random.nextIntJ(16)
            val z = blockZ + random.nextIntJ(16)
            val y = grower.surface(x, z)
            if (biomeAt(x, y, z) != BIOME) return@repeat
            if (grower.isAir(x, y, z) && grower.isEndStone(x, y - 1, z)) {
                grower.set(x, y, z, Part.PLANT)
                grower.grow(x, y, z, x, z, 0)
            }
        }
        return grower.blocks()
    }

    private class Grower(private val world: World, private val random: Xoroshiro128PlusPlus) {
        private val placed = LinkedHashMap<Long, Block>()
        private val tops = HashMap<Long, Int>()

        fun isAir(x: Int, y: Int, z: Int) = key(x, y, z) !in placed && world.isAir(x, y, z)

        fun isEndStone(x: Int, y: Int, z: Int) = key(x, y, z) !in placed && world.isEndStone(x, y, z)

        fun surface(x: Int, z: Int) = maxOf(world.surface(x, z), (tops[column(x, z)] ?: -1) + 1)

        fun set(x: Int, y: Int, z: Int, part: Part) {
            placed[key(x, y, z)] = Block(x, y, z, part)
            tops.merge(column(x, z), y, ::maxOf)
        }

        fun blocks(): List<Block> = placed.values.toList()

        fun grow(x: Int, y: Int, z: Int, originX: Int, originZ: Int, iterations: Int) {
            var height = random.nextIntJ(4) + 1
            if (iterations == 0) height++
            for (j in 0 until height) {
                val stemY = y + j + 1
                if (!neighboursAir(x, stemY, z, null)) return
                set(x, stemY, z, Part.PLANT)
                set(x, stemY - 1, z, Part.PLANT)
            }

            var branched = false
            if (iterations < 4) {
                var tries = random.nextIntJ(4)
                if (iterations == 0) tries++
                repeat(tries) {
                    val d = HORIZONTAL[random.nextIntJ(4)]
                    val bx = x + d.dx
                    val by = y + height
                    val bz = z + d.dz
                    if (abs(bx - originX) < MAX_SPREAD && abs(bz - originZ) < MAX_SPREAD &&
                        isAir(bx, by, bz) && isAir(bx, by - 1, bz) && neighboursAir(bx, by, bz, d.opposite)
                    ) {
                        branched = true
                        set(bx, by, bz, Part.PLANT)
                        set(bx - d.dx, by, bz - d.dz, Part.PLANT)
                        grow(bx, by, bz, originX, originZ, iterations + 1)
                    }
                }
            }
            if (!branched) set(x, y + height, z, Part.FLOWER)
        }

        private fun neighboursAir(x: Int, y: Int, z: Int, except: Direction?) =
            HORIZONTAL.all { it == except || isAir(x + it.dx, y, z + it.dz) }

        private fun key(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

        private fun column(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }

    private enum class Direction(val dx: Int, val dz: Int) {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        val opposite: Direction get() = entries[(ordinal + 2) % 4]
    }

    companion object {
        const val BIOME = "end_highlands"

        const val SALT = 90000

        const val MAX_SPREAD = 8

        private val HORIZONTAL = Direction.entries
    }
}

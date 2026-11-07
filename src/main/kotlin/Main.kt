package faith.mimikyumochi

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.addScene
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.scene.Node
import faith.mimikyumochi.core.block.CPos
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.features.EndPillars
import faith.mimikyumochi.end.features.EndPodium
import faith.mimikyumochi.end.features.GatewayExit
import java.util.concurrent.CompletableFuture
import faith.mimikyumochi.end.structures.EndCity
import faith.mimikyumochi.end.structures.EndCityGenerator
import faith.mimikyumochi.end.structures.EndGateway
import faith.mimikyumochi.core.block.BlockBox
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import faith.mimikyumochi.end.structures.SmallEndIsland
import faith.mimikyumochi.end.structures.ChorusPlant
import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.block.BlockDirection
import faith.mimikyumochi.end.terrain.EndTerrainGenerator

const val SEED = 1L

const val CHUNK_WIDTH = 16
const val WORLD_HEIGHT = 256

const val AIR = 0
const val END_STONE = 1
const val OBSIDIAN = 2
const val BEDROCK = 3
const val IRON_BARS = 4
const val WALL_TORCH_NORTH = 5
const val WALL_TORCH_SOUTH = 6
const val WALL_TORCH_WEST = 7
const val WALL_TORCH_EAST = 8
const val END_GATEWAY = 9
const val NETHERRACK = CITY_ENTITY_LAST + 1
const val LAVA = CITY_ENTITY_LAST + 2

val BLOCK_TEXTURES = listOf(
    "block/end_stone", "block/obsidian", "block/bedrock", "block/iron_bars", "block/torch",
    "block/purpur_block", "block/end_stone_bricks", "block/purpur_pillar_side", "block/purpur_pillar_top", "block/magenta_stained_glass",
    "block/end_rod", "block/ladder", "block/chorus_plant", "block/chorus_flower_dead",
    "block/brewing_stand", "block/brewing_stand_base", "block/item_frame", "block/birch_planks", "item/elytra",
    "block/netherrack",
)

val ATLAS_TILES: Int get() = BLOCK_TEXTURES.size + CityEntityModels.cuts.size

fun blockTile(block: Int, face: Int = 0): Int = when (block) {
    END_STONE -> 0
    OBSIDIAN -> 1
    BEDROCK -> 2
    IRON_BARS -> 3
    in WALL_TORCH_NORTH..WALL_TORCH_EAST -> 4
    PURPUR_BLOCK, PURPUR_SLAB_BOTTOM, PURPUR_SLAB_TOP, in PURPUR_STAIRS..PURPUR_STAIRS_LAST -> 5
    END_STONE_BRICKS -> 6
    PURPUR_PILLAR_X, PURPUR_PILLAR_Y, PURPUR_PILLAR_Z -> if (FACES[face].alongAxis(block)) 8 else 7
    MAGENTA_STAINED_GLASS -> 9
    in END_ROD_DOWN..END_ROD_EAST -> 10
    in LADDER_NORTH..LADDER_EAST -> 11
    CHORUS_PLANT -> 12
    CHORUS_FLOWER -> 13
    NETHERRACK -> 19
    else -> 0
}

private fun Face.alongAxis(pillar: Int) = when (pillar) {
    PURPUR_PILLAR_X -> dx != 0
    PURPUR_PILLAR_Z -> dz != 0
    else -> dy != 0
}

fun pillarSideUv(block: Int, face: Face, u: Float, v: Float, out: MutableVec2f): MutableVec2f {
    val turned = when (block) {
        PURPUR_PILLAR_X -> face.dx == 0
        PURPUR_PILLAR_Z -> face.dx != 0
        else -> false
    }
    return if (turned) tileUv(block, v, 1f - u, out, face) else tileUv(block, u, v, out, face)
}

fun isOpaque(block: Int): Boolean = when (block) {
    END_STONE, OBSIDIAN, BEDROCK, PURPUR_BLOCK, END_STONE_BRICKS, PURPUR_PILLAR_X, PURPUR_PILLAR_Y, PURPUR_PILLAR_Z, NETHERRACK -> true
    else -> false
}

const val INNER_GATEWAY_Y = 75

class InnerGateway(val x: Int, val z: Int, val number: Int)

class IslandFeatures(
    private val terrain: EndTerrainGenerator,
    val showExits: Boolean = false,
) {
    val pillars = EndPillars.getPillarsForSeed(terrain.worldSeed)
    val podiumY = EndPodium.getY(terrain)

    private val gateways = EndGateway(terrain.version)
    private val smallIslands = SmallEndIsland(terrain.version)

    fun smallIslands(chunkX: Int, chunkZ: Int): List<SmallEndIsland.Island> = placedIslands.getOrPut(chunkKey(chunkX, chunkZ)) {
        if (smallIslands.getIslands(chunkX, chunkZ, terrain.worldSeed).isEmpty()) emptyList()
        else smallIslands.getPlacedIslands(chunkX, chunkZ, terrain.biomeSource)
    }

    private val placedIslands = ConcurrentHashMap<Long, List<SmallEndIsland.Island>>()
    private val outerGateways = ConcurrentHashMap<Long, Optional<BPos>>()
    private val chorusPlans = ConcurrentHashMap<Long, List<ChorusPlant.Block>>()
    private val chorusFeature = ChorusPlant(terrain.version)

    fun chorus(chunkX: Int, chunkZ: Int): List<ChorusPlant.Block> = chorusPlans.getOrPut(chunkKey(chunkX, chunkZ)) {
        val columns = HashMap<Long, IntArray>()
        fun column(x: Int, z: Int) = columns.getOrPut(chunkKey(x, z)) { generateBase(x, z, 1, terrain, this)[0][0] }
        val round = chorusRound(chunkX, chunkZ)
        val earlier = HashSet<Long>()
        val earlierTops = HashMap<Long, Int>()
        for (cx in chunkX - 1..chunkX + 1) for (cz in chunkZ - 1..chunkZ + 1) {
            if (chorusRound(cx, cz) >= round) continue
            for (b in chorus(cx, cz)) {
                earlier += blockKey(b.x, b.y, b.z)
                earlierTops.merge(chunkKey(b.x, b.z), b.y, ::maxOf)
            }
        }
        val world = object : ChorusPlant.World {
            override fun isAir(x: Int, y: Int, z: Int) =
                y !in 0 until WORLD_HEIGHT || (column(x, z)[y] == AIR && blockKey(x, y, z) !in earlier)
            override fun isEndStone(x: Int, y: Int, z: Int) = y in 0 until WORLD_HEIGHT && column(x, z)[y] == END_STONE
            override fun surface(x: Int, z: Int) =
                maxOf(column(x, z).indexOfLast(::blocksMotion) + 1, (earlierTops[chunkKey(x, z)] ?: -1) + 1)
        }
        chorusFeature.generate(chunkX, chunkZ, terrain.worldSeed, terrain.biomeSource, world)
    }

    private fun chorusRound(chunkX: Int, chunkZ: Int) = (chunkX and 1) + 2 * (chunkZ and 1)

    private fun blockKey(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

    private fun chunkKey(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    val innerGateways: List<InnerGateway> = gateways.getEndIslandGatewayOrder(terrain.biomeSource)
        .mapIndexed { n, i -> gateways.linkedGateways[i].let { InnerGateway(it.x, it.z, n + 1) } }

    val exits: CompletableFuture<List<GatewayExit.Exit>> = CompletableFuture.supplyAsync {
        val finder = GatewayExit(terrain)
        innerGateways.map { finder.find(it.x, it.z) }
    }

    fun mayHoldExit(chunkX: Int, chunkZ: Int): Boolean {
        val x = chunkX * CHUNK_WIDTH + CHUNK_WIDTH / 2
        val z = chunkZ * CHUNK_WIDTH + CHUNK_WIDTH / 2
        return x.toLong() * x + z.toLong() * z >= 700L * 700L
    }

    private val endCity = EndCity()

    private val cityPlans = ConcurrentHashMap<Long, Optional<CityPlan>>()

    fun cities(chunkX: Int, chunkZ: Int): List<CityPlan> {
        val minX = chunkX * CHUNK_WIDTH - 1
        val minZ = chunkZ * CHUNK_WIDTH - 1
        return cities(minX, minZ, minX + CHUNK_WIDTH + 1, minZ + CHUNK_WIDTH + 1)
    }

    fun cities(minX: Int, minZ: Int, maxX: Int, maxZ: Int): List<CityPlan> {
        val regionX = Math.floorDiv(Math.floorDiv(minX + 1, CHUNK_WIDTH), endCity.spacing)
        val regionZ = Math.floorDiv(Math.floorDiv(minZ + 1, CHUNK_WIDTH), endCity.spacing)
        val near = ArrayList<CityPlan>()
        for (rx in regionX - 1..regionX + 1) for (rz in regionZ - 1..regionZ + 1) {
            val key = (rx.toLong() shl 32) or (rz.toLong() and 0xFFFFFFFFL)
            val city = cityPlans.computeIfAbsent(key) { Optional.ofNullable(planCity(rx, rz)) }.orElse(null) ?: continue
            if (city.bounds.minX <= maxX && city.bounds.maxX >= minX && city.bounds.minZ <= maxZ && city.bounds.maxZ >= minZ) near += city
        }
        return near
    }

    private fun planCity(regionX: Int, regionZ: Int): CityPlan? {
        val chunk = endCity.getInRegion(regionX, regionZ, terrain.worldSeed)
        if (!endCity.canSpawn(chunk.x, chunk.z, terrain.biomeSource)) return null
        val generator = EndCityGenerator()
        if (!generator.generate(terrain, chunk, terrain.version)) return null
        val entities = generator.pieces.flatMap { it.entities() }.map { entity ->
            val id = when (entity) {
                is EndCityGenerator.Entity.Shulker -> SHULKER
                is EndCityGenerator.Entity.ElytraFrame ->
                    ELYTRA_FRAME_NORTH + listOf(BlockDirection.NORTH, BlockDirection.SOUTH, BlockDirection.WEST, BlockDirection.EAST).indexOf(entity.facing)
            }
            CityEntity(entity.pos.x, entity.pos.y, entity.pos.z, id)
        }
        return CityPlan(entities = entities, pieces = generator.pieces.map { piece ->
            val blocks = piece.blocks().toList()
            CityPiece(
                piece.box.copy(),
                IntArray(blocks.size) { blocks[it].pos.x },
                IntArray(blocks.size) { blocks[it].pos.y },
                IntArray(blocks.size) { blocks[it].pos.z },
                IntArray(blocks.size) { cityBlockId(blocks[it].state) },
            )
        })
    }

    fun outerGateway(chunkX: Int, chunkZ: Int): BPos? =
        outerGateways.getOrPut(chunkKey(chunkX, chunkZ)) { Optional.ofNullable(findOuterGateway(chunkX, chunkZ)) }.orElse(null)

    private fun findOuterGateway(chunkX: Int, chunkZ: Int): BPos? {
        val pos = gateways.getPos(chunkX, chunkZ, terrain.worldSeed) ?: return null
        val surface = terrain.generateColumn(pos.x, pos.z).lastIndexOf(END_STONE) + 1
        val y = surface + pos.offsetY
        return if (gateways.isPlacedAt(pos.x, y, pos.z, terrain.biomeSource)) BPos(pos.x, y, pos.z) else null
    }
}

class CityPiece(val box: BlockBox, val x: IntArray, val y: IntArray, val z: IntArray, val block: IntArray)

class CityEntity(val x: Int, val y: Int, val z: Int, val block: Int)

class CityPlan(val pieces: List<CityPiece>, val entities: List<CityEntity> = emptyList()) {
    val bounds = BlockBox(
        pieces.minOf { it.box.minX }, pieces.minOf { it.box.minY }, pieces.minOf { it.box.minZ },
        pieces.maxOf { it.box.maxX }, pieces.maxOf { it.box.maxY }, pieces.maxOf { it.box.maxZ },
    )
}

val SKY_COLOUR = Mimikyu.SKY

class Chunk(val pos: CPos, private val blocks: Array<Array<IntArray>>) {
    fun blockAt(x: Int, y: Int, z: Int): Int =
        if (y in 0 until WORLD_HEIGHT) blocks[x + 1][z + 1][y] else AIR

    fun isOpaque(x: Int, y: Int, z: Int): Boolean = isOpaque(blockAt(x, y, z))

    val topY: Int = blocks.maxOf { columns -> columns.maxOf { column -> column.indexOfLast { it != AIR } } }

    val isEmpty: Boolean
        get() = (1..CHUNK_WIDTH).all { x -> (1..CHUNK_WIDTH).all { z -> blocks[x][z].all { it == AIR } } }
}

fun generateChunk(chunkX: Int, chunkZ: Int, terrain: EndTerrainGenerator, features: IslandFeatures): Chunk {
    val originX = chunkX * CHUNK_WIDTH - 1
    val originZ = chunkZ * CHUNK_WIDTH - 1
    val blocks = generateBase(originX, originZ, CHUNK_WIDTH + 2, terrain, features)
    for (cx in chunkX - 1..chunkX + 1) for (cz in chunkZ - 1..chunkZ + 1) {
        for (b in features.chorus(cx, cz)) {
            val x = b.x - originX
            val z = b.z - originZ
            if (x in blocks.indices && z in blocks.indices && b.y in 0 until WORLD_HEIGHT) {
                blocks[x][z][b.y] = if (b.part == ChorusPlant.Part.FLOWER) CHORUS_FLOWER else CHORUS_PLANT
            }
        }
    }
    stampEndPlatform(blocks, originX, originZ)
    if (features.showExits && features.mayHoldExit(chunkX, chunkZ)) {
        for (exit in features.exits.get()) {
            exit.island?.let { stampIsland(blocks, originX, originZ, it) }
            stampGateway(blocks, originX, originZ, exit.pos.x, exit.pos.y, exit.pos.z)
        }
    }
    return Chunk(CPos(chunkX, chunkZ), blocks)
}

private val END_SPAWN = BPos(100, 50, 0)

private fun stampEndPlatform(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int) {
    for (dx in -2..2) for (dz in -2..2) {
        val x = END_SPAWN.x + dx - originX
        val z = END_SPAWN.z + dz - originZ
        if (x !in blocks.indices || z !in blocks.indices) continue
        for (dy in -1..2) blocks[x][z][END_SPAWN.y - 1 + dy] = if (dy == -1) OBSIDIAN else AIR
    }
}

fun generateBase(originX: Int, originZ: Int, size: Int, terrain: EndTerrainGenerator, features: IslandFeatures): Array<Array<IntArray>> {
    val blocks = Array(size) { x ->
        Array(size) { z -> terrain.generateColumn(originX + x, originZ + z).toIntArray().copyOf(WORLD_HEIGHT) }
    }
    val last = size - 1
    val minChunkX = Math.floorDiv(originX - 7, CHUNK_WIDTH)
    val maxChunkX = Math.floorDiv(originX + last + 7, CHUNK_WIDTH)
    val minChunkZ = Math.floorDiv(originZ - 7, CHUNK_WIDTH)
    val maxChunkZ = Math.floorDiv(originZ + last + 7, CHUNK_WIDTH)
    for (cx in minChunkX..maxChunkX) for (cz in minChunkZ..maxChunkZ) {
        for (island in features.smallIslands(cx, cz)) stampIsland(blocks, originX, originZ, island)
    }
    for (city in features.cities(originX, originZ, originX + last, originZ + last)) stampCity(blocks, originX, originZ, city)
    for (pillar in features.pillars) stampPillar(blocks, originX, originZ, pillar)
    stampPodium(blocks, originX, originZ, features.podiumY)
    for (gateway in features.innerGateways) stampGateway(blocks, originX, originZ, gateway.x, INNER_GATEWAY_Y, gateway.z)
    for (cx in minChunkX..maxChunkX) for (cz in minChunkZ..maxChunkZ) {
        features.outerGateway(cx, cz)?.let { stampGateway(blocks, originX, originZ, it.x, it.y, it.z) }
    }
    return blocks
}

fun blocksMotion(block: Int): Boolean = when (block) {
    AIR, END_GATEWAY, in WALL_TORCH_NORTH..WALL_TORCH_EAST, in DRAGON_WALL_HEAD_NORTH..CITY_ENTITY_LAST -> false
    else -> true
}

private fun stampPodium(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int, centreY: Int) {
    val last = blocks.size - 1
    fun set(x: Int, y: Int, z: Int, block: Int) {
        if (x in originX..originX + last && z in originZ..originZ + last && y in 0 until WORLD_HEIGHT) {
            blocks[x - originX][z - originZ][y] = block
        }
    }

    for (dx in -4..4) for (dz in -4..4) for (dy in -1..32) {
        val block = when (EndPodium.partAt(dx, dy, dz, active = false) ?: continue) {
            EndPodium.Part.BEDROCK -> BEDROCK
            EndPodium.Part.END_STONE -> END_STONE
            EndPodium.Part.AIR, EndPodium.Part.PORTAL -> AIR
        }
        set(EndPodium.X + dx, centreY + dy, EndPodium.Z + dz, block)
    }
    for (dy in 0 until EndPodium.PILLAR_HEIGHT) set(EndPodium.X, centreY + dy, EndPodium.Z, BEDROCK)

    val torchY = centreY + EndPodium.TORCH_HEIGHT
    set(EndPodium.X, torchY, EndPodium.Z - 1, WALL_TORCH_NORTH)
    set(EndPodium.X, torchY, EndPodium.Z + 1, WALL_TORCH_SOUTH)
    set(EndPodium.X - 1, torchY, EndPodium.Z, WALL_TORCH_WEST)
    set(EndPodium.X + 1, torchY, EndPodium.Z, WALL_TORCH_EAST)
}

private fun stampCity(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int, city: CityPlan) {
    val last = blocks.size - 1
    for (piece in city.pieces) {
        val box = piece.box
        if (box.maxX < originX || box.minX > originX + last || box.maxZ < originZ || box.minZ > originZ + last) continue
        for (i in piece.block.indices) {
            val x = piece.x[i] - originX
            val z = piece.z[i] - originZ
            val y = piece.y[i]
            if (x in 0..last && z in 0..last && y in 0 until WORLD_HEIGHT) blocks[x][z][y] = piece.block[i]
        }
    }
    for (e in city.entities) {
        val x = e.x - originX
        val z = e.z - originZ
        if (x in 0..last && z in 0..last && e.y in 0 until WORLD_HEIGHT && blocks[x][z][e.y] == AIR) blocks[x][z][e.y] = e.block
    }
}

private fun stampIsland(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int, island: SmallEndIsland.Island) {
    val last = blocks.size - 1
    val reach = island.reach
    val minX = maxOf(island.x - reach, originX)
    val maxX = minOf(island.x + reach, originX + last)
    val minZ = maxOf(island.z - reach, originZ)
    val maxZ = minOf(island.z + reach, originZ + last)
    for (x in minX..maxX) for (z in minZ..maxZ) {
        val column = blocks[x - originX][z - originZ]
        for (i in island.layers.indices) {
            val y = island.y - i
            if (y in 0 until WORLD_HEIGHT && island.isBlock(x - island.x, -i, z - island.z)) column[y] = END_STONE
        }
    }
}

private fun stampGateway(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int, x: Int, y: Int, z: Int) {
    val last = blocks.size - 1
    for (dx in -1..1) for (dz in -1..1) for (dy in -2..2) {
        val bx = x + dx
        val bz = z + dz
        val by = y + dy
        if (bx !in originX..originX + last || bz !in originZ..originZ + last || by !in 0 until WORLD_HEIGHT) continue
        val sameX = dx == 0
        val sameZ = dz == 0
        val end = kotlin.math.abs(dy) == 2
        blocks[bx - originX][bz - originZ][by] = when {
            sameX && dy == 0 && sameZ -> END_GATEWAY
            dy == 0 -> AIR
            end && sameX && sameZ -> BEDROCK
            (sameX || sameZ) && !end -> BEDROCK
            else -> AIR
        }
    }
}

private fun stampPillar(blocks: Array<Array<IntArray>>, originX: Int, originZ: Int, pillar: EndPillars.Pillar) {
    val r = pillar.radius
    val last = blocks.size - 1
    val minX = maxOf(pillar.x - r, originX)
    val maxX = minOf(pillar.x + r, originX + last)
    val minZ = maxOf(pillar.z - r, originZ)
    val maxZ = minOf(pillar.z + r, originZ + last)
    if (minX > maxX || minZ > maxZ) return

    val top = minOf(pillar.height + 10, WORLD_HEIGHT - 1)
    for (x in minX..maxX) for (z in minZ..maxZ) {
        val column = blocks[x - originX][z - originZ]
        val dx = x - pillar.x
        val dz = z - pillar.z
        val inCircle = dx * dx + dz * dz <= r * r + 1
        for (y in 0..top) {
            if (inCircle && y < pillar.height) column[y] = OBSIDIAN
            else if (y > 65) column[y] = AIR
        }
    }

    if (pillar.guarded) {
        for (dx in -2..2) for (dz in -2..2) for (dy in 0..3) {
            if (kotlin.math.abs(dx) != 2 && kotlin.math.abs(dz) != 2 && dy != 3) continue
            val x = pillar.x + dx
            val z = pillar.z + dz
            val y = pillar.height + dy
            if (x in originX..originX + last && z in originZ..originZ + last && y < WORLD_HEIGHT) {
                blocks[x - originX][z - originZ][y] = IRON_BARS
            }
        }
    }

    if (pillar.x in minX..maxX && pillar.z in minZ..maxZ && pillar.height < WORLD_HEIGHT) {
        blocks[pillar.x - originX][pillar.z - originZ][pillar.height] = BEDROCK
    }
}

class Face(val dx: Int, val dy: Int, val dz: Int, val brightness: Float, vararg val corners: Vec3f) {
    val normal = Vec3f(dx.toFloat(), dy.toFloat(), dz.toFloat())

    val aoNeighbours: List<IntArray> = corners.map { c ->
        val n = intArrayOf(dx, dy, dz)
        val (a, b) = (0..2).filter { n[it] == 0 }
        val pos = floatArrayOf(c.x, c.y, c.z)
        val towardsA = if (pos[a] > 0.5f) 1 else -1
        val towardsB = if (pos[b] > 0.5f) 1 else -1
        val side1 = n.copyOf().also { it[a] += towardsA }
        val side2 = n.copyOf().also { it[b] += towardsB }
        val diagonal = side1.copyOf().also { it[b] += towardsB }
        side1 + side2 + diagonal
    }

    val uvs = corners.map { c -> cubeUv(dx, dy, dz, c) }
}

fun cubeUv(dx: Int, dy: Int, dz: Int, c: Vec3f): Vec2f = when {
    dy > 0 -> Vec2f(c.x, c.z)
    dy < 0 -> Vec2f(c.x, 1f - c.z)
    dz > 0 -> Vec2f(c.x, 1f - c.y)
    dz < 0 -> Vec2f(1f - c.x, 1f - c.y)
    dx > 0 -> Vec2f(1f - c.z, 1f - c.y)
    else -> Vec2f(c.z, 1f - c.y)
}

val FACES = listOf(
    Face(0, 1, 0, 1.0f, Vec3f(0f, 1f, 1f), Vec3f(1f, 1f, 1f), Vec3f(1f, 1f, 0f), Vec3f(0f, 1f, 0f)),
    Face(0, -1, 0, 0.5f, Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f), Vec3f(1f, 0f, 1f), Vec3f(0f, 0f, 1f)),
    Face(0, 0, 1, 0.8f, Vec3f(0f, 0f, 1f), Vec3f(1f, 0f, 1f), Vec3f(1f, 1f, 1f), Vec3f(0f, 1f, 1f)),
    Face(0, 0, -1, 0.8f, Vec3f(0f, 1f, 0f), Vec3f(1f, 1f, 0f), Vec3f(1f, 0f, 0f), Vec3f(0f, 0f, 0f)),
    Face(1, 0, 0, 0.6f, Vec3f(1f, 0f, 0f), Vec3f(1f, 1f, 0f), Vec3f(1f, 1f, 1f), Vec3f(1f, 0f, 1f)),
    Face(-1, 0, 0, 0.6f, Vec3f(0f, 0f, 1f), Vec3f(0f, 1f, 1f), Vec3f(0f, 1f, 0f), Vec3f(0f, 0f, 0f)),
)

fun main() = KoolApplication(KoolConfigJvm(windowTitle = "kumiko - seed: $SEED")) {
    val blockAtlas = loadBlockAtlas()
    val portalShader = PortalShader.load().apply { fogColor = SKY_COLOUR }
    val lavaShader = LavaShader.load().apply { fogColor = SKY_COLOUR }

    lateinit var camera: FlyCamera
    lateinit var chunks: ChunkManager
    lateinit var hud: Hud
    lateinit var gatewayLabels: GatewayLabels

    addScene {
        camera = FlyCamera(this)
        camera.goTo(0.0, 200.0, 0.0, yaw = 0f, pitch = 90f)

        val world = Node("world")
        addNode(world)

        clearColor = ClearColorFill(SKY_COLOUR)
        val blockShader = BlockShader(blockAtlas, ATLAS_TILES).apply { fogColor = SKY_COLOUR }
        val glassShader = BlockShader(blockAtlas, ATLAS_TILES, translucent = true).apply { fogColor = SKY_COLOUR }

        chunks = ChunkManager(world, blockShader, portalShader, glassShader, lavaShader, camera, Dimension.END.terrain(SEED, MCVersion.LATEST))
        onUpdate { chunks.update() }
        gatewayLabels = GatewayLabels(world, camera, chunks, blockShader)

        Screenshots(this, world) { hud.showMessage(it) }
    }

    addScene { hud = Hud(this, camera, chunks, SEED, gatewayLabels) }
}

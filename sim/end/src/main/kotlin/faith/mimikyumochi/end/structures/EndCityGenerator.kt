package faith.mimikyumochi.end.structures

import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.block.BlockBox
import faith.mimikyumochi.core.block.BlockDirection
import faith.mimikyumochi.core.block.BlockMirror
import faith.mimikyumochi.core.block.BlockRotation
import faith.mimikyumochi.core.block.CPos
import faith.mimikyumochi.core.rand.Rand
import faith.mimikyumochi.core.structure.StructureTemplate
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.terrain.TerrainGenerator

class EndCityGenerator {
    val pieces = mutableListOf<Piece>()
    private val towerBridgeGenerator = TowerBridgeGenerator()

    fun reset() {
        towerBridgeGenerator.init()
        pieces.clear()
    }

    fun start(start: BPos, rotation: BlockRotation, pieces: MutableList<Piece>, rand: Rand) {
        towerBridgeGenerator.init()
        var base = Piece("base_floor", start, rotation, overwrite = true)
        pieces.add(base)
        base = addPiece(pieces, base, BPos(-1, 0, -1), "second_floor_1", rotation, overwrite = false)
        base = addPiece(pieces, base, BPos(-1, 4, -1), "third_floor_1",  rotation, overwrite = false)
        base = addPiece(pieces, base, BPos(-1, 8, -1), "third_roof",     rotation, overwrite = true)
        generateRecursively(towerGenerator, 1, base, null, pieces, rand)
    }

    fun generate(terrainGenerator: TerrainGenerator, chunk: CPos, version: MCVersion = MCVersion.LATEST): Boolean {
        reset()

        val rand = Rand(0)
        rand.setCarverSeed(terrainGenerator.worldSeed, chunk.x, chunk.z)

        val rotation = BlockRotation.getRandom(rand)
        val heightRotation =
            if (version >= MCVersion.V1_19) rotation
            else EndCity.getLegacyHeightRotation(chunk.x, chunk.z)

        val y = EndCity.getLowestYIn5by5BoxOffset7Blocks(terrainGenerator, chunk.x, chunk.z, heightRotation)

        if (y < 60) return false
        val start = BPos(chunk.x * 16 + 7, y, chunk.z * 16 + 7)

        start(start, rotation, pieces, rand)
        return true
    }

    fun generateRecursively(generator: StructureGenerator, depth: Int, template: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean {
        if (depth > 8) return false
        val local = mutableListOf<Piece>()
        if (!generator.generate(depth, template, pos, local, rand)) return false

        var blocking = false
        val genDepth = rand.nextInt()

        for (piece in local) {
            piece.genDepth = genDepth
            val collision = piece.findCollision(pieces)
            if (collision != null && collision.genDepth != template.genDepth) {
                blocking = true
                break
            }
        }
        if (blocking) return false
        pieces.addAll(local)
        return true
    }

    fun getPiece(prev: Piece, pos: BPos, name: String, rotation: BlockRotation, overrite: Boolean): Piece {
        val piece = Piece(name, prev.pos, rotation, overrite)
        val t1 = pos.transform(BlockMirror.NONE, prev.rotation, BPos(0, 0, 0))
        val t2 = BPos(0, 0, 0).transform(BlockMirror.NONE, piece.rotation, BPos(0, 0, 0))
        piece.move(t1.subtract(t2))
        return piece
    }

    fun addPiece(pieces: MutableList<Piece>, prev: Piece, pos: BPos, name: String, rotation: BlockRotation, overwrite: Boolean): Piece {
        val piece = getPiece(prev, pos, name, rotation, overwrite)
        pieces.add(piece)
        return piece
    }

    fun hasShip() = pieces.any { it.name == "ship" }

    fun getShip() = pieces.firstOrNull() { it.name == "ship" }

    fun getChestsPos()   = getLoot().filter { it.first.isChest }

    fun getSentriesPos() = getLoot().filter { it.first.isSentry }

    fun getLoot(): List<Pair<LootType, BPos>> {
        val result = mutableListOf<Pair<LootType, BPos>>()
        for (piece in pieces) {
            val loot = STRUCTURE_TO_LOOT[piece.name] ?: continue
            for ((lootType, offsets) in loot) {
                for (offset in offsets) {
                    val lootPos = piece.box.getRotated(piece.rotation).getInside(offset, piece.rotation)
                    result.add(lootType to lootPos)
                }
            }
        }
        return result
    }

    sealed class Entity(val pos: BPos) {
        class Shulker(pos: BPos) : Entity(pos)
        class ElytraFrame(pos: BPos, val facing: BlockDirection) : Entity(pos)
    }

    data class Piece(
        val name: String,
        var pos: BPos,
        val rotation: BlockRotation,
        val overwrite: Boolean,
        var genDepth: Int = 0,
        var box: BlockBox = BlockBox.getBoundingBox(pos, rotation, BPos(0, 0, 0), BlockMirror.NONE, STRUCTURE_SIZE[name]!!)
    ) {
        fun move(by: BPos) {
            pos = pos.add(by)
            box.move(by.x, by.y, by.z)
        }

        fun findCollision(others: List<Piece>): Piece? =
            others.firstOrNull { it.box.intersects(box) }

        fun entities(): List<Entity> = EndCityTemplates[name].markers(pos, rotation).mapNotNull { marker ->
            when {
                marker.metadata.startsWith("Chest") -> null
                marker.metadata.startsWith("Sentry") -> Entity.Shulker(marker.pos)
                marker.metadata.startsWith("Elytra") -> Entity.ElytraFrame(marker.pos, rotation.rotate(BlockDirection.SOUTH))
                else -> null
            }
        }

        fun blocks(): Sequence<StructureTemplate.Block> = EndCityTemplates[name].place(pos, rotation, overwrite)
    }

    interface StructureGenerator {
        fun init() {}
        fun generate(depth: Int, current: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean
    }

    private val fatTowerGenerator = object : StructureGenerator {
        val fatTowerBridges = listOf(
            BlockRotation.NONE             to BPos(4, -1, 0),
            BlockRotation.CLOCKWISE_90     to BPos(12, -1, 4),
            BlockRotation.CLOCKWISE_270    to BPos(0, -1, 8),
            BlockRotation.CLOCKWISE_180    to BPos(8, -1, 12)
        )

        override fun generate(depth: Int, current: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean {
            val rotation = current.rotation
            var base = addPiece(pieces, current, BPos(-3, 4, -3), "fat_tower_base",   rotation, overwrite = true)
            base     = addPiece(pieces, base, BPos(0, 4, 0), "fat_tower_middle", rotation, overwrite = true)
            var floor = 0
            while (floor < 2 && rand.nextInt(3) != 0) {
                base = addPiece(pieces, base, BPos(0, 8, 0), "fat_tower_middle", rotation, overwrite = true)
                for ((rotOffset, bridgePos) in fatTowerBridges) {
                    if (rand.nextBoolean()) {
                        val bridge = addPiece(pieces, base, bridgePos, "bridge_end", rotation.getRotated(rotOffset), overwrite = true)
                        generateRecursively(towerBridgeGenerator, depth + 1, bridge, null, pieces, rand)
                    }
                }
                floor++
            }
            addPiece(pieces, base, BPos(-2, 8, -2), "fat_tower_top", rotation, overwrite = true)
            return true
        }
    }

    private val houseTowerGenerator = object : StructureGenerator {
        override fun generate(depth: Int, current: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean {
            if (depth > 8) return false
            val rotation = current.rotation
            val base = addPiece(pieces, current, pos!!, "base_floor", rotation, overwrite = true)
            when (rand.nextInt(3)) {
                0 -> addPiece(pieces, base, BPos(-1, 4, -1), "base_roof", rotation, overwrite = true)
                1 -> {
                    val second = addPiece(pieces, base, BPos(-1, 0, -1), "second_floor_2", rotation, overwrite = false)
                    val roof   = addPiece(pieces, second, BPos(-1, 8, -1), "second_roof",   rotation, overwrite = false)
                    generateRecursively(towerGenerator, depth + 1, roof, null, pieces, rand)
                }
                else -> {
                    val second = addPiece(pieces, base, BPos(-1, 0, -1), "second_floor_2", rotation, overwrite = false)
                    val third  = addPiece(pieces, second, BPos(-1, 4, -1), "third_floor_2",  rotation, overwrite = false)
                    val roof   = addPiece(pieces, third, BPos(-1, 8, -1), "third_roof",     rotation, overwrite = true)
                    generateRecursively(towerGenerator, depth + 1, roof, null, pieces, rand)
                }
            }
            return true
        }
    }

    private inner class TowerBridgeGenerator : StructureGenerator {
        var shipCreated = false

        override fun init() { shipCreated = false }

        override fun generate(depth: Int, current: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean {
            val rotation = current.rotation
            val size = rand.nextInt(4) + 1
            var base = addPiece(pieces, current, BPos(0, 0, -4), "bridge_piece", rotation, overwrite = true)
            base.genDepth = -1
            var y = 0
            for (floor in 0 until size) {
                if (rand.nextBoolean()) {
                    base = addPiece(pieces, base, BPos(0, y, -4), "bridge_piece", rotation, overwrite = true)
                    y = 0
                } else {
                    if (rand.nextBoolean()) {
                        base = addPiece(pieces, base, BPos(0, y, -4), "bridge_steep_stairs",  rotation, overwrite = true)
                    } else {
                        base = addPiece(pieces, base, BPos(0, y, -8), "bridge_gentle_stairs", rotation, overwrite = true)
                    }
                    y = 4
                }
            }
            if (!shipCreated && rand.nextInt(10 - depth) == 0) {
                addPiece(pieces, base,
                    BPos(-8 + rand.nextInt(8), y, -70 + rand.nextInt(10)), "ship", rotation, overwrite = true)
                shipCreated = true
            } else if (!generateRecursively(houseTowerGenerator, depth + 1, base, BPos(-3, y + 1, -11), pieces, rand)) {
                return false
            }
            val end = addPiece(pieces, base,
                BPos(4, y, 0), "bridge_end", rotation.getRotated(BlockRotation.CLOCKWISE_180), overwrite = true)
            end.genDepth = -1
            return true
        }
    }

    private val towerGenerator = object : StructureGenerator {
        val towerBridges = listOf(
            BlockRotation.NONE                to BPos(1, -1, 0),
            BlockRotation.CLOCKWISE_90        to BPos(6, -1, 1),
            BlockRotation.CLOCKWISE_270       to BPos(0, -1, 5),
            BlockRotation.CLOCKWISE_180       to BPos(5, -1, 6)
        )

        override fun generate(depth: Int, current: Piece, pos: BPos?, pieces: MutableList<Piece>, rand: Rand): Boolean {
            val rotation = current.rotation
            var base = addPiece(pieces, current,
                BPos(3 + rand.nextInt(2), -3, 3 + rand.nextInt(2)), "tower_base",  rotation, overwrite = true)
            base     = addPiece(pieces, base, BPos(0, 7, 0), "tower_piece", rotation, overwrite = true)

            var currentFloor: Piece? = if (rand.nextInt(3) == 0) base else null
            val size = rand.nextInt(3) + 1
            for (floor in 0 until size) {
                base = addPiece(pieces, base, BPos(0, 4, 0), "tower_piece", rotation, overwrite = true)
                if (floor < size - 1 && rand.nextBoolean()) currentFloor = base
            }

            if (currentFloor != null) {
                for ((rotOffset, bridgePos) in towerBridges) {
                    if (rand.nextBoolean()) {
                        val bridge = addPiece(pieces, currentFloor, bridgePos, "bridge_end", rotation.getRotated(rotOffset), overwrite = true)
                        generateRecursively(towerBridgeGenerator, depth + 1, bridge, null, pieces, rand)
                    }
                }
            } else if (depth != 7) {
                return generateRecursively(fatTowerGenerator, depth + 1, base, null, pieces, rand)
            }
            addPiece(pieces, base, BPos(-1, 4, -1), "tower_top", rotation, overwrite = true)
            return true
        }
    }

    enum class LootType {
        BASE_FLOOR_SENTRY_1, BASE_FLOOR_SENTRY_2,
        FAT_TOWER_MIDDLE_SENTRY_1, FAT_TOWER_MIDDLE_SENTRY_2,
        FAT_TOWER_MIDDLE_SENTRY_3, FAT_TOWER_MIDDLE_SENTRY_4,
        SECOND_FLOOR_SENTRY,
        SHIP_SENTRY_1, SHIP_SENTRY_2, SHIP_SENTRY_3,
        THIRD_FLOOR_SENTRY_1, THIRD_FLOOR_SENTRY_2,
        TOWER_TOP_SENTRY,

        FAT_TOWER_TOP_CHEST_1, FAT_TOWER_TOP_CHEST_2,
        THIRD_FLOOR_CHEST,
        SHIP_CHEST_1, SHIP_CHEST_2,

        SHIP_ELYTRA;

        val isChest get() = this in setOf(FAT_TOWER_TOP_CHEST_1, FAT_TOWER_TOP_CHEST_2, THIRD_FLOOR_CHEST, SHIP_CHEST_1, SHIP_CHEST_2)
        val isSentry get() = !isChest && this != SHIP_ELYTRA
    }

    companion object {
        val STRUCTURE_SIZE = mapOf(
            "base_floor"           to BPos(10, 4, 10),
            "base_roof"            to BPos(12, 2, 12),
            "bridge_end"           to BPos(5, 6, 2),
            "bridge_gentle_stairs" to BPos(5, 7, 8),
            "bridge_piece"         to BPos(5, 6, 4),
            "bridge_steep_stairs"  to BPos(5, 7, 4),
            "fat_tower_base"       to BPos(13, 4, 13),
            "fat_tower_middle"     to BPos(13, 8, 13),
            "fat_tower_top"        to BPos(17, 6, 17),
            "second_floor_1"       to BPos(12, 8, 12),
            "second_floor_2"       to BPos(12, 8, 12),
            "second_roof"          to BPos(14, 2, 14),
            "ship"                 to BPos(13, 24, 29),
            "third_floor_1"        to BPos(14, 8, 14),
            "third_floor_2"        to BPos(14, 8, 14),
            "third_roof"           to BPos(16, 2, 16),
            "tower_base"           to BPos(7, 7, 7),
            "tower_floor"          to BPos(7, 4, 7),
            "tower_piece"          to BPos(7, 4, 7),
            "tower_top"            to BPos(9, 5, 9),
        )

        val STRUCTURE_TO_LOOT = mapOf(
            "base_floor" to mapOf(
                LootType.BASE_FLOOR_SENTRY_1 to listOf(BPos(3, 2, 9)),
                LootType.BASE_FLOOR_SENTRY_2 to listOf(BPos(6, 2, 9))
            ),
            "fat_tower_middle" to mapOf(
                LootType.FAT_TOWER_MIDDLE_SENTRY_1 to listOf(BPos(2, 2, 6)),
                LootType.FAT_TOWER_MIDDLE_SENTRY_2 to listOf(BPos(10, 2, 6)),
                LootType.FAT_TOWER_MIDDLE_SENTRY_3 to listOf(BPos(6, 6, 2)),
                LootType.FAT_TOWER_MIDDLE_SENTRY_4 to listOf(BPos(6, 6, 10))
            ),
            "fat_tower_top" to mapOf(
                LootType.FAT_TOWER_TOP_CHEST_1 to listOf(BPos(3, 2, 11)),
                LootType.FAT_TOWER_TOP_CHEST_2 to listOf(BPos(5, 2, 13))
            ),
            "second_floor_2" to mapOf(
                LootType.SECOND_FLOOR_SENTRY to listOf(BPos(8, 5, 6))
            ),
            "ship" to mapOf(
                LootType.SHIP_SENTRY_1 to listOf(BPos(6, 4, 8)),
                LootType.SHIP_SENTRY_2 to listOf(BPos(8, 6, 27)),
                LootType.SHIP_SENTRY_3 to listOf(BPos(4, 11, 27)),
                LootType.SHIP_CHEST_1  to listOf(BPos(5, 5, 7)),
                LootType.SHIP_CHEST_2  to listOf(BPos(7, 5, 7)),
                LootType.SHIP_ELYTRA   to listOf(BPos(6, 5, 7))
            ),
            "third_floor_2" to mapOf(
                LootType.THIRD_FLOOR_SENTRY_1 to listOf(BPos(2, 5, 2)),
                LootType.THIRD_FLOOR_SENTRY_2 to listOf(BPos(11, 5, 2)),
                LootType.THIRD_FLOOR_CHEST    to listOf(BPos(6, 6, 2))
            ),
            "tower_top" to mapOf(
                LootType.TOWER_TOP_SENTRY to listOf(BPos(4, 3, 4))
            ),
            "base_roof"            to emptyMap(),
            "bridge_end"           to emptyMap(),
            "bridge_gentle_stairs" to emptyMap(),
            "bridge_piece"         to emptyMap(),
            "bridge_steep_stairs"  to emptyMap(),
            "fat_tower_base"       to emptyMap(),
            "second_floor_1"       to emptyMap(),
            "second_roof"          to emptyMap(),
            "third_floor_1"        to emptyMap(),
            "third_roof"           to emptyMap(),
            "tower_base"           to emptyMap(),
            "tower_floor"          to emptyMap(),
            "tower_piece"          to emptyMap(),
        )
    }
}

package faith.mimikyumochi

import faith.mimikyumochi.core.structure.BlockState

const val PURPUR_BLOCK = 10
const val END_STONE_BRICKS = 11
const val PURPUR_PILLAR_X = 12
const val PURPUR_PILLAR_Y = 13
const val PURPUR_PILLAR_Z = 14
const val MAGENTA_STAINED_GLASS = 15
const val PURPUR_SLAB_BOTTOM = 16
const val PURPUR_SLAB_TOP = 17
const val END_ROD_DOWN = 18
const val END_ROD_EAST = 23
const val LADDER_NORTH = 24
const val LADDER_EAST = 27
const val PURPUR_STAIRS = 32
const val PURPUR_STAIRS_LAST = PURPUR_STAIRS + 39

const val CHORUS_PLANT = 72
const val CHORUS_FLOWER = 73

const val CHEST_NORTH = 74
const val ENDER_CHEST_NORTH = 78
const val BREWING_STAND = 82
const val DRAGON_WALL_HEAD_NORTH = 90
const val SHULKER = 94
const val ELYTRA_FRAME_NORTH = 95
const val CITY_ENTITY_LAST = ELYTRA_FRAME_NORTH + 3

private val ROD_FACINGS = listOf("down", "up", "north", "south", "west", "east")
private val FACINGS = listOf("north", "south", "west", "east")
private val STAIR_SHAPES = listOf("straight", "inner_left", "inner_right", "outer_left", "outer_right")

fun cityBlockId(state: BlockState): Int = when (state.name) {
    "minecraft:purpur_block" -> PURPUR_BLOCK
    "minecraft:end_stone_bricks" -> END_STONE_BRICKS
    "minecraft:obsidian" -> OBSIDIAN
    "minecraft:magenta_stained_glass" -> MAGENTA_STAINED_GLASS
    "minecraft:purpur_pillar" -> when (state["axis"]) { "x" -> PURPUR_PILLAR_X; "z" -> PURPUR_PILLAR_Z; else -> PURPUR_PILLAR_Y }
    "minecraft:purpur_slab" -> when (state["type"]) { "top" -> PURPUR_SLAB_TOP; "double" -> PURPUR_BLOCK; else -> PURPUR_SLAB_BOTTOM }
    "minecraft:end_rod" -> END_ROD_DOWN + ROD_FACINGS.indexOf(state["facing"]).coerceAtLeast(0)
    "minecraft:ladder" -> LADDER_NORTH + FACINGS.indexOf(state["facing"]).coerceAtLeast(0)
    "minecraft:chest" -> CHEST_NORTH + FACINGS.indexOf(state["facing"]).coerceAtLeast(0)
    "minecraft:ender_chest" -> ENDER_CHEST_NORTH + FACINGS.indexOf(state["facing"]).coerceAtLeast(0)
    "minecraft:dragon_wall_head" -> DRAGON_WALL_HEAD_NORTH + FACINGS.indexOf(state["facing"]).coerceAtLeast(0)
    "minecraft:brewing_stand" -> BREWING_STAND + (0 until 3).sumOf { if (state["has_bottle_$it"] == "true") 1 shl it else 0 }
    "minecraft:purpur_stairs" -> PURPUR_STAIRS +
        FACINGS.indexOf(state["facing"]).coerceAtLeast(0) * 10 +
        (if (state["half"] == "top") 5 else 0) +
        STAIR_SHAPES.indexOf(state["shape"]).coerceAtLeast(0)
    else -> AIR
}

fun cityModel(block: Int): List<Quad>? = when (block) {
    PURPUR_SLAB_BOTTOM -> SlabModel.BOTTOM
    PURPUR_SLAB_TOP -> SlabModel.TOP
    in END_ROD_DOWN..END_ROD_EAST -> EndRodModel.quads(block)
    in LADDER_NORTH..LADDER_EAST -> LadderModel.quads(block)
    in PURPUR_STAIRS..PURPUR_STAIRS_LAST -> StairsModel.quads(block)
    in CHEST_NORTH until ENDER_CHEST_NORTH -> CityEntityModels.chest(block)
    in ENDER_CHEST_NORTH until BREWING_STAND -> CityEntityModels.enderChest(block)
    in BREWING_STAND until DRAGON_WALL_HEAD_NORTH -> CityEntityModels.brewingStand(block)
    in DRAGON_WALL_HEAD_NORTH until SHULKER -> CityEntityModels.dragonWallHead(block)
    in ELYTRA_FRAME_NORTH..CITY_ENTITY_LAST -> CityEntityModels.elytraFrame(block)
    else -> null
}

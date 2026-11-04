package faith.mimikyumochi

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TextureCut(val texture: String, val x: Int, val y: Int, val w: Int, val h: Int)

object CityEntityModels {
    private val cutList = ArrayList<TextureCut>()
    private val cutIndex = HashMap<TextureCut, Int>()

    val cuts: List<TextureCut> get() = cutList

    private fun tile(cut: TextureCut): Int = cutIndex.getOrPut(cut) {
        cutList += cut
        BLOCK_TEXTURES.size + cutList.size - 1
    }

    private fun blockTile(name: String) = BLOCK_TEXTURES.indexOf(name).also { check(it >= 0) { "no block texture $name" } }

    private class ModelFace(val corners: List<Vec3f>, val uvs: List<Vec2f>)

    private fun box(u: Int, v: Int, x: Float, y: Float, z: Float, w: Int, h: Int, d: Int, mirror: Boolean = false): List<ModelFace> {
        var x0 = x
        var x1 = x + w
        if (mirror) x0 = x1.also { x1 = x0 }
        val y0 = y
        val y1 = y + h
        val z0 = z
        val z1 = z + d
        val p = listOf(
            Vec3f(x0, y0, z0), Vec3f(x1, y0, z0), Vec3f(x1, y1, z0), Vec3f(x0, y1, z0),
            Vec3f(x0, y0, z1), Vec3f(x1, y0, z1), Vec3f(x1, y1, z1), Vec3f(x0, y1, z1),
        )
        val l = u.toFloat()
        val m = l + d
        val n = m + w
        val o = n + w
        val q = n + d
        val r = v.toFloat()
        val s = r + d
        val t = s + h
        fun face(i0: Int, i1: Int, i2: Int, i3: Int, u1: Float, v1: Float, u2: Float, v2: Float): ModelFace {
            val corners = listOf(p[i0], p[i1], p[i2], p[i3])
            val uvs = listOf(Vec2f(u2, v1), Vec2f(u1, v1), Vec2f(u1, v2), Vec2f(u2, v2))
            return if (mirror) ModelFace(corners.reversed(), uvs.reversed()) else ModelFace(corners, uvs)
        }
        return listOf(
            face(5, 4, 0, 1, m, r, n, s),
            face(2, 3, 7, 6, n, s, o, r),
            face(0, 4, 7, 3, l, s, m, t),
            face(1, 0, 3, 2, m, s, n, t),
            face(5, 1, 2, 6, n, s, q, t),
            face(4, 5, 6, 7, q, s, q + w, t),
        )
    }

    private fun List<ModelFace>.toQuads(texture: String, transform: (Vec3f) -> Vec3f): List<Quad> = mapNotNull { f ->
        val minU = f.uvs.minOf { it.x }
        val minV = f.uvs.minOf { it.y }
        val w = (f.uvs.maxOf { it.x } - minU).roundToInt()
        val h = (f.uvs.maxOf { it.y } - minV).roundToInt()
        if (w == 0 || h == 0) return@mapNotNull null
        check(w <= TILE_SIZE && h <= TILE_SIZE) { "$texture face is ${w}x$h texels, more than a tile" }
        val tile = tile(TextureCut(texture, minU.roundToInt(), minV.roundToInt(), w, h))
        val corners = f.corners.map(transform)
        Quad(corners, f.uvs.map { Vec2f((it.x - minU) / TILE_SIZE, (it.y - minV) / TILE_SIZE) }, normalOf(corners), null, true, tile)
    }

    private fun normalOf(c: List<Vec3f>): Vec3f {
        val ax = c[1].x - c[0].x; val ay = c[1].y - c[0].y; val az = c[1].z - c[0].z
        val bx = c[2].x - c[0].x; val by = c[2].y - c[0].y; val bz = c[2].z - c[0].z
        val nx = ay * bz - az * by
        val ny = az * bx - ax * bz
        val nz = ax * by - ay * bx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        fun clean(f: Float) = (f / len).let { if (kotlin.math.abs(it) < 1e-4f) 0f else it }
        return Vec3f(clean(nx), clean(ny), clean(nz))
    }

    private val FROM_NORTH = intArrayOf(0, 2, 3, 1)

    private fun chest(texture: String): List<List<Quad>> {
        val faces = box(0, 19, 1f, 0f, 1f, 14, 10, 14) +
            box(0, 0, 1f, 9f, 1f, 14, 5, 14) +
            box(0, 0, 7f, 7f, 15f, 2, 4, 1)
        val south = faces.toQuads(texture) { Vec3f(it.x / 16f, it.y / 16f, it.z / 16f) }
        return listOf(2, 0, 1, 3).map { turns -> south.map { it.rotateY(turns) } }
    }

    private val chests = chest("entity/chest/normal")
    private val enderChests = chest("entity/chest/ender")

    fun chest(block: Int): List<Quad> = chests[block - CHEST_NORTH]
    fun enderChest(block: Int): List<Quad> = enderChests[block - ENDER_CHEST_NORTH]

    private val shulkerUp: List<Quad> = (
        box(0, 0, -8f, 8f, -8f, 16, 12, 16) +
            box(0, 28, -8f, 16f, -8f, 16, 8, 16)
        ).toQuads("entity/shulker/shulker") {
        val k = 0.9995f
        Vec3f(0.5f + k * (it.x / 16f), 0.5f + k * (1f - it.y / 16f), 0.5f + k * (-it.z / 16f))
    }

    private val shulkers = listOf(0 to 0, 2 to 0, 1 to 0, 1 to 2, 1 to 3, 1 to 1).map { (x, y) -> shulkerUp.map { it.rotate(x, y) } }

    fun shulker(facing: Int): List<Quad> = shulkers[facing]

    val SHULKER_FACINGS = listOf(intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, -1), intArrayOf(0, 0, 1), intArrayOf(-1, 0, 0), intArrayOf(1, 0, 0))

    private val dragonNorth: List<Quad> = run {
        val headY = -7.986666f * 0.75f
        val headScale = 0.75f
        fun place(x: Float, y: Float, z: Float) = Vec3f(0.5f - x / 16f, 0.25f - y / 16f, 0.75f + z / 16f)
        val head = (
            box(176, 44, -6f, -1f, -24f, 12, 5, 16) +
                box(112, 30, -8f, -8f, -10f, 16, 16, 16) +
                box(0, 0, -5f, -12f, -4f, 2, 4, 6, mirror = true) +
                box(112, 0, -5f, -3f, -22f, 2, 2, 4, mirror = true) +
                box(0, 0, 3f, -12f, -4f, 2, 4, 6) +
                box(112, 0, 3f, -3f, -22f, 2, 2, 4)
            ).toQuads("entity/enderdragon/dragon") { place(headScale * it.x, headY + headScale * it.y, headScale * it.z) }
        val angle = 0.2f
        val jaw = box(176, 65, -6f, 0f, -16f, 12, 4, 16).toQuads("entity/enderdragon/dragon") {
            val y = it.y * cos(angle) - it.z * sin(angle)
            val z = it.y * sin(angle) + it.z * cos(angle)
            place(headScale * it.x, headY + headScale * (4f + y), headScale * (-8f + z))
        }
        head + jaw
    }
    private val dragonHeads = FROM_NORTH.map { turns -> dragonNorth.map { it.rotateY(turns) } }

    fun dragonWallHead(block: Int): List<Quad> = dragonHeads[block - DRAGON_WALL_HEAD_NORTH]

    private val brewingBase: List<Quad> = run {
        val stand = blockTile("block/brewing_stand")
        val base = blockTile("block/brewing_stand_base")
        element(
            Vec3f(7f, 0f, 7f), Vec3f(9f, 14f, 9f),
            face(Side.DOWN, 7, 7, 9, 9), face(Side.UP, 7, 7, 9, 9),
            face(Side.NORTH, 7, 2, 9, 16), face(Side.SOUTH, 7, 2, 9, 16), face(Side.WEST, 7, 2, 9, 16), face(Side.EAST, 7, 2, 9, 16),
        ).map { it.withTile(stand) } + element(
            Vec3f(9f, 0f, 5f), Vec3f(15f, 2f, 11f),
            face(Side.DOWN, 9, 5, 15, 11, cull = Side.DOWN), face(Side.UP, 9, 5, 15, 11),
            face(Side.NORTH, 9, 14, 15, 16), face(Side.SOUTH, 9, 14, 15, 16), face(Side.WEST, 5, 14, 11, 16), face(Side.EAST, 5, 14, 11, 16),
        ).map { it.withTile(base) } + element(
            Vec3f(1f, 0f, 1f), Vec3f(7f, 2f, 7f),
            face(Side.DOWN, 1, 1, 7, 7, cull = Side.DOWN), face(Side.UP, 1, 1, 7, 7),
            face(Side.NORTH, 1, 14, 7, 16), face(Side.SOUTH, 1, 14, 7, 16), face(Side.WEST, 1, 14, 7, 16), face(Side.EAST, 1, 14, 7, 16),
        ).map { it.withTile(base) } + element(
            Vec3f(1f, 0f, 9f), Vec3f(7f, 2f, 15f),
            face(Side.DOWN, 1, 9, 7, 15, cull = Side.DOWN), face(Side.UP, 1, 9, 7, 15),
            face(Side.NORTH, 1, 14, 7, 16), face(Side.SOUTH, 1, 14, 7, 16), face(Side.WEST, 9, 14, 15, 16), face(Side.EAST, 9, 14, 15, 16),
        ).map { it.withTile(base) }
    }

    private fun bottle(slot: Int, full: Boolean): List<Quad> {
        val stand = blockTile("block/brewing_stand")
        val (nu1, nu2, su1, su2) = when {
            slot == 0 && full -> listOf(0, 8, 8, 0)
            slot == 0 -> listOf(16, 8, 8, 16)
            full -> listOf(8, 0, 0, 8)
            else -> listOf(8, 16, 16, 8)
        }
        val faces = arrayOf(face(Side.NORTH, nu1, 0, nu2, 16), face(Side.SOUTH, su1, 0, su2, 16))
        val quads = if (slot == 0) {
            element(Vec3f(8f, 0f, 8f), Vec3f(16f, 16f, 8f), *faces)
        } else {
            element(
                Vec3f(-0.41f, 0f, 8f), Vec3f(7.59f, 16f, 8f), *faces,
                rotation = ElementRotation(Vec3f(8f, 8f, 8f), 1, if (slot == 1) -45f else 45f),
            )
        }
        return quads.map { it.withTile(stand) }
    }

    private val brewingStands = (0 until 8).map { bits ->
        brewingBase + (0 until 3).flatMap { slot -> bottle(slot, (bits shr slot) and 1 != 0) }
    }

    fun brewingStand(block: Int): List<Quad> = brewingStands[block - BREWING_STAND]

    private val frameNorth: List<Quad> = run {
        val wood = blockTile("block/birch_planks")
        val back = blockTile("block/item_frame")
        val elytra = blockTile("item/elytra")
        element(Vec3f(3f, 3f, 15.5f), Vec3f(13f, 13f, 16f), face(Side.NORTH, 3, 3, 13, 13), face(Side.SOUTH, 3, 3, 13, 13)).map { it.withTile(back) } +
            element(
                Vec3f(2f, 2f, 15f), Vec3f(14f, 3f, 16f),
                face(Side.DOWN, 2, 0, 14, 1), face(Side.UP, 2, 15, 14, 16), face(Side.NORTH, 2, 13, 14, 14),
                face(Side.SOUTH, 2, 13, 14, 14), face(Side.WEST, 15, 13, 16, 14), face(Side.EAST, 0, 13, 1, 14),
            ).map { it.withTile(wood) } +
            element(
                Vec3f(2f, 13f, 15f), Vec3f(14f, 14f, 16f),
                face(Side.DOWN, 2, 0, 14, 1), face(Side.UP, 2, 15, 14, 16), face(Side.NORTH, 2, 2, 14, 3),
                face(Side.SOUTH, 2, 2, 14, 3), face(Side.WEST, 15, 2, 16, 3), face(Side.EAST, 0, 2, 1, 3),
            ).map { it.withTile(wood) } +
            element(
                Vec3f(2f, 3f, 15f), Vec3f(3f, 13f, 16f),
                face(Side.NORTH, 13, 3, 14, 13), face(Side.SOUTH, 2, 3, 3, 13), face(Side.WEST, 15, 3, 16, 13), face(Side.EAST, 0, 3, 1, 13),
            ).map { it.withTile(wood) } +
            element(
                Vec3f(13f, 3f, 15f), Vec3f(14f, 13f, 16f),
                face(Side.NORTH, 2, 3, 3, 13), face(Side.SOUTH, 13, 3, 14, 13), face(Side.WEST, 15, 3, 16, 13), face(Side.EAST, 0, 3, 1, 13),
            ).map { it.withTile(wood) } +
            element(Vec3f(4f, 4f, 15f), Vec3f(12f, 12f, 15f), face(Side.NORTH, 0, 0, 16, 16)).map { it.withTile(elytra) }
    }
    private val frames = FROM_NORTH.map { turns -> frameNorth.map { it.rotateY(turns) } }

    fun elytraFrame(block: Int): List<Quad> = frames[block - ELYTRA_FRAME_NORTH]
}

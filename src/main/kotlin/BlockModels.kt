package faith.mimikyumochi

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f

class Quad(
    val corners: List<Vec3f>, val uvs: List<Vec2f>, val normal: Vec3f, val cull: IntArray?, val shade: Boolean = true,
    val tile: Int = -1,
) {
    val brightness = if (shade) shadeFor(normal) else 1f

    fun rotateY90() = Quad(
        corners.map { Vec3f(1f - it.z, it.y, it.x) },
        uvs,
        Vec3f(-normal.z, normal.y, normal.x),
        cull?.let { (dx, dy, dz) -> intArrayOf(-dz, dy, dx) },
        shade, tile,
    )

    fun rotateY(times: Int): Quad = (0 until times.mod(4)).fold(this) { q, _ -> q.rotateY90() }

    fun rotateX90() = Quad(
        corners.map { Vec3f(it.x, it.z, 1f - it.y) },
        uvs,
        Vec3f(normal.x, normal.z, -normal.y),
        cull?.let { (dx, dy, dz) -> intArrayOf(dx, dz, -dy) },
        shade, tile,
    )

    fun rotate(x: Int, y: Int): Quad = (0 until x.mod(4)).fold(this) { q, _ -> q.rotateX90() }.rotateY(y)

    fun withWorldUvs() = Quad(
        corners,
        corners.map { cubeUv(normal.x.toInt(), normal.y.toInt(), normal.z.toInt(), it) },
        normal, cull, shade, tile,
    )

    fun withTile(tile: Int) = Quad(corners, uvs, normal, cull, shade, tile)

    private companion object {
        fun shadeFor(n: Vec3f) = when {
            n.y > 0f -> 1.0f
            n.y < 0f -> 0.5f
            n.z != 0f -> 0.8f
            else -> 0.6f
        }
    }
}

enum class Side(val face: Int) { UP(0), DOWN(1), SOUTH(2), NORTH(3), EAST(4), WEST(5) }

class ElementFace(val side: Side, val uv: FloatArray, val cull: Side? = null)

fun face(side: Side, u1: Number, v1: Number, u2: Number, v2: Number, cull: Side? = null) =
    ElementFace(side, floatArrayOf(u1.toFloat(), v1.toFloat(), u2.toFloat(), v2.toFloat()), cull)

class ElementRotation(val origin: Vec3f, val axis: Int, val angle: Float)

fun element(
    from: Vec3f, to: Vec3f, vararg faces: ElementFace,
    rotation: ElementRotation? = null, shade: Boolean = true,
): List<Quad> = faces.map { f ->
    val template = FACES[f.side.face]
    val (u1, v1, u2, v2) = f.uv.map { it / 16f }
    val corners = template.corners.map { c ->
        val p = Vec3f(lerp(from.x, to.x, c.x), lerp(from.y, to.y, c.y), lerp(from.z, to.z, c.z))
        val turned = rotation?.let { r -> rotate(p - r.origin, r) + r.origin } ?: p
        Vec3f(turned.x / 16f, turned.y / 16f, turned.z / 16f)
    }
    val uvs = template.corners.map { c ->
        val (s, t) = when (f.side) {
            Side.UP -> c.x to c.z
            Side.DOWN -> c.x to 1f - c.z
            Side.SOUTH -> c.x to 1f - c.y
            Side.NORTH -> 1f - c.x to 1f - c.y
            Side.WEST -> c.z to 1f - c.y
            Side.EAST -> 1f - c.z to 1f - c.y
        }
        Vec2f(lerp(u1, u2, s), lerp(v1, v2, t))
    }
    val cull = f.cull?.let { FACES[it.face] }?.let { intArrayOf(it.dx, it.dy, it.dz) }
    Quad(corners, uvs, rotation?.let { rotate(template.normal, it) } ?: template.normal, cull, shade)
}

private fun rotate(p: Vec3f, r: ElementRotation): Vec3f {
    val a = Math.toRadians(r.angle.toDouble())
    val cos = kotlin.math.cos(a).toFloat()
    val sin = kotlin.math.sin(a).toFloat()
    return when (r.axis) {
        0 -> Vec3f(p.x, p.y * cos - p.z * sin, p.y * sin + p.z * cos)
        1 -> Vec3f(p.z * sin + p.x * cos, p.y, p.z * cos - p.x * sin)
        else -> Vec3f(p.x * cos - p.y * sin, p.x * sin + p.y * cos, p.z)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun v(x: Number, y: Number, z: Number) = Vec3f(x.toFloat(), y.toFloat(), z.toFloat())

object BarsModel {
    private val POST_ENDS = element(v(7, 0.001, 7), v(9, 0.001, 9), face(Side.DOWN, 7, 7, 9, 9), face(Side.UP, 7, 7, 9, 9)) +
        element(v(7, 15.999, 7), v(9, 15.999, 9), face(Side.DOWN, 7, 7, 9, 9), face(Side.UP, 7, 7, 9, 9))

    private val POST = element(v(8, 0, 7), v(8, 16, 9), face(Side.WEST, 7, 0, 9, 16), face(Side.EAST, 9, 0, 7, 16)) +
        element(v(7, 0, 8), v(9, 16, 8), face(Side.NORTH, 7, 0, 9, 16), face(Side.SOUTH, 9, 0, 7, 16))

    private val CAP = element(v(8, 0, 8), v(8, 16, 9), face(Side.WEST, 8, 0, 7, 16), face(Side.EAST, 7, 0, 8, 16)) +
        element(v(7, 0, 9), v(9, 16, 9), face(Side.NORTH, 9, 0, 7, 16), face(Side.SOUTH, 7, 0, 9, 16))

    private val CAP_ALT = element(v(8, 0, 7), v(8, 16, 8), face(Side.WEST, 8, 0, 9, 16), face(Side.EAST, 9, 0, 8, 16)) +
        element(v(7, 0, 7), v(9, 16, 7), face(Side.NORTH, 7, 0, 9, 16), face(Side.SOUTH, 9, 0, 7, 16))

    private val SIDE = element(v(8, 0, 0), v(8, 16, 8), face(Side.WEST, 16, 0, 8, 16), face(Side.EAST, 8, 0, 16, 16)) +
        element(v(7, 0, 0), v(9, 16, 7), face(Side.NORTH, 7, 0, 9, 16, cull = Side.NORTH)) +
        element(v(7, 0.001, 0), v(9, 0.001, 7), face(Side.DOWN, 9, 0, 7, 7), face(Side.UP, 7, 0, 9, 7)) +
        element(v(7, 15.999, 0), v(9, 15.999, 7), face(Side.DOWN, 9, 0, 7, 7), face(Side.UP, 7, 0, 9, 7))

    private val SIDE_ALT = element(v(8, 0, 8), v(8, 16, 16), face(Side.WEST, 8, 0, 0, 16), face(Side.EAST, 0, 0, 8, 16)) +
        element(
            v(7, 0, 9), v(9, 16, 16),
            face(Side.SOUTH, 7, 0, 9, 16, cull = Side.SOUTH), face(Side.DOWN, 9, 9, 7, 16), face(Side.UP, 7, 9, 9, 16),
        ) +
        element(v(7, 0.001, 9), v(9, 0.001, 16), face(Side.DOWN, 9, 9, 7, 16), face(Side.UP, 7, 9, 9, 16)) +
        element(v(7, 15.999, 9), v(9, 15.999, 16), face(Side.DOWN, 9, 9, 7, 16), face(Side.UP, 7, 9, 9, 16))

    private fun List<Quad>.rotated() = map { it.rotateY90() }

    private fun build(north: Boolean, south: Boolean, west: Boolean, east: Boolean): List<Quad> {
        val none = !north && !south && !west && !east
        val parts = ArrayList(POST_ENDS)
        if (none) parts += POST
        if (north && !south && !west && !east) parts += CAP
        if (east && !north && !south && !west) parts += CAP.rotated()
        if (south && !north && !west && !east) parts += CAP_ALT
        if (west && !north && !south && !east) parts += CAP_ALT.rotated()
        if (north) parts += SIDE
        if (east) parts += SIDE.rotated()
        if (south) parts += SIDE_ALT
        if (west) parts += SIDE_ALT.rotated()
        return parts
    }

    private val shapes = List(16) { i -> build(i and 1 != 0, i and 2 != 0, i and 4 != 0, i and 8 != 0) }

    fun quads(north: Boolean, south: Boolean, west: Boolean, east: Boolean): List<Quad> =
        shapes[(if (north) 1 else 0) or (if (south) 2 else 0) or (if (west) 4 else 0) or (if (east) 8 else 0)]
}

object TorchModel {
    private val EAST = element(
        v(-1, 3.5, 7), v(1, 13.5, 9),
        face(Side.DOWN, 7, 13, 9, 15), face(Side.UP, 7, 6, 9, 8),
        face(Side.NORTH, 7, 6, 9, 16), face(Side.EAST, 7, 6, 9, 16),
        face(Side.SOUTH, 7, 6, 9, 16), face(Side.WEST, 7, 6, 9, 16),
        rotation = ElementRotation(v(0, 3.5, 8), axis = 2, angle = -22.5f),
        shade = false,
    )

    private val wall = listOf(270, 90, 180, 0).map { y -> EAST.map { it.rotateY(y / 90) } }

    fun wall(block: Int): List<Quad> = wall[block - WALL_TORCH_NORTH]
}

object StairsModel {
    private val SLAB = element(
        v(0, 0, 0), v(16, 8, 16),
        face(Side.DOWN, 0, 0, 16, 16, cull = Side.DOWN), face(Side.UP, 0, 0, 16, 16),
        face(Side.NORTH, 0, 8, 16, 16, cull = Side.NORTH), face(Side.SOUTH, 0, 8, 16, 16, cull = Side.SOUTH),
        face(Side.WEST, 0, 8, 16, 16, cull = Side.WEST), face(Side.EAST, 0, 8, 16, 16, cull = Side.EAST),
    )
    private val STEP = element(
        v(8, 8, 0), v(16, 16, 16),
        face(Side.UP, 8, 0, 16, 16, cull = Side.UP), face(Side.NORTH, 0, 0, 8, 8, cull = Side.NORTH),
        face(Side.SOUTH, 8, 0, 16, 8, cull = Side.SOUTH), face(Side.WEST, 0, 0, 16, 8), face(Side.EAST, 0, 0, 16, 8, cull = Side.EAST),
    )
    private val STRAIGHT = SLAB + STEP
    private val INNER = SLAB + STEP + element(
        v(0, 8, 8), v(8, 16, 16),
        face(Side.UP, 0, 8, 8, 16, cull = Side.UP), face(Side.NORTH, 8, 0, 16, 8),
        face(Side.SOUTH, 0, 0, 8, 8, cull = Side.SOUTH), face(Side.WEST, 8, 0, 16, 8, cull = Side.WEST),
    )
    private val OUTER = SLAB + element(
        v(8, 8, 8), v(16, 16, 16),
        face(Side.UP, 8, 8, 16, 16, cull = Side.UP), face(Side.NORTH, 0, 0, 8, 8),
        face(Side.SOUTH, 8, 0, 16, 8, cull = Side.SOUTH), face(Side.WEST, 8, 0, 16, 8), face(Side.EAST, 0, 0, 8, 8, cull = Side.EAST),
    )

    private const val VARIANTS = "S03 I02 I03 O02 O03 S23 I23 I20 O23 O20 S01 I00 I01 O00 O01 S21 I21 I22 O21 O22 " +
        "S02 I01 I02 O01 O02 S22 I22 I23 O22 O23 S00 I03 I00 O03 O00 S20 I20 I21 O20 O21"

    private val models = VARIANTS.split(' ').map { v ->
        val model = when (v[0]) { 'S' -> STRAIGHT; 'I' -> INNER; else -> OUTER }
        model.map { it.rotate(v[1].digitToInt(), v[2].digitToInt()).withWorldUvs() }
    }

    fun quads(block: Int): List<Quad> = models[block - PURPUR_STAIRS]
}

object SlabModel {
    val BOTTOM = element(
        v(0, 0, 0), v(16, 8, 16),
        face(Side.DOWN, 0, 0, 16, 16, cull = Side.DOWN), face(Side.UP, 0, 0, 16, 16),
        face(Side.NORTH, 0, 8, 16, 16, cull = Side.NORTH), face(Side.SOUTH, 0, 8, 16, 16, cull = Side.SOUTH),
        face(Side.WEST, 0, 8, 16, 16, cull = Side.WEST), face(Side.EAST, 0, 8, 16, 16, cull = Side.EAST),
    )
    val TOP = element(
        v(0, 8, 0), v(16, 16, 16),
        face(Side.DOWN, 0, 0, 16, 16), face(Side.UP, 0, 0, 16, 16, cull = Side.UP),
        face(Side.NORTH, 0, 0, 16, 8, cull = Side.NORTH), face(Side.SOUTH, 0, 0, 16, 8, cull = Side.SOUTH),
        face(Side.WEST, 0, 0, 16, 8, cull = Side.WEST), face(Side.EAST, 0, 0, 16, 8, cull = Side.EAST),
    )
}

object EndRodModel {
    private val UP = element(
        v(6, 0, 6), v(10, 1, 10),
        face(Side.DOWN, 6, 6, 2, 2, cull = Side.DOWN), face(Side.UP, 2, 2, 6, 6),
        face(Side.NORTH, 2, 6, 6, 7), face(Side.SOUTH, 2, 6, 6, 7), face(Side.WEST, 2, 6, 6, 7), face(Side.EAST, 2, 6, 6, 7),
    ) + element(
        v(7, 1, 7), v(9, 16, 9),
        face(Side.UP, 2, 0, 4, 2, cull = Side.UP),
        face(Side.NORTH, 0, 0, 2, 15), face(Side.SOUTH, 0, 0, 2, 15), face(Side.WEST, 0, 0, 2, 15), face(Side.EAST, 0, 0, 2, 15),
    )

    private val models = listOf(2 to 0, 0 to 0, 1 to 0, 1 to 2, 1 to 3, 1 to 1).map { (x, y) -> UP.map { it.rotate(x, y) } }

    fun quads(block: Int): List<Quad> = models[block - END_ROD_DOWN]
}

object LadderModel {
    private val NORTH = element(
        v(0, 0, 15.2), v(16, 16, 15.2),
        face(Side.NORTH, 0, 0, 16, 16), face(Side.SOUTH, 16, 0, 0, 16),
        shade = false,
    )

    private val models = listOf(0, 2, 3, 1).map { y -> NORTH.map { it.rotateY(y) } }

    fun quads(block: Int): List<Quad> = models[block - LADDER_NORTH]
}

object ChorusPlantModel {
    private val ARM = element(
        v(4, 4, 0), v(12, 12, 4),
        face(Side.DOWN, 0, 0, 0, 0), face(Side.UP, 0, 0, 0, 0), face(Side.NORTH, 0, 0, 0, 0, cull = Side.NORTH),
        face(Side.WEST, 0, 0, 0, 0), face(Side.EAST, 0, 0, 0, 0),
    )
    private val CORE_END = element(v(4, 4, 4), v(12, 12, 12), face(Side.NORTH, 0, 0, 0, 0))
    private fun nub(from: Vec3f, to: Vec3f) = CORE_END + element(
        from, to,
        face(Side.DOWN, 0, 0, 0, 0), face(Side.UP, 0, 0, 0, 0), face(Side.NORTH, 0, 0, 0, 0),
        face(Side.WEST, 0, 0, 0, 0), face(Side.EAST, 0, 0, 0, 0),
    )
    private val NOSIDE = CORE_END
    private val NOSIDE1 = nub(v(4, 4, 3), v(12, 12, 4))
    private val NOSIDE2 = nub(v(5, 5, 2), v(11, 11, 4))
    private val NOSIDE3 = nub(v(4, 4, 3), v(12, 12, 4))

    private class SidePart(val x: Int, val y: Int, val nosides: List<List<Quad>>)

    private val SIDES = listOf(
        SidePart(0, 0, listOf(NOSIDE, NOSIDE, NOSIDE1, NOSIDE2, NOSIDE3)),
        SidePart(0, 1, listOf(NOSIDE1, NOSIDE2, NOSIDE3, NOSIDE, NOSIDE)),
        SidePart(0, 2, listOf(NOSIDE2, NOSIDE3, NOSIDE, NOSIDE, NOSIDE1)),
        SidePart(0, 3, listOf(NOSIDE3, NOSIDE, NOSIDE, NOSIDE1, NOSIDE2)),
        SidePart(3, 0, listOf(NOSIDE, NOSIDE, NOSIDE3, NOSIDE1, NOSIDE2)),
        SidePart(1, 0, listOf(NOSIDE3, NOSIDE2, NOSIDE1, NOSIDE, NOSIDE)),
    )

    private fun List<Quad>.turned(part: SidePart) = map { it.rotate(part.x, part.y).withWorldUvs() }

    private val arms = SIDES.map { ARM.turned(it) }
    private val ends = SIDES.map { side -> side.nosides.map { it.turned(side) } }
    private val models = arrayOfNulls<List<Quad>>(64 * 5)

    fun quads(connected: Int, x: Int, y: Int, z: Int): List<Quad> {
        val roll = pick(x, y, z)
        val index = connected * 5 + roll
        return models[index] ?: SIDES.indices.flatMap { side ->
            if ((connected shr side) and 1 != 0) arms[side] else ends[side][roll]
        }.also { models[index] = it }
    }

    private fun pick(x: Int, y: Int, z: Int): Int {
        var seed = (x * 3129871).toLong() xor z.toLong() * 116129781L xor y.toLong()
        seed = seed * seed * 42317861L + seed * 11L
        val random = java.util.Random(seed shr 16)
        return java.util.Random(random.nextLong()).nextInt(5)
    }
}

object ChorusFlowerModel {
    private const val PLANT_TILE = 12
    private const val FLOWER_TILE = 13

    val QUADS: List<Quad> = listOf(
        element(v(2, 14, 2), v(14, 16, 14), face(Side.UP, 2, 2, 14, 14)).map { it.withTile(FLOWER_TILE) },
        element(
            v(2, 14, 2), v(14, 16, 14),
            face(Side.NORTH, 2, 0, 14, 2), face(Side.SOUTH, 2, 0, 14, 2), face(Side.WEST, 2, 0, 14, 2), face(Side.EAST, 2, 0, 14, 2),
        ).map { it.withTile(PLANT_TILE) },
        element(v(0, 2, 2), v(2, 14, 14), face(Side.WEST, 2, 2, 14, 14)).map { it.withTile(FLOWER_TILE) },
        element(
            v(0, 2, 2), v(2, 14, 14),
            face(Side.DOWN, 16, 14, 14, 2), face(Side.UP, 0, 2, 2, 14), face(Side.NORTH, 14, 2, 16, 14), face(Side.SOUTH, 0, 2, 2, 14),
        ).map { it.withTile(PLANT_TILE) },
        element(v(2, 2, 0), v(14, 14, 2), face(Side.NORTH, 2, 2, 14, 14)).map { it.withTile(FLOWER_TILE) },
        element(
            v(2, 2, 0), v(14, 14, 2),
            face(Side.DOWN, 14, 2, 2, 0), face(Side.UP, 2, 0, 14, 2), face(Side.WEST, 0, 2, 2, 14), face(Side.EAST, 14, 2, 16, 14),
        ).map { it.withTile(PLANT_TILE) },
        element(v(2, 2, 14), v(14, 14, 16), face(Side.SOUTH, 2, 2, 14, 14)).map { it.withTile(FLOWER_TILE) },
        element(
            v(2, 2, 14), v(14, 14, 16),
            face(Side.DOWN, 14, 16, 2, 14), face(Side.UP, 2, 14, 14, 16), face(Side.WEST, 14, 2, 16, 14), face(Side.EAST, 0, 2, 2, 14),
        ).map { it.withTile(PLANT_TILE) },
        element(v(14, 2, 2), v(16, 14, 14), face(Side.EAST, 2, 2, 14, 14)).map { it.withTile(FLOWER_TILE) },
        element(
            v(14, 2, 2), v(16, 14, 14),
            face(Side.DOWN, 2, 14, 0, 2), face(Side.UP, 14, 2, 16, 14), face(Side.NORTH, 0, 2, 2, 14), face(Side.SOUTH, 14, 2, 16, 14),
        ).map { it.withTile(PLANT_TILE) },
        element(
            v(2, 0, 2), v(14, 14, 14),
            face(Side.UP, 2, 2, 14, 14), face(Side.DOWN, 14, 14, 2, 2),
            face(Side.NORTH, 2, 2, 14, 16), face(Side.SOUTH, 2, 2, 14, 16), face(Side.WEST, 2, 2, 14, 16), face(Side.EAST, 2, 2, 14, 16),
        ).map { it.withTile(PLANT_TILE) },
    ).flatten()
}

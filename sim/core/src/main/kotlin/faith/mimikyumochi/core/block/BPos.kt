package faith.mimikyumochi.core.block

import kotlin.math.sqrt

data class BPos(val x: Int = 0, val y: Int = 0, val z: Int = 0) {
    fun transform(mirror: BlockMirror, rotation: BlockRotation, pivot: BPos): BPos {
        return rotation.rotate(mirror.mirror(this), pivot)
    }

    fun toChunkPos(): CPos = CPos(x shr 4, z shr 4)

    fun relative(direction: BlockDirection, offset: Int): BPos {
        return if (offset == 0) this else BPos(
            this.x + direction.vec.x * offset,
            this.y + direction.vec.y * offset,
            this.z + direction.vec.z * offset
        )
    }
    fun add(pos: BPos): BPos {
        return this.add(pos.x, pos.y, pos.z)
    }
    fun add(x: Int, y: Int, z: Int): BPos {
        return BPos(this.x + x, this.y + y, this.z + z)
    }

    fun subtract(x: Int, y: Int, z: Int): BPos {
        return BPos(this.x - x, this.y - y, this.z - z)
    }
    fun subtract(pos: BPos): BPos {
        return subtract(pos.x, pos.y, pos.z)
    }

    fun getStrongholdRing(): Int? {
        val distance = sqrt((x.toDouble() * x) + (z.toDouble() * z))

        return when (distance) {
            in 1280.0..2816.0 -> 1
            in 4352.0..5888.0 -> 2
            in 7424.0..8960.0 -> 3
            in 10496.0..12032.0 -> 4
            in 13568.0..15104.0 -> 5
            in 16640.0..18176.0 -> 6
            in 19712.0..21248.0 -> 7
            in 22784.0..24320.0 -> 8
            else -> null
        }
    }

    fun isInStrongholdRing(): Boolean {
        return getStrongholdRing() != null
    }
}

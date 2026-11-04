package faith.mimikyumochi.core.block

import faith.mimikyumochi.core.rand.Rand

enum class BlockRotation(val direction: BlockDirection) {
    NONE(BlockDirection.NORTH),
    CLOCKWISE_90(BlockDirection.EAST),
    CLOCKWISE_180(BlockDirection.SOUTH),
    CLOCKWISE_270(BlockDirection.WEST);

    companion object {
        fun getRandom(rand: Rand) = entries[rand.nextInt(entries.size)]
    }

    fun rotate(origin: BPos, pivot: BPos): BPos {
        val px: Int = pivot.x
        val pz: Int = pivot.z
        return when (this) {
            CLOCKWISE_90 -> BPos(px + pz - origin.z, origin.y, pz - px + origin.x)
            CLOCKWISE_180 -> BPos(px + px - origin.x, origin.y, pz + pz - origin.z)
            CLOCKWISE_270 -> BPos(px - pz + origin.z, origin.y, px + pz - origin.x)

            else -> origin
        }
    }

    fun rotate(direction: BlockDirection): BlockDirection {
        return if (direction.axis === Axis.Y) {
            direction
        } else {
            when (this) {
                CLOCKWISE_90 -> direction.getClockWise()
                CLOCKWISE_180 -> direction.getOpposite()
                CLOCKWISE_270 -> direction.getCounterClockWise()
                else -> direction
            }
        }
    }

    fun getRotated(rotation: BlockRotation): BlockRotation {
        return when (rotation) {
            CLOCKWISE_180 -> this.direction.getOpposite().getRotation()
            CLOCKWISE_270 -> this.direction.getCounterClockWise().getRotation()
            CLOCKWISE_90 -> this.direction.getClockWise().getRotation()
            else -> this
        }
    }

    fun getSize(size: BPos): BPos {
        return when (this) {
            CLOCKWISE_270, CLOCKWISE_90 -> BPos(
                size.z,
                size.y,
                size.x
            )

            else -> size
        }
    }
}

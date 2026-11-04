package faith.mimikyumochi.core.block

enum class BlockDirection(val axis: Axis, val vec: BPos) {
    DOWN(Axis.Y, BPos(0, -1, 0)),
    UP(Axis.Y, BPos(0, 1, 0)),
    NORTH(Axis.Z, BPos(0, 0, -1)),
    SOUTH(Axis.Z, BPos(0, 0, 1)),
    WEST(Axis.X, BPos(-1, 0, 0)),
    EAST(Axis.X, BPos(1, 0, 0));

    fun getClockWise(): BlockDirection {
        return getDirection(EAST, WEST, NORTH, SOUTH)
    }

    fun getCounterClockWise(): BlockDirection {
        return getDirection(WEST, EAST, SOUTH, NORTH)
    }

    fun getOpposite(): BlockDirection {
        return getDirection(SOUTH, NORTH, EAST, WEST)
    }

    private fun getDirection(
        dir1: BlockDirection,
        dir2: BlockDirection,
        dir3: BlockDirection,
        dir4: BlockDirection
    ): BlockDirection {
        return when (this) {
            NORTH -> dir1
            SOUTH -> dir2
            WEST -> dir3
            EAST -> dir4
            else -> throw IllegalStateException("unable to get facing of $this")
        }
    }

    fun getRotation(): BlockRotation {
        return when (this) {
            NORTH -> BlockRotation.NONE
            SOUTH -> BlockRotation.CLOCKWISE_180
            WEST -> BlockRotation.CLOCKWISE_270
            EAST -> BlockRotation.CLOCKWISE_90
            else -> throw java.lang.IllegalStateException("unable to get facing of $this")
        }
    }
}
enum class Axis {
    X, Y, Z;

    fun get2DRotated(): Axis {
        return when (this) {
            X -> Z
            Z -> X
            else -> Y
        }
    }
}

package faith.mimikyumochi.core.block

data class BlockBox(
    var minX: Int, var minY: Int, var minZ: Int,
    var maxX: Int, var maxY: Int, var maxZ: Int
) {
    constructor(xMin: Int, zMin: Int, xMax: Int, zMax: Int) : this(xMin, 1, zMin, xMax, 512, zMax)

    fun move(x: Int, y: Int, z: Int) {
        minX += x; maxX += x
        minY += y; maxY += y
        minZ += z; maxZ += z
    }

    fun offset(x: Int, y: Int, z: Int) =
        BlockBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z)

    fun intersects(box: BlockBox) =
        maxX >= box.minX && minX <= box.maxX &&
                maxZ >= box.minZ && minZ <= box.maxZ &&
                maxY >= box.minY && minY <= box.maxY

    fun getInside(offset: BPos, rotation: BlockRotation): BPos = when (rotation) {
        BlockRotation.NONE             -> BPos(minX + offset.x, minY + offset.y, minZ + offset.z)
        BlockRotation.CLOCKWISE_90     -> BPos(minX - offset.z, minY + offset.y, minZ + offset.x)
        BlockRotation.CLOCKWISE_180    -> BPos(minX - offset.x, minY + offset.y, minZ - offset.z)
        BlockRotation.CLOCKWISE_270 -> BPos(minX + offset.z, minY + offset.y, minZ - offset.x)
    }

    fun getRotated(rotation: BlockRotation): BlockBox = when (rotation) {
        BlockRotation.NONE                -> copy()
        BlockRotation.CLOCKWISE_90        -> BlockBox(maxX, minY, minZ, minX, maxY, maxZ)
        BlockRotation.CLOCKWISE_180       -> BlockBox(maxX, minY, maxZ, minX, maxY, minZ)
        BlockRotation.CLOCKWISE_270 -> BlockBox(minX, minY, maxZ, maxX, maxY, minZ)
    }

    fun intersectsXZ(minX: Int, minZ: Int, maxX: Int, maxZ: Int) =
        this.maxX >= minX && this.minX <= maxX && this.maxZ >= minZ && this.minZ <= maxZ

    fun contains(v: BPos) =
        v.x in minX..maxX && v.y in minY..maxY && v.z in minZ..maxZ

    fun encompass(box: BlockBox) {
        minX = minOf(minX, box.minX); minY = minOf(minY, box.minY); minZ = minOf(minZ, box.minZ)
        maxX = maxOf(maxX, box.maxX); maxY = maxOf(maxY, box.maxY); maxZ = maxOf(maxZ, box.maxZ)
    }

    val xSpan get() = maxX - minX + 1
    val ySpan get() = maxY - minY + 1
    val zSpan get() = maxZ - minZ + 1

    fun getDimensions() = BPos(maxX - minX, maxY - minY, maxZ - minZ)
    fun getCenter()     = BPos(minX + xSpan / 2, minY + ySpan / 2, minZ + zSpan / 2)

    companion object {
        fun empty() = BlockBox(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

        fun rotated(x: Int, y: Int, z: Int, offsetX: Int, offsetY: Int, offsetZ: Int,
                    sizeX: Int, sizeY: Int, sizeZ: Int, rotation: BlockRotation
        ) = when (rotation) {
            BlockRotation.CLOCKWISE_270 ->
                BlockBox(x - sizeZ + 1 + offsetZ, y + offsetY, z + offsetX, x + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX)
            BlockRotation.CLOCKWISE_90 ->
                BlockBox(x + offsetZ, y + offsetY, z + offsetX, x + sizeZ - 1 + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX)
            BlockRotation.CLOCKWISE_180 ->
                BlockBox(x + offsetX, y + offsetY, z + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + sizeZ - 1 + offsetZ)
            BlockRotation.NONE ->
                BlockBox(x + offsetX, y + offsetY, z - sizeZ + 1 + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + offsetZ)
        }

        fun getBoundingBox(anchor: BPos, rotation: BlockRotation, pivot: BPos, mirror: BlockMirror, size: BPos): BlockBox {
            val rotationSize = rotation.getSize(size)
            val pivotX = pivot.x
            val pivotZ = pivot.z
            val sx = rotationSize.x - 1
            val sy = rotationSize.y - 1
            val sz = rotationSize.z - 1

            var box = when (rotation) {
                BlockRotation.CLOCKWISE_270 -> BlockBox(pivotX - pivotZ,           0, pivotX + pivotZ - sz, pivotX - pivotZ + sx, sy, pivotX + pivotZ)
                BlockRotation.CLOCKWISE_90        -> BlockBox(pivotX + pivotZ - sx,       0, pivotZ - pivotX,      pivotX + pivotZ,      sy, pivotZ - pivotX + sz)
                BlockRotation.CLOCKWISE_180       -> BlockBox(pivotX + pivotX - sx,       0, pivotZ + pivotZ - sz, pivotX + pivotX,      sy, pivotZ + pivotZ)
                BlockRotation.NONE                -> BlockBox(0,                           0, 0,                    sx,                   sy, sz)
            }

            box = when (mirror) {
                BlockMirror.LEFT_RIGHT  -> mirrorAABB(rotation, sz, sx, box, BlockDirection.NORTH, BlockDirection.SOUTH)
                BlockMirror.FRONT_BACK  -> mirrorAABB(rotation, sx, sz, box, BlockDirection.WEST,  BlockDirection.EAST)
                BlockMirror.NONE        -> box
            }

            return box.offset(anchor.x, anchor.y, anchor.z)
        }

        private fun mirrorAABB(rotation: BlockRotation, x: Int, z: Int, box: BlockBox,
                               dir: BlockDirection, dir1: BlockDirection
        ): BlockBox {
            val origin = BPos(0, 0, 0)
            val move = when {
                rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.CLOCKWISE_270 ->
                    origin.relative(rotation.rotate(dir), z)
                rotation == BlockRotation.CLOCKWISE_180 ->
                    origin.relative(dir1, x)
                else ->
                    origin.relative(dir, x)
            }
            return box.offset(move.x, 0, move.z)
        }
    }
}

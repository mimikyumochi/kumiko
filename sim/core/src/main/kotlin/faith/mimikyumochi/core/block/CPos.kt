package faith.mimikyumochi.core.block

data class CPos(val x: Int, val z: Int) {
    fun toRegionPos(spacing: Int): RPos {
        val x: Int = if (x < 0) x - spacing + 1 else x
        val z: Int = if (z < 0) z - spacing + 1 else z
        return RPos(x / spacing, z / spacing, spacing)
    }

    fun toBlockPos(): BPos = BPos(x*16, 0, z*16)
}

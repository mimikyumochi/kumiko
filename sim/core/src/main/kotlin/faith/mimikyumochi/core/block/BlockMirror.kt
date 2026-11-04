package faith.mimikyumochi.core.block

import faith.mimikyumochi.core.rand.Rand

enum class BlockMirror(val orientation: BPos) {
    NONE(BPos(0, 0, 0)),
    LEFT_RIGHT(BPos(0, 0, 1)),
    FRONT_BACK(BPos(1, 0, 0));

    companion object {
        fun getRandom(rand: Rand) = entries[rand.nextInt(entries.size)]
    }

    fun mirror(pos: BPos): BPos {
        return when (this) {
            LEFT_RIGHT -> BPos(pos.x, pos.y, -pos.z)
            FRONT_BACK -> BPos(-pos.x, pos.y, pos.z)
            else -> BPos(pos.x, pos.y, pos.z)
        }
    }
}

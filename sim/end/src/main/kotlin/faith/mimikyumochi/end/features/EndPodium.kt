package faith.mimikyumochi.end.features

import faith.mimikyumochi.end.terrain.EndTerrainGenerator

object EndPodium {
    const val X = 0
    const val Z = 0

    fun getY(terrain: EndTerrainGenerator): Int = maxOf(1, terrain.getHeight(X, Z))

    enum class Part { BEDROCK, END_STONE, AIR, PORTAL }

    fun partAt(dx: Int, dy: Int, dz: Int, active: Boolean): Part? {
        if (dx !in -4..4 || dz !in -4..4 || dy !in -1..32) return null
        val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
        val inner = distSq < 2.5 * 2.5
        if (!inner && distSq >= 3.5 * 3.5) return null
        return when {
            dy < 0 -> if (inner) Part.BEDROCK else Part.END_STONE
            dy > 0 -> Part.AIR
            inner -> if (active) Part.PORTAL else Part.AIR
            else -> Part.BEDROCK
        }
    }

    const val PILLAR_HEIGHT = 4

    const val TORCH_HEIGHT = 2
}

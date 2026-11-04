package faith.mimikyumochi.end.features

import faith.mimikyumochi.core.Utils
import faith.mimikyumochi.core.rand.Rand
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object EndPillars {
    const val NUM_PILLARS = 10
    const val PILLAR_DISTANCE = 42

    fun getPillarsForSeed(seed: Long): List<Pillar> {
        val pillars = mutableListOf<Pillar>()

        val pillarSeed = Rand(seed).nextLong() and 65535L
        val rand = Rand(pillarSeed)

        val sizes = Utils.toShuffledList((0 until NUM_PILLARS), rand)

        for (i in 0 until NUM_PILLARS) {
            val x = floor(PILLAR_DISTANCE * cos(2.0 * (-Math.PI + (Math.PI / NUM_PILLARS) * i)))
            val z = floor(PILLAR_DISTANCE * sin(2.0 * (-Math.PI + (Math.PI / NUM_PILLARS) * i)))
            val size = sizes[i]
            val radius = 2 + size / 3
            val height = 76 + size * 3
            val guarded = size == 1 || size == 2

            pillars.add(Pillar(x.toInt(), z.toInt(), radius, height, guarded))
        }

        return pillars
    }

    data class Pillar(val x: Int, val z: Int, val radius: Int, val height: Int, val guarded: Boolean)
}

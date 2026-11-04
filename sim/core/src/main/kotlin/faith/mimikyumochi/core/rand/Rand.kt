package faith.mimikyumochi.core.rand

class Rand(seed: Long = 0) {
    private val multiplier = 0x5DEECE66DL
    private val addend = 0xBL
    private val mask = (1L shl 48) - 1
    private val inverseMultiplier = 246154705703781L

    private var seed: Long = (seed xor multiplier) and mask

    fun setSeed(newSeed: Long): Long {
        seed = (newSeed xor multiplier) and mask
        return seed
    }

    fun setRawSeed(rawSeed: Long) {
        seed = rawSeed and mask
    }

    fun getRawSeed(): Long = seed

    fun advance(n: Int) {
        repeat(n) { nextInt() }
    }

    fun stepBackward() {
        seed = ((seed - addend) * inverseMultiplier) and mask
    }

    fun stepBackward(n: Int) {
        repeat(n) { stepBackward() }
    }

    fun next(bits: Int): Int {
        seed = (seed * multiplier + addend) and mask
        return (seed ushr (48 - bits)).toInt()
    }

    fun nextFloat(): Float = next(24) / 16777216f

    fun nextDouble(): Double = (((next(26).toLong() shl 27) + next(27).toLong()) * 1.1102230246251565E-16)

    fun nextInt(): Int = next(32)

    fun nextLong(): Long = (next(32).toLong() shl 32) + next(32)

    fun nextBoolean(): Boolean = next(1) != 0

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        if ((bound and -bound) == bound) {
            return ((bound.toLong() * next(31)) shr 31).toInt()
        }
        var bits: Int
        var value: Int
        do {
            bits = next(31)
            value = bits % bound
        } while (bits - value + (bound - 1) < 0)

        return value
    }

    fun setRegionSeed(worldSeed: Long, regionX: Int, regionZ: Int, salt: Int): Long {
        val seedVal: Long = regionX.toLong() * 341873128712L + regionZ.toLong() * 132897987541L + worldSeed + salt.toLong()
        setSeed(seedVal)
        return seedVal and mask
    }

    fun setPopulationSeed(worldSeed: Long, blockX: Int, blockZ: Int): Long {
        setSeed(worldSeed)
        val a = nextLong() or 1L
        val b = nextLong() or 1L
        val popSeed = (blockX.toLong() * a + blockZ.toLong() * b) xor worldSeed
        setSeed(popSeed)
        return popSeed and mask
    }

    fun setDecoratorSeed(worldSeed: Long, blockX: Int, blockZ: Int, salt: Int, index: Int = 0): Long {
        val popSeed = setPopulationSeed(worldSeed, blockX, blockZ)
        val decSeed = popSeed + salt + index * 10000L
        setSeed(decSeed)
        return decSeed and mask
    }

    fun setCarverSeed(worldSeed: Long, chunkX: Int, chunkZ: Int): Long {
        setSeed(worldSeed)
        val a = nextLong()
        val b = nextLong()
        val seedVal = chunkX * a xor chunkZ * b xor worldSeed
        setSeed(seedVal)
        return seedVal and mask
    }
}

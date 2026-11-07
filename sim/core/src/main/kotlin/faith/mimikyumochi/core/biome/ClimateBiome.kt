package faith.mimikyumochi.core.biome

data class ClimateBiome(val name: String, val temperature: Float, val humidity: Float, val offset: Float) {
    fun fitness(temperature: Float, humidity: Float): Float {
        val dt = this.temperature - temperature
        val dh = this.humidity - humidity
        return dt * dt + dh * dh + offset * offset
    }

    fun quantizedFitness(temperature: Float, humidity: Float): Long {
        val dt = quantize(this.temperature) - quantize(temperature)
        val dh = quantize(this.humidity) - quantize(humidity)
        val offset = quantize(offset)
        return dt * dt + dh * dh + offset * offset
    }

    private fun quantize(v: Float): Long = (v * 10000.0f).toLong()
}

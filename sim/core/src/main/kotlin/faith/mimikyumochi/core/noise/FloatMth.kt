package faith.mimikyumochi.core.noise

import kotlin.math.floor

object FloatMth {
    fun lerp(delta: Float, start: Float, end: Float): Float = start + delta * (end - start)

    fun lerp2(dx: Float, dy: Float, v00: Float, v10: Float, v01: Float, v11: Float): Float =
        lerp(dy, lerp(dx, v00, v10), lerp(dx, v01, v11))

    fun lerp3(
        dx: Float, dy: Float, dz: Float,
        v000: Float, v100: Float, v010: Float, v110: Float,
        v001: Float, v101: Float, v011: Float, v111: Float,
    ): Float = lerp(dz, lerp2(dx, dy, v000, v100, v010, v110), lerp2(dx, dy, v001, v101, v011, v111))

    fun smoothstep(t: Float): Float = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f)

    fun clamp(v: Float, min: Float, max: Float): Float = if (v < min) min else Math.min(v, max)

    fun sqrt(v: Float): Float = Math.sqrt(v.toDouble()).toFloat()

    fun floorToInt(d: Double): Int = floor(d).toInt()

    private val HALF_ROUND_OFF = Math.nextDown(1.6777216E7)

    fun wrap(v: Double): Double {
        if (v >= -HALF_ROUND_OFF && v < HALF_ROUND_OFF) return v
        return v - floor(v / 3.3554432E7 + 0.5) * 3.3554432E7
    }
}

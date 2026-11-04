package faith.mimikyumochi.core.noise

import faith.mimikyumochi.core.rand.Rand
import kotlin.math.floor

class SimplexNoise(rand: Rand) {
    val originX: Double = rand.nextDouble() * 256.0
    val originY: Double = rand.nextDouble() * 256.0
    val originZ: Double = rand.nextDouble() * 256.0

    private val perm = IntArray(256)

    init {
        for (i in 0 until 256) perm[i] = i
        for (i in 0 until 256) {
            val j = rand.nextInt(256 - i) + i
            val tmp = perm[i]
            perm[i] = perm[j]
            perm[j] = tmp
        }
    }

    fun lookup(i: Int): Int = perm[i and 255]

    private val grad3 = arrayOf(
        1.0 to 1.0,  -1.0 to 1.0,  1.0 to -1.0, -1.0 to -1.0,
        1.0 to 0.0,  -1.0 to 0.0,  1.0 to 0.0,  -1.0 to 0.0,
        0.0 to 1.0,   0.0 to -1.0, 0.0 to 1.0,   0.0 to -1.0
    )

    private fun grad2(hash: Int, x: Double, y: Double): Double {
        val h = Math.floorMod(hash, 12)
        return grad3[h].first * x + grad3[h].second * y
    }

    fun sample2D(x: Double, y: Double): Double {
        val F2 = 0.3660254037844386
        val G2 = 0.21132486540518713

        val s = (x + y) * F2
        val i = floor(x + s).toInt()
        val j = floor(y + s).toInt()

        val t = (i + j) * G2
        val x0 = x - (i - t)
        val y0 = y - (j - t)

        val (i1, j1) = if (x0 > y0) 1 to 0 else 0 to 1

        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2

        val ii = i and 255
        val jj = j and 255

        fun noise(ix: Int, iy: Int, dx: Double, dy: Double): Double {
            val t = 0.5 - dx * dx - dy * dy
            if (t < 0) return 0.0
            val h = Math.floorMod(lookup(ix + lookup(iy)), 12)
            val tt = t * t
            return tt * tt * grad2(h, dx, dy)
        }

        return 70.0 * (
                noise(ii,     jj,     x0, y0) +
                        noise(ii + i1, jj + j1, x1, y1) +
                        noise(ii + 1,  jj + 1,  x2, y2)
                )
    }

    fun sample3D(x: Double, y: Double, z: Double): Double {
        val F3 = 1.0 / 3.0
        val G3 = 1.0 / 6.0

        val s = (x + y + z) * F3
        val i = floor(x + s).toInt()
        val j = floor(y + s).toInt()
        val k = floor(z + s).toInt()

        val t = (i + j + k) * G3
        val x0 = x - (i - t)
        val y0 = y - (j - t)
        val z0 = z - (k - t)

        val (i1, j1, k1, i2, j2, k2) = if (x0 >= y0) {
            when {
                y0 >= z0 -> Sextuple(1,0,0, 1,1,0)
                x0 >= z0 -> Sextuple(1,0,0, 1,0,1)
                else     -> Sextuple(0,0,1, 1,0,1)
            }
        } else {
            when {
                y0 < z0  -> Sextuple(0,0,1, 0,1,1)
                x0 < z0  -> Sextuple(0,1,0, 0,1,1)
                else     -> Sextuple(0,1,0, 1,1,0)
            }
        }

        val x1 = x0 - i1 + G3;  val y1 = y0 - j1 + G3;  val z1 = z0 - k1 + G3
        val x2 = x0 - i2 + 0.3333333333333333; val y2 = y0 - j2 + 0.3333333333333333; val z2 = z0 - k2 + 0.3333333333333333
        val x3 = x0 - 1.0 + 0.5; val y3 = y0 - 1.0 + 0.5; val z3 = z0 - 1.0 + 0.5

        val ii = i and 255
        val jj = j and 255
        val kk = k and 255

        fun grad3f(hash: Int, x: Double, y: Double, z: Double): Double {
            val h = hash and 15
            val u = if (h < 8) x else y
            val v = if (h < 4) y else if (h == 12 || h == 14) x else z
            return (if (h and 1 == 0) u else -u) + (if (h and 2 == 0) v else -v)
        }

        fun noise(ix: Int, iy: Int, iz: Int, dx: Double, dy: Double, dz: Double): Double {
            val t = 0.6 - dx*dx - dy*dy - dz*dz
            if (t < 0) return 0.0
            val h = lookup(ix + lookup(iy + lookup(iz))) % 12
            val tt = t * t
            return tt * tt * grad3f(h, dx, dy, dz)
        }

        return 32.0 * (
                noise(ii,      jj,      kk,      x0, y0, z0) +
                        noise(ii+i1,   jj+j1,   kk+k1,   x1, y1, z1) +
                        noise(ii+i2,   jj+j2,   kk+k2,   x2, y2, z2) +
                        noise(ii+1,    jj+1,    kk+1,    x3, y3, z3)
                )
    }

    private data class Sextuple(val a:Int,val b:Int,val c:Int,val d:Int,val e:Int,val f:Int)
}

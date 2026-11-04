package faith.mimikyumochi

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Uint8Buffer

const val TILE_SIZE = 16

private const val MIP_LEVELS = 5

suspend fun loadBlockAtlas(): Texture2d {
    val blockTiles = BLOCK_TEXTURES.map { path ->
        val image = GameTextures.imageData(path)
        check(image.width == TILE_SIZE && image.height == TILE_SIZE) {
            "$path is ${image.width}x${image.height}, expected ${TILE_SIZE}x$TILE_SIZE"
        }
        image.data as Uint8Buffer
    }
    val entityImages = HashMap<String, BufferedImageData2d>()
    val cutTiles = CityEntityModels.cuts.map { cut ->
        val image = entityImages.getOrPut(cut.texture) {
            GameTextures.imageData(cut.texture)
        }
        val src = image.data as Uint8Buffer
        val tile = Uint8Buffer(TILE_SIZE * TILE_SIZE * 4)
        for (y in 0 until cut.h) for (x in 0 until cut.w) {
            val from = ((cut.y + y) * image.width + cut.x + x) * 4
            val to = (y * TILE_SIZE + x) * 4
            for (c in 0 until 4) tile[to + c] = src[from + c]
        }
        tile
    }
    val tiles = blockTiles + cutTiles

    val width = TILE_SIZE * tiles.size
    val pixels = Uint8Buffer(width * TILE_SIZE * 4)
    tiles.forEachIndexed { t, src ->
        val fill = visibleAverage(src)
        for (y in 0 until TILE_SIZE) for (x in 0 until TILE_SIZE) {
            val from = (y * TILE_SIZE + x) * 4
            val to = (y * width + t * TILE_SIZE + x) * 4
            val transparent = src[from + 3].toInt() == 0
            for (c in 0 until 4) {
                pixels[to + c] = if (transparent && c < 3) fill[c] else src[from + c]
            }
        }
    }

    val image = BufferedImageData2d(pixels, width, TILE_SIZE, TexFormat.RGBA, id = "block atlas")
    return Texture2d(image, MipMapping.Limited(MIP_LEVELS), SamplerSettings().nearest(), name = "blocks")
}

private fun visibleAverage(src: Uint8Buffer): List<UByte> {
    val sum = IntArray(3)
    var count = 0
    for (i in 0 until TILE_SIZE * TILE_SIZE) {
        if (src[i * 4 + 3].toInt() == 0) continue
        for (c in 0 until 3) sum[c] += src[i * 4 + c].toInt()
        count++
    }
    return List(3) { c -> (if (count == 0) 0 else sum[c] / count).toUByte() }
}

fun tileUv(block: Int, u: Float, v: Float, out: MutableVec2f, face: Face? = null): MutableVec2f =
    out.set(blockTile(block, face?.let(FACES::indexOf) ?: 0) * 2f + u, v)

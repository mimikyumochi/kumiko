package faith.mimikyumochi

import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.util.Uint8Buffer
import faith.mimikyumochi.core.vanilla.VanillaAssets
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object GameTextures {
    operator fun get(path: String): BufferedImage {
        val bytes = VanillaAssets.read("assets/minecraft/textures/$path.png")
            ?: error("no texture $path in ${VanillaAssets.default.name}")
        val img = ImageIO.read(bytes.inputStream()) ?: error("unreadable texture $path")
        return if (img.height > img.width) img.getSubimage(0, 0, img.width, img.width) else img
    }

    fun imageData(path: String): BufferedImageData2d {
        val image = get(path)
        val pixels = Uint8Buffer(image.width * image.height * 4)
        val gray = image.colorModel.colorSpace.type == ColorSpace.TYPE_GRAY
        val raster = image.raster
        val max = (1 shl image.colorModel.getComponentSize(0)) - 1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = if (gray) {
                val v = raster.getSample(x, y, 0) * 255 / max
                val a = if (raster.numBands > 1) raster.getSample(x, y, 1) * 255 / max else 255
                (a shl 24) or (v shl 16) or (v shl 8) or v
            } else {
                image.getRGB(x, y)
            }
            val i = (y * image.width + x) * 4
            pixels[i] = ((argb shr 16) and 0xFF).toUByte()
            pixels[i + 1] = ((argb shr 8) and 0xFF).toUByte()
            pixels[i + 2] = (argb and 0xFF).toUByte()
            pixels[i + 3] = (argb ushr 24).toUByte()
        }
        return BufferedImageData2d(pixels, image.width, image.height, TexFormat.RGBA, id = path)
    }
}

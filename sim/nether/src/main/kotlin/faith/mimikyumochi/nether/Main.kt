package faith.mimikyumochi.nether

import faith.mimikyumochi.nether.biome.NetherBiomeSource
import faith.mimikyumochi.nether.terrain.NetherTerrainGenerator
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun biomeColour(biome: String): Int = when (biome) {
    "nether_wastes" -> 0x8C4141
    "crimson_forest" -> 0xB42828
    "warped_forest" -> 0x3CA596
    "soul_sand_valley" -> 0x735F41
    "basalt_deltas" -> 0x787878
    else -> 0x000000
}

fun renderBiomes(size: Int, centreX: Int, centreZ: Int, source: NetherBiomeSource): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val startX = centreX - size / 2
    val startZ = centreZ - size / 2
    for (z in 0 until size) for (x in 0 until size) {
        image.setRGB(x, z, biomeColour(source.sampleBiome((startX + x) shr 2, 0, (startZ + z) shr 2)))
    }
    return image
}

fun renderSlice(width: Int, centreZ: Int, terrain: NetherTerrainGenerator): BufferedImage {
    val image = BufferedImage(width, terrain.worldHeight, BufferedImage.TYPE_INT_RGB)
    val startX = -width / 2
    for (x in 0 until width) {
        val column = terrain.generateColumn(startX + x, centreZ)
        for (y in column.indices) {
            val colour = when (column[y]) {
                terrain.defaultBlock -> 0x6F3636
                NetherTerrainGenerator.LAVA -> 0xE8741C
                else -> 0x1A1014
            }
            image.setRGB(x, terrain.worldHeight - 1 - y, colour)
        }
    }
    return image
}

fun main() {
    val source = NetherBiomeSource(5)
    ImageIO.write(renderBiomes(2048, 0, 0, source), "png", File("nether_map.png"))
    ImageIO.write(renderSlice(512, 0, NetherTerrainGenerator(source)), "png", File("nether_slice.png"))
}

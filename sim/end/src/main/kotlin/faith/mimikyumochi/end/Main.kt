package faith.mimikyumochi.end

import faith.mimikyumochi.end.biome.EndBiomeSource
import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.block.CPos
import faith.mimikyumochi.core.block.RPos
import faith.mimikyumochi.end.structures.EndCity
import faith.mimikyumochi.end.structures.EndCityGenerator
import faith.mimikyumochi.end.structures.EndGateway
import faith.mimikyumochi.end.terrain.EndTerrainGenerator
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO

fun renderEndMap(
    width: Int, height: Int,
    centreX: Int, centreZ: Int,
    terrainGenerator: EndTerrainGenerator
): BufferedImage {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val data = (img.raster.dataBuffer as DataBufferInt).data

    val startBlockX = centreX - width  / 2
    val startBlockZ = centreZ - height / 2

    val endCity = EndCity()
    val endCityGenerator = EndCityGenerator()
    val gateway = EndGateway()
    val seed = terrainGenerator.worldSeed

    val regionCache = mutableMapOf<RPos, CPos>()
    for (z in 0 until height) {
        val wz = startBlockZ + z
        val row = z * width

        for (x in 0 until width) {
            val wx = startBlockX + x

            val pixel = BPos(wx, 0, wz)
            val pixelChunk = pixel.toChunkPos()

            val region = pixelChunk.toRegionPos(endCity.spacing)
            val chunk = regionCache.getOrPut(region) {
                endCity.getInRegion(region.x, region.z, seed)
            }

            var endCityCanSpawn = false
            var endCityCanGen = false

            if (chunk == pixelChunk) {
                endCityCanSpawn = endCity.canSpawn(chunk.x, chunk.z, terrainGenerator.biomeSource)
                endCityCanGen = endCityGenerator.generate(terrainGenerator, chunk)
            }

            val endGatewayCanStart = gateway.canStart(pixelChunk.x, pixelChunk.z, terrainGenerator.biomeSource)

            if (endCityCanSpawn && endCityCanGen) {
                data[row + x] = (178 shl 16) or (76 shl 8) or (216)
            } else if (endGatewayCanStart) {
                data[row + x] = (58 shl 16) or (142 shl 8) or (140)
            } else {
                val h = terrainGenerator.getHeight(wx, wz)

                val t = (h.toFloat() / 128f).coerceIn(0f, 1f)
                val brightness = (t * 255).toInt()

                val r = (brightness * 0.968f).toInt().coerceIn(0, 255)
                val g = (brightness * 0.913f).toInt().coerceIn(0, 255)
                val b = (brightness * 0.639f).toInt().coerceIn(0, 255)

                data[row + x] = (r shl 16) or (g shl 8) or (b)
            }
        }
    }

    return img
}

fun main() {
    val seed: Long = 4

    val endCity = EndCity()
    val endCityGenerator = EndCityGenerator()
    val endGateway = EndGateway()

    val source = EndBiomeSource(seed)
    val terrain = EndTerrainGenerator(source)

    val region = BPos(1024, 0, 1696).toChunkPos().toRegionPos(endCity.spacing)
    val chunk = endCity.getInRegion(region.x, region.z, seed)
    val block = chunk.toBlockPos()

    val canSpawn = endCity.canSpawn(chunk.x, chunk.z, source)
    val canGen = endCityGenerator.generate(terrain, chunk)
    val hasShip = endCityGenerator.hasShip()

    println("chunk: $chunk")
    println("block: $block")
    println("canSpawn: $canSpawn")
    println("canGen: $canGen")
    if (canGen) println("hasShip: $hasShip")

    val gatewayOrder = endGateway.getEndIslandGatewayOrder(source)
    println(gatewayOrder.contentToString())

    val gateways = listOf(
        CPos(60, -95),
        CPos(84, 140),
        CPos(66, 107)
    )
    for ((i, gateway) in gateways.withIndex()) {
        val canSpawn = endGateway.canStart(gateway.x, gateway.z, source)
        println("gateway ${i+1}: $canSpawn")
    }

    println("creating end island image....")
    val img = renderEndMap(512, 512, 1044, 1708, terrain)
    ImageIO.write(img, "png", File("end_map.png"))
    println("complete!")
}

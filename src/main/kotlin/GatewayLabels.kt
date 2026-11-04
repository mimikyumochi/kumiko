package faith.mimikyumochi

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.LineMesh
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.geometry.Usage
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Uint8Buffer
import java.awt.BasicStroke
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.image.BufferedImage

class GatewayLabels(world: Node, private val camera: FlyCamera, private val chunks: ChunkManager, private val blocks: BlockShader) {
    private val shader = BlockShader(numberTexture(), MAX_NUMBER).apply { fogColor = SKY_COLOUR }

    private val mesh = Mesh(VertexLayouts.PositionNormalTexCoordColor, name = "gateway labels", usage = Usage.DYNAMIC).apply {
        shader = this@GatewayLabels.shader
        isFrustumChecked = false
    }

    private val lineShader = LineShader().apply { fogColor = SKY_COLOUR }

    private val links = LineMesh(name = "gateway links").apply { shader = lineShader }

    private var linksFor: IslandFeatures? = null
    private var linkedIndex = -1

    var isVisible: Boolean
        get() = mesh.isVisible
        set(value) {
            mesh.isVisible = value
            links.isVisible = value
        }

    private val look = MutableVec3f()
    private val right = MutableVec3f()
    private val up = MutableVec3f()
    private val centre = MutableVec3f()
    private val corner = MutableVec3f()
    private val normal = MutableVec3f()
    private val uv = MutableVec2f()

    init {
        world.addNode(mesh)
        world.addNode(links)
        world.onUpdate { rebuild() }
    }

    private fun rebuild() {
        updateLinks()
        shader.fogStart = blocks.fogStart
        shader.fogEnd = blocks.fogEnd
        lineShader.fogStart = blocks.fogStart
        lineShader.fogEnd = blocks.fogEnd

        val cam = camera.scene.camera
        look.set(cam.lookAt).subtract(cam.position).norm()
        look.cross(cam.up, right).norm()
        right.cross(look, up).norm()
        normal.set(look).mul(-1f)
        val maxDist = (chunks.renderDistance - 1) * CHUNK_WIDTH.toFloat()

        mesh.generate {
            color = Color.WHITE
            for (gateway in chunks.features.innerGateways) {
                centre.set(gateway.x + 0.5f, INNER_GATEWAY_Y + 3f, gateway.z + 0.5f)
                val dx = centre.x - cam.position.x
                val dz = centre.z - cam.position.z
                if (dx * dx + dz * dz > maxDist * maxDist) continue
                val depth = corner.set(centre).subtract(cam.position).dot(look)
                if (depth <= 0.1f) continue

                val h = LABEL_HEIGHT
                val w = h * TILE_ASPECT
                centre.y += LIFT + h / 2f

                val tile = gateway.number - 1
                val i0 = quadCorner(-w / 2f, -h / 2f, tile, 0f, 1f)
                val i1 = quadCorner(w / 2f, -h / 2f, tile, 1f, 1f)
                val i2 = quadCorner(w / 2f, h / 2f, tile, 1f, 0f)
                val i3 = quadCorner(-w / 2f, h / 2f, tile, 0f, 0f)
                addTriIndices(i0, i1, i2)
                addTriIndices(i0, i2, i3)
            }
        }
    }

    private fun updateLinks() {
        val features = chunks.features
        if (features !== linksFor) { links.clear(); linksFor = null; linkedIndex = -1 }
        if (!features.exits.isDone) return
        val exits = runCatching { features.exits.getNow(null) }
            .onFailure { if (linksFor == null) System.err.println("Couldn't find the gateway exits: $it") }
            .getOrNull()
        linksFor = features
        if (exits == null) return

        val cam = camera.scene.camera.position
        val ends = features.innerGateways.zip(exits).map { (gateway, exit) ->
            Vec3f(gateway.x + 0.5f, INNER_GATEWAY_Y + 0.5f, gateway.z + 0.5f) to
                Vec3f(exit.pos.x + 0.5f, exit.pos.y + 0.5f, exit.pos.z + 0.5f)
        }
        val nearest = ends.indices.minByOrNull { i ->
            minOf(ends[i].first.sqrDistance(cam), ends[i].second.sqrDistance(cam))
        } ?: return
        if (nearest == linkedIndex) return
        linkedIndex = nearest
        links.clear()
        links.addLine(ends[nearest].first, ends[nearest].second, Mimikyu.CHEEK)
    }

    private fun de.fabmax.kool.scene.geometry.MeshBuilder<*>.quadCorner(x: Float, y: Float, tile: Int, u: Float, v: Float): Int {
        corner.set(centre).add(MutableVec3f(right).mul(x)).add(MutableVec3f(up).mul(y))
        return vertex(corner, normal, uv.set(tile * 2f + u, v))
    }

    private companion object {
        const val MAX_NUMBER = 20

        const val TILE_W = 64
        const val TILE_H = 32
        const val TILE_ASPECT = TILE_W.toFloat() / TILE_H

        const val LABEL_HEIGHT = 1.4f

        const val LIFT = 0.5f

        fun numberTexture(): Texture2d {
            val width = TILE_W * MAX_NUMBER
            val image = BufferedImage(width, TILE_H, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val font = Font(Font.SANS_SERIF, Font.BOLD, 24)
            for (n in 1..MAX_NUMBER) {
                val layout = TextLayout("$n", font, g.fontRenderContext)
                val bounds = layout.bounds
                val x = (n - 1) * TILE_W + (TILE_W - bounds.width.toFloat()) / 2f - bounds.x.toFloat()
                val y = (TILE_H - bounds.height.toFloat()) / 2f - bounds.y.toFloat()
                val outline = layout.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
                g.color = awt(Mimikyu.INK)
                g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(outline)
                g.color = awt(Mimikyu.CLOTH)
                g.fill(outline)
            }
            g.dispose()

            val pixels = Uint8Buffer(width * TILE_H * 4)
            val ink = awt(Mimikyu.INK).rgb
            for (y in 0 until TILE_H) for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                val a = argb ushr 24
                val rgb = if (a == 0) ink else argb
                val i = (y * width + x) * 4
                pixels[i] = ((rgb shr 16) and 0xFF).toUByte()
                pixels[i + 1] = ((rgb shr 8) and 0xFF).toUByte()
                pixels[i + 2] = (rgb and 0xFF).toUByte()
                pixels[i + 3] = a.toUByte()
            }
            val data = BufferedImageData2d(pixels, width, TILE_H, TexFormat.RGBA, id = "gateway numbers")
            return Texture2d(data, MipMapping.Limited(6), SamplerSettings(), name = "gateway numbers")
        }

        fun awt(c: Color) = java.awt.Color(c.r, c.g, c.b)
    }
}

package faith.mimikyumochi

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.MutableColor
import de.fabmax.kool.util.releaseDelayed
import faith.mimikyumochi.core.terrain.TerrainGenerator
import faith.mimikyumochi.end.terrain.EndTerrainGenerator
import faith.mimikyumochi.nether.terrain.NetherTerrainGenerator
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.floor

class ChunkManager(
    private val world: Node,
    private val shader: BlockShader,
    private val portalShader: PortalShader,
    private val glassShader: BlockShader,
    private val lavaShader: LavaShader,
    private val camera: FlyCamera,
    terrain: TerrainGenerator,
) {
    var terrain = terrain
        private set

    var features = islandFeatures(terrain, false)
        private set

    var showExits = false
        set(value) {
            field = value
            reset(terrain)
        }

    var renderDistance = 16
        set(value) {
            field = value
            centreKey = null
        }

    private val workers = Executors.newFixedThreadPool(maxOf(1, Runtime.getRuntime().availableProcessors() - 1)) { task ->
        Thread(task, "chunk-gen").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    private class Geometry(val blocks: IndexedVertexList<*>?, val portals: IndexedVertexList<*>?, val glass: IndexedVertexList<*>? = null, val lava: IndexedVertexList<*>? = null)

    private class Generated(val generation: Int, val key: Long, val geometry: Geometry)

    private val pending = HashMap<Long, Future<*>>()
    private val generated = ConcurrentLinkedQueue<Generated>()
    private val loaded = HashMap<Long, List<Mesh<*>>>()

    private var generation = 0
    private var centreKey: Long? = null

    val loadedCount get() = loaded.size
    val pendingCount get() = pending.size

    fun reset(newTerrain: TerrainGenerator) {
        generation++
        pending.values.forEach { it.cancel(false) }
        pending.clear()
        generated.clear()
        loaded.values.forEach { it.forEach(::unloadMesh) }
        loaded.clear()
        terrain = newTerrain
        features = islandFeatures(newTerrain, showExits)
        centreKey = null
    }

    fun update() {
        updateFog()
        portalShader.update()
        lavaShader.update()
        val centreX = floor(camera.position.x / CHUNK_WIDTH).toInt()
        val centreZ = floor(camera.position.z / CHUNK_WIDTH).toInt()
        val centre = key(centreX, centreZ)
        if (centre != centreKey) {
            centreKey = centre
            requestAndUnload(centreX, centreZ)
        }
        buildMeshes()
    }

    private fun requestAndUnload(centreX: Int, centreZ: Int) {
        val r = renderDistance
        fun inView(dx: Int, dz: Int): Boolean {
            val ex = maxOf(0, kotlin.math.abs(dx) - 1).toLong()
            val ez = maxOf(0, kotlin.math.abs(dz) - 1).toLong()
            return ex * ex + ez * ez < r.toLong() * r
        }
        fun distSq(key: Long): Int {
            val dx = keyX(key) - centreX
            val dz = keyZ(key) - centreZ
            return dx * dx + dz * dz
        }

        val unloadSq = (r + 2) * (r + 2)
        loaded.entries.removeAll { (key, mesh) ->
            (distSq(key) > unloadSq).also { far -> if (far) mesh.forEach(::unloadMesh) }
        }
        pending.entries.removeAll { (key, future) ->
            (distSq(key) > unloadSq).also { far -> if (far) future.cancel(false) }
        }

        val wanted = ArrayList<Long>()
        for (dx in -r - 1..r + 1) for (dz in -r - 1..r + 1) {
            if (!inView(dx, dz)) continue
            val key = key(centreX + dx, centreZ + dz)
            if (key !in loaded && key !in pending) wanted += key
        }
        wanted.sortBy(::distSq)

        val terrain = terrain
        val features = features
        val generation = generation
        for (key in wanted) {
            pending[key] = workers.submit {
                try {
                    val chunk = generate(keyX(key), keyZ(key), terrain, features)
                    generated += Generated(generation, key, if (chunk.isEmpty) Geometry(null, null) else buildGeometry(chunk))
                } catch (e: Exception) {
                    System.err.println("Couldn't generate chunk ${keyX(key)}, ${keyZ(key)}: $e")
                }
            }
        }
    }

    private fun islandFeatures(terrain: TerrainGenerator, showExits: Boolean): IslandFeatures? =
        (terrain as? EndTerrainGenerator)?.let { IslandFeatures(it, showExits) }

    private fun generate(chunkX: Int, chunkZ: Int, terrain: TerrainGenerator, features: IslandFeatures?): Chunk = when (terrain) {
        is EndTerrainGenerator -> generateChunk(chunkX, chunkZ, terrain, features!!)
        is NetherTerrainGenerator -> generateNetherChunk(chunkX, chunkZ, terrain)
        else -> error("no chunks for ${terrain::class.simpleName}")
    }

    private fun updateFog() {
        val end = renderDistance * CHUNK_WIDTH.toFloat()
        shader.fogEnd = end
        shader.fogStart = end - (end / 10f).coerceIn(4f, 64f)
        portalShader.fogEnd = shader.fogEnd
        portalShader.fogStart = shader.fogStart
        glassShader.fogEnd = shader.fogEnd
        glassShader.fogStart = shader.fogStart
        lavaShader.fogEnd = shader.fogEnd
        lavaShader.fogStart = shader.fogStart
    }

    private fun buildMeshes() {
        val start = System.nanoTime()
        while (System.nanoTime() - start < MESH_BUDGET_NANOS) {
            val done = generated.poll() ?: break
            if (done.generation != generation || pending.remove(done.key) == null) continue
            val name = "chunk ${keyX(done.key)}, ${keyZ(done.key)}"
            loaded[done.key] = listOfNotNull(
                done.geometry.blocks?.let { addMesh(it, name, shader) },
                done.geometry.portals?.let { addMesh(it, "$name gateways", portalShader) },
                done.geometry.glass?.let { addMesh(it, "$name glass", glassShader).apply { drawGroupId = GLASS_DRAW_GROUP } },
                done.geometry.lava?.let { addMesh(it, "$name lava", lavaShader) },
            )
        }
    }

    private fun addMesh(geometry: IndexedVertexList<*>, name: String, meshShader: de.fabmax.kool.pipeline.DrawShader): Mesh<*> =
        Mesh(geometry, name = name).also { mesh ->
            mesh.shader = meshShader
            mesh.updateGeometryBounds()
            world.addNode(mesh)
        }

    private fun buildGeometry(chunk: Chunk): Geometry {
        val solid = IndexedVertexList(VertexLayouts.PositionNormalTexCoordColor)
        val glass = IndexedVertexList(VertexLayouts.PositionNormalTexCoordColor)
        val portals = IndexedVertexList(VertexLayouts.PositionNormalTexCoordColor)
        val lava = IndexedVertexList(VertexLayouts.PositionNormalTexCoordColor)
        val colour = MutableColor(1f, 1f, 1f, 1f)
        val solidBuilder = MeshBuilder(solid).apply { color = colour }
        val glassBuilder = MeshBuilder(glass).apply { color = colour }
        val portalBuilder = MeshBuilder(portals)
        val lavaBuilder = MeshBuilder(lava).apply { color = colour }
        val originX = chunk.pos.x * CHUNK_WIDTH
        val originZ = chunk.pos.z * CHUNK_WIDTH
        val corner = MutableVec3f()
        val light = FloatArray(4)
        val uv = MutableVec2f()
        val index = IntArray(4)

        for (y in 0..chunk.topY) for (x in 0 until CHUNK_WIDTH) for (z in 0 until CHUNK_WIDTH) {
            val block = chunk.blockAt(x, y, z)
            if (block == AIR) continue
            val model = when (block) {
                IRON_BARS -> {
                    fun connects(dx: Int, dz: Int) = chunk.blockAt(x + dx, y, z + dz).let { it == IRON_BARS || isOpaque(it) }
                    BarsModel.quads(north = connects(0, -1), south = connects(0, 1), west = connects(-1, 0), east = connects(1, 0))
                }
                in WALL_TORCH_NORTH..WALL_TORCH_EAST -> TorchModel.wall(block)
                CHORUS_PLANT -> {
                    fun chorus(dx: Int, dy: Int, dz: Int) = chunk.blockAt(x + dx, y + dy, z + dz).let { it == CHORUS_PLANT || it == CHORUS_FLOWER }
                    val connected = (if (chorus(0, 0, -1)) 1 else 0) or (if (chorus(1, 0, 0)) 2 else 0) or
                        (if (chorus(0, 0, 1)) 4 else 0) or (if (chorus(-1, 0, 0)) 8 else 0) or (if (chorus(0, 1, 0)) 16 else 0) or
                        (if (chorus(0, -1, 0) || chunk.blockAt(x, y - 1, z) == END_STONE) 32 else 0)
                    ChorusPlantModel.quads(connected, originX + x, y, originZ + z)
                }
                CHORUS_FLOWER -> ChorusFlowerModel.QUADS
                SHULKER -> {
                    val clings = listOf(0, 1, 3, 2, 5, 4).firstOrNull { lid ->
                        val (dx, dy, dz) = CityEntityModels.SHULKER_FACINGS[lid]
                        isOpaque(chunk.blockAt(x - dx, y - dy, z - dz)) && chunk.blockAt(x + dx, y + dy, z + dz) == AIR
                    }
                    clings?.let(CityEntityModels::shulker) ?: emptyList()
                }
                else -> cityModel(block)
            }
            if (model != null) {
                for (quad in model) {
                    val cull = quad.cull
                    if (cull != null) {
                        val neighbour = chunk.blockAt(x + cull[0], y + cull[1], z + cull[2])
                        if (isOpaque(neighbour)) continue
                        if (block == IRON_BARS && neighbour == IRON_BARS) continue
                    }
                    colour.set(quad.brightness, quad.brightness, quad.brightness, 1f)
                    for (i in 0 until 4) {
                        val c = quad.corners[i]
                        val t = quad.uvs[i]
                        val texel = if (quad.tile >= 0) uv.set(quad.tile * 2f + t.x, t.y) else tileUv(block, t.x, t.y, uv)
                        index[i] = solidBuilder.vertex(corner.set(originX + x + c.x, y + c.y, originZ + z + c.z), quad.normal, texel)
                    }
                    solidBuilder.addTriIndices(index[0], index[1], index[2])
                    solidBuilder.addTriIndices(index[0], index[2], index[3])
                }
                continue
            }
            if (block == END_GATEWAY) {
                for (face in FACES) {
                    if (chunk.isOpaque(x + face.dx, y + face.dy, z + face.dz)) continue
                    for (i in 0 until 4) {
                        val c = face.corners[i]
                        index[i] = portalBuilder.vertex(corner.set(originX + x + c.x, y + c.y, originZ + z + c.z), face.normal)
                    }
                    portalBuilder.addTriIndices(index[0], index[1], index[2])
                    portalBuilder.addTriIndices(index[0], index[2], index[3])
                }
                continue
            }
            if (block == LAVA) {
                val height = if (chunk.blockAt(x, y + 1, z) == LAVA) 1f else LAVA_HEIGHT
                for (face in FACES) {
                    val neighbour = chunk.blockAt(x + face.dx, y + face.dy, z + face.dz)
                    if (neighbour == LAVA || (isOpaque(neighbour) && face.dy <= 0)) continue
                    colour.set(face.brightness, face.brightness, face.brightness, 1f)
                    for (i in 0 until 4) {
                        val c = face.corners[i]
                        val t = face.uvs[i]
                        index[i] = lavaBuilder.vertex(corner.set(originX + x + c.x, y + c.y * height, originZ + z + c.z), face.normal, uv.set(t.x, t.y))
                    }
                    lavaBuilder.addTriIndices(index[0], index[1], index[2])
                    lavaBuilder.addTriIndices(index[0], index[2], index[3])
                }
                continue
            }
            val isGlass = block == MAGENTA_STAINED_GLASS
            val b = if (isGlass) glassBuilder else solidBuilder
            for (face in FACES) {
                val neighbour = chunk.blockAt(x + face.dx, y + face.dy, z + face.dz)
                if (isOpaque(neighbour)) continue
                if (isGlass && neighbour == block) continue
                if (neighbour == LAVA && (face.dy > 0 || chunk.blockAt(x + face.dx, y + face.dy + 1, z + face.dz) == LAVA)) continue

                for (i in 0 until 4) {
                    val n = face.aoNeighbours[i]
                    val side1 = chunk.isOpaque(x + n[0], y + n[1], z + n[2])
                    val side2 = chunk.isOpaque(x + n[3], y + n[4], z + n[5])
                    val diagonal = (side1 && side2) || chunk.isOpaque(x + n[6], y + n[7], z + n[8])
                    val occluders = (if (side1) 1 else 0) + (if (side2) 1 else 0) + (if (diagonal) 1 else 0)
                    light[i] = 1f - 0.2f * occluders
                }

                for (i in 0 until 4) {
                    val l = face.brightness * light[i]
                    colour.set(l, l, l, 1f)
                    val c = face.corners[i]
                    val t = face.uvs[i]
                    index[i] = b.vertex(corner.set(originX + x + c.x, y + c.y, originZ + z + c.z), face.normal, pillarSideUv(block, face, t.x, t.y, uv))
                }

                if (light[0] + light[2] > light[1] + light[3]) {
                    b.addTriIndices(index[1], index[2], index[3])
                    b.addTriIndices(index[1], index[3], index[0])
                } else {
                    b.addTriIndices(index[0], index[1], index[2])
                    b.addTriIndices(index[0], index[2], index[3])
                }
            }
        }
        return Geometry(
            solid.takeIf { it.numIndices > 0 },
            portals.takeIf { it.numIndices > 0 },
            glass.takeIf { it.numIndices > 0 },
            lava.takeIf { it.numIndices > 0 },
        )
    }

    private fun unloadMesh(mesh: Mesh<*>) {
        world.removeNode(mesh)
        mesh.releaseDelayed(3)
    }

    private companion object {
        const val MESH_BUDGET_NANOS = 4_000_000L

        const val GLASS_DRAW_GROUP = 1

        const val LAVA_HEIGHT = 8f / 9f

        fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
        fun keyX(key: Long): Int = (key shr 32).toInt()
        fun keyZ(key: Long): Int = key.toInt()
    }
}

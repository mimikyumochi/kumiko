package faith.mimikyumochi

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Time
import faith.mimikyumochi.core.version.MCVersion
import faith.mimikyumochi.end.biome.BiomeSource
import faith.mimikyumochi.end.terrain.TerrainGenerator
import java.util.Locale
import kotlin.math.floor

class Hud(
    uiScene: Scene,
    private val camera: FlyCamera,
    private val chunks: ChunkManager,
    initialSeed: Long,
    private val gatewayLabels: GatewayLabels,
) {
    private val seedText = mutableStateOf(initialSeed.toString())
    private val coordinates = mutableStateOf("")
    private val tpText = mutableStateOf("")
    private val renderDistance = mutableStateOf(chunks.renderDistance)

    private val message = mutableStateOf("")
    private var messageTimer = 0f

    private var f3Held = false

    private var seedField: TextFieldScope? = null
    private val versionIndex = mutableStateOf(VERSIONS.indexOf(chunks.terrain.version))

    private var biomeAt: Pair<Any, Triple<Int, Int, Int>>? = null
    private var biome = ""
    private var tpField: TextFieldScope? = null

    init {
        uiScene.setupUiScene()
        setTitle(initialSeed)

        val surface = uiScene.addPanelSurface(
            colors = Mimikyu.UI_COLOURS,
            width = FitContent,
            height = FitContent,
            backgroundColor = { Mimikyu.PANEL },
        ) {
            modifier.padding(sizes.gap)

            Row {
                Text("Seed: ") { modifier.alignY(AlignmentY.Center) }
                seedField = TextField(seedText.use()) {
                    modifier
                        .width(Dp(220f))
                        .hint("number or text, Enter to load")
                        .onChange { seedText.set(it) }
                        .onEnterPressed {
                            loadSeed(it)
                            surface.requestFocus(null)
                        }
                }
            }
            Row {
                modifier.margin(top = sizes.smallGap)
                Text("Version: ") { modifier.alignY(AlignmentY.Center) }
                ComboBox {
                    modifier
                        .width(Dp(220f))
                        .items(VERSIONS.map(::versionLabel))
                        .selectedIndex(versionIndex.use())
                        .onItemSelected {
                            versionIndex.set(it)
                            loadWorld(chunks.terrain.worldSeed, VERSIONS[it])
                        }
                }
            }
            Row {
                modifier.margin(top = sizes.smallGap)
                Text("Teleport: ") { modifier.alignY(AlignmentY.Center) }
                tpField = TextField(tpText.use()) {
                    modifier
                        .width(Dp(220f))
                        .hint("paste F3+C or x y z, Enter")
                        .onChange { tpText.set(it) }
                        .onEnterPressed {
                            if (teleport(it)) tpText.set("")
                            surface.requestFocus(null)
                        }
                }
            }
            Row {
                modifier.margin(top = sizes.smallGap)
                Text("Render distance: ") { modifier.alignY(AlignmentY.Center) }
                Slider(renderDistance.use().toFloat(), MIN_RENDER_DISTANCE.toFloat(), MAX_RENDER_DISTANCE.toFloat()) {
                    modifier
                        .width(Dp(160f))
                        .alignY(AlignmentY.Center)
                        .onChange { renderDistance.set(Math.round(it)) }
                        .onChangeEnd {
                            chunks.renderDistance = renderDistance.value
                            surface.requestFocus(null)
                        }
                }
                Text(" ${renderDistance.use()} chunks") { modifier.alignY(AlignmentY.Center) }
            }
            Text(coordinates.use()) {
                modifier.margin(top = sizes.gap)
            }
        }

        uiScene.addPanelSurface(
            colors = Mimikyu.UI_COLOURS,
            width = FitContent,
            height = FitContent,
            backgroundColor = { if (message.use().isEmpty()) null else Mimikyu.PANEL },
        ) {
            modifier.align(AlignmentX.Start, AlignmentY.Bottom).margin(sizes.gap).padding(sizes.smallGap)
            if (message.use().isNotEmpty()) {
                Text(message.use()) { modifier.textColor(Mimikyu.MESSAGE) }
            }
        }

        uiScene.onUpdate {
            coordinates.set(describePosition())
            if (messageTimer > 0f) {
                messageTimer -= Time.deltaT
                if (messageTimer <= 0f) message.set("")
            }
        }

        surface.inputHandler.keyboardListeners += InputStack.KeyboardListener { events, _ ->
            for (ev in events) {
                if (!ev.isPressed || !ev.isCtrlDown || ev.localKeyCode != LocalKeyCode('v')) continue
                val target = when {
                    tpField?.isFocused?.value == true -> tpText
                    seedField?.isFocused?.value == true -> seedText
                    else -> continue
                }
                ev.isConsumed = true
                val pasted = NativeClipboard.get()?.replace(Regex("[\r\n]+"), " ")?.trim() ?: continue
                target.set(target.value + pasted)
            }
        }

        InputStack.defaultInputHandler.keyboardListeners += InputStack.KeyboardListener { events, _ ->
            for (ev in events) {
                if (ev.keyCode == KeyboardInput.KEY_F1 && ev.isPressed) {
                    surface.isVisible = !surface.isVisible
                    gatewayLabels.isVisible = surface.isVisible
                } else if (ev.keyCode == KeyboardInput.KEY_F4 && ev.isPressed) {
                    chunks.showExits = !chunks.showExits
                    showMessage(if (chunks.showExits) "Showing exit gateways" else "Hiding exit gateways")
                } else if (ev.keyCode == KeyboardInput.KEY_F3) {
                    if (ev.isPressed) f3Held = true else if (ev.isReleased) f3Held = false
                } else if (f3Held && ev.isPressed && ev.keyCode == UniversalKeyCode('c')) {
                    NativeClipboard.set(tpCommand())
                    showMessage("Copied location to clipboard")
                }
            }
        }
    }

    fun showMessage(text: String) {
        message.set(text)
        messageTimer = MESSAGE_SECONDS
    }

    private fun tpCommand(): String = String.format(
        Locale.ROOT, "/execute in minecraft:the_end run tp @s %.2f %.2f %.2f %.2f %.2f",
        camera.position.x, camera.feetY, camera.position.z, camera.yaw, camera.pitch,
    )

    private fun teleport(input: String): Boolean {
        val target = try {
            parseTeleport(input, camera.position.x.toDouble(), camera.feetY.toDouble(), camera.position.z.toDouble(), camera.yaw, camera.pitch)
        } catch (e: IllegalArgumentException) {
            showMessage("Can't teleport: ${e.message}")
            return false
        }
        camera.goTo(target.x, target.y, target.z, target.yaw, target.pitch)
        val dimension = Regex("""\bin\s+(\S+)""").find(input)?.groupValues?.get(1)
        if (dimension != null && dimension.removePrefix("minecraft:") != "the_end") {
            showMessage("Teleported, but that position is in $dimension, not the End")
        } else {
            showMessage(String.format(Locale.ROOT, "Teleported to %.2f, %.2f, %.2f", target.x, target.y, target.z))
        }
        return true
    }

    private fun loadSeed(input: String) {
        val seed = parseSeed(input)
        seedText.set(seed.toString())
        loadWorld(seed, chunks.terrain.version)
    }

    private fun loadWorld(seed: Long, version: MCVersion) {
        chunks.reset(TerrainGenerator(BiomeSource(seed, version)))
        biomeAt = null
        setTitle(seed)
        showMessage("Loaded seed $seed for ${versionLabel(version)}")
    }

    private fun biomeAt(blockX: Int, blockY: Int, blockZ: Int): String {
        val terrain = chunks.terrain
        val key = terrain to Triple(blockX, blockY, blockZ)
        if (key != biomeAt) {
            biomeAt = key
            biome = "minecraft:" + terrain.biomeSource.biomeAt(blockX, blockY, blockZ)
        }
        return biome
    }

    private fun setTitle(seed: Long) {
        KoolSystem.requireContext().window.title = "kumiko - seed: $seed"
    }

    private fun describePosition(): String {
        val p = camera.position
        val feetY = camera.feetY
        val blockX = floor(p.x).toInt()
        val blockY = floor(feetY).toInt()
        val blockZ = floor(p.z).toInt()

        val yaw = ((camera.yaw % 360f) + 540f) % 360f - 180f
        val facing = when {
            yaw >= -45f && yaw < 45f -> "south (towards +Z)"
            yaw >= 45f && yaw < 135f -> "west (towards -X)"
            yaw >= -135f && yaw < -45f -> "east (towards +X)"
            else -> "north (towards -Z)"
        }

        return buildString {
            appendLine("XYZ: ${fmt(p.x, 3)} / ${fmt(feetY, 5)} / ${fmt(p.z, 3)}")
            appendLine("Block: $blockX $blockY $blockZ")
            appendLine("Chunk: ${Math.floorMod(blockX, 16)} ${Math.floorMod(blockY, 16)} ${Math.floorMod(blockZ, 16)} in ${Math.floorDiv(blockX, 16)} ${Math.floorDiv(blockY, 16)} ${Math.floorDiv(blockZ, 16)}")
            appendLine("Biome: ${biomeAt(blockX, blockY, blockZ)}")
            appendLine("Facing: $facing (${fmt(yaw, 1)} / ${fmt(camera.pitch, 1)})")
            append("Chunks: ${chunks.loadedCount} loaded, ${chunks.pendingCount} generating")
        }
    }

    private fun fmt(v: Float, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)

    companion object {
        private val VERSIONS = MCVersion.entries.reversed()

        fun versionLabel(version: MCVersion): String = when (version) {
            MCVersion.V1_13 -> "1.13 - 1.15"
            MCVersion.V1_16 -> "1.16"
            MCVersion.V1_17 -> "1.17"
            MCVersion.V1_18 -> "1.18"
            MCVersion.V1_19 -> "1.19 - 26.2"
            MCVersion.V26_3 -> "26.3+"
        }
        private const val MESSAGE_SECONDS = 4f

        private const val MIN_RENDER_DISTANCE = 2
        private const val MAX_RENDER_DISTANCE = 96

        class TeleportTarget(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

        fun parseTeleport(input: String, x: Double, y: Double, z: Double, yaw: Float, pitch: Float): TeleportTarget {
            var tokens = input.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val tp = tokens.indexOfLast { it.removePrefix("/") == "tp" || it.removePrefix("/") == "teleport" }
            if (tp >= 0) tokens = tokens.drop(tp + 1)
            if (tokens.firstOrNull()?.startsWith("@") == true) tokens = tokens.drop(1)
            require(tokens.size == 3 || tokens.size == 5) { "expected x y z, or x y z yaw pitch" }

            fun coord(token: String, current: Double): Double {
                require(!token.startsWith("^")) { "^ (local) coordinates aren't supported" }
                val number = if (token.startsWith("~")) token.drop(1).ifEmpty { "0" } else token
                val value = requireNotNull(number.toDoubleOrNull()) { "not a number: $token" }
                return if (token.startsWith("~")) current + value else value
            }

            return TeleportTarget(
                coord(tokens[0], x), coord(tokens[1], y), coord(tokens[2], z),
                if (tokens.size == 5) coord(tokens[3], yaw.toDouble()).toFloat() else yaw,
                if (tokens.size == 5) coord(tokens[4], pitch.toDouble()).toFloat().coerceIn(-90f, 90f) else pitch,
            )
        }

        fun parseSeed(input: String): Long {
            val text = input.trim()
            if (text.isEmpty()) return kotlin.random.Random.nextLong()
            return text.toLongOrNull() ?: text.hashCode().toLong()
        }
    }
}

package faith.mimikyumochi

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import faith.mimikyumochi.core.vanilla.VanillaAssets

class LavaShader(texture: Texture2d, frameCount: Int, private val sequence: IntArray, private val ticksPerFrame: Int) : KslShader("lava") {
    var fogColor: Color by uniformColor("uFogColor")
    var fogStart: Float by uniform1f("uFogStart")
    var fogEnd: Float by uniform1f("uFogEnd")

    private var frame: Float by uniform1f("uFrame")

    @Suppress("unused")
    private val lava by texture2d("tLava", texture)

    init {
        program.apply {
            val uv = interStageFloat2()
            val shade = interStageFloat4()
            val worldPos = interStageFloat3()

            vertexStage {
                main {
                    val pos = float3Var(vertexAttrib(VertexLayouts.Position.position))
                    uv.input set vertexAttrib(VertexLayouts.TexCoord.texCoord)
                    shade.input set vertexAttrib(VertexLayouts.Color.color)
                    worldPos.input set pos
                    outPosition set cameraData().viewProjMat * float4Value(pos, 1f.const)
                }
            }
            fragmentStage {
                main {
                    val inFrame = float2Var(clamp(uv.output, float2Value(HALF_TEXEL, HALF_TEXEL), float2Value(1f - HALF_TEXEL, 1f - HALF_TEXEL)))
                    val stripUv = float2Value(inFrame.x, (uniformFloat1("uFrame") + inFrame.y) / frameCount.toFloat().const)
                    val colour = float4Var(sampleTexture(texture2d("tLava"), stripUv) * shade.output)
                    val offset = float3Var(worldPos.output - cameraData().position)
                    val distance = float1Var(max(length(offset.xz), abs(offset.y)))
                    val fog = float1Var(clamp((distance - uniformFloat1("uFogStart")) / (uniformFloat1("uFogEnd") - uniformFloat1("uFogStart")), 0f.const, 1f.const))
                    colorOutput(mix(colour.rgb, uniformFloat4("uFogColor").rgb, fog), 1f.const)
                }
            }
        }
    }

    fun update() {
        val tick = (Time.gameTime * 20.0).toLong()
        frame = sequence[((tick / ticksPerFrame) % sequence.size).toInt()].toFloat()
    }

    companion object {
        private const val HALF_TEXEL = 0.5f / TILE_SIZE
        private const val PATH = "block/lava_still"
        private const val MIP_LEVELS = 5

        fun load(): LavaShader {
            val image = GameTextures.imageData(PATH, allFrames = true)
            val frameCount = image.height / image.width
            val meta = VanillaAssets.read("assets/minecraft/textures/$PATH.png.mcmeta")?.decodeToString().orEmpty()
            val ticks = Regex(""""frametime"\s*:\s*(\d+)""").find(meta)?.groupValues?.get(1)?.toInt() ?: 1
            val listed = Regex(""""frames"\s*:\s*\[([^]]*)]""").find(meta)?.groupValues?.get(1)
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
            val sequence = listed.ifEmpty { (0 until frameCount).toList() }.toIntArray()
            val texture = Texture2d(image, MipMapping.Limited(MIP_LEVELS), SamplerSettings().nearest(), name = PATH)
            return LavaShader(texture, frameCount, sequence, ticks)
        }
    }
}

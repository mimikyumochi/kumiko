package faith.mimikyumochi

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.math.Vec3f
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PortalShader(sky: Texture2d, stars: Texture2d) : KslShader("end gateway") {
    var fogColor: Color by uniformColor("uFogColor")
    var fogStart: Float by uniform1f("uFogStart")
    var fogEnd: Float by uniform1f("uFogEnd")

    var gameTime: Float by uniform1f("uGameTime")

    @Suppress("unused")
    private val skyTexture by texture2d("tSky", sky)

    @Suppress("unused")
    private val starTexture by texture2d("tStars", stars)

    init {
        program.apply {
            val texProj = interStageFloat4()
            val worldPos = interStageFloat3()

            vertexStage {
                main {
                    val pos = float3Var(vertexAttrib(VertexLayouts.Position.position))
                    val clip = float4Var(cameraData().viewProjMat * float4Value(pos, 1f.const))
                    val up = if (KoolSystem.requireContext().backend.isInvertedNdcY) -clip.y else clip.y
                    texProj.input set float4Value((clip.x + clip.w) * 0.5f.const, (up + clip.w) * 0.5f.const, clip.z, clip.w)
                    worldPos.input set pos
                    outPosition set clip
                }
            }
            fragmentStage {
                main {
                    val screen = float2Var(texProj.output.xy / texProj.output.w)
                    val time = uniformFloat1("uGameTime")
                    val sky = texture2d("tSky")
                    val stars = texture2d("tStars")

                    val colour = float3Var(sampleTexture(sky, screen).rgb * COLORS[0].const)
                    for (i in 0 until LAYERS) {
                        val layer = Layer(i + 1)
                        val x = screen.x * layer.xx.const + screen.y * layer.yx.const + layer.shiftX.const
                        val y = screen.x * layer.xy.const + screen.y * layer.yy.const + time * layer.drift.const
                        val uv = float2Value(x * 0.5f.const + 0.25f.const, y * 0.5f.const + 0.25f.const)
                        colour set colour + sampleTexture(stars, uv).rgb * COLORS[i].const
                    }

                    val offset = float3Var(worldPos.output - cameraData().position)
                    val distance = float1Var(max(length(offset.xz), abs(offset.y)))
                    val fog = float1Var(clamp((distance - uniformFloat1("uFogStart")) / (uniformFloat1("uFogEnd") - uniformFloat1("uFogStart")), 0f.const, 1f.const))
                    colorOutput(mix(colour, uniformFloat4("uFogColor").rgb, fog), 1f.const)
                }
            }
        }
    }

    fun update() {
        gameTime = ((Time.gameTime * 20.0) % 24000.0 / 24000.0).toFloat()
    }

    private class Layer(layer: Int) {
        private val l = layer.toFloat()
        private val k = (4.5f - l / 4f) * 2f
        private val angle = ((l * l * 4321f + l * 9f) * 2f * (PI.toFloat() / 180f)).toDouble()
        val xx = k * cos(angle).toFloat()
        val yx = -k * sin(angle).toFloat()
        val xy = k * sin(angle).toFloat()
        val yy = k * cos(angle).toFloat()
        val shiftX = 17f / l
        val drift = (2f + l / 1.5f) * 1.5f
    }

    companion object {
        const val LAYERS = 16

        private val COLORS = listOf(
            Vec3f(0.022087f, 0.098399f, 0.110818f),
            Vec3f(0.011892f, 0.095924f, 0.089485f),
            Vec3f(0.027636f, 0.101689f, 0.100326f),
            Vec3f(0.046564f, 0.109883f, 0.114838f),
            Vec3f(0.064901f, 0.117696f, 0.097189f),
            Vec3f(0.063761f, 0.086895f, 0.123646f),
            Vec3f(0.084817f, 0.111994f, 0.166380f),
            Vec3f(0.097489f, 0.154120f, 0.091064f),
            Vec3f(0.106152f, 0.131144f, 0.195191f),
            Vec3f(0.097721f, 0.110188f, 0.187229f),
            Vec3f(0.133516f, 0.138278f, 0.148582f),
            Vec3f(0.070006f, 0.243332f, 0.235792f),
            Vec3f(0.196766f, 0.142899f, 0.214696f),
            Vec3f(0.047281f, 0.315338f, 0.321970f),
            Vec3f(0.204675f, 0.390010f, 0.302066f),
            Vec3f(0.080955f, 0.314821f, 0.661491f),
        )

        suspend fun load(): PortalShader {
            fun texture(path: String): Texture2d {
                val image = GameTextures.imageData(path)
                return Texture2d(image, MipMapping.Off, SamplerSettings().nearest(), name = path)
            }
            return PortalShader(texture("environment/end_sky"), texture("entity/end_portal/end_portal"))
        }
    }
}

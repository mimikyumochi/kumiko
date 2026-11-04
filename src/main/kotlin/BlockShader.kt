package faith.mimikyumochi

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.PipelineConfig
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color

class BlockShader(texture: Texture2d, tileCount: Int, translucent: Boolean = false) :
    KslShader(KslProgram(if (translucent) "translucent blocks" else "blocks"), PipelineConfig(isWriteDepth = !translucent)) {
    var fogColor: Color by uniformColor("uFogColor")
    var fogStart: Float by uniform1f("uFogStart")
    var fogEnd: Float by uniform1f("uFogEnd")

    @Suppress("unused")
    private val blocks by texture2d("tBlocks", texture)

    init {
        program.apply {
            val tile = interStageFloat1(interpolation = KslInterStageInterpolation.Flat)
            val uv = interStageFloat2()
            val shade = interStageFloat4()
            val worldPos = interStageFloat3()

            vertexStage {
                main {
                    val pos = float3Var(vertexAttrib(VertexLayouts.Position.position))
                    val packed = float2Var(vertexAttrib(VertexLayouts.TexCoord.texCoord))
                    val tileIndex = float1Var(floor(packed.x * 0.5f.const))
                    tile.input set tileIndex
                    uv.input set float2Value(packed.x - tileIndex * 2f.const, packed.y)
                    shade.input set vertexAttrib(VertexLayouts.Color.color)
                    worldPos.input set pos
                    outPosition set cameraData().viewProjMat * float4Value(pos, 1f.const)
                }
            }
            fragmentStage {
                main {
                    val inTile = float2Var(clamp(uv.output, float2Value(HALF_TEXEL, HALF_TEXEL), float2Value(1f - HALF_TEXEL, 1f - HALF_TEXEL)))
                    val atlasUv = float2Value((tile.output + inTile.x) / tileCount.toFloat().const, inTile.y)
                    val colour = float4Var(sampleTexture(texture2d("tBlocks"), atlasUv) * shade.output)
                    `if`(colour.a lt (if (translucent) 0.004f else 0.5f).const) { discard() }
                    val offset = float3Var(worldPos.output - cameraData().position)
                    val distance = float1Var(max(length(offset.xz), abs(offset.y)))
                    val fog = float1Var(clamp((distance - uniformFloat1("uFogStart")) / (uniformFloat1("uFogEnd") - uniformFloat1("uFogStart")), 0f.const, 1f.const))
                    colorOutput(mix(colour.rgb, uniformFloat4("uFogColor").rgb, fog), if (translucent) colour.a else 1f.const)
                }
            }
        }
    }

    private companion object {
        const val HALF_TEXEL = 0.5f / TILE_SIZE
    }
}

package faith.mimikyumochi

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.PipelineConfig
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color

class LineShader : KslShader(KslProgram("fogged lines"), PipelineConfig()) {
    var fogColor: Color by uniformColor("uFogColor")
    var fogStart: Float by uniform1f("uFogStart")
    var fogEnd: Float by uniform1f("uFogEnd")

    init {
        program.apply {
            val colour = interStageFloat4()
            val worldPos = interStageFloat3()

            vertexStage {
                main {
                    val pos = float3Var(vertexAttrib(VertexLayouts.Position.position))
                    colour.input set vertexAttrib(VertexLayouts.Color.color)
                    worldPos.input set pos
                    outPosition set cameraData().viewProjMat * float4Value(pos, 1f.const)
                }
            }
            fragmentStage {
                main {
                    val offset = float3Var(worldPos.output - cameraData().position)
                    val distance = float1Var(max(length(offset.xz), abs(offset.y)))
                    val fog = float1Var(clamp((distance - uniformFloat1("uFogStart")) / (uniformFloat1("uFogEnd") - uniformFloat1("uFogStart")), 0f.const, 1f.const))
                    `if`(fog ge 1f.const) { discard() }
                    colorOutput(mix(colour.output.rgb, uniformFloat4("uFogColor").rgb, fog), 1f.const)
                }
            }
        }
    }
}

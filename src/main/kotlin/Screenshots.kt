package faith.mimikyumochi

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.pipeline.AttachmentConfig
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Uint8Buffer
import de.fabmax.kool.util.delayFrames
import de.fabmax.kool.util.launchOnMainThread
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

class Screenshots(
    private val scene: Scene,
    private val content: Node,
    private val onMessage: (String) -> Unit = ::println,
) {
    private var busy = false

    init {
        InputStack.defaultInputHandler.keyboardListeners += InputStack.KeyboardListener { events, _ ->
            if (events.any { it.isPressed && it.keyCode == KeyboardInput.KEY_F2 } && !busy) {
                busy = true
                launchOnMainThread {
                    try {
                        val file = take()
                        println("Saved screenshot as ${file.path}")
                        onMessage("Saved screenshot as ${file.name}")
                    } catch (e: Exception) {
                        onMessage("Couldn't save screenshot: $e")
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    private suspend fun take(): File {
        val size = KoolSystem.requireContext().window.framebufferSize
        val pass = OffscreenPass2d(
            drawNode = content,
            attachmentConfig = AttachmentConfig.singleColorDefaultDepth(TexFormat.RGBA, scene.clearColor),
            initialSize = size,
            name = "screenshot",
        )
        pass.isUpdateDrawNode = false
        pass.isReleaseDrawNode = false
        pass.camera = scene.camera
        val copy = pass.copyColor()

        scene.addOffscreenPass(pass)
        try {
            delayFrames(2)
            val image = copy.download()
            val rgba = image.data as Uint8Buffer
            val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val i = (y * image.width + x) * 4
                val r = rgba[i].toInt()
                val g = rgba[i + 1].toInt()
                val b = rgba[i + 2].toInt()
                out.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
            val name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"))
            val file = File("screenshots/$name.png")
            file.parentFile.mkdirs()
            ImageIO.write(out, "png", file)
            return file.absoluteFile
        } finally {
            scene.removeOffscreenPass(pass)
            pass.release()
            copy.release()
        }
    }
}

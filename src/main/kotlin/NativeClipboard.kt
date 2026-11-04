package faith.mimikyumochi

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.platform.glfw.GlfwWindow
import org.lwjgl.glfw.GLFW

object NativeClipboard {
    private val window: Long get() = (KoolSystem.requireContext().window as GlfwWindow).windowHandle

    fun get(): String? = GLFW.glfwGetClipboardString(window)

    fun set(text: String) = GLFW.glfwSetClipboardString(window, text)
}

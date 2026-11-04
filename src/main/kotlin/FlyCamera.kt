package faith.mimikyumochi

import de.fabmax.kool.input.CursorMode
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Time
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class FlyCamera(val scene: Scene) {
    val position = MutableVec3f()
    var yaw = 0f
    var pitch = 0f
    var speed = 20f
    var fov = DEFAULT_FOV

    private val held = mutableSetOf<KeyCode>()

    init {
        InputStack.defaultInputHandler.keyboardListeners += InputStack.KeyboardListener { events, _ ->
            for (ev in events) {
                if (ev.isPressed || ev.isRepeated) {
                    held += ev.keyCode
                } else if (ev.isReleased) {
                    held -= ev.keyCode
                }
            }
        }
    }

    val feetY: Float get() = position.y - EYE_HEIGHT.toFloat()

    private fun anyHeld(vararg keys: KeyCode) = keys.any { it in held }

    private fun axis(positive: Boolean, negative: Boolean) = (if (positive) 1f else 0f) - (if (negative) 1f else 0f)

    private var looking = false
    private val lookAt = MutableVec3f()

    init {
        scene.onUpdate { update(Time.deltaT) }
    }

    fun goTo(x: Double, feetY: Double, z: Double, yaw: Float, pitch: Float) {
        position.set(x.toFloat(), (feetY + EYE_HEIGHT).toFloat(), z.toFloat())
        this.yaw = yaw
        this.pitch = pitch
    }

    private fun update(dt: Float) {
        val pointer = PointerInput.primaryPointer

        if (InputStack.handlerStack.any { it.blockAllKeyboardInput }) held.clear()

        if (pointer.isRightButtonDown != looking) {
            looking = pointer.isRightButtonDown
            PointerInput.cursorMode = if (looking) CursorMode.LOCKED else CursorMode.NORMAL
        }
        if (looking) {
            val sensitivity = MOUSE_SENSITIVITY * fov / DEFAULT_FOV
            yaw += pointer.delta.x * sensitivity
            pitch = (pitch + pointer.delta.y * sensitivity).coerceIn(-90f, 90f)
        }

        if (pointer.scroll.y != 0f) {
            fov = (fov * 0.9f.pow(pointer.scroll.y)).coerceIn(MIN_FOV, MAX_FOV)
        }
        if (pointer.isMiddleButtonPressed) fov = DEFAULT_FOV
        (scene.camera as? PerspectiveCamera)?.fovY = fov.deg

        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val sinYaw = sin(yawRad).toFloat()
        val cosYaw = cos(yawRad).toFloat()

        val fast = anyHeld(KeyboardInput.KEY_CTRL_LEFT, KeyboardInput.KEY_CTRL_RIGHT)
        val step = speed * dt * (if (fast) 4f else 1f)
        val forward = axis(
            anyHeld(UniversalKeyCode('w'), KeyboardInput.KEY_CURSOR_UP),
            anyHeld(UniversalKeyCode('s'), KeyboardInput.KEY_CURSOR_DOWN),
        )
        val strafe = axis(
            anyHeld(UniversalKeyCode('d'), KeyboardInput.KEY_CURSOR_RIGHT),
            anyHeld(UniversalKeyCode('a'), KeyboardInput.KEY_CURSOR_LEFT),
        )
        val vertical = axis(
            anyHeld(UniversalKeyCode(' ')),
            anyHeld(KeyboardInput.KEY_SHIFT_LEFT, KeyboardInput.KEY_SHIFT_RIGHT),
        )
        position.x += (-sinYaw * forward - cosYaw * strafe) * step
        position.z += (cosYaw * forward - sinYaw * strafe) * step
        position.y += vertical * step

        val cosPitch = cos(pitchRad).toFloat()
        val sinPitch = sin(pitchRad).toFloat()
        val look = Vec3f(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch)
        val up = Vec3f(-sinYaw * sinPitch, cosPitch, cosYaw * sinPitch)
        scene.camera.setupCamera(position, up, lookAt.set(position).add(look))
    }

    private companion object {
        const val EYE_HEIGHT = 1.62
        const val MOUSE_SENSITIVITY = 0.15f
        const val DEFAULT_FOV = 70f
        const val MIN_FOV = 5f
        const val MAX_FOV = 110f
    }
}

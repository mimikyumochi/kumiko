package faith.mimikyumochi

import de.fabmax.kool.modules.ui2.Colors
import de.fabmax.kool.util.Color

object Mimikyu {
    val CLOTH = Color("F2DC8F")
    val CLOTH_SHADE = Color("D9BC62")
    val CLOTH_LIGHT = Color("FBF1CC")

    val INK = Color("2B2019")

    val CHEEK = Color("E8786A")
    val CHEEK_SHADE = Color("B9554B")

    val STICK = Color("8A6640")

    val SHADOW = Color("1E1624")
    val SHADOW_LIGHT = Color("2E2236")

    val SKY = SHADOW

    val UI_COLOURS = Colors(
        primary = CLOTH,
        primaryVariant = CLOTH_SHADE,
        secondary = CHEEK,
        secondaryVariant = CHEEK_SHADE,
        background = SHADOW,
        backgroundVariant = SHADOW_LIGHT,
        onPrimary = INK,
        onSecondary = INK,
        onBackground = CLOTH_LIGHT,
        isLight = false,
    )

    val PANEL = SHADOW.withAlpha(0.75f)

    val MESSAGE = CHEEK
}

package faith.mimikyumochi.end.structures

import faith.mimikyumochi.core.structure.StructureTemplate
import faith.mimikyumochi.core.vanilla.VanillaAssets
import java.util.concurrent.ConcurrentHashMap

object EndCityTemplates {
    private val loaded = ConcurrentHashMap<String, StructureTemplate>()

    operator fun get(name: String): StructureTemplate = loaded.computeIfAbsent(name) {
        val bytes = VanillaAssets.read("data/minecraft/structure/end_city/$name.nbt")
            ?: error("No end city template '$name' in ${VanillaAssets.default.name}")
        StructureTemplate.read(bytes.inputStream())
    }
}

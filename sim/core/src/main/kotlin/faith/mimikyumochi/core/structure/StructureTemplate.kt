package faith.mimikyumochi.core.structure

import faith.mimikyumochi.core.block.BPos
import faith.mimikyumochi.core.block.BlockMirror
import faith.mimikyumochi.core.block.BlockRotation
import java.io.InputStream

data class BlockState(val name: String, val properties: Map<String, String> = emptyMap()) {
    operator fun get(property: String): String? = properties[property]

    fun rotated(rotation: BlockRotation): BlockState {
        if (rotation == BlockRotation.NONE || properties.isEmpty()) return this
        val turned = properties.mapValues { (key, value) ->
            when (key) {
                "facing" -> HORIZONTAL.indexOf(value).takeIf { it >= 0 }?.let { HORIZONTAL[(it + rotation.ordinal) % 4] } ?: value
                "axis" -> if (rotation.ordinal % 2 == 1) mapOf("x" to "z", "z" to "x")[value] ?: value else value
                else -> value
            }
        }
        return BlockState(name, turned)
    }

    private companion object {
        val HORIZONTAL = listOf("north", "east", "south", "west")
    }
}

class StructureTemplate(val size: BPos, val blocks: List<Block>, val markers: List<Marker> = emptyList()) {
    class Block(val pos: BPos, val state: BlockState)

    class Marker(val pos: BPos, val metadata: String)

    fun place(origin: BPos, rotation: BlockRotation, overwrite: Boolean): Sequence<Block> = blocks.asSequence()
        .filter { it.state.name != STRUCTURE_BLOCK && (overwrite || it.state.name != AIR) }
        .map { Block(it.pos.transform(BlockMirror.NONE, rotation, ZERO).add(origin), it.state.rotated(rotation)) }

    fun markers(origin: BPos, rotation: BlockRotation): List<Marker> =
        markers.map { Marker(it.pos.transform(BlockMirror.NONE, rotation, ZERO).add(origin), it.metadata) }

    companion object {
        const val AIR = "minecraft:air"
        const val STRUCTURE_BLOCK = "minecraft:structure_block"
        private val ZERO = BPos(0, 0, 0)

        fun read(input: InputStream): StructureTemplate {
            val root = Nbt.readGzipped(input)
            val size = (root["size"] as List<*>).map { it as Int }
            @Suppress("UNCHECKED_CAST")
            val palette = (root["palette"] as List<Map<String, Any?>>).map { entry ->
                @Suppress("UNCHECKED_CAST")
                val properties = ((entry["properties"] ?: entry["Properties"]) as Map<String, Any?>?)?.mapValues { it.value as String } ?: emptyMap()
                BlockState((entry["id"] ?: entry["Name"]) as String, properties)
            }
            @Suppress("UNCHECKED_CAST")
            val entries = root["blocks"] as List<Map<String, Any?>>
            val blocks = entries.map { block ->
                val (x, y, z) = (block["pos"] as List<*>).map { it as Int }
                Block(BPos(x, y, z), palette[block["state"] as Int])
            }
            val markers = entries.mapNotNull { block ->
                val state = palette[block["state"] as Int]
                val nbt = block["nbt"] as Map<String, Any?>? ?: return@mapNotNull null
                if (state.name != STRUCTURE_BLOCK || nbt["mode"] != "DATA") return@mapNotNull null
                val (x, y, z) = (block["pos"] as List<*>).map { it as Int }
                Marker(BPos(x, y, z), nbt["metadata"] as String? ?: "")
            }
            return StructureTemplate(BPos(size[0], size[1], size[2]), blocks, markers)
        }
    }
}

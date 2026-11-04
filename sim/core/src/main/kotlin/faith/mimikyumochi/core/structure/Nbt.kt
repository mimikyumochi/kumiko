package faith.mimikyumochi.core.structure

import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object Nbt {
    fun readGzipped(input: InputStream): Map<String, Any?> =
        DataInputStream(GZIPInputStream(input).buffered()).use { data ->
            val type = data.readByte().toInt()
            require(type == COMPOUND) { "NBT root isn't a compound (tag type $type)" }
            data.readUTF()
            @Suppress("UNCHECKED_CAST")
            read(data, type) as Map<String, Any?>
        }

    private fun read(data: DataInputStream, type: Int): Any? = when (type) {
        1 -> data.readByte()
        2 -> data.readShort()
        3 -> data.readInt()
        4 -> data.readLong()
        5 -> data.readFloat()
        6 -> data.readDouble()
        7 -> ByteArray(data.readInt()).also { data.readFully(it) }
        8 -> data.readUTF()
        9 -> {
            val elementType = data.readByte().toInt()
            List(data.readInt()) { read(data, elementType) }
        }
        COMPOUND -> buildMap {
            while (true) {
                val tagType = data.readByte().toInt()
                if (tagType == 0) break
                put(data.readUTF(), read(data, tagType))
            }
        }
        11 -> IntArray(data.readInt()) { data.readInt() }
        12 -> LongArray(data.readInt()) { data.readLong() }
        else -> error("Unknown NBT tag type $type")
    }

    private const val COMPOUND = 10
}

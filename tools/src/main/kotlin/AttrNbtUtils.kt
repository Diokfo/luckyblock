package mod.lucky.tools

import mod.lucky.common.attribute.*
import br.com.gamemods.nbtmanipulator.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.GZIPInputStream

class DynamicByteBuffer(
    private val byteOrder: ByteOrder,
    var byteBuffer: ByteBuffer = ByteBuffer.allocate(8).order(byteOrder),
) {
    private fun ensureSpace() {
        if (byteBuffer.remaining() < 8) {
            val newByteBuffer = ByteBuffer.allocate(byteBuffer.capacity() * 2).order(byteOrder)
            byteBuffer.flip()
            newByteBuffer.put(byteBuffer)
            byteBuffer = newByteBuffer
        }
    }

    fun putByte(v: Byte) { ensureSpace(); byteBuffer.put(v) }
    fun putShort(v: Short) { ensureSpace(); byteBuffer.putShort(v) }
    fun putInt(v: Int) { ensureSpace(); byteBuffer.putInt(v) }
    fun putFloat(v: Float) { ensureSpace(); byteBuffer.putFloat(v) }
    fun putDouble(v: Double) { ensureSpace(); byteBuffer.putDouble(v) }
    fun putLong(v: Long) { ensureSpace(); byteBuffer.putLong(v) }
}

private fun putString(byteBuffer: DynamicByteBuffer, value: String) {
    val byteArray = value.encodeToByteArray()
    byteBuffer.putShort(byteArray.size.toShort())
    byteArray.forEach { byteBuffer.putByte(it) }
}

private fun readString(byteBuffer: ByteBuffer): String {
    val size = byteBuffer.get().toInt()
    val byteArray = ByteArray(size)
    byteBuffer.get(byteArray, 0, size)
    return byteArray.decodeToString()
}

val NBT_TYPE_TO_ID = mapOf(
    AttrType.BYTE to 1,
    AttrType.BOOLEAN to 1,
    AttrType.SHORT to 2,
    AttrType.INT to 3,
    AttrType.LONG to 4,
    AttrType.FLOAT to 5,
    AttrType.DOUBLE to 6,
    AttrType.BYTE_ARRAY to 7,
    AttrType.STRING to 8,
    AttrType.LIST to 9,
    AttrType.DICT to 10,
    AttrType.INT_ARRAY to 11,
    AttrType.LONG_ARRAY to 12,
)
val NBT_ID_TO_TYPE = NBT_TYPE_TO_ID.entries.associate { (k, v) -> v to k }

fun writeAttrToNbt(buffer: DynamicByteBuffer, attr: Attr) {
    when(attr) {
        is ValueAttr -> {
            when(attr.type) {
                AttrType.BYTE -> buffer.putByte(attr.value as Byte)
                AttrType.BOOLEAN -> buffer.putByte(if (attr.value as Boolean) 1 else 0)
                AttrType.SHORT -> buffer.putShort(attr.value as Short)
                AttrType.INT -> buffer.putInt(attr.value as Int)
                AttrType.FLOAT -> buffer.putFloat(attr.value as Float)
                AttrType.DOUBLE -> buffer.putDouble(attr.value as Double)
                AttrType.LONG -> buffer.putLong(attr.value as Long)
                AttrType.STRING -> putString(buffer, attr.value as String)
                AttrType.BYTE_ARRAY -> {
                    val array = attr.value as ByteArray
                    buffer.putInt(array.size)
                    array.forEach { buffer.putByte(it) }
                }
                AttrType.INT_ARRAY -> {
                    val array = attr.value as IntArray
                    buffer.putInt(array.size)
                    array.forEach { buffer.putInt(it) }
                }
                AttrType.LONG_ARRAY -> {
                    val array = attr.value as LongArray
                    buffer.putInt(array.size)
                    array.forEach { buffer.putLong(it) }
                }
                AttrType.LIST -> throw Exception()
                AttrType.DICT -> throw Exception()
            }
        }
        is ListAttr -> {
            buffer.putByte(
                if (attr.children.size == 0) 0
                else NBT_TYPE_TO_ID[attr.children.first().type]!!.toByte()
            )
            buffer.putInt(attr.children.size)
            attr.children.forEach {
                writeAttrToNbt(buffer, it)
            }
        }
        is DictAttr -> {
            attr.children.forEach { (k, v) ->
                buffer.putByte(NBT_TYPE_TO_ID[v.type]!!.toByte())
                putString(buffer, k)
                writeAttrToNbt(buffer, v)
            }
            buffer.putByte(0)
        }
    }
}

fun attrToNbt(attr: Attr, byteOrder: ByteOrder): ByteBuffer {
    val byteBuffer = DynamicByteBuffer(byteOrder)
    writeAttrToNbt(byteBuffer, attr)
    return byteBuffer.byteBuffer
}

fun writeBufferToFile(buffer: ByteBuffer, file: File) {
    file.parentFile.mkdirs()
    val channel = FileOutputStream(file).channel
    buffer.flip()
    channel.write(buffer)
    channel.close()
}

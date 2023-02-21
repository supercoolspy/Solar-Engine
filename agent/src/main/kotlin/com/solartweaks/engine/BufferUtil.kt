package com.solartweaks.engine

import java.io.DataInput
import java.io.DataOutput
import java.util.*

fun DataInput.readVarInt(): Int {
    var value = 0
    var position = 0

    while (true) {
        val currentByte = readByte().toInt()
        value = value or (currentByte and 0x7F shl position)
        if (currentByte and 0x80 == 0) break
        position += 7
        if (position >= 32) error("VarInt is too big")
    }

    return value
}

fun DataInput.readVarLong(): Long {
    var value = 0L
    var position = 0

    while (true) {
        val currentByte = readByte()
        value = value or (currentByte.toLong() and 0x7F shl position)
        if (currentByte.toInt() and 0x80 == 0) break
        position += 7
        if (position >= 64) error("VarLong is too big")
    }

    return value
}

fun DataOutput.writeVarInt(value: Int) {
    var mut = value
    while (true) {
        if (mut and 0x7F.inv() == 0) {
            writeByte(mut)
            return
        }

        writeByte(mut and 0x7F or 0x80)
        mut = mut ushr 7
    }
}

fun DataOutput.writeVarLong(value: Long) {
    var mut = value
    while (true) {
        if (mut and 0x7FL.inv() == 0L) {
            writeByte(mut.toInt())
            return
        }

        writeByte((mut and 0x7FL or 0x80L).toInt())
        mut = mut ushr 7
    }
}

fun DataInput.readUUID() = UUID(readLong(), readLong())
fun DataOutput.writeUUID(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

const val maxStringSize = 32767

fun DataOutput.writeMCString(str: String, max: Int = maxStringSize) {
    require(str.length <= max) { "string too long" }

    writeVarInt(str.length)
    write(str.encodeToByteArray(), 0, str.length)
}

fun DataInput.readMCString(max: Int = maxStringSize): String {
    val len = readVarInt()
    require(len <= max) { "string too long" }

    val buf = ByteArray(len)
    readFully(buf)
    return buf.decodeToString()
}
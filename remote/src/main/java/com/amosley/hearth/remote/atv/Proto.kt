package com.amosley.hearth.remote.atv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Just enough protobuf to speak the Android TV Remote v2 protocol without a codegen step. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()
    private fun varint(v: Long) {
        var x = v
        while (true) {
            if (x and 0x7FL.inv() == 0L) { out.write(x.toInt()); return }
            out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7
        }
    }
    fun int(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
    fun bool(field: Int, v: Boolean) = int(field, if (v) 1 else 0)
    fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
    fun string(field: Int, s: String) = bytes(field, s.toByteArray())
    fun msg(field: Int, w: ProtoWriter) = bytes(field, w.toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Decoded message: field number → values (Long for varints, ByteArray for length-delimited). */
class ProtoMsg(val fields: Map<Int, List<Any>>) {
    fun has(f: Int) = fields.containsKey(f)
    fun int(f: Int): Long? = fields[f]?.firstOrNull() as? Long
    fun bytes(f: Int): ByteArray? = fields[f]?.firstOrNull() as? ByteArray
    fun msg(f: Int): ProtoMsg? = bytes(f)?.let { parse(it) }

    companion object {
        fun parse(b: ByteArray): ProtoMsg {
            val m = HashMap<Int, MutableList<Any>>()
            var i = 0
            fun varint(): Long {
                var shift = 0; var r = 0L
                while (true) {
                    val x = b[i++].toInt() and 0xFF
                    r = r or ((x and 0x7F).toLong() shl shift)
                    if (x and 0x80 == 0) return r
                    shift += 7
                }
            }
            while (i < b.size) {
                val tag = varint().toInt()
                val field = tag ushr 3
                val v: Any = when (tag and 7) {
                    0 -> varint()
                    2 -> { val len = varint().toInt(); b.copyOfRange(i, i + len).also { i += len } }
                    1 -> { i += 8; 0L }
                    5 -> { i += 4; 0L }
                    else -> return ProtoMsg(m)
                }
                m.getOrPut(field) { ArrayList() }.add(v)
            }
            return ProtoMsg(m)
        }
    }
}

/** Messages on the wire are prefixed with their length as a varint. */
fun OutputStream.writeDelimited(b: ByteArray) {
    var x = b.size
    while (x and 0x7F.inv() != 0) { write((x and 0x7F) or 0x80); x = x ushr 7 }
    write(x); write(b); flush()
}

fun InputStream.readDelimited(): ByteArray {
    var shift = 0; var len = 0
    while (true) {
        val x = read(); if (x < 0) throw java.io.EOFException()
        len = len or ((x and 0x7F) shl shift)
        if (x and 0x80 == 0) break
        shift += 7
    }
    val buf = ByteArray(len)
    var off = 0
    while (off < len) { val n = read(buf, off, len - off); if (n < 0) throw java.io.EOFException(); off += n }
    return buf
}

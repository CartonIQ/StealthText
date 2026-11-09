package com.cartoniq.stealthtext

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

object Payload {
    const val TEXT = 0
    const val FILE = 1
    const val AUDIO = 2
    const val MARK = 3

    class Item(val type: Int, val name: String, val data: ByteArray, val burn: Boolean = false, val id: String = "")

    fun pack(type: Int, name: String, data: ByteArray, burn: Boolean = false): ByteArray {
        var nb = name.toByteArray(Charsets.UTF_8)
        if (nb.size > 200) nb = nb.copyOf(200)
        val raw = ByteArray(2 + nb.size + data.size)
        raw[0] = (type or (if (burn) 0x80 else 0)).toByte()
        raw[1] = nb.size.toByte()
        System.arraycopy(nb, 0, raw, 2, nb.size)
        System.arraycopy(data, 0, raw, 2 + nb.size, data.size)
        val d = Deflater(9)
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) {
            val n = d.deflate(buf)
            out.write(buf, 0, n)
        }
        d.end()
        return out.toByteArray()
    }

    fun unpack(packed: ByteArray): Item {
        val inf = Inflater()
        val out = ByteArrayOutputStream()
        try {
            inf.setInput(packed)
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > 100_000_000) throw StegoException("Hidden data is corrupt.")
            }
        } catch (e: DataFormatException) {
            throw StegoException("Hidden data is corrupt.")
        } finally {
            inf.end()
        }
        val raw = out.toByteArray()
        if (raw.size < 2) throw StegoException("Hidden data is corrupt.")
        val nameLen = raw[1].toInt() and 0xFF
        if (2 + nameLen > raw.size) throw StegoException("Hidden data is corrupt.")
        val name = String(raw, 2, nameLen, Charsets.UTF_8)
        val head = raw[0].toInt() and 0xFF
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        val id = digest.take(8).joinToString("") { "%02x".format(it) }
        return Item(head and 0x7F, name, raw.copyOfRange(2 + nameLen, raw.size), (head and 0x80) != 0, id)
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        if (a.size != b.size) throw StegoException("The two images do not belong together.")
        val r = ByteArray(a.size)
        for (i in a.indices) r[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return r
    }
}

package com.cartoniq.stealthtext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class StegoException(message: String) : Exception(message)

/**
 * Image layout (R,G,B bytes are "slots"):
 *  single: header A at slots 0..167, one body region after it.
 *  decoy : header A at 0..167, header B at 168..335, region A and region B split the rest.
 * Header = salt(16) + masked flags(1) + masked length(4), 1 bit per slot (looks random).
 * Body = iv(12) + AES-GCM ciphertext, k bits per slot, visited in a password-derived scattered order.
 */
object Stego {
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_LEN = 16
    private const val HEADER_BYTES = 21
    private const val HEADER_BITS = HEADER_BYTES * 8
    private const val ITERATIONS = 200_000
    private const val NOT_FOUND = "No hidden message found in this image."
    private const val BAD_KEY = "Wrong password, or the image was changed (compressed or edited)."
    const val MIN_PASSWORD = 6
    const val MAX_PIXELS = 25_000_000L

    private class Region(val start: Int, val size: Int, val elig: IntArray?) {
        fun slot(i: Int): Int = if (elig == null) start + i else elig[start + i]
    }

    private class Hdr(val salt: ByteArray, val flags: Int, val len: Int)

    const val SMART_MAX_PIXELS = 8_000_000L

    fun capacity(w: Int, h: Int, k: Int, dual: Boolean): Int {
        val total = minOf(w.toLong() * h * 3L, Int.MAX_VALUE.toLong()).toInt()
        if (total < 2 * HEADER_BITS + 512) return 0
        val base = if (dual) 2 * HEADER_BITS else HEADER_BITS
        val r = total - base
        val size = if (dual) r / 2 else r
        val bytes = size.toLong() * k / 8L - (IV_LEN + TAG_LEN)
        return if (bytes <= 0L) 0 else minOf(bytes, 500_000_000L).toInt()
    }

    private fun regions(px: IntArray, w: Int, dual: Boolean, smart: Boolean): Pair<Region, Region> {
        if (!smart) {
            val base = if (dual) 2 * HEADER_BITS else HEADER_BITS
            val r = px.size * 3 - base
            val a = if (dual) r / 2 else r
            return Pair(Region(base, a, null), Region(base + a, r - a, null))
        }
        val elig = eligible(px, w)
        val r = elig.size
        if (r < 1024) throw StegoException(tr("This photo has too few busy areas for smart hiding."))
        val a = if (dual) r / 2 else r
        return Pair(Region(0, a, elig), Region(a, r - a, elig))
    }

    private fun gray5(c: Int): Int =
        (((c shr 16) and 0xFF) shr 3) * 3 + (((c shr 8) and 0xFF) shr 3) * 6 + ((c and 0xFF) shr 3)

    private fun busy(px: IntArray, p: Int, w: Int, h: Int): Boolean {
        val x = p % w
        val y = p / w
        if (x >= w - 1 || y >= h - 1) return false
        val g = gray5(px[p])
        return Math.abs(g - gray5(px[p + 1])) + Math.abs(g - gray5(px[p + w])) >= 40
    }

    /** Slots of textured pixels. Uses only the top 5 bits of each colour, which hiding never changes. */
    private fun eligible(px: IntArray, w: Int): IntArray {
        val h = px.size / w
        val first = (2 * HEADER_BITS) / 3
        var count = 0
        for (p in first until px.size) if (busy(px, p, w, h)) count++
        val out = IntArray(count * 3)
        var n = 0
        for (p in first until px.size) {
            if (busy(px, p, w, h)) {
                out[n++] = p * 3
                out[n++] = p * 3 + 1
                out[n++] = p * 3 + 2
            }
        }
        return out
    }

    fun encode(src: Bitmap, real: ByteArray, realPw: String, decoy: ByteArray?, decoyPw: String?, k: Int, smart: Boolean = false): Bitmap {
        if (k !in 1..3) throw StegoException(tr("Invalid bit depth."))
        if (realPw.length < MIN_PASSWORD) throw StegoException(tr("Use a password of at least 6 characters."))
        if (real.isEmpty()) throw StegoException(tr("Nothing to hide."))
        val dual = decoy != null
        if (decoy != null) {
            if (decoyPw == null || decoyPw.length < MIN_PASSWORD) throw StegoException(tr("Use a decoy password of at least 6 characters."))
            if (decoyPw == realPw) throw StegoException(tr("The decoy password must be different."))
        }
        val w = src.width
        val h = src.height
        if (w.toLong() * h > MAX_PIXELS) throw StegoException(tr("Photo is too large (max about 25 megapixels)."))
        if (smart && w.toLong() * h > SMART_MAX_PIXELS) throw StegoException(tr("Smart hiding works with photos up to 8 megapixels."))
        val px = flatten(src)
        if (px.size * 3 < 2 * HEADER_BITS + 512) throw StegoException(tr("Photo is too small."))
        val reg = regions(px, w, dual, smart)
        val capA = reg.first.size.toLong() * k / 8L - (IV_LEN + TAG_LEN)
        val capB = reg.second.size.toLong() * k / 8L - (IV_LEN + TAG_LEN)
        if (real.size > capA || (decoy != null && decoy.size > capB)) {
            throw StegoException(tr("Too big for this photo. Use a bigger photo or more bits."))
        }
        val flags = k or (if (dual) 4 else 0) or (if (smart) 8 else 0)
        writeRecord(px, real, realPw, flags, 0, reg.first)
        if (decoy != null && decoyPw != null) {
            writeRecord(px, decoy, decoyPw, flags, HEADER_BITS, reg.second)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun decode(src: Bitmap, password: String): ByteArray {
        if (password.isEmpty()) throw StegoException(tr("Enter the password."))
        if (src.width.toLong() * src.height > MAX_PIXELS) throw StegoException(tr("Photo is too large (max about 25 megapixels)."))
        val px = flatten(src)
        val total = px.size * 3
        if (total < 2 * HEADER_BITS + 512) throw StegoException(tr(NOT_FOUND))
        val a = readHeader(px, 0)
        val k = a.flags and 3
        if (a.flags !in 1..15 || k !in 1..3) throw StegoException(tr(NOT_FOUND))
        val dual = (a.flags shr 2) and 1 == 1
        val smart = (a.flags shr 3) and 1 == 1
        if (smart && px.size.toLong() > SMART_MAX_PIXELS) throw StegoException(tr(NOT_FOUND))
        val reg = try {
            regions(px, src.width, dual, smart)
        } catch (e: StegoException) {
            throw StegoException(tr(NOT_FOUND))
        }
        if (!validLen(a.len, reg.first.size, k)) throw StegoException(tr(NOT_FOUND))
        val ra = tryRecord(px, password, a, k, reg.first)
        if (ra != null) return ra
        if (dual) {
            val b = readHeader(px, HEADER_BITS)
            if ((b.flags and 3) == k && validLen(b.len, reg.second.size, k)) {
                val rb = tryRecord(px, password, b, k, reg.second)
                if (rb != null) return rb
            }
        }
        throw StegoException(tr(BAD_KEY))
    }

    /** True if the first bytes of the image look like a StealthText header (no password needed). */
    fun probe(src: Bitmap): Boolean {
        val w = src.width
        val h = src.height
        val total = w.toLong() * h * 3L
        if (total < 2 * HEADER_BITS + 512 || w.toLong() * h > MAX_PIXELS) return false
        val n = (HEADER_BITS + 2) / 3
        val px = IntArray(n)
        for (i in 0 until n) px[i] = src.getPixel(i % w, i / w) or (0xFF shl 24)
        val hd = readHeader(px, 0)
        val k = hd.flags and 3
        if (hd.flags !in 1..15 || k !in 1..3) return false
        return hd.len >= IV_LEN + TAG_LEN + 1 && hd.len.toLong() <= total * k / 8L
    }

    private fun validLen(len: Int, size: Int, k: Int): Boolean =
        len >= IV_LEN + TAG_LEN + 1 && len.toLong() <= size.toLong() * k / 8L

    private fun writeRecord(px: IntArray, plain: ByteArray, password: String, flags: Int, headerAt: Int, region: Region) {
        val k = flags and 3
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val keyMaterial = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyMaterial, 0, 32, "AES"), GCMParameterSpec(TAG_LEN * 8, iv))
            val body = iv + cipher.doFinal(plain)
            val seed = ByteBuffer.wrap(keyMaterial, 32, 8).long and Long.MAX_VALUE
            val walk = pickWalk(region.size.toLong(), seed)

            val mask = sha256(salt)
            val header = ByteArray(HEADER_BYTES)
            System.arraycopy(salt, 0, header, 0, SALT_LEN)
            header[SALT_LEN] = (flags xor (mask[0].toInt() and 0xFF)).toByte()
            for (i in 0 until 4) {
                val lenByte = (body.size ushr (24 - 8 * i)) and 0xFF
                header[SALT_LEN + 1 + i] = (lenByte xor (mask[1 + i].toInt() and 0xFF)).toByte()
            }
            for (i in 0 until HEADER_BITS) {
                val bit = (header[i shr 3].toInt() shr (7 - (i and 7))) and 1
                putSlot(px, headerAt + i, (getSlot(px, headerAt + i) and 0xFE) or bit)
            }

            val slotMask = (1 shl k) - 1
            val totalBits = body.size * 8
            var bitPos = 0
            var j = 0L
            while (bitPos < totalBits) {
                var v = 0
                for (t in 0 until k) {
                    v = v shl 1
                    if (bitPos < totalBits) {
                        v = v or ((body[bitPos shr 3].toInt() shr (7 - (bitPos and 7))) and 1)
                    }
                    bitPos++
                }
                val s = region.slot(((walk.first + j * walk.second) % region.size).toInt())
                putSlot(px, s, (getSlot(px, s) and slotMask.inv()) or v)
                j++
            }
        } finally {
            keyMaterial.fill(0)
        }
    }

    private fun readHeader(px: IntArray, headerAt: Int): Hdr {
        val header = ByteArray(HEADER_BYTES)
        for (i in 0 until HEADER_BITS) {
            if ((getSlot(px, headerAt + i) and 1) == 1) {
                header[i shr 3] = (header[i shr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            }
        }
        val salt = header.copyOfRange(0, SALT_LEN)
        val mask = sha256(salt)
        val flags = (header[SALT_LEN].toInt() and 0xFF) xor (mask[0].toInt() and 0xFF)
        var len = 0
        for (i in 0 until 4) {
            len = (len shl 8) or ((header[SALT_LEN + 1 + i].toInt() and 0xFF) xor (mask[1 + i].toInt() and 0xFF))
        }
        return Hdr(salt, flags, len)
    }

    private fun tryRecord(px: IntArray, password: String, hdr: Hdr, k: Int, region: Region): ByteArray? {
        val keyMaterial = derive(password, hdr.salt)
        try {
            val seed = ByteBuffer.wrap(keyMaterial, 32, 8).long and Long.MAX_VALUE
            val walk = pickWalk(region.size.toLong(), seed)
            val slotMask = (1 shl k) - 1
            val len = hdr.len
            val body = ByteArray(len)
            val totalBits = len * 8
            var bitPos = 0
            var j = 0L
            while (bitPos < totalBits) {
                val s = region.slot(((walk.first + j * walk.second) % region.size).toInt())
                val v = getSlot(px, s) and slotMask
                for (t in k - 1 downTo 0) {
                    if (bitPos < totalBits && ((v shr t) and 1) == 1) {
                        body[bitPos shr 3] = (body[bitPos shr 3].toInt() or (1 shl (7 - (bitPos and 7)))).toByte()
                    }
                    bitPos++
                }
                j++
            }
            val iv = body.copyOfRange(0, IV_LEN)
            val ct = body.copyOfRange(IV_LEN, len)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyMaterial, 0, 32, "AES"), GCMParameterSpec(TAG_LEN * 8, iv))
            return try {
                cipher.doFinal(ct)
            } catch (e: GeneralSecurityException) {
                null
            }
        } finally {
            keyMaterial.fill(0)
        }
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 320)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun pickWalk(m: Long, seed: Long): Pair<Long, Long> {
        val start = seed % m
        var step = m / 2 + (seed / 7) % (m / 4 + 1)
        if (step < 1L) step = 1L
        while (gcd(step, m) != 1L) {
            step++
            if (step >= m) step = 1L
        }
        return Pair(start, step)
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    private fun flatten(src: Bitmap): IntArray {
        val w = src.width
        val h = src.height
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        val px = IntArray(w * h)
        flat.getPixels(px, 0, w, 0, 0, w, h)
        flat.recycle()
        for (i in px.indices) {
            px[i] = px[i] or (0xFF shl 24)
        }
        return px
    }

    private fun getSlot(px: IntArray, s: Int): Int {
        val shift = 16 - 8 * (s % 3)
        return (px[s / 3] shr shift) and 0xFF
    }

    private fun putSlot(px: IntArray, s: Int, value: Int) {
        val shift = 16 - 8 * (s % 3)
        val p = s / 3
        px[p] = (px[p] and (0xFF shl shift).inv()) or ((value and 0xFF) shl shift)
    }
}

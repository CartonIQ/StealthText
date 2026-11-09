package com.cartoniq.stealthtext

import android.graphics.Bitmap
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val ITER = 200_000

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = derive(password, salt)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return salt + iv + c.doFinal(plain)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(blob: ByteArray, password: String): ByteArray? {
        if (blob.size < 16 + 12 + 16 + 1) return null
        val salt = blob.copyOfRange(0, 16)
        val iv = blob.copyOfRange(16, 28)
        val ct = blob.copyOfRange(28, blob.size)
        val key = derive(password, salt)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return try {
                c.doFinal(ct)
            } catch (e: GeneralSecurityException) {
                null
            }
        } finally {
            key.fill(0)
        }
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Password plus an optional key file: the file's SHA-256 is mixed into the password. */
    fun mixKey(pw: String, key: ByteArray?): String {
        if (key == null) return pw
        val h = MessageDigest.getInstance("SHA-256").digest(key)
        return pw + ":" + h.joinToString("") { "%02x".format(it) }
    }
}

/** Hides an encrypted message inside normal text using invisible characters. */
object ZeroWidth {
    private val chars = charArrayOf('\u200B', '\u200C', '\u200D', '\u2060')

    fun hide(cover: String, secret: String, password: String): String {
        if (cover.isEmpty()) throw StegoException(tr("Write some normal text first."))
        if (password.length < Stego.MIN_PASSWORD) throw StegoException(tr("Use a password of at least 6 characters."))
        val blob = Crypto.encrypt(Payload.pack(Payload.TEXT, "", secret.toByteArray(Charsets.UTF_8)), password)
        val sb = StringBuilder()
        for (b in blob) {
            val v = b.toInt() and 0xFF
            for (shift in intArrayOf(6, 4, 2, 0)) sb.append(chars[(v shr shift) and 3])
        }
        val cut = cover.offsetByCodePoints(0, 1)
        return cover.substring(0, cut) + sb.toString() + cover.substring(cut)
    }

    fun reveal(text: String, password: String): String {
        val vals = ArrayList<Int>()
        for (c in text) {
            val i = chars.indexOf(c)
            if (i >= 0) vals.add(i)
        }
        if (vals.size < 4 * 45) throw StegoException(tr("No hidden text found."))
        val n = vals.size / 4
        val blob = ByteArray(n)
        for (b in 0 until n) {
            var v = 0
            for (j in 0 until 4) v = (v shl 2) or vals[b * 4 + j]
            blob[b] = v.toByte()
        }
        val plain = Crypto.decrypt(blob, password) ?: throw StegoException(tr("Wrong password, or the text was changed."))
        return String(Payload.unpack(plain).data, Charsets.UTF_8)
    }
}

/** Shamir secret sharing over GF(256). Each share is: threshold byte, x byte, then the y bytes. */
object Shamir {
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            var x2 = x shl 1
            if ((x2 and 0x100) != 0) x2 = x2 xor 0x11B
            x = x2 xor x
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]
    private fun div(a: Int, b: Int): Int = if (a == 0) 0 else exp[log[a] + 255 - log[b]]

    fun split(secret: ByteArray, k: Int, n: Int): List<ByteArray> {
        if (k < 2 || k > n || n > 5) throw StegoException(tr("Invalid split settings."))
        val rnd = SecureRandom()
        val shares = ArrayList<ByteArray>()
        for (i in 0 until n) {
            val s = ByteArray(2 + secret.size)
            s[0] = k.toByte()
            s[1] = (i + 1).toByte()
            shares.add(s)
        }
        val coef = IntArray(k)
        for (pos in secret.indices) {
            coef[0] = secret[pos].toInt() and 0xFF
            for (c in 1 until k) coef[c] = rnd.nextInt(256)
            for (i in 0 until n) {
                val x = i + 1
                var y = 0
                for (c in k - 1 downTo 0) y = mul(y, x) xor coef[c]
                shares[i][2 + pos] = y.toByte()
            }
        }
        return shares
    }

    fun combine(shares: List<ByteArray>): ByteArray {
        if (shares.isEmpty()) throw StegoException(tr("No images chosen."))
        val k = shares[0][0].toInt() and 0xFF
        val len = shares[0].size
        for (s in shares) {
            if (s.size != len || (s[0].toInt() and 0xFF) != k || s.size < 3) {
                throw StegoException(tr("These images do not belong together."))
            }
        }
        if (shares.size < k) throw StegoException(tr("Not enough images to rebuild the secret."))
        val use = shares.take(k)
        val xs = IntArray(k) { use[it][1].toInt() and 0xFF }
        if (xs.toSet().size != k || xs.any { it == 0 }) throw StegoException(tr("These images do not belong together."))
        val basis = IntArray(k)
        for (i in 0 until k) {
            var num = 1
            var den = 1
            for (j in 0 until k) {
                if (j != i) {
                    num = mul(num, xs[j])
                    den = mul(den, xs[i] xor xs[j])
                }
            }
            basis[i] = div(num, den)
        }
        val out = ByteArray(len - 2)
        for (pos in out.indices) {
            var v = 0
            for (i in 0 until k) v = v xor mul(use[i][2 + pos].toInt() and 0xFF, basis[i])
            out[pos] = v.toByte()
        }
        return out
    }
}

class HideOut(val uri: android.net.Uri, val level: Int, val pct: Double)

object Analyzer {
    class Scan(val stealth: Boolean, val p: Double)

    fun scan(bmp: Bitmap): Scan {
        val w = bmp.width
        val h = bmp.height
        val hist = LongArray(256)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (c in row) {
                hist[(c shr 16) and 0xFF]++
                hist[(c shr 8) and 0xFF]++
                hist[c and 0xFF]++
            }
        }
        var chi = 0.0
        var pairs = 0
        for (i in 0 until 128) {
            val a = hist[2 * i].toDouble()
            val b = hist[2 * i + 1].toDouble()
            val ex = (a + b) / 2.0
            if (ex >= 5.0) {
                chi += (a - ex) * (a - ex) / ex
                pairs++
            }
        }
        val p = if (pairs < 12) 0.0 else upperTail(chi, pairs - 1)
        return Scan(Stego.probe(bmp), p)
    }

    /** Level 0 low, 1 medium, 2 high, plus the share of colour data used, in percent. Estimate only. */
    fun detectability(payloadBytes: Int, w: Int, h: Int, k: Int, smart: Boolean): Pair<Int, Double> {
        val slots = w.toDouble() * h * 3.0
        val rate = (payloadBytes + 28) * 8.0 / slots
        var score = rate * (1.0 + 0.25 * (k - 1))
        if (smart) score *= 0.7
        val level = if (score < 0.05) 0 else if (score < 0.2) 1 else 2
        return Pair(level, rate * 100.0)
    }

    private fun upperTail(x: Double, k: Int): Double {
        val kk = k.toDouble()
        val z = (Math.cbrt(x / kk) - (1.0 - 2.0 / (9.0 * kk))) / Math.sqrt(2.0 / (9.0 * kk))
        return 1.0 - normCdf(z)
    }

    private fun normCdf(z: Double): Double = 0.5 * (1.0 + erf(z / Math.sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = Math.abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-ax * ax)
        return sign * y
    }
}

/** Makes a colourful film-grain style cover photo, busy enough for Smart hiding. */
object CoverGen {
    private fun hash(x: Int, y: Int, s: Int): Float {
        var n = x * 374761393 + y * 668265263 + s * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    private fun noise(x: Float, y: Float, s: Int): Float {
        val xi = Math.floor(x.toDouble()).toInt()
        val yi = Math.floor(y.toDouble()).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)
        val a = hash(xi, yi, s)
        val b = hash(xi + 1, yi, s)
        val c = hash(xi, yi + 1, s)
        val d = hash(xi + 1, yi + 1, s)
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return top + (bottom - top) * v
    }

    fun make(size: Int, seed: Int): Bitmap {
        val px = IntArray(size * size)
        val rnd = java.util.Random(seed.toLong())
        for (y in 0 until size) {
            for (x in 0 until size) {
                var r = 0f
                var g = 0f
                var b = 0f
                var amp = 0.5f
                var total = 0f
                var freq = 4f / size
                for (o in 0 until 5) {
                    r += amp * noise(x * freq, y * freq, seed + o * 3)
                    g += amp * noise(x * freq, y * freq, seed + o * 3 + 1)
                    b += amp * noise(x * freq, y * freq, seed + o * 3 + 2)
                    total += amp
                    amp *= 0.5f
                    freq *= 2f
                }
                val grain = rnd.nextInt(97) - 48
                val rr = (((r / total - 0.5f) * 1.6f + 0.5f) * 255f + grain + rnd.nextInt(17) - 8).toInt().coerceIn(0, 255)
                val gg = (((g / total - 0.5f) * 1.6f + 0.5f) * 255f + grain + rnd.nextInt(17) - 8).toInt().coerceIn(0, 255)
                val bb = (((b / total - 0.5f) * 1.6f + 0.5f) * 255f + grain + rnd.nextInt(17) - 8).toInt().coerceIn(0, 255)
                px[y * size + x] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}

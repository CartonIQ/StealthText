package com.cartoniq.stealthtext

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Prefs {
    private fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)

    fun lang(ctx: Context): String = Lang.initial(sp(ctx).getString("lang", null))
    fun putLang(ctx: Context, v: String) { sp(ctx).edit().putString("lang", v).apply() }

    fun onboarded(ctx: Context): Boolean = sp(ctx).getBoolean("onboarded", false)
    fun putOnboarded(ctx: Context) { sp(ctx).edit().putBoolean("onboarded", true).apply() }

    fun hasPin(ctx: Context): Boolean = sp(ctx).getString("pin_hash", null) != null

    fun savePin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(ctx).edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hashPin(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(ctx: Context, pin: String): Boolean {
        val s = sp(ctx)
        val saltS = s.getString("pin_salt", null) ?: return false
        val hashS = s.getString("pin_hash", null) ?: return false
        if (pin.isEmpty()) return false
        val got = hashPin(pin, Base64.decode(saltS, Base64.NO_WRAP))
        return MessageDigest.isEqual(got, Base64.decode(hashS, Base64.NO_WRAP))
    }

    fun clearPin(ctx: Context) { sp(ctx).edit().remove("pin_salt").remove("pin_hash").apply() }

    fun shake(ctx: Context): Boolean = sp(ctx).getBoolean("shake", true)
    fun putShake(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean("shake", v).apply() }

    fun limit(ctx: Context): Int = sp(ctx).getInt("limit", 5)
    fun putLimit(ctx: Context, v: Int) { sp(ctx).edit().putInt("limit", v).apply() }

    fun disguise(ctx: Context): Boolean = sp(ctx).getBoolean("disguise", false)
    fun putDisguise(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean("disguise", v).apply() }

    fun fails(ctx: Context): Int = sp(ctx).getInt("fails", 0)
    fun putFails(ctx: Context, v: Int) { sp(ctx).edit().putInt("fails", v).apply() }

    fun duressSet(ctx: Context): Boolean = sp(ctx).getString("duress_hash", null) != null

    fun saveDuress(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(ctx).edit()
            .putString("duress_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("duress_hash", Base64.encodeToString(hashPin(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verifyDuress(ctx: Context, pin: String): Boolean {
        val s = sp(ctx)
        val saltS = s.getString("duress_salt", null) ?: return false
        val hashS = s.getString("duress_hash", null) ?: return false
        if (pin.isEmpty()) return false
        val got = hashPin(pin, Base64.decode(saltS, Base64.NO_WRAP))
        return MessageDigest.isEqual(got, Base64.decode(hashS, Base64.NO_WRAP))
    }

    fun clearDuress(ctx: Context) { sp(ctx).edit().remove("duress_salt").remove("duress_hash").apply() }

    fun isBurned(ctx: Context, id: String): Boolean = sp(ctx).getStringSet("burned", emptySet())?.contains(id) == true

    fun markBurned(ctx: Context, id: String) {
        val set = HashSet<String>(sp(ctx).getStringSet("burned", emptySet()) ?: emptySet())
        set.add(id)
        sp(ctx).edit().putStringSet("burned", set).apply()
    }

    /** Silent wipe used by the duress PIN: removes vault images and temporary files, keeps the PIN and settings. */
    fun wipeVaultOnly(ctx: Context) {
        ImageIO.vaultDir(ctx).listFiles()?.forEach { it.delete() }
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Wipes everything the app owns: vault images, cache, PIN and settings. Gallery PNGs are not touched. */
    fun wipeAll(ctx: Context) {
        ImageIO.vaultDir(ctx).listFiles()?.forEach { it.delete() }
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        sp(ctx).edit().clear().commit()
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

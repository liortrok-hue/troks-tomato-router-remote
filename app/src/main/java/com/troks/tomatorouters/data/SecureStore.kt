package com.troks.tomatorouters.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedLogin(
    val host: String = "192.168.1.1",
    val port: Int = 80,
    val https: Boolean = false,
    val username: String = "",
    val password: String = "",
    val remember: Boolean = false,
)

/**
 * Stores the last login. The password is encrypted with an AES key kept in the
 * Android Keystore, so it never sits in plain text on the device.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("router_login", Context.MODE_PRIVATE)

    fun load(): SavedLogin {
        if (!prefs.contains("host")) return SavedLogin()
        val remember = prefs.getBoolean("remember", false)
        return SavedLogin(
            host = prefs.getString("host", null) ?: "192.168.1.1",
            port = prefs.getInt("port", 80),
            https = prefs.getBoolean("https", false),
            username = prefs.getString("user", null).orEmpty(),
            password = if (remember) prefs.getString("pass", null)?.let { decrypt(it) }.orEmpty() else "",
            remember = remember,
        )
    }

    fun save(login: SavedLogin) {
        prefs.edit()
            .putString("host", login.host)
            .putInt("port", login.port)
            .putBoolean("https", login.https)
            .putString("user", login.username)
            .putBoolean("remember", login.remember)
            .apply {
                if (login.remember) putString("pass", encrypt(login.password)) else remove("pass")
            }
            .apply()
    }

    fun forgetPassword() {
        prefs.edit().remove("pass").putBoolean("remember", false).apply()
    }

    var languageCode: String?
        get() = prefs.getString("lang", null)
        set(v) = prefs.edit().putString("lang", v).apply()

    var themeMode: String?
        get() = prefs.getString("theme", null)
        set(v) = prefs.edit().putString("theme", v).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", false)
        set(v) = prefs.edit().putBoolean("dynamic_color", v).apply()

    /** Extra text scale chosen in the app (multiplies the system font size). */
    var textScale: Float
        get() = prefs.getFloat("text_scale", 1f)
        set(v) = prefs.edit().putFloat("text_scale", v).apply()

    fun certPin(host: String, port: Int): String? = prefs.getString("pin_${host}_$port", null)

    fun setCertPin(host: String, port: Int, fp: String?) {
        prefs.edit().apply { if (fp == null) remove("pin_${host}_$port") else putString("pin_${host}_$port", fp) }.apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(c.iv) + ":" + b64(ct)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (iv, ct) = stored.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private companion object {
        const val ALIAS = "tomato_router_login"
    }
}

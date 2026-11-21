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
import org.json.JSONArray
import org.json.JSONObject

data class SavedLogin(
    val host: String = "192.168.1.1",
    val port: Int = 80,
    val https: Boolean = false,
    val username: String = "",
    val password: String = "",
    val remember: Boolean = false,
    /** External address for remote access (DDNS / public IP); empty = local only. */
    val remoteHost: String = "",
    val remotePort: Int = 443,
    val remoteHttps: Boolean = true,
)

/** A named saved login; [name] empty = the default name ("My router" in the chosen language). */
data class Profile(val id: String, val name: String, val login: SavedLogin)

const val MAX_PROFILES = 5

/**
 * Stores the last login. The password is encrypted with an AES key kept in the
 * Android Keystore, so it never sits in plain text on the device.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("router_login", Context.MODE_PRIVATE)

    // ---- login profiles ----
    // Up to MAX_PROFILES named logins ("Lior", "Oriyan"...), each with its own addresses, user and
    // (encrypted) password. load()/save() work on the active one, so everything that only needs
    // "the current login" (the background log check, too) keeps working unchanged.

    /** All saved profiles, in the order they were added. */
    fun profiles(): List<Profile> {
        val raw = prefs.getString("profiles", null) ?: return listOfNotNull(legacyProfile())
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { profileFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveProfiles(list: List<Profile>) {
        prefs.edit()
            .putString("profiles", JSONArray(list.take(MAX_PROFILES).map { profileToJson(it) }).toString())
            // the single login from before profiles has moved into the list
            .apply { LEGACY_KEYS.forEach { remove(it) } }
            .apply()
    }

    /** The profile load()/save() use; falls back to the first one. */
    var activeProfileId: String?
        get() = prefs.getString("active_profile", null)
        set(v) = prefs.edit().putString("active_profile", v).apply()

    fun activeProfile(): Profile? = profiles().let { ps -> ps.firstOrNull { it.id == activeProfileId } ?: ps.firstOrNull() }

    fun load(): SavedLogin = activeProfile()?.login ?: SavedLogin()

    /** Saves [login] into the active profile (creating a first, unnamed one if there's none). */
    fun save(login: SavedLogin) {
        val ps = profiles()
        val active = activeProfile()
        if (active == null) {
            val p = Profile(newProfileId(), "", login)
            saveProfiles(ps + p)
            activeProfileId = p.id
        } else {
            saveProfiles(ps.map { if (it.id == active.id) it.copy(login = login) else it })
        }
    }

    fun newProfileId(): String = java.util.UUID.randomUUID().toString()

    /** The one login stored before profiles existed (1.1.7.1 and older), as profile #1. */
    private fun legacyProfile(): Profile? {
        if (!prefs.contains("host")) return null
        val remember = prefs.getBoolean("remember", false)
        val login = SavedLogin(
            host = prefs.getString("host", null) ?: "192.168.1.1",
            port = prefs.getInt("port", 80),
            https = prefs.getBoolean("https", false),
            username = prefs.getString("user", null).orEmpty(),
            password = if (remember) prefs.getString("pass", null)?.let { decrypt(it) }.orEmpty() else "",
            remember = remember,
            remoteHost = prefs.getString("remote_host", null).orEmpty(),
            remotePort = prefs.getInt("remote_port", 443),
            remoteHttps = prefs.getBoolean("remote_https", true),
        )
        return Profile(LEGACY_ID, "", login)
    }

    private fun profileToJson(p: Profile) = JSONObject().apply {
        val l = p.login
        put("id", p.id)
        put("name", p.name)
        put("host", l.host)
        put("port", l.port)
        put("https", l.https)
        put("user", l.username)
        put("remember", l.remember)
        // password only when remembered, and only encrypted (Android Keystore AES)
        if (l.remember && l.password.isNotEmpty()) put("pass", encrypt(l.password))
        put("remote_host", l.remoteHost)
        put("remote_port", l.remotePort)
        put("remote_https", l.remoteHttps)
    }

    private fun profileFromJson(o: JSONObject): Profile {
        val remember = o.optBoolean("remember", false)
        return Profile(
            id = o.optString("id").ifEmpty { newProfileId() },
            name = o.optString("name"),
            login = SavedLogin(
                host = o.optString("host", "192.168.1.1"),
                port = o.optInt("port", 80),
                https = o.optBoolean("https", false),
                username = o.optString("user"),
                password = if (remember) o.optString("pass").takeIf { it.isNotEmpty() }?.let { decrypt(it) }.orEmpty() else "",
                remember = remember,
                remoteHost = o.optString("remote_host"),
                remotePort = o.optInt("remote_port", 443),
                remoteHttps = o.optBoolean("remote_https", true),
            ),
        )
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

    var colorPalette: String?
        get() = prefs.getString("palette", null)
        set(v) = prefs.edit().putString("palette", v).apply()

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
        const val LEGACY_ID = "default"
        val LEGACY_KEYS = listOf("host", "port", "https", "user", "pass", "remember", "remote_host", "remote_port", "remote_https")
    }
}

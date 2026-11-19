package com.troks.tomatorouters.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers which devices were seen on WiFi (and on which band), so a phone that is
 * offline right now still shows as a WiFi device instead of "wired". Stored on this phone only.
 */
object DeviceMemory {
    private var prefs: SharedPreferences? = null
    private val bands = mutableMapOf<String, String>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("device_memory", Context.MODE_PRIVATE)
        prefs = p
        p.getStringSet(KEY, emptySet()).orEmpty().forEach { e ->
            val mac = e.substringBefore('|')
            bands[mac] = e.substringAfter('|', "")
        }
    }

    /** Band last seen for a wireless device ("" if unknown), or null if it was never seen on WiFi. */
    @Synchronized
    fun wirelessBand(mac: String): String? = bands[mac]

    @Synchronized
    fun rememberWireless(seen: Map<String, String?>) {
        var changed = false
        seen.forEach { (mac, band) ->
            val b = band.orEmpty()
            if (bands[mac] != b && !(b.isEmpty() && bands.containsKey(mac))) { bands[mac] = b; changed = true }
        }
        if (changed) prefs?.edit()?.putStringSet(KEY, bands.map { "${it.key}|${it.value}" }.toSet())?.apply()
    }

    private const val KEY = "wireless"
}

package com.troks.tomatorouters.data

import android.content.Context

/** Devices starred in Devices (by MAC address). Stored on this phone only. */
object Favorites {
    private const val PREFS = "favorites"
    private const val KEY = "macs"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()

    /** Adds or removes [mac], saves, and returns the new set. */
    fun toggle(context: Context, mac: String): Set<String> {
        val now = load(context).let { if (mac in it) it - mac else it + mac }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, now).apply()
        return now
    }
}

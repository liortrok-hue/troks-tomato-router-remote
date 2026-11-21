package com.troks.tomatorouters.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** One way to reach the router: the local (home) address, or the external one for remote access. */
data class Endpoint(val host: String, val port: Int, val https: Boolean, val remote: Boolean)

/** "https://router.ddns.net/" -> "router.ddns.net" */
fun cleanHost(h: String) = h.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

/**
 * The addresses to try, in order: the local address first, then the external one (if one is set).
 *
 * The local address is skipped when the phone is on mobile data only (no Wi-Fi, Ethernet or VPN):
 * a home LAN address can't be reached that way, so there's no point waiting for it to time out.
 * With a VPN (e.g. WireGuard to home) the local address may work, so it's still tried first.
 */
fun SavedLogin.endpoints(ctx: Context): List<Endpoint> {
    val local = Endpoint(cleanHost(host), port, https, remote = false)
    val ext = cleanHost(remoteHost)
    if (ext.isEmpty()) return listOf(local)
    val remote = Endpoint(ext, remotePort, remoteHttps, remote = true)
    return if (mayReachLan(ctx)) listOf(local, remote) else listOf(remote)
}

private fun mayReachLan(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

/** Couldn't reach the address at all (as opposed to: reached it, and it said no). */
fun isNetworkError(e: Throwable) = e is IOException && e !is RouterException && e !is CertChangedException

/**
 * How long to wait for the TCP connection: short when another address is still left to try,
 * so being away from home doesn't mean a long wait on the local address first.
 */
fun connectTimeoutFor(hasFallback: Boolean): Long = if (hasFallback) 4 else 8

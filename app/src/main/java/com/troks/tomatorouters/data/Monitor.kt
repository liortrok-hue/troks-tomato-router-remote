package com.troks.tomatorouters.data

/*
 * Real-time monitoring, the data behind bwm-realtime.asp and ipt-realtime.asp:
 *  - per-interface byte counters from /proc/net/dev
 *  - per-IP byte counters from /proc/net/ipt_account (needs "IP Traffic Monitoring" / cstats_enable)
 * Rates are computed in the app from the difference between two samples.
 */

/** Which interface is what, for friendly names. */
data class IfaceInfo(
    val wan: Set<String>,
    val lan: String,
    /** wireless ifname -> band label */
    val wireless: Map<String, String>,
)

/** Byte counters at one moment. rx = received by the router on that interface. */
data class Counters(val rx: Long, val tx: Long)

/** Per-IP counters. download = bytes sent to the device, upload = bytes sent by it. */
data class IpCounters(val download: Long, val upload: Long)

suspend fun RouterApi.ifaceInfo(): IfaceInfo {
    val out = client.shell(
        """
        echo @@wan; nvram get wan_iface; nvram get wan_ifname
        echo @@lan; nvram get lan_ifname
        ${RouterApi.DETECT_T64}
        echo @@wl
        ${RouterApi.WL_BANDS_SH.replace("\n", "\n        ")}
        """.trimIndent()
    )
    val s = RouterApi.sections(out)
    return IfaceInfo(
        wan = s["wan"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        lan = s["lan"].orEmpty().trim().ifEmpty { "br0" },
        wireless = s["wl"].orEmpty().lines().mapNotNull { l ->
            val f = l.trim().split(' ')
            if (f.size >= 2) f[0] to RouterApi.bandName(f[1]) else null
        }.toMap(),
    )
}

suspend fun RouterApi.netDev(): Map<String, Counters> {
    val out = client.shell("cat /proc/net/dev")
    return out.lines().mapNotNull { line ->
        val colon = line.indexOf(':')
        if (colon < 0) return@mapNotNull null
        val name = line.substring(0, colon).trim()
        val f = line.substring(colon + 1).trim().split(Regex("\\s+"))
        if (name.isEmpty() || f.size < 9) return@mapNotNull null
        val rx = f[0].toLongOrNull() ?: return@mapNotNull null
        val tx = f[8].toLongOrNull() ?: return@mapNotNull null
        name to Counters(rx, tx)
    }.toMap()
}

private val IPT_RE = Regex("ip = (\\S+) bytes_src = (\\d+).*?bytes_dst = (\\d+)")

/** Per-IP counters, or null when IP traffic monitoring is turned off on the router. */
suspend fun RouterApi.ipTraffic(): Map<String, IpCounters>? {
    val out = client.shell(
        "if [ -d /proc/net/ipt_account ] && [ -n \"\$(ls /proc/net/ipt_account 2>/dev/null)\" ]; then " +
            "cat /proc/net/ipt_account/* 2>/dev/null; else echo @@disabled; fi"
    )
    if (out.contains("@@disabled")) return null
    return out.lines().mapNotNull { line ->
        val m = IPT_RE.find(line) ?: return@mapNotNull null
        val ip = m.groupValues[1]
        // skip the whole-subnet counter (x.x.x.0) and broadcast; they would double the totals
        if (ip == "0.0.0.0" || ip.endsWith(".0") || ip.endsWith(".255")) return@mapNotNull null
        ip to IpCounters(download = m.groupValues[3].toLong(), upload = m.groupValues[2].toLong())
    }.toMap()
}

/** Turns on IP traffic monitoring (cstats) and reloads the firewall rules that count per IP. */
suspend fun RouterApi.enableIpTraffic() {
    nvramSet(mapOf("cstats_enable" to "1"), listOf("cstats", "firewall"))
}

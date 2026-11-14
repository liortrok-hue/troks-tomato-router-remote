package com.troks.tomatorouters.data

/*
 * Static DHCP / IP reservations (basic-static.asp), stored in nvram "dhcpd_static":
 *   MAC<IP<HOSTNAME<BIND_ARP>MAC<IP<HOSTNAME<BIND_ARP>...
 * MAC may hold several addresses separated by ",". Older builds have no BIND_ARP field;
 * any extra fields we don't know are kept as-is when saving.
 */

data class StaticLease(
    val macs: List<String>,
    val ip: String,
    val hostname: String,
    val bindArp: Boolean,
    val extra: List<String> = emptyList(),
) {
    val mac get() = macs.firstOrNull().orEmpty()
}

private val HOSTNAME_RE = Regex("^[A-Za-z0-9._-]*$")

fun isValidHostname(s: String) = HOSTNAME_RE.matches(s) && s.length <= 63

suspend fun RouterApi.staticLeases(): List<StaticLease> {
    val v = nvramGet(listOf("dhcpd_static", "lan_ipaddr")).first
    val raw = v["dhcpd_static"].orEmpty()
    // some builds store only the last octet; expand it with the LAN prefix
    val lanPrefix = v["lan_ipaddr"].orEmpty().substringBeforeLast('.', "192.168.1")
    return raw.split('>').mapNotNull { e ->
        val f = e.split('<')
        if (f.size < 3) return@mapNotNull null
        val macs = f[0].split(',').mapNotNull { RouterApi.normMac(it) }
        if (macs.isEmpty()) return@mapNotNull null
        StaticLease(
            macs = macs,
            ip = if (f[1].isNotEmpty() && f[1].all(Char::isDigit)) "$lanPrefix.${f[1]}" else f[1],
            hostname = f[2],
            bindArp = f.getOrNull(3) == "1",
            extra = if (f.size > 4) f.drop(4) else emptyList(),
        )
    }
}

suspend fun RouterApi.saveStaticLeases(list: List<StaticLease>) {
    list.forEach { l ->
        if (l.macs.isEmpty() || l.macs.any { RouterApi.normMac(it) == null }) throw RouterException(Err.BAD_MAC)
        if (!isIp(l.ip)) throw RouterException(Err.BAD_HOST)
        if (!isValidHostname(l.hostname)) throw RouterException(Err.BAD_VALUE)
    }
    val value = list.joinToString("") { l ->
        (listOf(l.macs.joinToString(","), l.ip.trim(), l.hostname.trim(), if (l.bindArp) "1" else "0") + l.extra)
            .joinToString("<") + ">"
    }
    // dhcpd = dnsmasq; arpbind applies the "bind to MAC" option
    nvramSet(mapOf("dhcpd_static" to value), listOf("dnsmasq", "arpbind"))
}

/** Adds or updates the reservation for one device and applies it. */
suspend fun RouterApi.reserveIp(mac: String, ip: String, hostname: String, bindArp: Boolean) {
    val m = RouterApi.normMac(mac) ?: throw RouterException(Err.BAD_MAC)
    val list = staticLeases().toMutableList()
    if (list.any { it.ip == ip && m !in it.macs }) throw RouterException(Err.IP_IN_USE)
    val i = list.indexOfFirst { m in it.macs }
    if (i >= 0) list[i] = list[i].copy(ip = ip, hostname = hostname, bindArp = bindArp)
    else list.add(StaticLease(listOf(m), ip, hostname, bindArp))
    saveStaticLeases(list)
}

/** Removes the reservation of one device and applies it. */
suspend fun RouterApi.removeReservation(mac: String) {
    val m = RouterApi.normMac(mac) ?: throw RouterException(Err.BAD_MAC)
    val list = staticLeases().mapNotNull { l ->
        if (m !in l.macs) l else l.copy(macs = l.macs - m).takeIf { it.macs.isNotEmpty() }
    }
    saveStaticLeases(list)
}

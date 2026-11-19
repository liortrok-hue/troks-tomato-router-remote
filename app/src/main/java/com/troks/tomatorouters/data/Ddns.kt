package com.troks.tomatorouters.data

/*
 * Dynamic DNS clients 1 and 2, as on Basic > DDNS (basic-ddns.asp).
 *
 * nvram ddnsx0 / ddnsx1 = service<user:pass<host<wildcard<mx<backup mx<custom url or token
 * ddnsxN_refresh = days between forced updates (0 = off), ddnsxN_cache = "time,ip" of the last update
 * (cleared to force an update after a change), /var/lib/mdu/ddnsxN.msg = last response from the service.
 */

/**
 * A DDNS service the router knows. [flags] are the router's own letters:
 * u = username + password, p = password only, h/j = hostname, t = hostname (top-level), w = wildcard,
 * m = MX, b = backup MX, c = custom URL, n = token, o = OpenDNS, s = save state.
 * The labels replace the generic field names where the router uses its own (e.g. "API token").
 */
data class DdnsService(
    val id: String,
    val name: String,
    val url: String,
    val flags: String,
    val userLabel: String? = null,
    val passLabel: String? = null,
    val hostLabel: String? = null,
    val wildLabel: String? = null,
    val bmxLabel: String? = null,
    val tokenLabel: String? = null,
) {
    fun has(c: Char) = c in flags
    val usesUser get() = has('u')
    val usesPass get() = has('u') || has('p')
    val usesHost get() = has('h') || has('j') || has('t')
}

/** Same list and order as FreshTomato's basic-ddns.asp. */
val DDNS_SERVICES = listOf(
    DdnsService("changeip", "ChangeIP (https)", "https://www.changeip.com/", "uh", userLabel = "Email Address"),
    DdnsService("cloudflare", "Cloudflare (https)", "https://www.cloudflare.com/", "phbnw", passLabel = "API token", wildLabel = "Proxied", bmxLabel = "Create record if needed", tokenLabel = "Zone ID"),
    DdnsService("dnsexit", "DNS Exit (https)", "https://www.dnsexit.com/", "ph", passLabel = "API key", hostLabel = "Hostname(s)"),
    DdnsService("dnshenet", "dns.he.net (https)", "https://dns.he.net/", "u", userLabel = "Host name", passLabel = "DDNS key"),
    DdnsService("dnsomatic", "DNS-O-Matic (https)", "https://www.dnsomatic.com/", "uj", hostLabel = "Domain (optional)"),
    DdnsService("duckdns", "Duck DNS (https)", "https://www.duckdns.org/", "tn"),
    DdnsService("dyndns", "DynDNS (https) - Dynamic", "https://www.dyndns.com/", "uhwmbs"),
    DdnsService("dyndns-static", "DynDNS (https) - Static", "https://www.dyndns.com/", "uhwmbs"),
    DdnsService("dyndns-custom", "DynDNS (https) - Custom", "https://www.dyndns.com/", "uhwmbs"),
    DdnsService("easydns", "easyDNS (https)", "https://www.easydns.com/", "uhwm"),
    DdnsService("enom", "eNom", "https://www.enom.com/", "ut", userLabel = "Domain"),
    DdnsService("afraid", "FreeDNS (https)", "https://freedns.afraid.org/", "n"),
    DdnsService("heipv6tb", "HE.net IPv6 Tunnel Broker (https)", "https://www.tunnelbroker.net/", "uh", userLabel = "Account Name", passLabel = "Update Key", hostLabel = "Tunnel ID"),
    DdnsService("namecheap", "namecheap (https)", "https://www.namecheap.com/", "ut", userLabel = "Domain"),
    DdnsService("noip", "No-IP.com (https)", "https://www.noip.com/", "uh", userLabel = "Email Address", hostLabel = "Hostname / Group"),
    DdnsService("opendns", "OpenDNS (https)", "https://www.opendns.com/", "uhoz", hostLabel = "Network (optional)"),
    DdnsService("ovh", "OVH (https)", "https://www.ovh.com/", "uh"),
    DdnsService("pairdomains", "pairDOMAINS (https)", "https://www.pairdomains.com/", "uh"),
    DdnsService("pubyun", "PubYun [3322]", "https://www.pubyun.com", "uhwmb"),
    DdnsService("pubyun-static", "PubYun [3322] - Static", "https://www.pubyun.com", "uhwmb"),
    DdnsService("zoneedit", "ZoneEdit (https)", "https://www.zoneedit.com/", "uh"),
    DdnsService("custom", "Custom URL", "", "c"),
)

fun ddnsService(id: String) = DDNS_SERVICES.firstOrNull { it.id == id }

/** OpenDNS servers the router puts in WAN DNS when "Use as DNS" is on. */
private const val OPENDNS = "208.67.222.222 208.67.220.220"

data class DdnsClient(
    /** "" = not used */
    val service: String = "",
    val user: String = "",
    val pass: String = "",
    val host: String = "",
    val wildcard: Boolean = false,
    val mx: String = "",
    val backupMx: Boolean = false,
    /** custom URL or token, depending on the service */
    val extra: String = "",
    /** "Save state when IP changes (nvram commit)" */
    val saveState: Boolean = true,
    /** "Force next update": clears the cache on save; not stored */
    val force: Boolean = false,
    /** IP address source: "wan", "@1" (external checker), "0.0.0.0", "10.1.1.1", or [CUSTOM_IP] */
    val ipSource: String = "wan",
    val customIp: String = "",
    /** minutes between external IP checks (only for "@" sources) */
    val checkMinutes: String = "10",
    /** OpenDNS "Use as DNS" bit mask per WAN (bit 0 = WAN0) */
    val openDnsMask: Int = 0,
    val refreshDays: String = "28",
    /** read-only status */
    val lastUpdate: String = "",
    val lastResponse: String = "",
) {
    companion object { const val CUSTOM_IP = "custom" }
}

/** [wanIp] is shown next to "Use WAN0 IP", like the router page does. */
data class DdnsSettings(val clients: List<DdnsClient>, val wanIp: String = "")

private val FIXED_SOURCES = setOf("", "wan", "wan2", "wan3", "wan4", "0.0.0.0", "10.1.1.1")

suspend fun RouterApi.ddnsSettings(): DdnsSettings {
    val out = client.shell(
        "echo @@wanip; nvram get wan_ipaddr\n" + (0..1).joinToString("\n") { i ->
            "echo @@v$i; nvram get ddnsx$i; echo @@r$i; nvram get ddnsx${i}_refresh; " +
                "echo @@c$i; nvram get ddnsx${i}_cache; echo @@s$i; nvram get ddnsx${i}_save; " +
                "echo @@ip$i; nvram get ddnsx${i}_ip; echo @@ck$i; nvram get ddnsx${i}_cktime; " +
                "echo @@o$i; nvram get ddnsx${i}_opendns; " +
                "echo @@m$i; cat /var/lib/mdu/ddnsx$i.msg 2>/dev/null; " +
                "echo @@t$i; date -r /var/lib/mdu/ddnsx$i.msg '+%Y-%m-%d %H:%M' 2>/dev/null"
        }
    )
    val s = RouterApi.sections(out)
    return DdnsSettings((0..1).map { i ->
        val f = s["v$i"].orEmpty().split('<')
        val userPass = f.getOrElse(1) { "" }
        val cache = s["c$i"].orEmpty() // "1731850000,85.130.213.84"
        val cacheTime = cache.substringBefore(',').toLongOrNull()?.takeIf { it > 946684800 }
        val lastUpdate = buildString {
            cacheTime?.let {
                append(java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            }
            val ip = cache.substringAfter(',', "")
            if (ip.isNotBlank()) { if (isNotEmpty()) append("  ·  "); append(ip) }
        }
        val msg = s["m$i"].orEmpty().trim()
        val ip = s["ip$i"].orEmpty().trim()
        val custom = ip !in FIXED_SOURCES && !ip.startsWith("@")
        DdnsClient(
            service = f.getOrElse(0) { "" },
            user = userPass.substringBefore(':'),
            pass = userPass.substringAfter(':', ""),
            host = f.getOrElse(2) { "" },
            wildcard = f.getOrElse(3) { "" } == "1",
            mx = f.getOrElse(4) { "" },
            backupMx = f.getOrElse(5) { "" } == "1",
            extra = f.drop(6).joinToString("<"),
            saveState = s["s$i"].orEmpty().trim().let { it.isEmpty() || it == "1" },
            ipSource = when { custom -> DdnsClient.CUSTOM_IP; ip.isEmpty() -> "wan"; else -> ip },
            customIp = if (custom) ip else "",
            checkMinutes = s["ck$i"].orEmpty().trim().ifEmpty { "10" },
            openDnsMask = s["o$i"].orEmpty().trim().toIntOrNull() ?: 0,
            refreshDays = s["r$i"].orEmpty().ifBlank { "28" },
            lastUpdate = lastUpdate,
            lastResponse = if (msg.isEmpty()) "" else listOf(s["t$i"].orEmpty().trim(), msg).filter { it.isNotEmpty() }.joinToString("  ·  "),
        )
    }, wanIp = s["wanip"].orEmpty().trim())
}

/** Builds ddnsxN the way basic-ddns.asp does: only the fields the service uses are filled in. */
fun DdnsClient.toNvram(): String {
    val svc = ddnsService(service) ?: return ""
    val creds = when {
        svc.usesUser -> "$user:$pass"
        svc.usesPass -> ":$pass"
        // custom URL: credentials written in the URL (https://user:pass@host/...) are also passed to wget
        svc.has('c') && '@' in extra -> extra.substringAfter("//", "").substringBefore('@')
            .takeIf { ':' in it && it.substringBefore(':').isNotEmpty() && it.substringAfter(':').isNotEmpty() }.orEmpty()
        else -> ""
    }
    val parts = listOf(
        svc.id,
        creds,
        if (svc.usesHost) host.trim() else "",
        if (svc.has('w')) (if (wildcard) "1" else "0") else "",
        if (svc.has('m')) mx.trim() else "",
        if (svc.has('b')) (if (backupMx) "1" else "0") else "",
        if (svc.has('c') || svc.has('n')) extra.trim() else "",
    )
    return parts.joinToString("<") { it.replace("<", "") }
}

private fun DdnsClient.ipValue() = if (ipSource == DdnsClient.CUSTOM_IP) customIp.trim() else ipSource

/** Checks a client before saving; returns false when a field is out of range. */
fun DdnsClient.isValid(): Boolean {
    if ((refreshDays.toIntOrNull() ?: -1) !in 0..90) return false
    if (service.isEmpty()) return true
    if (ipSource == DdnsClient.CUSTOM_IP && !isIp(customIp)) return false
    if (ipSource.startsWith("@") && (checkMinutes.toIntOrNull() ?: 0) !in 5..99999) return false
    return true
}

suspend fun RouterApi.saveDdns(new: DdnsSettings, old: DdnsSettings) {
    val m = linkedMapOf<String, String>()
    val services = mutableListOf("ddns")
    new.clients.forEachIndexed { i, c ->
        if (!c.isValid()) throw RouterException(Err.BAD_VALUE)
        val before = old.clients.getOrNull(i)
        val value = c.toNvram()
        m["ddnsx$i"] = value
        m["ddnsx${i}_refresh"] = c.refreshDays.trim().toInt().toString()
        if (c.service.isNotEmpty()) {
            m["ddnsx${i}_ip"] = c.ipValue()
            if (ddnsService(c.service)?.has('s') == true) m["ddnsx${i}_save"] = if (c.saveState) "1" else "0"
            if (c.ipSource.startsWith("@")) m["ddnsx${i}_cktime"] = c.checkMinutes.trim().toInt().toString()
        }
        // a changed client (or "Force next update") gets a fresh update right away, like the router page
        if (c.force || value != before?.toNvram() || c.ipValue() != before?.ipValue()) m["ddnsx${i}_cache"] = ""

        // OpenDNS "Use as DNS": put OpenDNS in WAN0's DNS servers, or give DNS back to automatic
        val useNow = c.service == "opendns" && (c.openDnsMask and 1) != 0
        val usedBefore = before != null && (before.openDnsMask and 1) != 0
        if (useNow != usedBefore) {
            if (useNow) { m["wan_dns"] = OPENDNS; m["wan_dns_auto"] = "0" }
            else { m["wan_dns"] = ""; m["wan_dns_auto"] = "1" }
            if ("dnsmasq" !in services) services += "dnsmasq"
        }
        m["ddnsx${i}_opendns"] = (if (c.service == "opendns") c.openDnsMask else 0).toString()
    }
    nvramSet(m, services)
}

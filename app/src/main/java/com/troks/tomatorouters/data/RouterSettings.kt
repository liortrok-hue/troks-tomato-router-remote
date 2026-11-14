package com.troks.tomatorouters.data

import com.troks.tomatorouters.data.RouterClient.Companion.q

/*
 * Router configuration pages, read and written through nvram — the same variables the
 * FreshTomato web pages use:
 *   basic-network.asp  -> WAN settings        (service wan restart)
 *   forward-basic.asp  -> port forwarding     (service firewall restart)
 *   forward-dmz.asp    -> DMZ                 (service firewall restart)
 *   admin-access.asp   -> admin access        (service admin restart)
 */

data class WanSettings(
    val proto: String,
    val pppUser: String,
    val pppPass: String,
    val pppService: String,
    val keepAlive: Boolean,
    val redialPeriod: String,
    val idleTime: String,
    val ip: String,
    val netmask: String,
    val gateway: String,
    val dnsManual: Boolean,
    val dns1: String,
    val dns2: String,
    val mtuManual: Boolean,
    val mtu: String,
    /** "wan_ppp_" on multi-WAN firmware, "ppp_" on older builds. */
    val pppPrefix: String,
    val hasDnsAutoFlag: Boolean,
)

data class PortForward(
    val enabled: Boolean,
    /** 1 = TCP, 2 = UDP, 3 = both */
    val proto: Int,
    val source: String,
    /** e.g. "80", "1000-2000", "80,443" */
    val extPorts: String,
    val intPort: String,
    val intAddress: String,
    val description: String,
)

data class DmzSettings(
    val enabled: Boolean,
    val host: String,
    val source: String,
    val leaveRemoteAccess: Boolean,
)

data class AdminSettings(
    val httpEnabled: Boolean,
    val httpsEnabled: Boolean,
    val httpPort: String,
    val httpsPort: String,
    val remoteEnabled: Boolean,
    val remoteHttps: Boolean,
    val remotePort: String,
    val remoteAllowed: String,
    val sshAtStartup: Boolean,
    val sshPort: String,
    val sshRemote: Boolean,
    val sshRemotePort: String,
    val sshPasswordLogin: Boolean,
    val telnetAtStartup: Boolean,
    val username: String,
)

private val KEY_RE = Regex("^[a-z0-9_]+$")

/** Reads nvram variables. Also returns which of [checkExists] exist at all. */
suspend fun RouterApi.nvramGet(keys: List<String>, checkExists: List<String> = emptyList()): Pair<Map<String, String>, Set<String>> {
    (keys + checkExists).forEach { require(KEY_RE.matches(it)) }
    val sb = StringBuilder("for k in ${keys.joinToString(" ")}; do echo \"@@\$k\"; nvram get \$k; done\n")
    if (checkExists.isNotEmpty()) {
        sb.append("echo @@__exists; nvram show 2>/dev/null | grep -oE '^(${checkExists.joinToString("|")})=' | tr -d '='\n")
    }
    val sec = RouterApi.sections(client.shell(sb.toString()))
    val exists = sec["__exists"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return keys.associateWith { sec[it].orEmpty() } to exists
}

/** Sets nvram variables, commits, and restarts the given services in the background. */
suspend fun RouterApi.nvramSet(values: Map<String, String>, services: List<String>) {
    val sb = StringBuilder()
    values.forEach { (k, v) ->
        require(KEY_RE.matches(k))
        sb.append("nvram set $k=${q(v)}; ")
    }
    sb.append("nvram commit >/dev/null; ")
    if (services.isNotEmpty()) {
        services.forEach { require(KEY_RE.matches(it)) }
        sb.append("(sleep 1; ${services.joinToString("; ") { "service $it restart" }}) >/dev/null 2>&1 &")
    }
    sb.append(" echo ok")
    client.shell(sb.toString())
}

// ---------- WAN ----------

suspend fun RouterApi.wanSettings(): WanSettings {
    val (_, exists) = nvramGet(emptyList(), listOf("wan_ppp_username", "wan_dns_auto"))
    val p = if ("wan_ppp_username" in exists) "wan_ppp_" else "ppp_"
    val (v, _) = nvramGet(
        listOf(
            "wan_proto", "${p}username", "${p}passwd", "${p}service", "${p}demand", "${p}redialperiod", "${p}idletime",
            "wan_ipaddr", "wan_netmask", "wan_gateway", "wan_dns", "wan_dns_auto", "wan_mtu_enable", "wan_mtu",
        )
    )
    val hasAuto = "wan_dns_auto" in exists
    val dns = v["wan_dns"].orEmpty().split(' ').filter { it.isNotBlank() }
    return WanSettings(
        proto = v["wan_proto"].orEmpty().ifEmpty { "dhcp" },
        pppUser = v["${p}username"].orEmpty(),
        pppPass = v["${p}passwd"].orEmpty(),
        pppService = v["${p}service"].orEmpty(),
        keepAlive = v["${p}demand"] != "1",
        redialPeriod = v["${p}redialperiod"].orEmpty().ifEmpty { "20" },
        idleTime = v["${p}idletime"].orEmpty().ifEmpty { "5" },
        ip = v["wan_ipaddr"].orEmpty(),
        netmask = v["wan_netmask"].orEmpty(),
        gateway = v["wan_gateway"].orEmpty(),
        dnsManual = if (hasAuto) v["wan_dns_auto"] == "0" else dns.isNotEmpty(),
        dns1 = dns.getOrElse(0) { "" },
        dns2 = dns.getOrElse(1) { "" },
        mtuManual = v["wan_mtu_enable"] == "1",
        mtu = v["wan_mtu"].orEmpty().ifEmpty { "1500" },
        pppPrefix = p,
        hasDnsAutoFlag = hasAuto,
    )
}

suspend fun RouterApi.saveWan(w: WanSettings) {
    val p = w.pppPrefix
    val m = linkedMapOf("wan_proto" to w.proto)
    if (w.proto == "pppoe") {
        m["${p}username"] = w.pppUser
        m["${p}passwd"] = w.pppPass
        m["${p}service"] = w.pppService
        m["${p}demand"] = if (w.keepAlive) "0" else "1"
        m["${p}redialperiod"] = w.redialPeriod
        m["${p}idletime"] = w.idleTime
    }
    if (w.proto == "static") {
        if (!isIp(w.ip) || !isIp(w.netmask) || !isIp(w.gateway)) throw RouterException(Err.BAD_VALUE)
        m["wan_ipaddr"] = w.ip
        m["wan_netmask"] = w.netmask
        m["wan_gateway"] = w.gateway
    }
    if (w.dnsManual) {
        val list = listOf(w.dns1, w.dns2).map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty() || list.any { !isIp(it) }) throw RouterException(Err.BAD_VALUE)
        m["wan_dns"] = list.joinToString(" ")
    } else if (!w.hasDnsAutoFlag) {
        m["wan_dns"] = ""
    }
    if (w.hasDnsAutoFlag) m["wan_dns_auto"] = if (w.dnsManual) "0" else "1"
    m["wan_mtu_enable"] = if (w.mtuManual) "1" else "0"
    if (w.mtuManual) {
        val mtu = w.mtu.toIntOrNull()
        if (mtu == null || mtu !in 576..1500) throw RouterException(Err.BAD_VALUE)
        m["wan_mtu"] = mtu.toString()
    }
    nvramSet(m, listOf("wan"))
}

// ---------- Port forwarding ----------

suspend fun RouterApi.portForwards(): List<PortForward> {
    val raw = nvramGet(listOf("portforward")).first["portforward"].orEmpty()
    // entries: on<proto<src<ext<int<ip<desc  separated by '>'
    return raw.split('>').mapNotNull { e ->
        val f = e.split('<')
        if (f.size < 7) return@mapNotNull null
        PortForward(
            enabled = f[0] != "0",
            proto = f[1].toIntOrNull() ?: 3,
            source = f[2],
            extPorts = f[3].replace(':', '-'),
            intPort = f[4],
            intAddress = f[5],
            description = f.drop(6).joinToString("<"),
        )
    }
}

suspend fun RouterApi.savePortForwards(list: List<PortForward>) {
    val value = list.joinToString(">") { r ->
        listOf(
            if (r.enabled) "1" else "0",
            r.proto.toString(),
            r.source.trim(),
            r.extPorts.trim().replace('-', ':'),
            r.intPort.trim(),
            r.intAddress.trim(),
            r.description.replace("<", "").replace(">", ""),
        ).joinToString("<")
    }
    nvramSet(mapOf("portforward" to value), listOf("firewall"))
}

// ---------- DMZ ----------

suspend fun RouterApi.dmzSettings(): DmzSettings {
    val v = nvramGet(listOf("dmz_enable", "dmz_ipaddr", "dmz_sip", "dmz_ra")).first
    return DmzSettings(
        enabled = v["dmz_enable"] == "1",
        host = v["dmz_ipaddr"].orEmpty(),
        source = v["dmz_sip"].orEmpty(),
        leaveRemoteAccess = v["dmz_ra"] != "0",
    )
}

suspend fun RouterApi.saveDmz(d: DmzSettings) {
    if (d.enabled && !isIp(d.host) && d.host.toIntOrNull() !in 1..254) throw RouterException(Err.BAD_VALUE)
    nvramSet(
        mapOf(
            "dmz_enable" to if (d.enabled) "1" else "0",
            "dmz_ipaddr" to d.host.trim(),
            "dmz_sip" to d.source.trim(),
            "dmz_ra" to if (d.leaveRemoteAccess) "1" else "0",
        ),
        listOf("firewall"),
    )
}

// ---------- Admin access ----------

suspend fun RouterApi.adminSettings(): AdminSettings {
    val v = nvramGet(
        listOf(
            "http_enable", "https_enable", "http_lanport", "https_lanport",
            "remote_management", "remote_mgt_https", "http_wanport", "rmgt_sip",
            "sshd_eas", "sshd_port", "sshd_remote", "sshd_rport", "sshd_pass",
            "telnetd_eas", "http_username",
        )
    ).first
    return AdminSettings(
        httpEnabled = v["http_enable"] != "0",
        httpsEnabled = v["https_enable"] == "1",
        httpPort = v["http_lanport"].orEmpty().ifEmpty { "80" },
        httpsPort = v["https_lanport"].orEmpty().ifEmpty { "443" },
        remoteEnabled = v["remote_management"] == "1",
        remoteHttps = v["remote_mgt_https"] == "1",
        remotePort = v["http_wanport"].orEmpty().ifEmpty { "8080" },
        remoteAllowed = v["rmgt_sip"].orEmpty(),
        sshAtStartup = v["sshd_eas"] == "1",
        sshPort = v["sshd_port"].orEmpty().ifEmpty { "22" },
        sshRemote = v["sshd_remote"] == "1",
        sshRemotePort = v["sshd_rport"].orEmpty().ifEmpty { "2222" },
        sshPasswordLogin = v["sshd_pass"] != "0",
        telnetAtStartup = v["telnetd_eas"] == "1",
        username = v["http_username"].orEmpty(),
    )
}

/** Saves admin settings. [newPassword] null = keep the current password. */
suspend fun RouterApi.saveAdmin(a: AdminSettings, newPassword: String?) {
    val ports = listOf(a.httpPort, a.httpsPort, a.remotePort, a.sshPort, a.sshRemotePort)
    if (ports.any { it.toIntOrNull() !in 1..65535 }) throw RouterException(Err.BAD_VALUE)
    if (!a.httpEnabled && !a.httpsEnabled) throw RouterException(Err.BAD_VALUE)
    val m = linkedMapOf(
        "http_enable" to if (a.httpEnabled) "1" else "0",
        "https_enable" to if (a.httpsEnabled) "1" else "0",
        "http_lanport" to a.httpPort,
        "https_lanport" to a.httpsPort,
        "remote_management" to if (a.remoteEnabled) "1" else "0",
        "remote_mgt_https" to if (a.remoteHttps) "1" else "0",
        "http_wanport" to a.remotePort,
        "rmgt_sip" to a.remoteAllowed.trim(),
        "sshd_eas" to if (a.sshAtStartup) "1" else "0",
        "sshd_port" to a.sshPort,
        "sshd_remote" to if (a.sshRemote) "1" else "0",
        "sshd_rport" to a.sshRemotePort,
        "sshd_pass" to if (a.sshPasswordLogin) "1" else "0",
        "telnetd_eas" to if (a.telnetAtStartup) "1" else "0",
    )
    if (a.username.isNotBlank()) m["http_username"] = a.username.trim()
    if (newPassword != null) m["http_passwd"] = newPassword
    nvramSet(m, listOf("admin"))
}

private val IP_RE = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")

fun isIp(s: String) = IP_RE.matches(s.trim())

package com.troks.tomatorouters.data

import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * A simulated FreshTomato router for demo mode (store screenshots, Google Play review,
 * trying the app without a router). It answers the same shell commands the real API sends.
 *
 * All addresses are fake: LAN 10.0.0.0/24, WAN from the documentation range 203.0.113.0/24,
 * and made-up MAC addresses. Changes are kept in memory only.
 */
class DemoShell : RouterShell {

    private data class Dev(val mac: String, val ip: String, val name: String, val wlIf: String?, val rssi: Int?, val avgMbit: Double)

    private val devices = listOf(
        Dev("3C:5A:B4:10:22:01", "10.0.0.11", "Living-Room-TV", "eth2", -48, 18.0),
        Dev("A4:83:E7:2B:61:02", "10.0.0.12", "Pixel-9", "eth2", -55, 6.0),
        Dev("F0:18:98:3C:44:03", "10.0.0.13", "iPad-Kids", "eth1", -63, 3.5),
        Dev("00:11:32:5D:08:04", "10.0.0.20", "NAS-Storage", null, null, 9.0),
        Dev("8C:85:90:6E:11:05", "10.0.0.15", "Work-Laptop", "eth2", -52, 4.0),
        Dev("DC:A6:32:7F:91:06", "10.0.0.31", "Game-Console", null, null, 12.0),
        Dev("50:C7:BF:80:2A:07", "10.0.0.40", "Smart-Plug", "eth1", -71, 0.02),
        Dev("30:FD:38:91:3B:08", "10.0.0.41", "Smart-Speaker", "eth1", -60, 0.4),
        Dev("64:16:66:A2:4C:09", "10.0.0.50", "Printer", null, null, 0.01),
        Dev("B8:27:EB:B3:5D:0A", "10.0.0.60", "Home-Server", null, null, 1.5),
    )

    private val blocked = mutableSetOf<String>()

    private val nvram = mutableMapOf(
        "router_name" to "Troks-Home",
        "t_model_name" to "Netgear R7000",
        "os_version" to "2026.5 K26ARM USB AIO-64K",
        "wan_proto" to "pppoe",
        "wan_iface" to "ppp0",
        "wan_ifname" to "vlan2",
        "wan_ipaddr" to "203.0.113.45",
        "wan_netmask" to "255.255.255.255",
        "wan_gateway" to "203.0.113.1",
        "wan_get_dns" to "1.1.1.1 8.8.8.8",
        "wan_dns" to "1.1.1.1 8.8.8.8",
        "wan_dns_auto" to "0",
        "wan_ppp_username" to "user@isp.example",
        "wan_ppp_passwd" to "demo-password",
        "wan_ppp_service" to "",
        "wan_ppp_demand" to "0",
        "wan_ppp_redialperiod" to "20",
        "wan_ppp_idletime" to "5",
        "wan_mtu_enable" to "0",
        "wan_mtu" to "1492",
        "lan_ipaddr" to "10.0.0.1",
        "lan_ifname" to "br0",
        "wl_ifnames" to "eth1 eth2",
        "wl0_ifname" to "eth1", "wl0_ssid" to "Troks-Home", "wl0_radio" to "1", "wl0_nband" to "2",
        "wl0_channel" to "6", "wl0_security_mode" to "wpa2_personal", "wl0_wpa_psk" to "DemoPass2026",
        "wl1_ifname" to "eth2", "wl1_ssid" to "Troks-Home-5G", "wl1_radio" to "1", "wl1_nband" to "1",
        "wl1_channel" to "36", "wl1_security_mode" to "wpa2_personal", "wl1_wpa_psk" to "DemoPass2026",
        "portforward" to "1<1<<8443<443<10.0.0.20<NAS web>1<3<<25565<<10.0.0.31<Game server>0<1<<3389<<10.0.0.15<Remote desktop",
        "dmz_enable" to "0", "dmz_ipaddr" to "", "dmz_sip" to "", "dmz_ra" to "1",
        "dhcpd_static" to "00:11:32:5D:08:04<10.0.0.20<NAS-Storage<1>DC:A6:32:7F:91:06<10.0.0.31<Game-Console<0>64:16:66:A2:4C:09<10.0.0.50<Printer<0>",
        "http_enable" to "1", "https_enable" to "1", "http_lanport" to "80", "https_lanport" to "443",
        "remote_management" to "1", "remote_mgt_https" to "1", "http_wanport" to "8443", "rmgt_sip" to "",
        "sshd_eas" to "1", "sshd_port" to "22", "sshd_remote" to "0", "sshd_rport" to "2222", "sshd_pass" to "1",
        "telnetd_eas" to "0", "http_username" to "admin",
        "cstats_enable" to "1",
    )

    // live counters
    private val start = System.nanoTime()
    private var lastTick = start
    private val ifCounters = mutableMapOf(
        "lo" to longArrayOf(1_000_000, 1_000_000),
        "ppp0" to longArrayOf(48_000_000_000, 6_100_000_000),
        "vlan2" to longArrayOf(49_000_000_000, 6_300_000_000),
        "br0" to longArrayOf(7_200_000_000, 51_000_000_000),
        "eth1" to longArrayOf(900_000_000, 6_400_000_000),
        "eth2" to longArrayOf(3_100_000_000, 29_000_000_000),
    )
    private val ipCounters = devices.associate { it.ip to longArrayOf(Random.nextLong(1_000_000_000), Random.nextLong(5_000_000_000)) }

    override suspend fun shell(command: String): String {
        delay(200)
        tick()
        val c = command
        return when {
            "echo @@arp" in c -> devicesOut()
            "echo @@name" in c -> statusOut()
            "_ssid)" in c -> wifiOut()
            "echo @@wan; nvram get wan_iface" in c -> ifaceOut()
            c.trim() == "cat /proc/net/dev" -> netDevOut()
            "ipt_account" in c -> iptOut()
            c.startsWith("for k in ") -> nvramGetOut(c)
            "-A ${RouterApi.BLOCK_CHAIN}" in c -> { macIn(c)?.let { blocked += it }; "ok" }
            RouterApi.BLOCK_CHAIN in c -> { macIn(c)?.let { blocked -= it }; "ok" }
            "nvram set " in c -> { nvramSet(c); "ok" }
            c.startsWith("ping") -> pingOut(c)
            c.startsWith("traceroute") -> traceOut(c)
            c.startsWith("nslookup") -> nslookupOut(c)
            "ether-wake" in c -> "done"
            "reboot" in c -> ""
            else -> "demo mode: commands are not run on a real router\n$ ${c.removeSuffix(" 2>&1")}"
        }
    }

    // ---------- simulation ----------

    private fun wave(seconds: Double, period: Double, phase: Double) =
        0.55 + 0.45 * sin(2 * PI * seconds / period + phase)

    private fun tick() {
        val now = System.nanoTime()
        val dt = (now - lastTick) / 1e9
        lastTick = now
        if (dt <= 0) return
        val t = (now - start) / 1e9
        var totalDown = 0.0
        var totalUp = 0.0
        devices.forEachIndexed { i, d ->
            if (d.mac in blocked) return@forEachIndexed
            val k = wave(t, 20.0 + i * 3, i.toDouble()) * (0.7 + Random.nextDouble() * 0.6)
            val down = d.avgMbit * 1e6 / 8 * k
            val up = down * (0.08 + 0.1 * Random.nextDouble())
            totalDown += down
            totalUp += up
            ipCounters[d.ip]?.let { it[0] += (down * dt).toLong(); it[1] += (up * dt).toLong() }
        }
        fun add(name: String, rx: Double, tx: Double) {
            ifCounters[name]?.let { it[0] += (rx * dt).toLong(); it[1] += (tx * dt).toLong() }
        }
        add("ppp0", totalDown, totalUp)
        add("vlan2", totalDown * 1.02, totalUp * 1.02)
        add("br0", totalUp, totalDown) // LAN direction is reversed
        add("eth1", totalUp * 0.2, totalDown * 0.15)
        add("eth2", totalUp * 0.5, totalDown * 0.55)
        add("lo", 200.0, 200.0)
    }

    // ---------- outputs ----------

    private fun statusOut(): String {
        val uptime = 1_209_600 + (System.nanoTime() - start) / 1_000_000_000 // 14 days
        return """
            @@name
            ${nvram["router_name"]}
            @@model
            ${nvram["t_model_name"]}
            @@fw
            ${nvram["os_version"]}
            @@uptime
            $uptime.42 2400000.00
            @@load
            0.18 0.12 0.09 1/142 4321
            @@mem
            MemTotal:         255488 kB
            MemFree:           98304 kB
            Buffers:            6144 kB
            Cached:            41984 kB
            @@wanip
            ${nvram["wan_ipaddr"]}
            @@wanproto
            ${nvram["wan_proto"]}
            @@wangw
            ${nvram["wan_gateway"]}
            @@dns
            ${nvram["wan_get_dns"]}
            @@lanip
            ${nvram["lan_ipaddr"]}
        """.trimIndent()
    }

    private fun devicesOut(): String {
        val sb = StringBuilder()
        sb.append("@@arp\nIP address       HW type     Flags       HW address            Mask     Device\n")
        devices.forEach { d -> sb.append("${d.ip}  0x1  0x2  ${d.mac.lowercase()}  *  br0\n") }
        sb.append("@@leases\n")
        val exp = System.currentTimeMillis() / 1000 + 80_000
        devices.forEach { d -> sb.append("$exp ${d.mac.lowercase()} ${d.ip} ${d.name} *\n") }
        sb.append("@@bands\neth1 2\neth2 1\n@@wl\n")
        devices.filter { it.wlIf != null && it.mac !in blocked }.forEach { d -> sb.append("${d.wlIf} ${d.mac} ${d.rssi}\n") }
        sb.append("@@blocked\n")
        blocked.forEach { sb.append("MAC $it\n") }
        sb.append("@@static\n").append(nvram["dhcpd_static"]).append('\n')
        return sb.toString()
    }

    private fun wifiOut(): String {
        val sb = StringBuilder()
        for (u in 0..1) {
            val ifn = nvram["wl${u}_ifname"] ?: continue
            sb.append("@@$u\n")
            sb.append("ifname=$ifn\nssid=${nvram["wl${u}_ssid"]}\nradio=${nvram["wl${u}_radio"]}\n")
            sb.append("nband=${nvram["wl${u}_nband"]}\nchannel=${nvram["wl${u}_channel"]}\n")
            sb.append("security=${nvram["wl${u}_security_mode"]}\npsk=${nvram["wl${u}_wpa_psk"]}\n")
            val on = nvram["wl${u}_radio"] != "0"
            sb.append("clients=${if (on) devices.count { it.wlIf == ifn && it.mac !in blocked } else 0}\n")
        }
        return sb.toString()
    }

    private fun ifaceOut() = "@@wan\nppp0\nvlan2\n@@lan\nbr0\n@@wl\neth1 2\neth2 1\n"

    private fun netDevOut(): String {
        val sb = StringBuilder("Inter-|   Receive                                                |  Transmit\n")
        sb.append(" face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed\n")
        ifCounters.forEach { (n, c) ->
            sb.append("%6s: %d %d 0 0 0 0 0 0 %d %d 0 0 0 0 0 0\n".format(n, c[0], c[0] / 1400, c[1], c[1] / 1400))
        }
        return sb.toString()
    }

    private fun iptOut(): String {
        if (nvram["cstats_enable"] != "1") return "@@disabled"
        val sb = StringBuilder()
        ipCounters.forEach { (ip, c) ->
            // bytes_src = sent by the device (upload), bytes_dst = received by it (download)
            sb.append("ip = $ip bytes_src = ${c[1]} 0 0 0 0 packets_src = ${c[1] / 1400} 0 0 0 0 ")
            sb.append("bytes_dst = ${c[0]} 0 0 0 0 packets_dst = ${c[0] / 1400} 0 0 0 0 time = 0\n")
        }
        return sb.toString()
    }

    private fun nvramGetOut(c: String): String {
        val keys = c.removePrefix("for k in ").substringBefore("; do").split(' ').filter { it.isNotBlank() }
        val sb = StringBuilder()
        keys.forEach { k -> sb.append("@@$k\n").append(nvram[k].orEmpty()).append('\n') }
        if ("@@__exists" in c) {
            val wanted = Regex("\\^\\(([^)]*)\\)=").find(c)?.groupValues?.get(1).orEmpty().split('|')
            sb.append("@@__exists\n")
            wanted.filter { it in nvram }.forEach { sb.append(it).append('\n') }
        }
        return sb.toString()
    }

    private val setRe = Regex("nvram set ([a-z0-9_]+)=('(?:[^']|'\\\\'')*'|[^;\\s]*)")

    private fun nvramSet(c: String) {
        // skip the "set security if it was open" fallback inside a case statement
        val plain = c.replace(Regex("case .*?esac;"), "")
        setRe.findAll(plain).forEach { m ->
            var v = m.groupValues[2]
            if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) v = v.substring(1, v.length - 1).replace("'\\''", "'")
            nvram[m.groupValues[1]] = v
        }
    }

    private fun macIn(c: String) = Regex("--mac-source ([0-9A-F:]{17})").find(c)?.groupValues?.get(1)

    private fun hostOf(c: String) = c.split(' ').lastOrNull { it.isNotBlank() && it != "2>&1" } ?: "8.8.8.8"

    private fun pingOut(c: String): String {
        val host = hostOf(c)
        val count = Regex("-c (\\d+)").find(c)?.groupValues?.get(1)?.toIntOrNull() ?: 4
        val ip = if (host.any { it.isLetter() }) "142.250.75.14" else host
        val times = List(count) { 8.0 + Random.nextDouble() * 6 }
        val sb = StringBuilder("PING $host ($ip): 56 data bytes\n")
        times.forEachIndexed { i, t -> sb.append("64 bytes from $ip: seq=$i ttl=116 time=%.3f ms\n".format(t)) }
        sb.append("\n--- $host ping statistics ---\n")
        sb.append("$count packets transmitted, $count packets received, 0% packet loss\n")
        sb.append("round-trip min/avg/max = %.3f/%.3f/%.3f ms\n".format(times.min(), times.average(), times.max()))
        return sb.toString()
    }

    private fun traceOut(c: String): String {
        val host = hostOf(c)
        return """
            traceroute to $host (142.250.75.14), 20 hops max, 38 byte packets
             1  203.0.113.1  2.114 ms
             2  198.51.100.9  4.870 ms
             3  198.51.100.33  6.402 ms
             4  192.0.2.17  8.951 ms
             5  142.250.75.14  ${"%.3f".format(max(9.0, 9 + Random.nextDouble() * 3))} ms
        """.trimIndent()
    }

    private fun nslookupOut(c: String): String {
        val host = hostOf(c)
        return "Server:    127.0.0.1\nAddress 1: 127.0.0.1 localhost\n\nName:      $host\nAddress 1: 142.250.75.14\nAddress 2: 2a00:1450:4001:80b::200e\n"
    }
}

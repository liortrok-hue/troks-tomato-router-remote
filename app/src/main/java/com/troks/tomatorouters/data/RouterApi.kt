package com.troks.tomatorouters.data

import com.troks.tomatorouters.data.RouterClient.Companion.q

data class RouterStatus(
    val routerName: String,
    val model: String,
    val firmware: String,
    val uptimeSeconds: Long,
    val load: String,
    val memTotalKb: Long,
    val memFreeKb: Long,
    val wanIp: String,
    val wanProto: String,
    val wanGateway: String,
    val dns: String,
    val lanIp: String,
)

data class Device(
    val mac: String,
    val ip: String,
    val name: String,
    val iface: String,
    val wireless: Boolean,
    val band: String?,
    val rssi: Int?,
    val leaseExpiresEpoch: Long?,
    val blocked: Boolean,
    /** Reserved (static DHCP) address, or null when the device has none. */
    val staticIp: String? = null,
)

data class WifiNetwork(
    val unit: Int,
    val ifname: String,
    val ssid: String,
    val enabled: Boolean,
    val band: String,
    val channel: String,
    val security: String,
    val password: String,
    val clients: Int,
)

/** High level router operations, all implemented on top of [RouterClient.shell]. */
class RouterApi(val client: RouterShell) {

    suspend fun status(): RouterStatus {
        val out = client.shell(
            """
            echo @@name; nvram get router_name
            echo @@model; nvram get t_model_name
            echo @@fw; nvram get os_version
            echo @@uptime; cat /proc/uptime
            echo @@load; cat /proc/loadavg
            echo @@mem; grep -E '^(MemTotal|MemFree|Buffers|Cached):' /proc/meminfo
            echo @@wanip; nvram get wan_ipaddr
            echo @@wanproto; nvram get wan_proto
            echo @@wangw; nvram get wan_gateway
            echo @@dns; nvram get wan_get_dns
            echo @@lanip; nvram get lan_ipaddr
            """.trimIndent()
        )
        val s = sections(out)
        val mem = s["mem"].orEmpty().lines().associate { line ->
            val parts = line.split(Regex("\\s+"))
            parts[0].trimEnd(':') to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }
        val freeKb = (mem["MemFree"] ?: 0) + (mem["Buffers"] ?: 0) + (mem["Cached"] ?: 0)
        return RouterStatus(
            routerName = s["name"].orEmpty(),
            model = s["model"].orEmpty(),
            firmware = s["fw"].orEmpty(),
            uptimeSeconds = s["uptime"].orEmpty().substringBefore(' ').substringBefore('.').toLongOrNull() ?: 0,
            load = s["load"].orEmpty().split(' ').take(3).joinToString("  "),
            memTotalKb = mem["MemTotal"] ?: 0,
            memFreeKb = freeKb,
            wanIp = s["wanip"].orEmpty(),
            wanProto = s["wanproto"].orEmpty(),
            wanGateway = s["wangw"].orEmpty(),
            dns = s["dns"].orEmpty().replace(' ', '\n').trim(),
            lanIp = s["lanip"].orEmpty(),
        )
    }

    suspend fun devices(): List<Device> {
        val out = client.shell(
            """
            echo @@arp; cat /proc/net/arp
            echo @@leases; cat /var/lib/misc/dnsmasq.leases 2>/dev/null
            echo @@bands; for u in 0 1 2 3; do i=${'$'}(nvram get wl${'$'}{u}_ifname); [ -n "${'$'}i" ] && echo "${'$'}i ${'$'}(nvram get wl${'$'}{u}_nband)"; done
            echo @@wl; for i in ${'$'}(nvram get wl_ifnames); do for m in ${'$'}(wl -i ${'$'}i assoclist 2>/dev/null | awk '{print ${'$'}2}'); do echo "${'$'}i ${'$'}m ${'$'}(wl -i ${'$'}i rssi ${'$'}m 2>/dev/null)"; done; done
            echo @@blocked; iptables -L $BLOCK_CHAIN -n 2>/dev/null | grep -o 'MAC [0-9A-Fa-f:]*'
            echo @@static; nvram get dhcpd_static
            """.trimIndent()
        )
        val s = sections(out)

        val names = mutableMapOf<String, String>()
        val ips = linkedMapOf<String, String>()
        val ifaces = mutableMapOf<String, String>()
        val leases = mutableMapOf<String, Long>()
        val staticIps = mutableMapOf<String, String>()

        // static DHCP: "MAC<IP<NAME<ARP>MAC<IP<NAME<ARP>..." (MAC may be "a,b")
        s["static"].orEmpty().split('>').forEach { entry ->
            val f = entry.split('<')
            if (f.size >= 3) {
                f[0].split(',').forEach { m ->
                    val mac = normMac(m) ?: return@forEach
                    if (f[2].isNotBlank()) names[mac] = f[2]
                    if (f[1].isNotBlank() && !ips.containsKey(mac)) ips[mac] = f[1]
                    if (f[1].isNotBlank()) staticIps[mac] = f[1]
                }
            }
        }
        // dnsmasq leases: "expiry mac ip hostname clientid"
        s["leases"].orEmpty().lines().forEach { line ->
            val f = line.trim().split(Regex("\\s+"))
            if (f.size >= 4) {
                val mac = normMac(f[1]) ?: return@forEach
                ips[mac] = f[2]
                if (f[3] != "*" && !names.containsKey(mac)) names[mac] = f[3]
                f[0].toLongOrNull()?.let { leases[mac] = it }
            }
        }
        // ARP: "IP HWtype Flags HWaddress Mask Device"
        s["arp"].orEmpty().lines().drop(1).forEach { line ->
            val f = line.trim().split(Regex("\\s+"))
            if (f.size >= 6 && f[2] != "0x0") {
                val mac = normMac(f[3]) ?: return@forEach
                if (mac == "00:00:00:00:00:00") return@forEach
                ips[mac] = f[0]
                ifaces[mac] = f[5]
            }
        }
        val bandOfIf = s["bands"].orEmpty().lines().mapNotNull { line ->
            val f = line.trim().split(' ')
            if (f.size >= 2) f[0] to bandName(f[1]) else null
        }.toMap()
        val wireless = mutableMapOf<String, Pair<String, Int?>>()
        s["wl"].orEmpty().lines().forEach { line ->
            val f = line.trim().split(Regex("\\s+"))
            if (f.size >= 2) {
                val mac = normMac(f[1]) ?: return@forEach
                wireless[mac] = f[0] to f.getOrNull(2)?.toIntOrNull()
            }
        }
        val blocked = s["blocked"].orEmpty().lines().mapNotNull { normMac(it.removePrefix("MAC").trim()) }.toSet()

        val all = LinkedHashSet<String>().apply { addAll(ips.keys); addAll(wireless.keys); addAll(blocked) }
        return all.map { mac ->
            val wl = wireless[mac]
            Device(
                mac = mac,
                ip = ips[mac].orEmpty(),
                name = names[mac].orEmpty(),
                iface = wl?.first ?: ifaces[mac].orEmpty(),
                wireless = wl != null,
                band = wl?.first?.let { bandOfIf[it] },
                rssi = wl?.second,
                leaseExpiresEpoch = leases[mac],
                blocked = mac in blocked,
                staticIp = staticIps[mac],
            )
        }.sortedWith(compareBy<Device> { !it.wireless && it.ip.isEmpty() }.thenBy { ipSortKey(it.ip) })
    }

    /**
     * Blocks a device from reaching the internet (FORWARD chain, matches by MAC).
     * Lives until the firewall restarts / the router reboots.
     */
    suspend fun setBlocked(mac: String, block: Boolean) {
        val m = normMac(mac) ?: throw RouterException(Err.BAD_MAC)
        val rule = "-m mac --mac-source $m -j DROP"
        val cmd = if (block) {
            """
            for t in iptables ip6tables; do
              ${'$'}t -N $BLOCK_CHAIN 2>/dev/null
              ${'$'}t -L FORWARD -n 2>/dev/null | grep -q '^$BLOCK_CHAIN ' || ${'$'}t -I FORWARD 1 -j $BLOCK_CHAIN 2>/dev/null
              ${'$'}t -D $BLOCK_CHAIN $rule 2>/dev/null
              ${'$'}t -A $BLOCK_CHAIN $rule 2>/dev/null
            done
            echo ok
            """.trimIndent()
        } else {
            """
            for t in iptables ip6tables; do
              while ${'$'}t -D $BLOCK_CHAIN $rule 2>/dev/null; do :; done
            done
            echo ok
            """.trimIndent()
        }
        client.shell(cmd)
    }

    suspend fun wakeOnLan(mac: String): String {
        val m = normMac(mac) ?: throw RouterException(Err.BAD_MAC)
        val lan = "\$(nvram get lan_ifname)"
        return client.shell("ether-wake -b -i $lan $m 2>&1 || ether-wake -i $lan $m 2>&1; echo done")
    }

    suspend fun wifiNetworks(): List<WifiNetwork> {
        val out = client.shell(
            """
            for u in 0 1 2 3; do
              i=${'$'}(nvram get wl${'$'}{u}_ifname); [ -z "${'$'}i" ] && continue
              echo "@@${'$'}u"
              echo "ifname=${'$'}i"
              echo "ssid=${'$'}(nvram get wl${'$'}{u}_ssid)"
              echo "radio=${'$'}(nvram get wl${'$'}{u}_radio)"
              echo "nband=${'$'}(nvram get wl${'$'}{u}_nband)"
              echo "channel=${'$'}(nvram get wl${'$'}{u}_channel)"
              echo "security=${'$'}(nvram get wl${'$'}{u}_security_mode)"
              echo "psk=${'$'}(nvram get wl${'$'}{u}_wpa_psk)"
              echo "clients=${'$'}(wl -i ${'$'}i assoclist 2>/dev/null | wc -l)"
            done
            """.trimIndent()
        )
        return sections(out).mapNotNull { (unit, body) ->
            val u = unit.toIntOrNull() ?: return@mapNotNull null
            val kv = body.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            WifiNetwork(
                unit = u,
                ifname = kv["ifname"].orEmpty(),
                ssid = kv["ssid"].orEmpty(),
                enabled = kv["radio"] != "0",
                band = bandName(kv["nband"].orEmpty()),
                channel = kv["channel"].orEmpty().ifEmpty { "0" },
                security = kv["security"].orEmpty(),
                password = kv["psk"].orEmpty(),
                clients = kv["clients"]?.trim()?.toIntOrNull() ?: 0,
            )
        }
    }

    /** Turns a radio on/off immediately and saves the setting. */
    suspend fun setRadio(unit: Int, ifname: String, on: Boolean) {
        val v = if (on) 1 else 0
        client.shell(
            "nvram set wl${unit}_radio=$v; nvram commit >/dev/null; " +
                "wl -i ${q(ifname)} radio ${if (on) "on" else "off"}; " +
                "(sleep 1; service wireless restart) >/dev/null 2>&1 &"
        )
    }

    /** Changes SSID and/or WPA password, saves, and restarts wireless. */
    suspend fun updateWifi(unit: Int, ssid: String, password: String?) {
        if (ssid.isEmpty() || ssid.toByteArray().size > 32) throw RouterException(Err.BAD_SSID)
        if (password != null && (password.length !in 8..63)) throw RouterException(Err.BAD_WIFI_PASS)
        val sb = StringBuilder()
        sb.append("nvram set wl${unit}_ssid=${q(ssid)}; ")
        if (password != null) {
            sb.append("nvram set wl${unit}_wpa_psk=${q(password)}; ")
            sb.append("case \"\$(nvram get wl${unit}_security_mode)\" in disabled|\"\") nvram set wl${unit}_security_mode=wpa2_personal; nvram set wl${unit}_crypto=aes;; esac; ")
        }
        sb.append("nvram commit >/dev/null; (sleep 1; service wireless restart) >/dev/null 2>&1 &")
        client.shell(sb.toString())
    }

    suspend fun ping(host: String, count: Int, size: Int): String {
        val h = checkHost(host)
        return client.shell("ping -c ${count.coerceIn(1, 20)} -s ${size.coerceIn(8, 1472)} -W 2 $h 2>&1")
    }

    suspend fun traceroute(host: String): String {
        val h = checkHost(host)
        return client.shell("traceroute -w 2 -q 1 -m 20 $h 2>&1")
    }

    suspend fun nslookup(host: String): String {
        val h = checkHost(host)
        return client.shell("nslookup $h 2>&1")
    }

    suspend fun reboot() {
        client.shell("(sleep 2; reboot) >/dev/null 2>&1 &")
    }

    suspend fun run(command: String): String = client.shell("$command 2>&1")

    companion object {
        const val BLOCK_CHAIN = "TTRBLOCK"
        private val HOST_RE = Regex("^[A-Za-z0-9.:\\-]{1,253}$")

        fun checkHost(host: String): String {
            val h = host.trim()
            if (!HOST_RE.matches(h) || h.startsWith("-")) throw RouterException(Err.BAD_HOST)
            return h
        }

        fun normMac(s: String): String? {
            val m = s.trim().uppercase()
            return if (Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(m)) m else null
        }

        fun bandName(nband: String) = when (nband.trim()) {
            "2" -> "2.4GHz"
            "1" -> "5GHz"
            "4" -> "6GHz"
            else -> "WiFi"
        }

        private fun ipSortKey(ip: String): Long =
            ip.split('.').mapNotNull { it.toLongOrNull() }.let { p ->
                if (p.size == 4) (p[0] shl 24) + (p[1] shl 16) + (p[2] shl 8) + p[3] else Long.MAX_VALUE
            }

        /** Splits "@@key\n...\n@@key2\n..." output into a map. */
        fun sections(out: String): Map<String, String> {
            val map = linkedMapOf<String, String>()
            var key: String? = null
            val sb = StringBuilder()
            for (line in out.lines()) {
                if (line.startsWith("@@")) {
                    key?.let { map[it] = sb.toString().trim() }
                    key = line.removePrefix("@@").trim()
                    sb.clear()
                } else if (key != null) {
                    sb.append(line).append('\n')
                }
            }
            key?.let { map[it] = sb.toString().trim() }
            return map
        }
    }
}

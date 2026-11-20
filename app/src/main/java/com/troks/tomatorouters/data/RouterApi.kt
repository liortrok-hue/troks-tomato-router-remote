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
    /** One line per LAN bridge, like the router: "br0 (LAN0) - 192.168.1.1/24" */
    val lanAddresses: List<String> = emptyList(),
    /** One line per LAN with DHCP on: "br0 (LAN0) - 192.168.1.150 - 192.168.1.230" */
    val dhcpRanges: List<String> = emptyList(),
    val wanState: WanState,
    /** IPv6, empty when the router has none (the rows are then hidden). */
    val wanIp6: String = "",
    val lanIp6: String = "",
    val dns6: String = "",
    /** CPU busy share over a 1 s sample (0..1), or null if it couldn't be measured. */
    val cpuUsage: Float?,
)

/** Internet (WAN) link state, as the router's own Overview reports it under WAN > Status. */
enum class WanState { UP, CONNECTING, DOWN }

/**
 * Prints up / connecting / down using FreshTomato's own rules (httpd asp_wanstatus + check_wanup):
 * DHCP renewing or a pending connect -> connecting; PPP link file pointing at a live pppd, or a
 * static/DHCP address -> up, as long as the WAN interface is running.
 */
private val WAN_STATE_SH = """
    p=${'$'}(nvram get wan_proto); s=down; up=0
    case "${'$'}p" in
      disabled) ;;
      pppoe|pptp|l2tp|ppp3g) l=${'$'}(cat /tmp/ppp/wan_link 2>/dev/null); [ -n "${'$'}l" ] && i=${'$'}(cat /var/run/${'$'}l.pid 2>/dev/null) && [ -n "${'$'}i" ] && [ -d /proc/${'$'}i ] && up=1 ;;
      *) a=${'$'}(nvram get wan_ipaddr); [ -n "${'$'}a" ] && [ "${'$'}a" != 0.0.0.0 ] && up=1 ;;
    esac
    if [ ${'$'}up = 1 ] && o=${'$'}(ip link show "${'$'}(nvram get wan_iface)" 2>/dev/null); then case "${'$'}o" in *LOWER_UP*|*RUNNING*) ;; *) up=0 ;; esac; fi
    if [ "${'$'}p" = dhcp ] && [ -f /var/lib/misc/wan_dhcpc.renewing ]; then s=connecting
    elif [ ${'$'}up = 1 ]; then s=up
    elif [ -f /var/lib/misc/wan.connecting ]; then s=connecting; fi
    echo ${'$'}s
""".trimIndent()

/**
 * IPv6 as on the router's Overview (only when IPv6 is enabled): the global address of the IPv6 WAN
 * interface (which one depends on the IPv6 type, like FreshTomato's get_wan6face), the LAN (br0)
 * global address, and the IPv6 DNS servers (manual ones first, otherwise the ones received).
 */
private val IPV6_SH = """
    s6=${'$'}(nvram get ipv6_service)
    if [ -n "${'$'}s6" ] && [ "${'$'}s6" != disabled ]; then
      case "${'$'}s6" in
        native|native-pd) w6=${'$'}(nvram get wan_iface) ;;
        6to4) w6=v6to4 ;; sit) w6=v6in4 ;; 6rd) w6=6rd ;; 6rd-pd) w6=6rd-pd ;;
        *) w6=${'$'}(nvram get ipv6_ifname) ;;
      esac
      echo @@v6wan; ip -6 addr show dev "${'$'}w6" scope global 2>/dev/null | awk '/inet6/{print ${'$'}2; exit}'
      echo @@v6lan; ip -6 addr show dev "${'$'}(nvram get lan_ifname)" scope global 2>/dev/null | awk '/inet6/{print ${'$'}2; exit}'
      d=${'$'}(nvram get ipv6_dns); [ -z "${'$'}d" ] && d=${'$'}(nvram get ipv6_get_dns)
      echo @@v6dns; echo "${'$'}d"
    fi
""".trimIndent()

private fun parseWanState(s: String?) = when (s?.trim()) {
    "up" -> WanState.UP
    "connecting" -> WanState.CONNECTING
    else -> WanState.DOWN
}

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
    /** Connected right now: associated to WiFi, or a live ARP entry on the router. */
    val online: Boolean = true,
)

data class WifiNetwork(
    /** nvram prefix of this network: "wl0" on FreshTomato, "wifi_phy0iface0" on Tomato64 */
    val id: String,
    /** Tomato64 (mac80211 / hostapd) instead of FreshTomato (Broadcom wl) */
    val tomato64: Boolean,
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
            echo @@cpu; head -n1 /proc/stat; sleep 1; head -n1 /proc/stat
            echo @@mem; grep -E '^(MemTotal|MemFree|Buffers|Cached):' /proc/meminfo
            echo @@wanip; nvram get wan_ipaddr
            echo @@wanproto; nvram get wan_proto
            echo @@wangw; g=${'$'}(nvram get wan_gateway_get); case "${'$'}g" in ''|0.0.0.0) nvram get wan_gateway ;; *) echo "${'$'}g" ;; esac
            echo @@dns; if [ "${'$'}(nvram get wan_dns_auto)" = 1 ]; then nvram get wan_get_dns; else nvram get wan_dns; [ "${'$'}(nvram get wan_addget)" = 1 ] && nvram get wan_get_dns; fi
            echo @@lanip; nvram get lan_ipaddr
            echo @@lans; for i in "" 1 2 3; do echo "${'$'}i|${'$'}(nvram get lan${'$'}{i}_ifname)|${'$'}(nvram get lan${'$'}{i}_ipaddr)|${'$'}(nvram get lan${'$'}{i}_netmask)|${'$'}(nvram get lan${'$'}{i}_proto)|${'$'}(nvram get dhcpd${'$'}{i}_startip)|${'$'}(nvram get dhcpd${'$'}{i}_endip)"; done
            """.trimIndent() + "\n" + IPV6_SH + "\necho @@wanstate\n" + WAN_STATE_SH
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
            // same list as the router's Overview (static + received, per its DNS settings), without the default :53
            dns = s["dns"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
                .map { it.removeSuffix(":53") }.distinct().joinToString("\n"),
            lanIp = s["lanip"].orEmpty(),
            lanAddresses = parseLans(s["lans"]).map { "${it.ifname} (LAN${it.index}) - ${it.ip}/${it.prefix}" },
            dhcpRanges = parseLans(s["lans"]).filter { it.dhcp && it.start.isNotEmpty() }.map { "${it.ifname} (LAN${it.index}) - ${it.start} - ${it.end}" },
            wanState = parseWanState(s["wanstate"]),
            wanIp6 = s["v6wan"].orEmpty().trim().substringBefore('/'),
            lanIp6 = s["v6lan"].orEmpty().trim().substringBefore('/'),
            dns6 = s["v6dns"].orEmpty().split(Regex("\\s+")).filter { ':' in it }.distinct().take(2).joinToString("\n"),
            cpuUsage = parseCpuUsage(s["cpu"]),
        )
    }

    /** Light poll for the top bar's status pill. */
    suspend fun wanState(): WanState = pulse().first

    /** Light poll for the header: internet status + router uptime in seconds (null if unknown). */
    suspend fun pulse(): Pair<WanState, Long?> {
        val s = sections(client.shell("echo @@wanstate\n$WAN_STATE_SH\necho @@uptime; cat /proc/uptime"))
        return parseWanState(s["wanstate"]) to s["uptime"]?.substringBefore(' ')?.substringBefore('.')?.trim()?.toLongOrNull()
    }

    suspend fun devices(): List<Device> {
        val out = client.shell(
            """
            $DETECT_T64
            echo @@arp; cat /proc/net/arp
            echo @@alive
            $PERM_ARP_PROBE_SH
            echo @@leases; cat /var/lib/misc/dnsmasq.leases 2>/dev/null
            echo @@bands
            ${WL_BANDS_SH.replace("\n", "\n            ")}
            echo @@wl
            ${WL_CLIENTS_SH.replace("\n", "\n            ")}
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
        val arpLive = mutableSetOf<String>()

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
        // Permanent entries (flag 0x4, static ARP for static DHCP IPs) never expire, so they say
        // nothing about the device being on; those count as online only if they answered a probe.
        val alive = s["alive"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        s["arp"].orEmpty().lines().drop(1).forEach { line ->
            val f = line.trim().split(Regex("\\s+"))
            val flags = f.getOrNull(2)?.removePrefix("0x")?.toIntOrNull(16) ?: 0
            if (f.size >= 6 && flags != 0) {
                val mac = normMac(f[3]) ?: return@forEach
                if (mac == "00:00:00:00:00:00") return@forEach
                ips[mac] = f[0]
                ifaces[mac] = f[5]
                if (flags and 0x4 == 0 || f[0] in alive) arpLive += mac
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

        // remember who uses WiFi, so they still show as WiFi devices while offline
        DeviceMemory.rememberWireless(wireless.mapValues { (_, w) -> bandOfIf[w.first] })
        val all = LinkedHashSet<String>().apply { addAll(ips.keys); addAll(wireless.keys); addAll(blocked) }
        return all.map { mac ->
            val wl = wireless[mac]
            Device(
                mac = mac,
                ip = ips[mac].orEmpty(),
                name = names[mac].orEmpty(),
                iface = wl?.first ?: ifaces[mac].orEmpty(),
                // on WiFi now; or offline and last seen on WiFi (online but not on WiFi = wired right now)
                wireless = wl != null || (mac !in arpLive && DeviceMemory.wirelessBand(mac) != null),
                band = wl?.first?.let { bandOfIf[it] } ?: if (mac !in arpLive) DeviceMemory.wirelessBand(mac)?.ifEmpty { null } else null,
                rssi = wl?.second,
                leaseExpiresEpoch = leases[mac],
                blocked = mac in blocked,
                staticIp = staticIps[mac],
                online = wl != null || mac in arpLive,
            )
        }.sortedWith(DEVICE_ORDER)
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

    /**
     * WiFi networks on either firmware:
     *  - FreshTomato: one per radio, wlN_ssid / wlN_radio / wlN_wpa_psk..., clients from `wl assoclist`
     *  - Tomato64: every access-point interface wifi_phyNifaceM_* (essid / enable / key / encryption),
     *    band and channel on the radio (wifi_phyN_band / _channel), clients from `iw station dump`
     */
    suspend fun wifiNetworks(): List<WifiNetwork> {
        val out = client.shell(
            """
            $DETECT_T64
            if [ -n "${'$'}T64" ]; then
              for p in 0 1 2 3 4 5 6 7; do
                [ ${'$'}p -ge ${'$'}T64N ] && break
                n=${'$'}(nvram get wifi_phy${'$'}{p}_ifaces); [ -z "${'$'}n" ] && continue
                j=0
                while [ ${'$'}j -lt ${'$'}n ]; do
                  t=wifi_phy${'$'}{p}iface${'$'}{j}
                  m=${'$'}(nvram get ${'$'}{t}_mode)
                  if [ -z "${'$'}m" ] || [ "${'$'}m" = ap ]; then
                    i=${'$'}(nvram get ${'$'}{t}_ifname); [ -z "${'$'}i" ] && i=phy${'$'}p-ap${'$'}j
                    echo "@@${'$'}t"
                    echo "t64=1"
                    echo "ifname=${'$'}i"
                    echo "ssid=${'$'}(nvram get ${'$'}{t}_essid)"
                    echo "radio=${'$'}(nvram get ${'$'}{t}_enable)"
                    echo "nband=${'$'}(nvram get wifi_phy${'$'}{p}_band)"
                    echo "channel=${'$'}(nvram get wifi_phy${'$'}{p}_channel)"
                    echo "security=${'$'}(nvram get ${'$'}{t}_encryption)"
                    echo "psk=${'$'}(nvram get ${'$'}{t}_key)"
                    echo "clients=${'$'}(iw dev ${'$'}i station dump 2>/dev/null | grep -c '^Station')"
                  fi
                  j=${'$'}((j+1))
                done
              done
            else
              for u in 0 1 2 3; do
                i=${'$'}(nvram get wl${'$'}{u}_ifname); [ -z "${'$'}i" ] && continue
                echo "@@wl${'$'}u"
                echo "ifname=${'$'}i"
                echo "ssid=${'$'}(nvram get wl${'$'}{u}_ssid)"
                echo "radio=${'$'}(nvram get wl${'$'}{u}_radio)"
                echo "nband=${'$'}(nvram get wl${'$'}{u}_nband)"
                echo "channel=${'$'}(nvram get wl${'$'}{u}_channel)"
                echo "security=${'$'}(nvram get wl${'$'}{u}_security_mode)"
                echo "psk=${'$'}(nvram get wl${'$'}{u}_wpa_psk)"
                echo "clients=${'$'}(wl -i ${'$'}i assoclist 2>/dev/null | wc -l)"
              done
            fi
            """.trimIndent()
        )
        return sections(out).mapNotNull { (id, body) ->
            if (!WIFI_ID.matches(id)) return@mapNotNull null
            val kv = body.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val t64 = kv["t64"] == "1"
            WifiNetwork(
                id = id,
                tomato64 = t64,
                ifname = kv["ifname"].orEmpty(),
                ssid = kv["ssid"].orEmpty(),
                enabled = if (t64) kv["radio"] == "1" else kv["radio"] != "0",
                band = bandName(kv["nband"].orEmpty()),
                channel = kv["channel"].orEmpty().let { if (it.isEmpty() || it == "auto") "0" else it },
                security = kv["security"].orEmpty(),
                password = kv["psk"].orEmpty(),
                clients = kv["clients"]?.trim()?.toIntOrNull() ?: 0,
            )
        }
    }

    /** Turns a WiFi network on/off immediately and saves the setting. */
    suspend fun setRadio(n: WifiNetwork, on: Boolean) {
        require(WIFI_ID.matches(n.id))
        val v = if (on) 1 else 0
        client.shell(
            if (n.tomato64) {
                "nvram set ${n.id}_enable=$v; nvram commit >/dev/null; (sleep 1; service wifi reload) >/dev/null 2>&1 &"
            } else {
                "nvram set ${n.id}_radio=$v; nvram commit >/dev/null; " +
                    "wl -i ${q(n.ifname)} radio ${if (on) "on" else "off"}; " +
                    "(sleep 1; service wireless restart) >/dev/null 2>&1 &"
            }
        )
    }

    /** Changes SSID and/or WPA password, saves, and restarts / reloads WiFi. */
    suspend fun updateWifi(n: WifiNetwork, ssid: String, password: String?) {
        require(WIFI_ID.matches(n.id))
        if (ssid.isEmpty() || ssid.toByteArray().size > 32) throw RouterException(Err.BAD_SSID)
        if (password != null && (password.length !in 8..63)) throw RouterException(Err.BAD_WIFI_PASS)
        val p = n.id
        val sb = StringBuilder()
        if (n.tomato64) {
            sb.append("nvram set ${p}_essid=${q(ssid)}; ")
            if (password != null) {
                sb.append("nvram set ${p}_key=${q(password)}; ")
                // an open network gets WPA2-PSK when a password is set (owe / none have no key)
                sb.append("case \"\$(nvram get ${p}_encryption)\" in none|owe|\"\") nvram set ${p}_encryption=psk2; nvram set ${p}_cipher=auto;; esac; ")
            }
            sb.append("nvram commit >/dev/null; (sleep 1; service wifi reload) >/dev/null 2>&1 &")
        } else {
            sb.append("nvram set ${p}_ssid=${q(ssid)}; ")
            if (password != null) {
                sb.append("nvram set ${p}_wpa_psk=${q(password)}; ")
                sb.append("case \"\$(nvram get ${p}_security_mode)\" in disabled|\"\") nvram set ${p}_security_mode=wpa2_personal; nvram set ${p}_crypto=aes;; esac; ")
            }
            sb.append("nvram commit >/dev/null; (sleep 1; service wireless restart) >/dev/null 2>&1 &")
        }
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

        /** Devices A–Z by name (any language, ignoring case); unnamed ones last, by IP. */
        private val NAME_COLLATOR: java.text.Collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.SECONDARY }
        val DEVICE_ORDER: Comparator<Device> = compareBy<Device> { it.name.isBlank() }
            .thenComparator { a, b -> NAME_COLLATOR.compare(a.name.trim(), b.name.trim()) }
            .thenBy { ipSortKey(it.ip) }

        /**
         * Probes every permanent (static) ARP entry in parallel and prints the IPs that answer.
         * arping gets a reply even from PCs whose firewall drops ping; ping is the fallback.
         * One line, so it can be dropped into an indented script.
         */
        const val PERM_ARP_PROBE_SH =
            "A=\$(command -v arping); awk 'NR>1 && \$3!=\"0x0\" && \$3!=\"0x2\" {print \$1, \$6}' /proc/net/arp | " +
                "{ while read i d; do ( if [ -n \"\$A\" ]; then arping -q -f -c1 -w1 -I \$d \$i; else ping -c1 -W1 \$i; fi " +
                ">/dev/null 2>&1 && echo \$i ) & done; wait; }"
        private val HOST_RE = Regex("^[A-Za-z0-9.:\\-]{1,253}$")

        fun checkHost(host: String): String {
            val h = host.trim()
            if (!HOST_RE.matches(h) || h.startsWith("-")) throw RouterException(Err.BAD_HOST)
            return h
        }

        /** "wl0" (FreshTomato) or "wifi_phy0iface0" (Tomato64) */
        private val WIFI_ID = Regex("^(wl[0-3]|wifi_phy[0-7]iface[0-9]+)$")

        fun normMac(s: String): String? {
            val m = s.trim().uppercase()
            return if (Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(m)) m else null
        }

        /** FreshTomato wlX_nband (2 / 1 / 4) or Tomato64 wifi_phyX_band (2g / 5g / 6g). */
        fun bandName(nband: String) = when (nband.trim()) {
            "2", "2g" -> "2.4GHz"
            "1", "5g" -> "5GHz"
            "4", "6g" -> "6GHz"
            "60g" -> "60GHz"
            else -> "WiFi"
        }

        /**
         * Shell prelude that sets T64=1 on Tomato64. Tomato64 runs WiFi with mac80211 / hostapd:
         * no Broadcom `wl` tool, settings in wifi_phyN* / wifi_phyNifaceM_* instead of wlN_*.
         */
        const val DETECT_T64 = "T64=; if ! command -v wl >/dev/null 2>&1 && [ -n \"\$(nvram get wifi_phy0_ifaces)\" ]; then T64=1; fi; " +
            // T64N = radios that really exist, as on Tomato64's own WiFi page
            // (nvram also keeps default settings for radios the router doesn't have)
            "T64N=\$(nvram get wifi_phy_count); if [ -z \"\$T64N\" ] || [ \"\$T64N\" = 0 ]; then T64N=\$(nvram get wifi_phy_count_expected); fi; [ -z \"\$T64N\" ] && T64N=8"

        /**
         * Prints "ifname band" for every wireless (access point) interface, on either firmware.
         * Needs [DETECT_T64] first.
         */
        val WL_BANDS_SH = """
            if [ -n "${'$'}T64" ]; then
              for p in 0 1 2 3 4 5 6 7; do [ ${'$'}p -ge ${'$'}T64N ] && break; n=${'$'}(nvram get wifi_phy${'$'}{p}_ifaces); [ -z "${'$'}n" ] && continue; b=${'$'}(nvram get wifi_phy${'$'}{p}_band); j=0
                while [ ${'$'}j -lt ${'$'}n ]; do i=${'$'}(nvram get wifi_phy${'$'}{p}iface${'$'}{j}_ifname); [ -z "${'$'}i" ] && i=phy${'$'}p-ap${'$'}j; echo "${'$'}i ${'$'}b"; j=${'$'}((j+1)); done
              done
            else
              for u in 0 1 2 3; do i=${'$'}(nvram get wl${'$'}{u}_ifname); [ -n "${'$'}i" ] && echo "${'$'}i ${'$'}(nvram get wl${'$'}{u}_nband)"; done
            fi
        """.trimIndent()

        /** Prints "ifname MAC rssi" for every associated wireless client, on either firmware. */
        val WL_CLIENTS_SH = """
            if [ -n "${'$'}T64" ]; then
              for i in ${'$'}(iw dev 2>/dev/null | awk '/Interface/{print ${'$'}2}'); do
                iw dev ${'$'}i station dump 2>/dev/null | awk -v i=${'$'}i '/^Station/{m=${'$'}2} /^[ \t]*signal:/{print i, m, ${'$'}2}'
              done
            else
              for i in ${'$'}(nvram get wl_ifnames); do for m in ${'$'}(wl -i ${'$'}i assoclist 2>/dev/null | awk '{print ${'$'}2}'); do echo "${'$'}i ${'$'}m ${'$'}(wl -i ${'$'}i rssi ${'$'}m 2>/dev/null)"; done; done
            fi
        """.trimIndent()

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

/** Busy share between two "cpu ..." lines of /proc/stat (idle + iowait count as idle). */
internal fun parseCpuUsage(text: String?): Float? {
    val samples = text.orEmpty().lines().filter { it.startsWith("cpu ") }
        .map { l -> l.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() } }
    if (samples.size < 2 || samples[0].size < 4 || samples[1].size < 4) return null
    val (a, b) = samples
    val idle = (b[3] + b.getOrElse(4) { 0 }) - (a[3] + a.getOrElse(4) { 0 })
    val total = b.sum() - a.sum()
    if (total <= 0) return null
    return (1f - idle.toFloat() / total).coerceIn(0f, 1f)
}

internal data class LanInfo(val index: Int, val ifname: String, val ip: String, val prefix: Int, val dhcp: Boolean, val start: String, val end: String)

/** Lines "i|ifname|ip|netmask|proto|dhcp start|dhcp end" for LAN0..3; unused bridges are skipped. */
internal fun parseLans(text: String?): List<LanInfo> = text.orEmpty().lines().mapNotNull { line ->
    val f = line.trim().split('|')
    if (f.size < 7 || f[1].isBlank() || f[2].isBlank()) return@mapNotNull null
    val prefix = f[3].split('.').mapNotNull { it.toIntOrNull() }.sumOf { Integer.bitCount(it and 0xFF) }
    LanInfo(f[0].toIntOrNull() ?: 0, f[1], f[2], prefix, f[4] == "dhcp", f[5], f[6])
}

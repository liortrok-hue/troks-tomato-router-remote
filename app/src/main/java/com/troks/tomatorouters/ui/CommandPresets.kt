package com.troks.tomatorouters.ui

/**
 * Ready-made, read-only router commands for Tools > Command.
 * Each has a label, the shell command (works on FreshTomato and Tomato64 BusyBox),
 * and a summary for the result line (text + whether it looks fine). null = use the generic summary.
 */
class CommandPreset(
    val label: (Strings) -> String,
    val command: String,
    val summary: (out: String, s: Strings) -> Pair<String, Boolean>?,
)

private fun firstLine(out: String) = out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

private fun firstNumber(out: String) = Regex("""\d+""").find(out)?.value?.toLongOrNull()

val COMMAND_PRESETS = listOf(
    CommandPreset({ it.cmdWanIp }, "nvram get wan_ipaddr") { out, s ->
        val ip = firstLine(out)
        if (ip.isEmpty() || ip == "0.0.0.0" || ip == s.noOutput) s.notConnected to false else "⁦$ip⁩" to true
    },
    CommandPreset({ it.cmdFirmware }, "nvram get os_version") { out, s ->
        firstLine(out).takeIf { it.isNotEmpty() && it != s.noOutput }?.let { "⁦$it⁩" to true }
    },
    CommandPreset({ it.cmdUptime }, "uptime") { out, _ ->
        // " 18:22:01 up 8 days,  2:02,  load average: 0.00, 0.01, 0.05"
        Regex("""up\s+(.*?),\s+load""").find(out)?.groupValues?.get(1)
            ?.replace(Regex("""\s+"""), " ")?.let { "⁦up $it⁩" to true }
    },
    CommandPreset({ it.cmdMemory }, "free") { out, s ->
        // "Mem:  255488  101234  154254 ..." (kB)
        val f = out.lines().firstOrNull { it.trim().startsWith("Mem:") }?.trim()?.split(Regex("""\s+"""))
        val total = f?.getOrNull(1)?.toLongOrNull()
        val used = f?.getOrNull(2)?.toLongOrNull()
        if (total == null || used == null || total == 0L) null
        else s.memUsed(used / 1024, total / 1024) to (used * 100 / total < 90)
    },
    CommandPreset(
        { it.cmdTemp },
        // FreshTomato ARM: same source as its Overview page (sysinfo-helper → "cpu Temp: 75");
        // otherwise /proc/dmu/temperature (older Broadcom) or thermal zones in milli-°C (Tomato64 / x86)
        "/usr/sbin/sysinfo-helper >/dev/null 2>&1; grep -i 'cpu temp' /tmp/sysinfo-helper 2>/dev/null" +
            " || cat /proc/dmu/temperature 2>/dev/null || cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null",
    ) { out, s ->
        val n = firstNumber(out) ?: return@CommandPreset s.toolFailed to false
        val c = if (n > 1000) n / 1000 else n
        s.tempC(c) to (c < 85)
    },
    CommandPreset(
        { it.cmdConnections },
        "cat /proc/sys/net/netfilter/nf_conntrack_count 2>/dev/null || cat /proc/sys/net/ipv4/netfilter/ip_conntrack_count",
    ) { out, s -> firstNumber(out)?.let { s.countConnections(it.toInt()) to true } },
    CommandPreset({ it.cmdLeases }, "cat /var/lib/misc/dnsmasq.leases 2>/dev/null") { out, s ->
        val n = if (out == s.noOutput) 0 else out.lines().count { it.isNotBlank() }
        s.countLeases(n) to true
    },
    CommandPreset({ it.cmdProcesses }, "ps") { out, s ->
        s.countProcesses((out.lines().count { it.isNotBlank() } - 1).coerceAtLeast(0)) to true
    },
    CommandPreset({ it.cmdRoutes }, "route -n") { out, s ->
        s.countRoutes(out.lines().count { Regex("""^\d""").containsMatchIn(it.trim()) }) to true
    },
    CommandPreset({ it.cmdInterfaces }, "ifconfig") { out, s ->
        // each interface block starts at column 0 ("br0       Link encap:...")
        s.countInterfaces(out.lines().count { it.isNotEmpty() && !it[0].isWhitespace() }) to true
    },
)

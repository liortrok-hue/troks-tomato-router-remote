package com.troks.tomatorouters.data

import java.time.LocalDate

/*
 * Bandwidth history and the system log, read the same way the router's own pages do:
 *  - rstats (Bandwidth Monitoring) writes /var/spool/rstats-speed.js on SIGUSR1 (last 24 h,
 *    one sample per minute) and /var/spool/rstats-history.js on SIGUSR2 (daily + monthly totals)
 *  - syslog lives in /var/log/messages (or the custom path), rotated copy in messages.0
 */

/** Bytes moved per minute over the last 24 hours, oldest first. rx = received on that interface. */
data class SpeedHistory(val rx: List<Long>, val tx: List<Long>)

/** Traffic for one day or month. For the WAN, rx is download and tx is upload. */
data class TrafficTotal(val date: LocalDate, val rxBytes: Long, val txBytes: Long)

data class TrafficHistory(val daily: List<TrafficTotal>, val monthly: List<TrafficTotal>)

/** null when Bandwidth Monitoring (rstats) is turned off on the router. */
class Last24h(val wan: Set<String>, val ifaces: Map<String, SpeedHistory>)

private fun rstatsDump(sig: String, file: String) = """
    if [ "${'$'}(nvram get rstats_enable)" != 1 ]; then echo @@off; else
      f=$file; rm -f ${'$'}f; killall -$sig rstats 2>/dev/null; i=0
      while [ ! -f ${'$'}f ] && [ ${'$'}i -lt 20 ]; do usleep 250000 2>/dev/null || sleep 1; i=${'$'}((i+1)); done
      echo @@wan; nvram get wan_iface; nvram get wan_ifname
      echo @@data; cat ${'$'}f 2>/dev/null; rm -f ${'$'}f
    fi
""".trimIndent()

suspend fun RouterApi.last24h(): Last24h? {
    val out = client.shell(rstatsDump("USR1", "/var/spool/rstats-speed.js"))
    if (out.lineSequence().any { it.trim() == "@@off" }) return null
    val s = RouterApi.sections(out)
    return Last24h(
        wan = s["wan"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        ifaces = parseSpeedJs(s["data"].orEmpty()),
    )
}

suspend fun RouterApi.trafficHistory(): TrafficHistory? {
    val out = client.shell(rstatsDump("USR2", "/var/spool/rstats-history.js"))
    if (out.lineSequence().any { it.trim() == "@@off" }) return null
    return parseHistoryJs(RouterApi.sections(out)["data"].orEmpty())
}

private val SPEED_IFACE = Regex("'([^'_][^']*)'\\s*:\\s*\\{")
private fun numbers(js: String, key: String): List<Long> =
    Regex("\\b$key\\s*:\\s*\\[([^\\]]*)]").find(js)?.groupValues?.get(1)
        ?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

/** speed_history = {'ppp0': { up: 1, rx: [...], rx_avg: .., tx: [...], ...}, '_next': 30}; */
internal fun parseSpeedJs(js: String): Map<String, SpeedHistory> {
    val heads = SPEED_IFACE.findAll(js).toList()
    return heads.mapIndexed { i, m ->
        val body = js.substring(m.range.last, heads.getOrNull(i + 1)?.range?.first ?: js.length)
        m.groupValues[1] to SpeedHistory(numbers(body, "rx"), numbers(body, "tx"))
    }.filter { it.second.rx.isNotEmpty() }.toMap()
}

private val ENTRY = Regex("\\[0x([0-9a-fA-F]+),0x([0-9a-fA-F]+),0x([0-9a-fA-F]+)]")

/** daily_history = [[0xXTIME,0xRX_KB,0xTX_KB],...]; monthly_history = [...]; */
internal fun parseHistoryJs(js: String): TrafficHistory {
    fun list(name: String, monthly: Boolean): List<TrafficTotal> {
        val start = js.indexOf("${name}_history")
        if (start < 0) return emptyList()
        val end = js.indexOf("];", start).let { if (it < 0) js.length else it + 1 }
        return ENTRY.findAll(js.substring(start, end)).mapNotNull { m ->
            val x = m.groupValues[1].toLong(16)
            // xtime = (tm_year << 16) | (tm_mon << 8) | tm_mday  (year since 1900, month 0-11)
            val year = (x shr 16).toInt() + 1900
            val month = ((x shr 8) and 0xFF).toInt() + 1
            val day = if (monthly) 1 else (x and 0xFF).toInt()
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let {
                TrafficTotal(it, m.groupValues[2].toLong(16) * 1024, m.groupValues[3].toLong(16) * 1024)
            }
        }.sortedBy { it.date }.toList()
    }
    return TrafficHistory(list("daily", false), list("monthly", true))
}

// ---------------- system log ----------------

enum class LogLevel { EMERG, ALERT, CRIT, ERR, WARNING, NOTICE, INFO, DEBUG }

data class LogLine(
    val date: String,
    val facility: String,
    val level: LogLevel,
    val process: String,
    val message: String,
)

/** Newest last. null when internal logging is turned off on the router. */
suspend fun RouterApi.syslog(maxLines: Int = 1000): List<LogLine>? {
    val out = client.shell(
        """
        if [ "${'$'}(nvram get log_file)" != 1 ]; then echo @@off; else
          f=${'$'}(head -n1 /etc/syslogd.cfg 2>/dev/null | awk '{print ${'$'}3}')
          case "${'$'}f" in /*) ;; *) if [ "${'$'}(nvram get log_file_custom)" = 1 ]; then f=${'$'}(nvram get log_file_path); else f=/var/log/messages; fi ;; esac
          echo @@log; cat "${'$'}f.0" "${'$'}f" 2>/dev/null | tail -n $maxLines
        fi
        """.trimIndent()
    )
    if (out.lineSequence().any { it.trim() == "@@off" }) return null
    return RouterApi.sections(out)["log"].orEmpty().lines().mapNotNull(::parseLogLine)
}

private val LOG_DATE = Regex("^([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(.*)$")
private val FAC_LEVEL = Regex("^([a-z0-9]+)\\.([a-z]+)$")

/** "Nov 17 21:18:26 Troks daemon.info dnsmasq-dhcp[7297]: DHCPACK(br0) ..." */
internal fun parseLogLine(line: String): LogLine? {
    if (line.isBlank()) return null
    val m = LOG_DATE.find(line) ?: return LogLine("", "", LogLevel.INFO, "", line.trim())
    var rest = m.groupValues[2]
    // optional hostname, then facility.level
    val tokens = rest.split(' ', limit = 3)
    val fi = tokens.indexOfFirst { FAC_LEVEL.matches(it) }.takeIf { it in 0..1 }
    var facility = ""
    var level = LogLevel.INFO
    if (fi != null) {
        val fl = FAC_LEVEL.find(tokens[fi])!!
        facility = fl.groupValues[1]
        level = when (fl.groupValues[2]) {
            "emerg", "panic" -> LogLevel.EMERG
            "alert" -> LogLevel.ALERT
            "crit" -> LogLevel.CRIT
            "err", "error" -> LogLevel.ERR
            "warn", "warning" -> LogLevel.WARNING
            "notice" -> LogLevel.NOTICE
            "debug" -> LogLevel.DEBUG
            else -> LogLevel.INFO
        }
        rest = tokens.drop(fi + 1).joinToString(" ")
    }
    val colon = rest.indexOf(": ")
    val (process, message) = if (colon in 1..60 && ' ' !in rest.substring(0, colon)) {
        rest.substring(0, colon) to rest.substring(colon + 2)
    } else "" to rest
    return LogLine(m.groupValues[1].replace(Regex("\\s+"), " "), facility, level, process, message)
}

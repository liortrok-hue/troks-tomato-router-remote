package com.troks.tomatorouters.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.Counters
import com.troks.tomatorouters.data.IfaceInfo
import com.troks.tomatorouters.data.IpCounters
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.enableIpTraffic
import com.troks.tomatorouters.data.ifaceInfo
import com.troks.tomatorouters.data.ipTraffic
import com.troks.tomatorouters.data.netDev
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

private const val POLL_MS = 2000L
private const val HISTORY = 60 // samples = 2 minutes

/** One rate sample in bytes per second. */
data class Rate(val down: Double, val up: Double)

@Composable
fun MonitorScreen(api: RouterApi, toast: (String) -> Unit) {
    val s = LocalStrings.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            SegmentedChoice(listOf(s.mon.bandwidth, s.mon.ipTraffic), page) { page = it }
        }
        when (page) {
            0 -> BandwidthPage(api)
            else -> IpTrafficPage(api, toast)
        }
    }
}

// ---------------- Bandwidth (bwm-realtime) ----------------

@Composable
private fun BandwidthPage(api: RouterApi) {
    val s = LocalStrings.current
    var info by remember { mutableStateOf<IfaceInfo?>(null) }
    val history = remember { mutableStateMapOf<String, List<Rate>>() }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        var last: Map<String, Counters>? = null
        var lastAt = 0L
        while (true) {
            try {
                if (info == null) info = api.ifaceInfo()
                val now = api.netDev()
                val t = System.nanoTime()
                val prev = last
                if (prev != null) {
                    val dt = (t - lastAt) / 1e9
                    now.forEach { (name, c) ->
                        val p = prev[name] ?: return@forEach
                        val drx = c.rx - p.rx
                        val dtx = c.tx - p.tx
                        if (drx < 0 || dtx < 0 || dt <= 0) return@forEach // counter reset/wrap
                        // for WAN, "received" is download; for LAN/WiFi the router *sends* the downloads
                        val isWan = info?.wan?.contains(name) == true
                        val r = if (isWan) Rate(drx / dt, dtx / dt) else Rate(dtx / dt, drx / dt)
                        history[name] = (history[name].orEmpty() + r).takeLast(HISTORY)
                    }
                }
                last = now
                lastAt = t
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            delay(POLL_MS)
        }
    }

    val inf = info
    // interfaces worth showing: WAN, LAN bridge, wireless radios, physical ports; skip loopback & helpers
    val ifaces = history.keys.filter { n ->
        n != "lo" && !n.startsWith("imq") && !n.startsWith("ifb") && !n.startsWith("teql") &&
            !n.startsWith("dpsta") && !n.startsWith("gre") && !n.startsWith("sit") && !n.startsWith("ip6tnl")
    }.sortedWith(compareBy({ iface -> ifaceOrder(iface, inf) }, { it }))
    val current = selected?.takeIf { it in history } ?: ifaces.firstOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { ErrorBox(it) {} }
        if (current == null) {
            if (error == null) {
                Text(s.mon.collecting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LoadingBox()
            }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ifaces.forEach { n ->
                FilterChip(
                    selected = n == current,
                    onClick = { selected = n },
                    label = { Text(ifaceLabel(n, inf, s)) },
                    leadingIcon = { Icon(ifaceIcon(n, inf), null, Modifier.size(18.dp)) },
                )
            }
        }
        val h = history[current].orEmpty()
        RateCard(ifaceLabel(current, inf, s), ifaceIcon(current, inf), h)

        SectionCard(s.mon.allInterfaces, icon = Icons.Outlined.QueryStats) {
            ifaces.forEach { n ->
                val r = history[n]?.lastOrNull() ?: Rate(0.0, 0.0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ifaceIcon(n, inf), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(ifaceLabel(n, inf, s), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    RatePair(r)
                }
            }
        }
    }
}

private fun ifaceOrder(n: String, inf: IfaceInfo?) = when {
    inf == null -> 5
    n in inf.wan -> 0
    n == inf.lan -> 1
    n in inf.wireless -> 2
    n.startsWith("br") -> 3
    n.startsWith("vlan") -> 4
    else -> 5
}

private fun ifaceLabel(n: String, inf: IfaceInfo?, s: Strings) = when {
    inf == null -> n
    n in inf.wan -> "${s.mon.wan} · $n"
    n == inf.lan -> "${s.mon.lan} · $n"
    n in inf.wireless -> "WiFi ${inf.wireless[n]} · $n"
    else -> n
}

private fun ifaceIcon(n: String, inf: IfaceInfo?): ImageVector = when {
    inf == null -> Icons.Outlined.SettingsEthernet
    n in inf.wan -> Icons.Outlined.Public
    n == inf.lan -> Icons.Outlined.Lan
    n in inf.wireless -> Icons.Outlined.Wifi
    else -> Icons.Outlined.SettingsEthernet
}

@Composable
private fun RateCard(title: String, icon: ImageVector, h: List<Rate>) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val now = h.lastOrNull() ?: Rate(0.0, 0.0)
    val peakDown = h.maxOfOrNull { it.down } ?: 0.0
    val peakUp = h.maxOfOrNull { it.up } ?: 0.0
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                LiveBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigRate(Icons.Outlined.ArrowDownward, s.mon.download, now.down, cs.primary, Modifier.weight(1f))
                BigRate(Icons.Outlined.ArrowUpward, s.mon.upload, now.up, cs.tertiary, Modifier.weight(1f))
            }
            RateChart(h, Modifier.fillMaxWidth().height(160.dp))
            Row {
                Text(s.mon.lastMinutes, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    "${s.mon.peak}: ↓ ${formatRate(peakDown)}  ↑ ${formatRate(peakUp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BigRate(icon: ImageVector, label: String, bytesPerSec: Double, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatRate(bytesPerSec), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun LiveBadge() {
    val s = LocalStrings.current
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(6.dp))
            Text(s.mon.live, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun RatePair(r: Rate) {
    val cs = MaterialTheme.colorScheme
    // numbers + arrows always left-to-right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(14.dp), tint = cs.primary)
            Text(formatRate(r.down), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
            Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(14.dp), tint = cs.tertiary)
            Text(formatRate(r.up), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
        }
    }
}

/** Line chart of download (filled) and upload. Time always flows left to right. */
@Composable
fun RateChart(h: List<Rate>, modifier: Modifier = Modifier) {
    val down = MaterialTheme.colorScheme.primary
    val up = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (h.maxOfOrNull { maxOf(it.down, it.up) } ?: 0.0).coerceAtLeast(1250.0) * 1.15
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            if (h.size < 2) return@Canvas
            val step = size.width / (HISTORY - 1)
            val x0 = size.width - step * (h.size - 1)
            fun y(v: Double) = (size.height - (v / max * size.height)).toFloat()
            fun line(sel: (Rate) -> Double) = Path().apply {
                h.forEachIndexed { i, r -> if (i == 0) moveTo(x0, y(sel(r))) else lineTo(x0 + i * step, y(sel(r))) }
            }
            val downLine = line { it.down }
            val fill = Path().apply {
                addPath(downLine)
                lineTo(size.width, size.height)
                lineTo(x0, size.height)
                close()
            }
            drawPath(fill, down.copy(alpha = 0.15f))
            drawPath(downLine, down, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(line { it.up }, up, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                formatRate(max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/**
 * Formats bytes/second as bits/second (how ISPs quote speeds).
 * Wrapped in a left-to-right isolate so "36.3 Mbit/s" doesn't get reordered in Hebrew/Arabic text.
 */
fun formatRate(bytesPerSec: Double): String = "\u2066" + formatRateRaw(bytesPerSec) + "\u2069"

private fun formatRateRaw(bytesPerSec: Double): String {
    val bits = bytesPerSec * 8
    return when {
        bits >= 1e9 -> String.format(Locale.US, "%.2f Gbit/s", bits / 1e9)
        bits >= 1e6 -> String.format(Locale.US, "%.1f Mbit/s", bits / 1e6)
        bits >= 1e3 -> String.format(Locale.US, "%.0f kbit/s", bits / 1e3)
        else -> String.format(Locale.US, "%.0f bit/s", bits)
    }
}

// ---------------- IP traffic (ipt-realtime) ----------------

@Composable
private fun IpTrafficPage(api: RouterApi, toast: (String) -> Unit) {
    val s = LocalStrings.current
    val m = s.mon
    val cs = MaterialTheme.colorScheme
    var disabled by remember { mutableStateOf(false) }
    var rates by remember { mutableStateOf<Map<String, Rate>?>(null) }
    // per-IP history, kept for every device so a newly followed one already has a chart
    val history = remember { mutableStateMapOf<String, List<Rate>>() }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var confirmEnable by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }
    // the one device pinned at the top; tapping another row replaces it
    var followed by rememberSaveable { mutableStateOf<String?>(null) }
    // row order is refreshed every few polls (by average speed), not every sample,
    // so rows don't jump around under the user's finger
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var polls by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(restartKey) {
        runCatching { api.devices() }.getOrNull()?.let { list ->
            names = list.filter { it.ip.isNotBlank() && it.name.isNotBlank() }.associate { it.ip to it.name }
        }
        var last: Map<String, IpCounters>? = null
        var lastAt = 0L
        while (true) {
            try {
                val now = api.ipTraffic()
                disabled = now == null
                if (now != null) {
                    val t = System.nanoTime()
                    val prev = last
                    if (prev != null) {
                        val dt = (t - lastAt) / 1e9
                        val r = now.mapNotNull { (ip, c) ->
                            val p = prev[ip] ?: return@mapNotNull null
                            val d = c.download - p.download
                            val u = c.upload - p.upload
                            if (d < 0 || u < 0 || dt <= 0) null else ip to Rate(d / dt, u / dt)
                        }.toMap()
                        rates = r
                        r.forEach { (ip, rate) -> history[ip] = (history[ip].orEmpty() + rate).takeLast(HISTORY) }
                        if (polls % 8 == 0 || r.keys.any { it !in order }) {
                            order = r.keys.sortedByDescending { ip ->
                                history[ip].orEmpty().takeLast(8).sumOf { it.down + it.up }
                            }
                        }
                        polls++
                    }
                    last = now
                    lastAt = t
                }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            delay(POLL_MS)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        error?.let { ErrorBox(it) {} }
        if (disabled) {
            SectionCard(m.ipDisabled, icon = Icons.Outlined.QueryStats) {
                Text(m.ipDisabledDesc, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Button(onClick = { confirmEnable = true }, modifier = Modifier.fillMaxWidth()) { Text(m.enable) }
            }
            return@Column
        }
        val r = rates
        if (r == null) {
            if (error == null) {
                Text(m.collecting, color = cs.onSurfaceVariant)
                LoadingBox()
            }
            return@Column
        }
        val rank = order.withIndex().associate { it.value to it.index }
        val active = r.filter { it.value.down + it.value.up > 64 }.toList().sortedBy { rank[it.first] ?: Int.MAX_VALUE }

        val f = followed
        if (f != null) {
            FollowedCard(
                title = names[f] ?: f,
                ip = f.takeIf { names[f] != null },
                h = history[f].orEmpty(),
                onClose = { followed = null },
            )
        } else {
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.totalNow, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        LiveBadge()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigRate(Icons.Outlined.ArrowDownward, m.download, r.values.sumOf { it.down }, cs.onPrimaryContainer, Modifier.weight(1f))
                        BigRate(Icons.Outlined.ArrowUpward, m.upload, r.values.sumOf { it.up }, cs.onPrimaryContainer, Modifier.weight(1f))
                    }
                    Text(m.activeDevices(active.size), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Outlined.TouchApp, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(m.tapToFollow, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }

        if (active.isEmpty()) {
            Text(m.noTraffic, color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        val maxRate = active.maxOfOrNull { it.second.down + it.second.up } ?: 1.0
        active.forEach { (ip, rate) ->
            val isFollowed = ip == followed
            Card(
                onClick = { followed = if (isFollowed) null else ip },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (isFollowed) cs.secondaryContainer else cs.surfaceContainerLow),
                border = if (isFollowed) BorderStroke(2.dp, cs.primary) else null,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFollowed) {
                            Icon(Icons.Outlined.PushPin, null, Modifier.size(18.dp), tint = cs.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(names[ip] ?: ip, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (names[ip] != null) Text(ip, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        RatePair(rate)
                    }
                    LinearProgressIndicator(
                        progress = { ((rate.down + rate.up) / maxRate).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }

    if (confirmEnable) {
        ConfirmDialog(
            text = m.enableConfirm,
            confirmLabel = m.enable,
            onDismiss = { confirmEnable = false },
            onConfirm = {
                confirmEnable = false
                scope.runAction(toast, s, m.enabledToast, onDone = { restartKey++ }) {
                    api.enableIpTraffic()
                    delay(4000)
                }
            },
        )
    }
}

/** The followed device, pinned at the top with its own live chart. */
@Composable
private fun FollowedCard(title: String, ip: String?, h: List<Rate>, onClose: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val now = h.lastOrNull() ?: Rate(0.0, 0.0)
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PushPin, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.mon.following, style = MaterialTheme.typography.labelMedium)
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (ip != null) Text(ip, style = MaterialTheme.typography.bodySmall)
                }
                LiveBadge()
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, s.mon.stopFollowing) }
            }
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigRate(Icons.Outlined.ArrowDownward, s.mon.download, now.down, cs.onPrimaryContainer, Modifier.weight(1f))
                BigRate(Icons.Outlined.ArrowUpward, s.mon.upload, now.up, cs.onPrimaryContainer, Modifier.weight(1f))
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = cs.surface,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                RateChart(h, Modifier.fillMaxWidth().height(140.dp).padding(8.dp))
            }
            Text(
                "${s.mon.lastMinutes}  ·  ${s.mon.peak}: ↓ ${formatRate(h.maxOfOrNull { it.down } ?: 0.0)}  ↑ ${formatRate(h.maxOfOrNull { it.up } ?: 0.0)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
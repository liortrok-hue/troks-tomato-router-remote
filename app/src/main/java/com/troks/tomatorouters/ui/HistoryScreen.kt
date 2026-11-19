package com.troks.tomatorouters.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.Last24h
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.TrafficTotal
import com.troks.tomatorouters.data.last24h
import com.troks.tomatorouters.data.nvramSet
import com.troks.tomatorouters.data.trafficHistory
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Bandwidth in the Monitor tab: live view plus the router's stored history (rstats). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandwidthSection(api: RouterApi, toast: (String) -> Unit, live: @Composable () -> Unit) {
    val m = LocalStrings.current.mon
    var range by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(m.live, m.last24h, m.daily, m.weekly, m.monthly)
    Column(Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = range,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            labels.forEachIndexed { i, l ->
                Tab(selected = range == i, onClick = { range = i }, text = { Text(l, maxLines = 1) })
            }
        }
        when (range) {
            0 -> live()
            1 -> Last24hPage(api, toast)
            else -> TotalsPage(api, toast, Period.entries[range - 2])
        }
    }
}

private enum class Period { DAY, WEEK, MONTH }

// ---------------- last 24 hours ----------------

@Composable
private fun Last24hPage(api: RouterApi, toast: (String) -> Unit) {
    val m = LocalStrings.current.mon
    val state = rememberLoadState { api.last24h() ?: OFF }
    // rstats adds one sample a minute; refresh with the other screens
    AutoRefresh { state.refreshQuietly() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    HistoryFrame(state.loading && state.data != null, state::reload, state.error, state.data == null && state.error == null) {
        val d = state.data ?: return@HistoryFrame
        if (d === OFF) { HistoryOff(api, toast, state::reload); return@HistoryFrame }
        val names = d.ifaces.keys.sortedWith(compareBy({ if (it in d.wan) 0 else if (it.startsWith("br")) 1 else 2 }, { it }))
        val current = selected?.takeIf { it in d.ifaces } ?: names.firstOrNull { it in d.wan } ?: names.firstOrNull()
        if (current == null) { Text(m.noData, color = MaterialTheme.colorScheme.onSurfaceVariant); return@HistoryFrame }
        if (names.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { n ->
                    FilterChip(
                        selected = n == current,
                        onClick = { selected = n },
                        label = { Text(if (n in d.wan) "${m.wan} · $n" else n) },
                        leadingIcon = { Icon(ifaceIconFor(n, n in d.wan), null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        val h = d.ifaces.getValue(current)
        val isWan = current in d.wan
        // per-minute bytes -> bytes/second; for LAN/WiFi the router *sends* what devices download
        val perMin = h.rx.indices.map { i ->
            val rx = h.rx[i].toDouble(); val tx = h.tx.getOrElse(i) { 0 }.toDouble()
            if (isWan) Rate(rx / 60, tx / 60) else Rate(tx / 60, rx / 60)
        }
        // 5-minute averages keep the chart readable
        val points = perMin.chunked(5).map { c -> Rate(c.sumOf { it.down } / c.size, c.sumOf { it.up } / c.size) }
        val totalDown = perMin.sumOf { it.down * 60 }
        val totalUp = perMin.sumOf { it.up * 60 }
        val cs = MaterialTheme.colorScheme
        HistoryCard(if (isWan) "${m.wan} · $current" else current, ifaceIconFor(current, isWan)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTotal(Icons.Outlined.ArrowDownward, m.download, formatBytes(totalDown), cs.primary, Modifier.weight(1f))
                BigTotal(Icons.Outlined.ArrowUpward, m.upload, formatBytes(totalUp), cs.tertiary, Modifier.weight(1f))
            }
            RateChart(points, Modifier.fillMaxWidth().height(170.dp), capacity = points.size.coerceAtLeast(2))
            AxisLabels("−24h", "−12h", m.live)
        }
        SectionCard(m.last24h, icon = Icons.Outlined.History) {
            InfoRow("${m.average} ↓", formatRate(perMin.map { it.down }.average()))
            InfoRow("${m.average} ↑", formatRate(perMin.map { it.up }.average()))
            InfoRow("${m.peak} ↓", formatRate(perMin.maxOfOrNull { it.down } ?: 0.0))
            InfoRow("${m.peak} ↑", formatRate(perMin.maxOfOrNull { it.up } ?: 0.0))
            InfoRow(m.total, formatBytes(totalDown + totalUp))
        }
    }
}

private val OFF = Last24h(emptySet(), emptyMap())

private fun ifaceIconFor(n: String, wan: Boolean): ImageVector = when {
    wan -> Icons.Outlined.Public
    n.startsWith("br") -> Icons.Outlined.Lan
    n.startsWith("eth") || n.startsWith("wl") -> Icons.Outlined.Wifi
    else -> Icons.Outlined.SettingsEthernet
}

// ---------------- daily / weekly / monthly ----------------

@Composable
private fun TotalsPage(api: RouterApi, toast: (String) -> Unit, period: Period) {
    val m = LocalStrings.current.mon
    val state = rememberLoadState { api.trafficHistory() ?: OFF_HISTORY }
    AutoRefresh { state.refreshQuietly() }

    HistoryFrame(state.loading && state.data != null, state::reload, state.error, state.data == null && state.error == null) {
        val d = state.data ?: return@HistoryFrame
        if (d === OFF_HISTORY) { HistoryOff(api, toast, state::reload); return@HistoryFrame }
        val rows: List<TrafficTotal> = when (period) {
            Period.DAY -> d.daily
            Period.MONTH -> d.monthly
            Period.WEEK -> {
                val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
                d.daily.groupBy { it.date.with(TemporalAdjusters.previousOrSame(first)) }
                    .map { (start, days) -> TrafficTotal(start, days.sumOf { it.rxBytes }, days.sumOf { it.txBytes }) }
                    .sortedBy { it.date }
            }
        }
        if (rows.isEmpty()) { Text(m.noData, color = MaterialTheme.colorScheme.onSurfaceVariant); return@HistoryFrame }

        val shown = rows.takeLast(if (period == Period.DAY) 30 else 12)
        val latest = rows.last()
        val cs = MaterialTheme.colorScheme
        val title = when (period) { Period.DAY -> m.daily; Period.WEEK -> m.weekly; Period.MONTH -> m.monthly }
        HistoryCard("${m.wan} · $title", Icons.Outlined.CalendarMonth) {
            Text(periodLabel(latest.date, period), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTotal(Icons.Outlined.ArrowDownward, m.download, formatBytes(latest.rxBytes.toDouble()), cs.primary, Modifier.weight(1f))
                BigTotal(Icons.Outlined.ArrowUpward, m.upload, formatBytes(latest.txBytes.toDouble()), cs.tertiary, Modifier.weight(1f))
            }
            TrafficBars(shown, Modifier.fillMaxWidth().height(150.dp))
            AxisLabels(shortLabel(shown.first().date, period), "", shortLabel(shown.last().date, period))
        }

        val max = rows.maxOf { it.rxBytes + it.txBytes }.coerceAtLeast(1)
        SectionCard("${m.total}: ${formatBytes(rows.sumOf { it.rxBytes + it.txBytes }.toDouble())}", icon = Icons.Outlined.History) {
            rows.asReversed().forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                TotalRow(periodLabel(t.date, period), t, max)
            }
        }
    }
}

private val OFF_HISTORY = com.troks.tomatorouters.data.TrafficHistory(emptyList(), emptyList())

@Composable
private fun TotalRow(label: String, t: TrafficTotal, max: Long) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("⁦" + formatBytes((t.rxBytes + t.txBytes).toDouble()) + "⁩", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { ((t.rxBytes + t.txBytes).toFloat() / max).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(14.dp), tint = cs.primary)
                Text(formatBytes(t.rxBytes.toDouble()), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(14.dp), tint = cs.tertiary)
                Text(formatBytes(t.txBytes.toDouble()), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** Side-by-side download/upload bars, oldest on the left (time flows left to right in every language). */
@Composable
private fun TrafficBars(rows: List<TrafficTotal>, modifier: Modifier = Modifier) {
    val down = MaterialTheme.colorScheme.primary
    val up = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (rows.maxOfOrNull { maxOf(it.rxBytes, it.txBytes) } ?: 1L).coerceAtLeast(1L) * 1.1
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        if (rows.isEmpty()) return@Canvas
        val slot = size.width / rows.size
        val bar = (slot * 0.36f).coerceAtMost(14.dp.toPx())
        val r = CornerRadius(bar / 2, bar / 2)
        rows.forEachIndexed { i, t ->
            val cx = slot * i + slot / 2
            val hd = (t.rxBytes / max * size.height).toFloat()
            val hu = (t.txBytes / max * size.height).toFloat()
            drawRoundRect(down, Offset(cx - bar, size.height - hd), Size(bar * 0.95f, hd), r)
            drawRoundRect(up, Offset(cx + bar * 0.05f, size.height - hu), Size(bar * 0.95f, hu), r)
        }
    }
}

private fun periodLabel(d: LocalDate, p: Period): String {
    val loc = Locale.getDefault()
    return when (p) {
        Period.DAY -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", loc))
        Period.WEEK -> {
            val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc)
            "⁨${d.format(f)}⁩ – ⁨${d.plusDays(6).format(f)}⁩"
        }
        Period.MONTH -> d.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc))
    }
}

private fun shortLabel(d: LocalDate, p: Period): String = when (p) {
    Period.MONTH -> d.format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
    else -> d.format(DateTimeFormatter.ofPattern("d/M", Locale.getDefault()))
}

// ---------------- shared pieces ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFrame(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    error: Throwable?,
    loading: Boolean,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { ErrorBox(it, onRefresh) }
            if (loading) LoadingBox() else content()
        }
    }
}

@Composable
private fun HistoryCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun BigTotal(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("⁦$value⁩", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Start / middle / end captions under a chart, always left to right like the chart itself. */
@Composable
private fun AxisLabels(start: String, middle: String, end: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth()) {
            val st = MaterialTheme.typography.labelSmall
            val c = MaterialTheme.colorScheme.onSurfaceVariant
            Text(start, style = st, color = c)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(middle, style = st, color = c) }
            Text(end, style = st, color = c)
        }
    }
}

@Composable
private fun HistoryOff(api: RouterApi, toast: (String) -> Unit, reload: () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    SectionCard(s.mon.historyOff, icon = Icons.Outlined.History) {
        Text(s.mon.historyOffDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                scope.runAction(toast, s, s.mon.historyEnabledToast, onDone = reload) {
                    api.nvramSet(mapOf("rstats_enable" to "1"), listOf("rstats"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(s.mon.enable) }
    }
}

/** Bytes as KB / MB / GB / TB (1024-based, like the router's pages). */
fun formatBytes(bytes: Double): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return when {
        u == 0 -> String.format(Locale.US, "%.0f %s", v, units[u])
        v >= 100 -> String.format(Locale.US, "%.0f %s", v, units[u])
        v >= 10 -> String.format(Locale.US, "%.1f %s", v, units[u])
        else -> String.format(Locale.US, "%.2f %s", v, units[u])
    }
}

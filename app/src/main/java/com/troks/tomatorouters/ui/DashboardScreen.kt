package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.RouterStatus
import com.troks.tomatorouters.data.WanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(api: RouterApi) {
    val s = LocalStrings.current
    val state = rememberLoadState { api.status() }
    AutoRefresh { state.refreshQuietly() }

    PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = state::reload, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBox(it, state::reload) }
            val st = state.data
            if (st == null) {
                if (state.error == null) LoadingBox()
            } else {
                StatusContent(st, s)
            }
        }
    }

}

@Composable
private fun StatusContent(st: RouterStatus, s: Strings) {
    val online = st.wanState == WanState.UP

    // hero card
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        // one compact block: router on the left, firmware version stacked on the right
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Router, null, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    st.routerName.ifBlank { st.model.ifBlank { "FreshTomato" } },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (st.model.isNotBlank()) Text(st.model, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (st.firmware.isNotBlank()) {
                    // e.g. "2026.5 K26ARM USB AIO-64K": number on top, build flavour below
                    Text(
                        "${s.firmware} ${st.firmware.substringBefore(' ')}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val build = st.firmware.substringAfter(' ', "")
                    if (build.isNotBlank()) Text(build, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }

    // (uptime is shown in the header, under the router address)

    if (st.memTotalKb > 0) {
        val used = (st.memTotalKb - st.memFreeKb).coerceAtLeast(0)
        val frac = used.toFloat() / st.memTotalKb
        SectionCard(s.secMemory, icon = Icons.Outlined.Memory, trailing = {
            Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }) {
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            InfoRow(s.used, "${used / 1024} / ${st.memTotalKb / 1024} MB")
        }
    }

    // CPU, laid out like Memory: busy % with a bar, load averages below
    SectionCard(s.secCpu, icon = Icons.Outlined.Speed, trailing = {
        st.cpuUsage?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
    }) {
        st.cpuUsage?.let { u ->
            LinearProgressIndicator(
                progress = { u },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )
        }
        InfoRow(s.loadAvg, st.load)
    }
    SectionCard(s.secWan, icon = Icons.Outlined.Public) {
        InfoRow(s.ipAddress, if (online) st.wanIp else s.notConnected)
        InfoRow(s.connType, st.wanProto.uppercase())
        InfoRow("Gateway", st.wanGateway)
        InfoRow("DNS", st.dns)
        // IPv6 rows only when the router has IPv6
        if (st.wanIp6.isNotEmpty()) InfoRow("IPv6", st.wanIp6)
        if (st.dns6.isNotEmpty()) InfoRow("IPv6 DNS", st.dns6)
    }
    // LAN, as on the router's Status > Overview: every bridge with its address and DHCP range
    SectionCard(s.secLan, icon = Icons.Outlined.Lan) {
        InfoRow(s.routerIps, st.lanAddresses.joinToString("\n").ifEmpty { st.lanIp })
        if (st.lanIp6.isNotEmpty()) InfoRow("IPv6", st.lanIp6)
        InfoRow("DHCP", st.dhcpRanges.joinToString("\n"))
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(icon, size = 36.dp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun formatUptime(sec: Long, s: Strings): String =
    s.uptimeFmt(sec / 86400, (sec % 86400) / 3600, (sec % 3600) / 60)

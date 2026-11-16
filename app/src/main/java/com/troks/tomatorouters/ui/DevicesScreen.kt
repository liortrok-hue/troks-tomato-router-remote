package com.troks.tomatorouters.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.PushPin
import com.troks.tomatorouters.data.StaticLease
import com.troks.tomatorouters.data.reserveIp
import com.troks.tomatorouters.data.removeReservation
import com.troks.tomatorouters.data.staticLeases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.Device
import com.troks.tomatorouters.data.RouterApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(api: RouterApi, toast: (String) -> Unit) {
    val s = LocalStrings.current
    val state = rememberLoadState { api.devices() }
    val scope = rememberCoroutineScope()
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 wifi, 2 wired, 3 blocked, 4 static IP
    var confirmBlock by remember { mutableStateOf<Device?>(null) }
    // static IP: the device being edited + the current reservation list (for its lease and taken IPs)
    var reserving by remember { mutableStateOf<Pair<Device, List<StaticLease>>?>(null) }
    var pendingSave by remember { mutableStateOf<Pair<Device, StaticLease>?>(null) }
    var pendingRemove by remember { mutableStateOf<Device?>(null) }
    // device being pinged; the popup is modal, so only one device can be pinged at a time
    var pinging by remember { mutableStateOf<Device?>(null) }

    PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = state::reload, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.error?.let { item { ErrorBox(it, state::reload) } }
            val all = state.data
            if (all == null) {
                if (state.error == null) item { LoadingBox() }
            } else {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(s.filterAll(all.size), Icons.Outlined.Devices, 0),
                            Triple("WiFi (${all.count { it.wireless }})", Icons.Outlined.Wifi, 1),
                            Triple(s.filterWired, Icons.Outlined.Cable, 2),
                            Triple(s.filterBlocked(all.count { it.blocked }), Icons.Outlined.Block, 3),
                            Triple("${s.m.reserveIp} (${all.count { it.staticIp != null }})", Icons.Outlined.PushPin, 4),
                        ).forEach { (label, icon, i) ->
                            FilterChip(
                                selected = filter == i,
                                onClick = { filter = i },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
                            )
                        }
                    }
                }
                val shown = when (filter) {
                    1 -> all.filter { it.wireless }
                    2 -> all.filter { !it.wireless }
                    3 -> all.filter { it.blocked }
                    4 -> all.filter { it.staticIp != null }
                    else -> all
                }
                if (shown.isEmpty()) item {
                    Text(s.noDevices, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(shown, key = { it.mac }) { d ->
                    DeviceCard(
                        d,
                        onBlock = {
                            if (d.blocked) scope.runAction(toast, s, s.unblocked, onDone = state::reload) { api.setBlocked(d.mac, false) }
                            else confirmBlock = d
                        },
                        onPing = { if (pinging == null) pinging = d },
                        onWake = { scope.runAction(toast, s, s.wolSent) { api.wakeOnLan(d.mac) } },
                        onStatic = {
                            scope.launch {
                                try {
                                    reserving = d to api.staticLeases()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    toast(s.errorPrefix + friendlyError(e, s))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    reserving?.let { (d, leases) ->
        val existing = leases.firstOrNull { d.mac in it.macs }
        LeaseDialog(
            initial = existing ?: StaticLease(listOf(d.mac), d.ip, toHostname(d.name), false),
            title = "${s.m.reserveIp} · ${d.name.ifBlank { d.mac }}",
            macEditable = false,
            takenIps = leases.filter { d.mac !in it.macs }.map { it.ip }.toSet(),
            onDismiss = { reserving = null },
            onRemove = if (existing != null) ({ reserving = null; pendingRemove = d }) else null,
        ) { lease ->
            reserving = null
            pendingSave = d to lease
        }
    }
    pendingSave?.let { (d, lease) ->
        ConfirmDialog(
            text = "${s.m.reserveIp}: ${lease.ip} → ${d.name.ifBlank { d.mac }}\n\n${s.m.dhcpNote}",
            confirmLabel = s.save,
            onDismiss = { pendingSave = null },
            onConfirm = {
                pendingSave = null
                scope.runAction(toast, s, s.m.reservationSaved, onDone = state::reload) {
                    api.reserveIp(d.mac, lease.ip, lease.hostname, lease.bindArp)
                }
            },
        )
    }
    pendingRemove?.let { d ->
        ConfirmDialog(
            text = s.m.removeReservationConfirm(d.name.ifBlank { d.mac }),
            confirmLabel = s.m.removeReservation,
            onDismiss = { pendingRemove = null },
            onConfirm = {
                pendingRemove = null
                scope.runAction(toast, s, s.m.reservationRemoved, onDone = state::reload) { api.removeReservation(d.mac) }
            },
        )
    }

    pinging?.let { d -> PingDialog(api, d, onDismiss = { pinging = null }) }

    confirmBlock?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmBlock = null },
            icon = { Icon(Icons.Outlined.Block, null) },
            title = { Text(s.blockTitle(d.name.ifBlank { d.mac })) },
            text = { Text(s.blockText) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = null
                    scope.runAction(toast, s, s.blockedToast, onDone = state::reload) { api.setBlocked(d.mac, true) }
                }) { Text(s.block) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = null }) { Text(s.cancel) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(d: Device, onBlock: () -> Unit, onPing: () -> Unit, onWake: () -> Unit, onStatic: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (d.blocked) cs.errorContainer else cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    d.blocked -> IconBadge(Icons.Outlined.Block, container = cs.error, content = cs.onError)
                    d.wireless -> IconBadge(Icons.Outlined.Wifi, container = cs.primaryContainer, content = cs.onPrimaryContainer)
                    else -> IconBadge(Icons.Outlined.Cable, container = cs.tertiaryContainer, content = cs.onTertiaryContainer)
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        d.name.ifBlank { s.unknownDevice },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        listOfNotNull(d.ip.ifBlank { null }, d.mac).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    val extra = buildList {
                        if (d.wireless) add(d.band ?: "WiFi")
                        d.rssi?.let { add(s.signal(it, signalLabel(it, s))) }
                        if (d.blocked) add(s.blockedBadge)
                    }
                    if (extra.isNotEmpty()) {
                        Text(extra.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    d.staticIp?.let { ip ->
                        Text(
                            s.m.reservedBadge("\u200E" + ip),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                d.rssi?.let { SignalBars(it) }
            }
            // one row of compact actions, aligned with the device name (start side in every language);
            // the name of each action shows on long-press and is read by TalkBack
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (d.ip.isNotBlank()) ActionButton(Icons.Outlined.NetworkPing, s.ping, onClick = onPing)
                if (!d.wireless) ActionButton(Icons.Outlined.PowerSettingsNew, s.wake, onClick = onWake)
                ActionButton(
                    Icons.Outlined.PushPin,
                    s.m.reserveIp,
                    container = if (d.staticIp != null) cs.tertiaryContainer else cs.secondaryContainer,
                    content = if (d.staticIp != null) cs.onTertiaryContainer else cs.onSecondaryContainer,
                    onClick = onStatic,
                )
                ActionButton(
                    if (d.blocked) Icons.Outlined.LockOpen else Icons.Outlined.Block,
                    if (d.blocked) s.unblock else s.block,
                    container = if (d.blocked) cs.primaryContainer else cs.errorContainer,
                    content = if (d.blocked) cs.onPrimaryContainer else cs.onErrorContainer,
                    onClick = onBlock,
                )
            }
        }
    }
}

/**
 * Popup that pings one device from the router (4 packets) and shows the result.
 * Closing it cancels a ping that is still running.
 */
@Composable
private fun PingDialog(api: RouterApi, d: Device, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var run by remember { mutableIntStateOf(0) }
    var output by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(run) {
        output = null
        output = try {
            api.ping(d.ip, 4, 56).ifBlank { s.noOutput }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            s.errorPrefix + friendlyError(e, s)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.NetworkPing, null) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.name.ifBlank { s.unknownDevice }, textAlign = TextAlign.Center)
                Text(
                    "‎" + d.ip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            val out = output
            if (out == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(s.pinging)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PingSummary(out)
                    Card(
                        Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        // terminal output is left-to-right
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                out.trimEnd(),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { run++ }, enabled = output != null) { Text(s.pingAgain) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}

/** Round tonal icon button with its name written underneath. */
@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small 4-bar signal strength indicator. */
@Composable
private fun SignalBars(rssi: Int) {
    val bars = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -75 -> 2
        else -> 1
    }
    val on = if (bars >= 3) MaterialTheme.colorScheme.success else if (bars == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(end = 8.dp)) {
        for (i in 1..4) {
            androidx.compose.foundation.Canvas(Modifier.size(width = 4.dp, height = (4 + i * 4).dp)) {
                drawRoundRect(
                    color = if (i <= bars) on else off,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

private fun signalLabel(rssi: Int, s: Strings) = when {
    rssi >= -55 -> s.signalExcellent
    rssi >= -67 -> s.signalGood
    rssi >= -75 -> s.signalFair
    else -> s.signalWeak
}

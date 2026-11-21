package com.troks.tomatorouters.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import com.troks.tomatorouters.data.WanState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Public
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wifi
import com.troks.tomatorouters.data.RouterApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Corner radius of the header's bottom and the tab bar's top. */
private val BAR_CORNER = 24.dp
private data class TabItem(val label: String, val selected: ImageVector, val unselected: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(api: RouterApi, vm: AppViewModel) {
    val s = LocalStrings.current
    val tabs = listOf(
        TabItem(s.tabOverview, Icons.Filled.Preview, Icons.Outlined.Preview),
        TabItem(s.tabDevices, Icons.Filled.Devices, Icons.Outlined.Devices),
        TabItem(s.tabWifi, Icons.Filled.Wifi, Icons.Outlined.Wifi),
        TabItem(s.mon.tab, Icons.Filled.QueryStats, Icons.Outlined.QueryStats),
        TabItem(s.m.tab, Icons.Filled.Tune, Icons.Outlined.Tune),
        TabItem(s.tabTools, Icons.Filled.Build, Icons.Outlined.Build),
    )
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // internet status pill + router uptime in the header, refreshed in the background on every tab
    var wan by remember { mutableStateOf<WanState?>(null) }
    var uptime by remember { mutableStateOf<Long?>(null) }
    // only while the app is on screen: nothing is polled once it goes to the background
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(api, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                runCatching { api.pulse() }.onSuccess { (w, up) -> wan = w; if (up != null) uptime = up }
                delay(15_000)
            }
        }
    }
    val toast: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            // Two rows: actions spread across the top, then the router icon with the page title and address.
            // Rounded bottom with a soft shadow, like the tab bar: the page scrolls in under it.
            Surface(
                color = cs.surfaceContainer,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = BAR_CORNER, bottomEnd = BAR_CORNER),
            ) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                    // Donate at the start, icons at the end, status pill in the middle of the screen
                    // whenever it fits there (otherwise in the space left between them, shrinking if needed)
                    CenteredActionsRow(
                        start = { DonateButton(vm, toast) },
                        center = { wan?.let { WanStatusPill(it) } },
                        end = {
                            Row {
                                IconButton(onClick = { showAbout = true }) { Icon(Icons.Outlined.Info, s.about) }
                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, s.settings) }
                                IconButton(onClick = vm::logout) { Icon(Icons.AutoMirrored.Outlined.Logout, s.logout) }
                            }
                        },
                        // the header has 16dp padding at the start and 4dp at the end: shift so "center" is the screen's
                        centerShift = (-6).dp,
                    )
                    // one line: router icon + page title at the start, router address at the end
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            Icons.Outlined.Router,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tabs[tab].label,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // tap the address to switch to another saved profile (signs out and connects to it)
                        val px = loginExtraStrings(vm.language)
                        val connectedName = vm.profiles.firstOrNull { it.id == vm.connectedProfileId }?.name.orEmpty()
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = vm.profiles.isNotEmpty(), onClickLabel = px.chooseProfile) { showProfiles = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // globe: connected through the external address (remote access)
                            if (!vm.isDemo && vm.connectedVia?.remote == true) {
                                Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                "⁨" + (if (vm.isDemo) "${s.demo} · 10.0.0.1" else listOf(connectedName, vm.connectedVia?.host ?: vm.login.host).filter { it.isNotBlank() }.joinToString(" · ")) + "⁩",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (vm.profiles.isNotEmpty()) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // router uptime under the address (same poll as the status pill)
                        uptime?.let { up ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Timer, s.uptime, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatUptime(up, s),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // rounded top with a soft shadow: the page scrolls in under it
            Surface(
                color = cs.surfaceContainer,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(topStart = BAR_CORNER, topEnd = BAR_CORNER),
            ) {
            NavigationBar(containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        // unselected tabs hide their label, so the icon carries the name for TalkBack
                        icon = { Icon(if (tab == i) t.selected else t.unselected, contentDescription = if (tab == i) null else t.label) },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        // 6 tabs: show the label only on the selected one so long words fit
                        alwaysShowLabel = false,
                    )
                }
            }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        // The page starts at the bars' edges, so at rest its own 16dp padding keeps a gap under the
        // header and above the tab bar; while scrolling, content slides under them (and their shadow).
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen(api)
                1 -> DevicesScreen(api, toast)
                2 -> WifiScreen(api, toast)
                3 -> MonitorScreen(api, toast)
                4 -> ManageScreen(api, toast, onLogout = vm::logout)
                5 -> ToolsScreen(api, initialHost = "", toast = toast)
            }
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
    if (showProfiles) ProfilePickerSheet(vm, loginExtraStrings(vm.language), onDismiss = { showProfiles = false }) { id ->
        showProfiles = false
        vm.switchProfile(id)
    }
}
/** Simple async loader for a screen's data. */
class LoadState<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set

    fun reload() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                data = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            } finally {
                loading = false
            }
        }
    }

    /** Background refresh: no spinner, and a failed attempt keeps the data already on screen. */
    private var quietBusy = false
    fun refreshQuietly() {
        if (loading || quietBusy) return
        quietBusy = true
        scope.launch {
            try {
                data = block()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (data == null) error = e
            } finally {
                quietBusy = false
            }
        }
    }
}

/** Auto-refresh every [periodMs] while the screen is visible (paused when the app is in the background). */
@Composable
fun AutoRefresh(periodMs: Long = AUTO_REFRESH_MS, action: () -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                delay(periodMs)
                action()
            }
        }
    }
}

/** Devices, Monitor and Overview refresh themselves this often. */
const val AUTO_REFRESH_MS = 30_000L

@Composable
fun <T> rememberLoadState(block: suspend () -> T): LoadState<T> {
    val scope = rememberCoroutineScope()
    val state = remember { LoadState(scope, block) }
    LaunchedEffect(Unit) { state.reload() }
    return state
}

/** Runs a one-off action and reports the result in a snackbar. */
fun CoroutineScope.runAction(
    toast: (String) -> Unit,
    s: Strings,
    success: String,
    onDone: () -> Unit = {},
    block: suspend () -> Unit,
) {
    launch {
        try {
            block()
            toast(success)
            onDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(s.errorPrefix + friendlyError(e, s))
        }
    }
}

/**
 * One row with [start] and [end] pinned to the edges and [center] placed at the true middle
 * (plus [centerShift]) when it doesn't collide with them; otherwise it's centered in the gap between
 * them, and with very large fonts the start and center items shrink (their text ellipsizes).
 */
@Composable
private fun CenteredActionsRow(
    start: @Composable () -> Unit,
    center: @Composable () -> Unit,
    end: @Composable () -> Unit,
    centerShift: Dp = 0.dp,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box { start() }
            Box { center() }
            Box { end() }
        },
    ) { m, constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val e = m[2].measure(loose)
        val room = (width - e.width - 2 * gap).coerceAtLeast(0)
        val sNat = m[0].maxIntrinsicWidth(Int.MAX_VALUE)
        val cNat = m[1].maxIntrinsicWidth(Int.MAX_VALUE)
        // share the room only when both don't fit at their natural width
        val (sMax, cMax) = if (sNat + cNat <= room) sNat to cNat else {
            val half = room / 2
            if (sNat <= half) sNat to room - sNat else if (cNat <= half) room - cNat to cNat else half to room - half
        }
        val s = m[0].measure(loose.copy(maxWidth = sMax.coerceAtLeast(0)))
        val c = m[1].measure(loose.copy(maxWidth = cMax.coerceAtLeast(0)))
        val h = maxOf(s.height, c.height, e.height)
        val leftLimit = s.width + gap
        val rightLimit = width - e.width - gap
        val ideal = (width - c.width) / 2 + centerShift.roundToPx()
        val cx = if (ideal >= leftLimit && ideal + c.width <= rightLimit) ideal
        else (leftLimit + (rightLimit - leftLimit - c.width) / 2).coerceAtLeast(leftLimit)
        layout(width, h) {
            s.placeRelative(0, (h - s.height) / 2)
            c.placeRelative(cx, (h - c.height) / 2)
            e.placeRelative(width - e.width, (h - e.height) / 2)
        }
    }
}

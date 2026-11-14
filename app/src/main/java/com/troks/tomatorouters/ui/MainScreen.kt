package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import com.troks.tomatorouters.data.RouterApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class TabItem(val label: String, val selected: ImageVector, val unselected: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(api: RouterApi, vm: AppViewModel) {
    val s = LocalStrings.current
    val tabs = listOf(
        TabItem(s.tabOverview, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        TabItem(s.tabDevices, Icons.Filled.Devices, Icons.Outlined.Devices),
        TabItem(s.tabWifi, Icons.Filled.Wifi, Icons.Outlined.Wifi),
        TabItem(s.mon.tab, Icons.Filled.QueryStats, Icons.Outlined.QueryStats),
        TabItem(s.m.tab, Icons.Filled.Tune, Icons.Outlined.Tune),
        TabItem(s.tabTools, Icons.Filled.Build, Icons.Outlined.Build),
    )
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var toolsHost by remember { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tabs[tab].label, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (vm.isDemo) "${s.demo} · 10.0.0.1" else vm.login.host,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        IconBadge(
                            Icons.Outlined.Router,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            size = 36.dp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, s.settings) }
                    IconButton(onClick = vm::logout) { Icon(Icons.AutoMirrored.Outlined.Logout, s.logout) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen(api)
                1 -> DevicesScreen(api, toast, onPing = { ip -> toolsHost = ip; tab = 5 })
                2 -> WifiScreen(api, toast)
                3 -> MonitorScreen(api, toast)
                4 -> ManageScreen(api, toast, onLogout = vm::logout)
                5 -> ToolsScreen(api, initialHost = toolsHost, toast = toast)
            }
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
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
}

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

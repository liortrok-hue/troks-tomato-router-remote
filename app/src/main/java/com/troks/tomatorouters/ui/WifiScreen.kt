package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.WifiNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(api: RouterApi, toast: (String) -> Unit) {
    val s = LocalStrings.current
    val state = rememberLoadState { api.wifiNetworks() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<WifiNetwork?>(null) }
    var confirmOff by remember { mutableStateOf<WifiNetwork?>(null) }
    val reloadLater: () -> Unit = { scope.launch { delay(6000); state.reload() } }

    PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = state::reload, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBox(it, state::reload) }
            val nets = state.data
            if (nets == null) {
                if (state.error == null) LoadingBox()
            } else {
                if (nets.isEmpty()) Text(s.noWifi, color = MaterialTheme.colorScheme.onSurfaceVariant)
                nets.forEach { n ->
                    WifiCard(
                        n,
                        onToggle = { on ->
                            if (on) scope.runAction(toast, s, s.radioTurningOn(n.band), onDone = reloadLater) { api.setRadio(n.unit, n.ifname, true) }
                            else confirmOff = n
                        },
                        onEdit = { editing = n },
                    )
                }
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(s.wifiRestartNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirmOff?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmOff = null },
            icon = { Icon(Icons.Outlined.WifiOff, null) },
            title = { Text(s.turnOffTitle(n.ssid, n.band)) },
            text = { Text(s.turnOffText(n.clients)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = null
                    scope.runAction(toast, s, s.radioTurnedOff(n.band), onDone = reloadLater) { api.setRadio(n.unit, n.ifname, false) }
                }) { Text(s.turnOff) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = null }) { Text(s.cancel) } },
        )
    }

    editing?.let { n ->
        EditWifiDialog(n, onDismiss = { editing = null }) { ssid, pass ->
            editing = null
            scope.runAction(toast, s, s.wifiSaved, onDone = reloadLater) { api.updateWifi(n.unit, ssid, pass) }
        }
    }
}

@Composable
private fun WifiCard(n: WifiNetwork, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    var showPass by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.enabled) IconBadge(Icons.Outlined.Wifi, container = cs.primaryContainer, content = cs.onPrimaryContainer, size = 48.dp)
                else IconBadge(Icons.Outlined.WifiOff, container = cs.surfaceContainerHighest, content = cs.onSurfaceVariant, size = 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(n.ssid, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${n.band}  ·  ${if (n.enabled) s.active else s.off}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (n.enabled) cs.success else cs.onSurfaceVariant,
                    )
                }
                Switch(checked = n.enabled, onCheckedChange = onToggle)
            }
            HorizontalDivider(color = cs.outlineVariant)
            InfoRow(s.connectedDevices, n.clients.toString())
            InfoRow(s.channel, if (n.channel == "0") s.auto else n.channel)
            InfoRow(s.security, securityLabel(n.security, s))
            InfoRow(s.iface, n.ifname)
            if (n.password.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (showPass) n.password else "••••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPass) s.hidePassword else s.showPassword,
                        )
                    }
                }
            }
            FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(s.edit)
            }
        }
    }
}

@Composable
private fun EditWifiDialog(n: WifiNetwork, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    val s = LocalStrings.current
    var ssid by remember { mutableStateOf(n.ssid) }
    var changePass by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val ssidOk = ssid.isNotEmpty() && ssid.toByteArray().size <= 32
    val passOk = !changePass || pass.length in 8..63

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Wifi, null) },
        title = { Text(s.editTitle(n.band)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text(s.ssidLabel) }, singleLine = true, isError = !ssidOk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(changePass, { changePass = it })
                    Text(s.changePassword)
                }
                if (changePass) {
                    OutlinedTextField(
                        pass, { pass = it },
                        label = { Text(s.newPasswordLabel) },
                        singleLine = true,
                        isError = !passOk,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) s.hide else s.show)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ssidOk && passOk, onClick = { onSave(ssid, if (changePass) pass else null) }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

private fun securityLabel(mode: String, s: Strings) = when (mode) {
    "disabled", "" -> s.secOpen
    "wpa2_personal" -> "WPA2"
    "wpa_personal" -> "WPA"
    "wpaX_personal" -> "WPA / WPA2"
    "wpa3_personal" -> "WPA3"
    "wpa2wpa3_personal" -> "WPA2 / WPA3"
    "wep" -> s.secWep
    else -> mode
}

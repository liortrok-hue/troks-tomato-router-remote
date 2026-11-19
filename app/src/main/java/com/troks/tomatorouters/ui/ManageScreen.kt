package com.troks.tomatorouters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.AdminSettings
import com.troks.tomatorouters.data.DmzSettings
import com.troks.tomatorouters.data.PortForward
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.WanSettings
import com.troks.tomatorouters.data.adminSettings
import com.troks.tomatorouters.data.dmzSettings
import com.troks.tomatorouters.data.isIp
import com.troks.tomatorouters.data.portForwards
import com.troks.tomatorouters.data.saveAdmin
import com.troks.tomatorouters.data.saveDmz
import com.troks.tomatorouters.data.savePortForwards
import com.troks.tomatorouters.data.saveWan
import com.troks.tomatorouters.data.wanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ManagePageId { WAN, STATIC, PORTS, DMZ, DDNS, ADMIN }

@Composable
fun ManageScreen(api: RouterApi, toast: (String) -> Unit, onLogout: () -> Unit) {
    val s = LocalStrings.current
    var page by rememberSaveable { mutableStateOf<ManagePageId?>(null) }
    val back = { page = null }

    when (page) {
        null -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuCard(Icons.Outlined.Public, s.m.wanTitle, s.m.wanDesc) { page = ManagePageId.WAN }
            MenuCard(Icons.Outlined.PushPin, s.m.staticTitle, s.m.staticDesc) { page = ManagePageId.STATIC }
            MenuCard(Icons.Outlined.SettingsEthernet, s.m.portFwdTitle, s.m.portFwdDesc) { page = ManagePageId.PORTS }
            MenuCard(Icons.Outlined.Security, s.m.dmzTitle, s.m.dmzDesc) { page = ManagePageId.DMZ }
            MenuCard(Icons.Outlined.Dns, s.m.ddnsTitle, s.m.ddnsDesc) { page = ManagePageId.DDNS }
            MenuCard(Icons.Outlined.AdminPanelSettings, s.m.adminTitle, s.m.adminDesc) { page = ManagePageId.ADMIN }
        }
        else -> {
            BackHandler { back() }
            when (page) {
                ManagePageId.WAN -> WanPage(api, toast, back)
                ManagePageId.STATIC -> StaticIpPage(api, toast, back)
                ManagePageId.PORTS -> PortForwardPage(api, toast, back)
                ManagePageId.DMZ -> DmzPage(api, toast, back)
                ManagePageId.DDNS -> DdnsPage(api, toast, back)
                ManagePageId.ADMIN -> AdminPage(api, toast, back, onLogout)
                null -> {}
            }
        }
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, container = cs.primaryContainer, content = cs.onPrimaryContainer, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = cs.onSurfaceVariant)
        }
    }
}

/**
 * Common load / edit / confirm / save flow for a settings page.
 * [edit] holds the user's working copy; [save] writes it after confirmation.
 */
class FormState<T>(val load: LoadState<T>) {
    var edit by mutableStateOf<T?>(null)
    var saving by mutableStateOf(false)
    var confirm by mutableStateOf(false)
    val dirty get() = edit != null && edit != load.data
}

@Composable
fun <T> rememberFormState(loader: suspend () -> T): FormState<T> {
    val load = rememberLoadState(loader)
    val form = remember { FormState(load) }
    LaunchedEffect(load.data) { if (load.data != null) form.edit = load.data }
    return form
}

@Composable
fun <T> FormState<T>.ConfirmAndSave(
    warning: String,
    toast: (String) -> Unit,
    afterSave: () -> Unit = {},
    save: suspend (T) -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    if (!confirm) return
    ConfirmDialog(
        text = warning,
        confirmLabel = s.m.apply,
        onDismiss = { confirm = false },
        onConfirm = {
            confirm = false
            val value = edit ?: return@ConfirmDialog
            saving = true
            scope.launch {
                try {
                    save(value)
                    toast(s.m.applied)
                    afterSave()
                    delay(4000)
                    load.reload()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(s.errorPrefix + friendlyError(e, s))
                } finally {
                    saving = false
                }
            }
        },
    )
}

// ---------------- WAN ----------------

@Composable
private fun WanPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val form = rememberFormState { api.wanSettings() }
    val w = form.edit

    ManagePage(m.wanTitle, onBack, form.saving, form.dirty, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (w == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        val set: (WanSettings) -> Unit = { form.edit = it }
        val types = buildList {
            add("dhcp" to m.typeDhcp); add("pppoe" to m.typePppoe); add("static" to m.typeStatic); add("disabled" to m.typeDisabled)
            if (none { it.first == w.proto }) add(w.proto to w.proto.uppercase())
        }
        FormSection(m.connectionType) {
            DropdownField(m.connectionType, types, w.proto) { set(w.copy(proto = it)) }
        }
        if (w.proto == "pppoe") {
            FormSection("PPPoE") {
                FormField(m.pppUsername, w.pppUser, { set(w.copy(pppUser = it)) })
                FormField(s.password, w.pppPass, { set(w.copy(pppPass = it)) }, password = true)
                FormField(m.serviceName, w.pppService, { set(w.copy(pppService = it)) }, hint = m.optional)
                FormLabel(m.connectMode)
                SegmentedChoice(listOf(m.keepAlive, m.onDemand), if (w.keepAlive) 0 else 1) { set(w.copy(keepAlive = it == 0)) }
                if (w.keepAlive) FormField(m.redialInterval, w.redialPeriod, { set(w.copy(redialPeriod = it.filter(Char::isDigit))) }, keyboard = KeyboardType.Number)
                else FormField(m.idleTime, w.idleTime, { set(w.copy(idleTime = it.filter(Char::isDigit))) }, keyboard = KeyboardType.Number)
            }
        }
        if (w.proto == "static") {
            FormSection(m.typeStatic) {
                FormField(m.staticIp, w.ip, { set(w.copy(ip = it.trim())) }, keyboard = KeyboardType.Decimal, isError = w.ip.isNotEmpty() && !isIp(w.ip))
                FormField(m.netmask, w.netmask, { set(w.copy(netmask = it.trim())) }, keyboard = KeyboardType.Decimal, isError = w.netmask.isNotEmpty() && !isIp(w.netmask))
                FormField(m.gateway, w.gateway, { set(w.copy(gateway = it.trim())) }, keyboard = KeyboardType.Decimal, isError = w.gateway.isNotEmpty() && !isIp(w.gateway))
            }
        }
        FormSection(m.dnsServer) {
            SegmentedChoice(listOf(m.dnsAuto, m.dnsManual), if (w.dnsManual) 1 else 0) { set(w.copy(dnsManual = it == 1)) }
            if (w.dnsManual) {
                FormField("DNS 1", w.dns1, { set(w.copy(dns1 = it.trim())) }, keyboard = KeyboardType.Decimal, isError = w.dns1.isNotEmpty() && !isIp(w.dns1))
                FormField("DNS 2", w.dns2, { set(w.copy(dns2 = it.trim())) }, keyboard = KeyboardType.Decimal, hint = m.optional, isError = w.dns2.isNotEmpty() && !isIp(w.dns2))
            }
        }
        FormSection(m.mtu) {
            SegmentedChoice(listOf(m.mtuDefault, m.mtuManual), if (w.mtuManual) 1 else 0) { set(w.copy(mtuManual = it == 1)) }
            if (w.mtuManual) FormField(m.mtu, w.mtu, { set(w.copy(mtu = it.filter(Char::isDigit).take(4))) }, keyboard = KeyboardType.Number, hint = "576 - 1500")
        }
        WarningNote(m.wanWarning)
    }
    form.ConfirmAndSave(m.wanWarning, toast) { api.saveWan(it) }
}

// ---------------- Port forwarding ----------------

@Composable
private fun PortForwardPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val form = rememberFormState { api.portForwards() }
    val rules = form.edit
    var editing by remember { mutableStateOf<Pair<Int, PortForward>?>(null) } // index -1 = new
    var deleting by remember { mutableIntStateOf(-1) }

    ManagePage(m.portFwdTitle, onBack, form.saving, form.dirty, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (rules == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        if (rules.isEmpty()) Text(m.noRules, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        rules.forEachIndexed { i, r ->
            RuleCard(
                r,
                onToggle = { on -> form.edit = rules.toMutableList().also { it[i] = r.copy(enabled = on) } },
                onEdit = { editing = i to r },
                onDelete = { deleting = i },
            )
        }
        OutlinedButton(
            onClick = { editing = -1 to PortForward(true, 1, "", "", "", "", "") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(m.addRule)
        }
        if (form.dirty) WarningNote(m.unsaved)
    }

    editing?.let { (index, rule) ->
        RuleDialog(rule, onDismiss = { editing = null }) { updated ->
            val list = (form.edit ?: emptyList()).toMutableList()
            if (index < 0) list.add(updated) else list[index] = updated
            form.edit = list
            editing = null
        }
    }
    if (deleting >= 0) {
        val r = rules?.getOrNull(deleting)
        if (r == null) deleting = -1 else ConfirmDialog(
            text = m.deleteConfirm(r.description.ifBlank { r.extPorts }),
            confirmLabel = m.delete,
            onDismiss = { deleting = -1 },
            onConfirm = {
                form.edit = rules.toMutableList().also { it.removeAt(deleting) }
                deleting = -1
            },
        )
    }
    form.ConfirmAndSave(m.confirmApply, toast) { api.savePortForwards(it) }
}

private fun protoName(p: Int, s: Strings) = when (p) { 1 -> "TCP"; 2 -> "UDP"; else -> "TCP/UDP" }

@Composable
private fun RuleCard(r: PortForward, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.description.ifBlank { "${protoName(r.proto, s)} ${r.extPorts}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (r.enabled) cs.onSurface else cs.onSurfaceVariant,
                )
                // ports/addresses always read left-to-right
                Text(
                    "‎${protoName(r.proto, s)}  ${r.extPorts} → ${r.intAddress}:${r.intPort.ifBlank { r.extPorts }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                if (r.source.isNotBlank()) Text("‎${s.m.srcAddress}: ${r.source}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(checked = r.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, s.m.editRule) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, s.m.delete, tint = cs.error) }
        }
    }
}

private val PORTS_RE = Regex("^\\d{1,5}([-,]\\d{1,5})*$")

@Composable
private fun RuleDialog(rule: PortForward, onDismiss: () -> Unit, onSave: (PortForward) -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    var r by remember { mutableStateOf(rule) }
    val portsOk = PORTS_RE.matches(r.extPorts.trim())
    val intOk = r.intPort.isBlank() || r.intPort.trim().toIntOrNull() in 1..65535
    val ipOk = isIp(r.intAddress)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.extPorts.isEmpty()) m.addRule else m.editRule) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormField(m.description, r.description, { r = r.copy(description = it.replace("<", "").replace(">", "")) }, hint = m.optional)
                FormLabel(m.protocol)
                SegmentedChoice(listOf("TCP", "UDP", m.both), r.proto - 1) { r = r.copy(proto = it + 1) }
                FormField(m.extPorts, r.extPorts, { r = r.copy(extPorts = it.trim()) }, hint = m.extPortsHint, keyboard = KeyboardType.Phone, isError = r.extPorts.isNotEmpty() && !portsOk)
                FormField(m.intAddress, r.intAddress, { r = r.copy(intAddress = it.trim()) }, hint = "192.168.1.x", keyboard = KeyboardType.Decimal, isError = r.intAddress.isNotEmpty() && !ipOk)
                FormField(m.intPort, r.intPort, { r = r.copy(intPort = it.filter(Char::isDigit).take(5)) }, hint = m.intPortHint, keyboard = KeyboardType.Number, isError = !intOk)
                FormField(m.srcAddress, r.source, { r = r.copy(source = it.trim()) }, hint = m.srcHint)
            }
        },
        confirmButton = { TextButton(enabled = portsOk && intOk && ipOk, onClick = { onSave(r) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

// ---------------- DMZ ----------------

@Composable
private fun DmzPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val form = rememberFormState { api.dmzSettings() }
    val d = form.edit
    val hostOk = d == null || !d.enabled || isIp(d.host)

    ManagePage(m.dmzTitle, onBack, form.saving, form.dirty && hostOk, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (d == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        val set: (DmzSettings) -> Unit = { form.edit = it }
        FormSection(m.dmzTitle) {
            SwitchRow(m.dmzEnable, d.enabled, { set(d.copy(enabled = it)) })
            if (d.enabled) {
                FormField(m.dmzHost, d.host, { set(d.copy(host = it.trim())) }, hint = m.dmzHostHint, keyboard = KeyboardType.Decimal, isError = !hostOk)
                FormField(m.srcAddress, d.source, { set(d.copy(source = it.trim())) }, hint = m.srcHint)
                SwitchRow(m.dmzLeaveRemote, d.leaveRemoteAccess, { set(d.copy(leaveRemoteAccess = it)) })
            }
        }
        WarningNote(m.dmzWarning)
    }
    form.ConfirmAndSave(m.confirmApply, toast) { api.saveDmz(it) }
}

// ---------------- Admin access ----------------

@Composable
private fun AdminPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit, onLogout: () -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val form = rememberFormState { api.adminSettings() }
    val a = form.edit
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    val passOk = pass == pass2
    val portOk: (String) -> Boolean = { it.toIntOrNull() in 1..65535 }
    val valid = a != null && passOk && (a.httpEnabled || a.httpsEnabled) &&
        listOf(a.httpPort, a.httpsPort, a.remotePort, a.sshPort, a.sshRemotePort).all(portOk)

    ManagePage(m.adminTitle, onBack, form.saving, (form.dirty || pass.isNotEmpty()) && valid, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (a == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        val set: (AdminSettings) -> Unit = { form.edit = it }
        val digits: (String) -> String = { it.filter(Char::isDigit).take(5) }

        FormSection(m.localAccess) {
            val mode = when { a.httpEnabled && a.httpsEnabled -> 2; a.httpsEnabled -> 1; else -> 0 }
            SegmentedChoice(listOf("HTTP", "HTTPS", "HTTP + HTTPS"), mode) {
                set(a.copy(httpEnabled = it != 1, httpsEnabled = it != 0))
            }
            if (a.httpEnabled) FormField(m.httpPort, a.httpPort, { set(a.copy(httpPort = digits(it))) }, keyboard = KeyboardType.Number, isError = !portOk(a.httpPort))
            if (a.httpsEnabled) FormField(m.httpsPort, a.httpsPort, { set(a.copy(httpsPort = digits(it))) }, keyboard = KeyboardType.Number, isError = !portOk(a.httpsPort))
        }
        FormSection(m.remoteAccess) {
            SwitchRow(m.remoteEnable, a.remoteEnabled, { set(a.copy(remoteEnabled = it)) })
            if (a.remoteEnabled) {
                SegmentedChoice(listOf("HTTP", "HTTPS"), if (a.remoteHttps) 1 else 0) { set(a.copy(remoteHttps = it == 1)) }
                FormField(m.remotePort, a.remotePort, { set(a.copy(remotePort = digits(it))) }, keyboard = KeyboardType.Number, isError = !portOk(a.remotePort))
                FormField(m.remoteAllowed, a.remoteAllowed, { set(a.copy(remoteAllowed = it.trim())) }, hint = m.remoteAllowedHint)
            }
        }
        FormSection(m.ssh) {
            SwitchRow(m.sshStartup, a.sshAtStartup, { set(a.copy(sshAtStartup = it)) })
            FormField(m.sshPort, a.sshPort, { set(a.copy(sshPort = digits(it))) }, keyboard = KeyboardType.Number, isError = !portOk(a.sshPort))
            SwitchRow(m.sshRemote, a.sshRemote, { set(a.copy(sshRemote = it)) })
            if (a.sshRemote) FormField(m.sshRemotePort, a.sshRemotePort, { set(a.copy(sshRemotePort = digits(it))) }, keyboard = KeyboardType.Number, isError = !portOk(a.sshRemotePort))
            SwitchRow(m.sshPassword, a.sshPasswordLogin, { set(a.copy(sshPasswordLogin = it)) })
            SwitchRow(m.telnetStartup, a.telnetAtStartup, { set(a.copy(telnetAtStartup = it)) })
        }
        FormSection(m.credentials) {
            FormField(s.usernameLabel, a.username, { set(a.copy(username = it.trim())) })
            FormField(m.newPassword, pass, { pass = it }, hint = m.newPasswordHint, password = true)
            if (pass.isNotEmpty()) {
                FormField(m.confirmPassword, pass2, { pass2 = it }, password = true, isError = !passOk, hint = if (!passOk) m.passwordsMismatch else null)
            }
        }
        WarningNote(m.adminWarning)
    }

    // the admin service restart drops our session, so log out after saving
    val scope = rememberCoroutineScope()
    form.ConfirmAndSave(m.adminWarning, toast, afterSave = { scope.launch { delay(2500); onLogout() } }) {
        api.saveAdmin(it, pass.ifEmpty { null })
    }
}

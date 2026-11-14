package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.StaticLease
import com.troks.tomatorouters.data.isIp
import com.troks.tomatorouters.data.isValidHostname
import com.troks.tomatorouters.data.saveStaticLeases
import com.troks.tomatorouters.data.staticLeases

/** Makes a device name usable as a DHCP hostname (ASCII letters, digits, '-', '.', '_'). */
fun toHostname(name: String) = name.replace(' ', '-').filter { it.isLetterOrDigit() && it.code < 128 || it in "-._" }.take(63)

/**
 * Add / edit a static IP reservation. Used both from the Devices screen (MAC fixed)
 * and from the Static IP page in Manage.
 * [onRemove] shows a "remove static IP" button when the lease already exists.
 */
@Composable
fun LeaseDialog(
    initial: StaticLease,
    title: String,
    macEditable: Boolean,
    takenIps: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onSave: (StaticLease) -> Unit,
) {
    val s = LocalStrings.current
    val m = s.m
    var mac by remember { mutableStateOf(initial.macs.joinToString(",")) }
    var ip by remember { mutableStateOf(initial.ip) }
    var name by remember { mutableStateOf(initial.hostname) }
    var bind by remember { mutableStateOf(initial.bindArp) }
    val macs = mac.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    val macOk = macs.isNotEmpty() && macs.all { RouterApi.normMac(it) != null }
    val ipTaken = ip in takenIps
    val ipOk = isIp(ip) && !ipTaken
    val nameOk = isValidHostname(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PushPin, null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (macEditable) {
                    FormField(m.macAddress, mac, { mac = it }, hint = "AA:BB:CC:DD:EE:FF", isError = mac.isNotEmpty() && !macOk)
                } else {
                    InfoRow(m.macAddress, "‎" + initial.mac)
                }
                FormField(
                    s.ipAddress, ip, { ip = it.trim() },
                    keyboard = KeyboardType.Decimal,
                    isError = ip.isNotEmpty() && !ipOk,
                    hint = if (ipTaken) m.ipInUse else "192.168.1.x",
                )
                FormField(m.hostname, name, { name = it.trim() }, hint = m.hostnameHint, isError = !nameOk)
                SwitchRow(m.bindArp, bind, { bind = it }, subtitle = m.bindArpDesc)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(m.dhcpNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onRemove != null) {
                    OutlinedButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(m.removeReservation)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = macOk && ipOk && nameOk,
                onClick = { onSave(initial.copy(macs = macs.mapNotNull { RouterApi.normMac(it) }, ip = ip, hostname = name, bindArp = bind)) },
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Manage > Static IP addresses: full list of reservations (basic-static.asp). */
@Composable
fun StaticIpPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val form = rememberFormState { api.staticLeases() }
    val leases = form.edit
    var editing by remember { mutableStateOf<Pair<Int, StaticLease>?>(null) } // index -1 = new
    var deleting by remember { mutableIntStateOf(-1) }

    ManagePage(m.staticTitle, onBack, form.saving, form.dirty, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (leases == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        if (leases.isEmpty()) Text(m.noLeases, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        leases.forEachIndexed { i, l ->
            LeaseCard(l, onEdit = { editing = i to l }, onDelete = { deleting = i })
        }
        OutlinedButton(
            onClick = { editing = -1 to StaticLease(emptyList(), "", "", false) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(m.addLease)
        }
        if (form.dirty) WarningNote(m.unsaved)
    }

    editing?.let { (index, lease) ->
        val others = leases.orEmpty().filterIndexed { i, _ -> i != index }.map { it.ip }.toSet()
        LeaseDialog(
            initial = lease,
            title = if (index < 0) m.addLease else m.editLease,
            macEditable = true,
            takenIps = others,
            onDismiss = { editing = null },
        ) { updated ->
            val list = leases.orEmpty().toMutableList()
            if (index < 0) list.add(updated) else list[index] = updated
            form.edit = list
            editing = null
        }
    }
    if (deleting >= 0) {
        val l = leases?.getOrNull(deleting)
        if (l == null) deleting = -1 else ConfirmDialog(
            text = m.removeReservationConfirm(l.hostname.ifBlank { l.mac }),
            confirmLabel = m.delete,
            onDismiss = { deleting = -1 },
            onConfirm = {
                form.edit = leases.toMutableList().also { it.removeAt(deleting) }
                deleting = -1
            },
        )
    }
    form.ConfirmAndSave(m.confirmApply, toast) { api.saveStaticLeases(it) }
}

@Composable
private fun LeaseCard(l: StaticLease, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.PushPin, container = cs.tertiaryContainer, content = cs.onTertiaryContainer)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(l.hostname.ifBlank { s.unknownDevice }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("‎${l.ip}", style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                Text("‎" + l.macs.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, s.m.editLease) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, s.m.delete, tint = cs.error) }
        }
    }
}

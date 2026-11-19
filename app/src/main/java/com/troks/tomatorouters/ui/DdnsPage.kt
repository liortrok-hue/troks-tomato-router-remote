package com.troks.tomatorouters.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.troks.tomatorouters.data.DDNS_SERVICES
import com.troks.tomatorouters.data.DdnsClient
import com.troks.tomatorouters.data.DdnsSettings
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.ddnsService
import com.troks.tomatorouters.data.ddnsSettings
import com.troks.tomatorouters.data.isIp
import com.troks.tomatorouters.data.isValid
import com.troks.tomatorouters.data.saveDdns

/** Basic > DDNS: Dynamic DNS clients 1 and 2, with the same fields as the router page. */
@Composable
internal fun DdnsPage(api: RouterApi, toast: (String) -> Unit, onBack: () -> Unit) {
    val m = LocalStrings.current.m
    val form = rememberFormState { api.ddnsSettings() }
    val d = form.edit
    val valid = d?.clients?.all { it.isValid() } == true

    ManagePage(m.ddnsTitle, onBack, form.saving, form.dirty && valid, onSave = { form.confirm = true }) {
        form.load.error?.let { ErrorBox(it, form.load::reload) }
        if (d == null) {
            if (form.load.error == null) LoadingBox()
            return@ManagePage
        }
        d.clients.forEachIndexed { i, c ->
            ClientSection(i + 1, c, d.wanIp) { updated ->
                form.edit = d.copy(clients = d.clients.toMutableList().also { it[i] = updated })
            }
        }
        WarningNote(m.ddnsWarning)
    }
    form.ConfirmAndSave(m.ddnsWarning, toast) { api.saveDdns(it, form.load.data ?: it) }
}

@Composable
private fun ClientSection(n: Int, c: DdnsClient, wanIp: String, set: (DdnsClient) -> Unit) {
    val s = LocalStrings.current
    val m = s.m
    val svc = ddnsService(c.service)
    FormSection(m.ddnsClient(n)) {
        val options = listOf("" to m.ddnsNone) + DDNS_SERVICES.map { it.id to it.name } +
            (if (c.service.isNotEmpty() && svc == null) listOf(c.service to c.service) else emptyList())
        DropdownField(m.ddnsService, options, c.service) { set(c.copy(service = it)) }
        if (svc == null) {
            if (c.lastResponse.isNotEmpty()) InfoRow(m.ddnsLastResponse, c.lastResponse)
            return@FormSection
        }
        if (svc.url.isNotEmpty()) {
            Text(svc.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        // service fields, in the router page's order
        if (svc.has('t')) FormField(m.ddnsHostname, c.host, { set(c.copy(host = it.trim())) }, keyboard = KeyboardType.Uri)
        if (svc.usesUser) FormField(svc.userLabel ?: m.ddnsUsername, c.user, { set(c.copy(user = it.trim())) })
        if (svc.usesPass) FormField(svc.passLabel ?: s.password, c.pass, { set(c.copy(pass = it)) }, password = true)
        if (svc.has('h') || svc.has('j')) FormField(svc.hostLabel ?: m.ddnsHostname, c.host, { set(c.copy(host = it.trim())) }, keyboard = KeyboardType.Uri)
        if (svc.has('c')) FormField(m.ddnsCustomUrl, c.extra, { set(c.copy(extra = it.trim())) }, keyboard = KeyboardType.Uri, hint = m.ddnsCustomUrlHint)
        if (svc.has('w')) SwitchRow(svc.wildLabel ?: m.ddnsWildcard, c.wildcard, { set(c.copy(wildcard = it)) })
        if (svc.has('m')) FormField("MX", c.mx, { set(c.copy(mx = it.trim())) }, keyboard = KeyboardType.Uri)
        if (svc.has('b')) SwitchRow(svc.bmxLabel ?: m.ddnsBackupMx, c.backupMx, { set(c.copy(backupMx = it)) })
        if (svc.has('o')) SwitchRow(m.ddnsUseAsDns, (c.openDnsMask and 1) != 0, { on ->
            set(c.copy(openDnsMask = if (on) c.openDnsMask or 1 else c.openDnsMask and 1.inv()))
        })
        if (svc.has('n')) FormField(svc.tokenLabel ?: m.ddnsToken, c.extra, { set(c.copy(extra = it.trim())) })
        if (svc.has('s')) SwitchRow(m.ddnsSaveState, c.saveState, { set(c.copy(saveState = it)) })
        SwitchRow(m.ddnsForce, c.force, { set(c.copy(force = it)) })

        // where the IP to publish comes from
        val ipOptions = buildList {
            add("wan" to m.ddnsUseWanIp(wanIp))
            add("@1" to m.ddnsExternalChecker)
            add("0.0.0.0" to "${m.ddnsOffline} (0.0.0.0)")
            add("10.1.1.1" to "${m.ddnsOffline} (10.1.1.1)")
            add(DdnsClient.CUSTOM_IP to m.ddnsCustomIp + "…")
            if (none { it.first == c.ipSource }) add(c.ipSource to c.ipSource) // e.g. a second WAN
        }
        DropdownField(m.ddnsIpAddress, ipOptions, c.ipSource) { set(c.copy(ipSource = it)) }
        if (c.ipSource == DdnsClient.CUSTOM_IP) {
            FormField(
                m.ddnsCustomIp, c.customIp, { set(c.copy(customIp = it.trim())) },
                keyboard = KeyboardType.Decimal, isError = c.customIp.isNotEmpty() && !isIp(c.customIp),
            )
        }
        if (c.ipSource.startsWith("@")) {
            FormField(
                m.ddnsCheckEvery, c.checkMinutes, { set(c.copy(checkMinutes = it.filter(Char::isDigit).take(5))) },
                keyboard = KeyboardType.Number, hint = m.ddnsCheckHint,
                isError = (c.checkMinutes.toIntOrNull() ?: 0) !in 5..99999,
            )
        }
        FormField(
            m.ddnsRefresh, c.refreshDays, { set(c.copy(refreshDays = it.filter(Char::isDigit).take(2))) },
            keyboard = KeyboardType.Number, hint = m.ddnsRefreshHint,
            isError = (c.refreshDays.toIntOrNull() ?: -1) !in 0..90,
        )
        InfoRow(m.ddnsLastUpdate, c.lastUpdate.ifEmpty { "—" })
        InfoRow(m.ddnsLastResponse, c.lastResponse.ifEmpty { "—" })
    }
}

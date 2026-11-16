package com.troks.tomatorouters.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.RouterApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


@Composable
fun ToolsScreen(api: RouterApi, initialHost: String, toast: (String) -> Unit) {
    val s = LocalStrings.current
    val tools = listOf(s.ping, "Traceroute", "DNS", s.toolCommand)
    var tool by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf(initialHost.ifBlank { "8.8.8.8" }) }
    var count by remember { mutableStateOf("4") }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var confirmReboot by remember { mutableStateOf(false) }

    fun run() {
        running?.cancel()
        output = ""
        running = scope.launch {
            output = try {
                when (tool) {
                    0 -> api.ping(host, count.toIntOrNull() ?: 4, 56)
                    1 -> api.traceroute(host)
                    2 -> api.nslookup(host)
                    else -> api.run(command)
                }.ifBlank { s.noOutput }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                s.errorPrefix + friendlyError(e, s)
            } finally {
                if (running === coroutineContext[Job]) running = null
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormLabel(s.m.diagnostics)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tools.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tool == i,
                    onClick = { tool = i; output = "" },
                    shape = segmentShape(i, tools.size),
                    icon = {},
                ) { Text(label, maxLines = 1) }
            }
        }

        if (tool < 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(s.hostOrIp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { run() }),
                    modifier = Modifier.weight(1f),
                )
                if (tool == 0) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter(Char::isDigit).take(2) },
                        label = { Text(s.count) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f),
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text(s.shellLabel) },
                placeholder = { Text("nvram get wan_ipaddr") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Text(
                s.shellWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { if (running != null) { running?.cancel(); running = null } else run() },
            enabled = if (tool < 3) host.isNotBlank() else command.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (running != null) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(s.stop)
            } else {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(s.run)
            }
        }

        if (tool == 0) PingSummary(output)

        if (output.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                // terminal output is left-to-right
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        Text(
                            output,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // system section: reboot lives here, behind an "are you sure?" dialog
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Outlined.RestartAlt,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(s.rebootRouter, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(s.m.rebootDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = { confirmReboot = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s.reboot) }
            }
        }
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            icon = { Icon(Icons.Outlined.RestartAlt, null) },
            title = { Text(s.rebootConfirmTitle) },
            text = { Text(s.rebootConfirmText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReboot = false
                        scope.runAction(toast, s, s.rebooting) { api.reboot() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s.reboot) }
            },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text(s.cancel) } },
        )
    }
}

@Composable
internal fun PingSummary(out: String) {
    val s = LocalStrings.current
    val stats = Regex("(\\d+) packets transmitted, (\\d+) (?:packets )?received").find(out) ?: return
    val sent = stats.groupValues[1].toInt()
    val recv = stats.groupValues[2].toInt()
    val avg = Regex("= [\\d.]+/([\\d.]+)/").find(out)?.groupValues?.get(1)
    val ok = recv > 0
    Text(
        (if (ok) "✅ " else "❌ ") + s.pingResult(recv, sent) + (avg?.let { "  ·  " + s.pingAvg(it) } ?: ""),
        fontWeight = FontWeight.SemiBold,
        color = if (ok) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error,
    )
}

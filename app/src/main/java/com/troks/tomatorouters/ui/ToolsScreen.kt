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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
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
    // empty by default (8.8.8.8 is only a hint); recent addresses are in the field's drop-down
    var host by remember { mutableStateOf(initialHost) }
    val context = LocalContext.current
    var hostHistory by remember { mutableStateOf(HostHistory.load(context)) }
    var count by remember { mutableStateOf("4") }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var confirmReboot by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    // result of the last run: which tool ran, whether it threw, and the popup with its full output
    var ranTool by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    // Command tool: index into COMMAND_PRESETS, or CUSTOM for a typed command
    var preset by remember { mutableIntStateOf(0) }
    var ranPreset by remember { mutableIntStateOf(CUSTOM) }
    var showCommand by remember { mutableStateOf(false) }

    fun run() {
        running?.cancel()
        output = ""
        failed = false
        // remember the address for the drop-down (Ping / Traceroute / DNS)
        if (tool < 3) hostHistory = HostHistory.add(context, host)
        ranTool = tool
        ranPreset = preset
        running = scope.launch {
            output = try {
                when (tool) {
                    0 -> api.ping(host, count.toIntOrNull() ?: 4, 56)
                    1 -> api.traceroute(host)
                    2 -> api.nslookup(host)
                    else -> api.run(if (preset == CUSTOM) command else COMMAND_PRESETS[preset].command)
                }.ifBlank { s.noOutput }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
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
                HostField(
                    value = host,
                    onChange = { host = it },
                    history = hostHistory,
                    label = s.hostOrIp,
                    onGo = { if (host.isNotBlank()) run() },
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
            // ready-made commands first, "Custom command…" last
            val options = COMMAND_PRESETS.mapIndexed { i, p -> i.toString() to p.label(s) } + (CUSTOM.toString() to s.cmdCustom)
            DropdownField(s.cmdChoose, options, preset.toString()) { preset = it.toInt(); output = "" }
            if (preset == CUSTOM) {
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
        }

        Box(Modifier.fillMaxWidth()) {
            Button(
                onClick = { if (running != null) { running?.cancel(); running = null } else run() },
                enabled = if (tool < 3) host.isNotBlank() else preset != CUSTOM || command.isNotBlank(),
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
            // ready-made command: a small "?" at the end of the Run button shows what runs on the router
            if (tool == 3 && preset != CUSTOM && running == null) {
                IconButton(
                    onClick = { showCommand = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, s.cmdShow, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // one result line instead of a console: "Running…", then a green/red summary; tap it for the full output
        ToolResult(
            running = running != null,
            summary = if (output.isEmpty()) null else toolSummary(ranTool, output, failed, s, ranPreset),
            onOpen = { showOutput = true },
        )
        Spacer(Modifier.weight(1f))

        // router system log (Status > Logs)
        Card(
            onClick = { showLogs = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.AutoMirrored.Outlined.ReceiptLong)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(s.mon.logs, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(s.mon.logsDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

    if (showLogs) LogsDialog(api) { showLogs = false }
    if (showCommand && preset != CUSTOM) OutputDialog(COMMAND_PRESETS[preset].label(s), COMMAND_PRESETS[preset].command, titleSuffix = false) { showCommand = false }
    if (showOutput) OutputDialog(if (ranTool == 3 && ranPreset != CUSTOM) COMMAND_PRESETS[ranPreset].label(s) else tools[ranTool], output) { showOutput = false }

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

/** Short result of a diagnostic: the text to show and whether it went well. */
private fun toolSummary(tool: Int, out: String, failed: Boolean, s: Strings, preset: Int = CUSTOM): Pair<String, Boolean> {
    if (failed) return out.removePrefix(s.errorPrefix).ifBlank { s.toolFailed } to false
    val firstLine = out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return when (tool) {
        0 -> {
            val stats = Regex("""(\d+) packets transmitted, (\d+) (?:packets )?received""").find(out)
                ?: return firstLine.ifEmpty { s.toolFailed } to false
            val sent = stats.groupValues[1].toInt()
            val recv = stats.groupValues[2].toInt()
            val avg = Regex("""= [\d.]+/([\d.]+)/""").find(out)?.groupValues?.get(1)
            (s.pingResult(recv, sent) + (avg?.let { "  ·  " + s.pingAvg(it) } ?: "")) to (recv > 0)
        }
        1 -> {
            // hop lines look like " 3  10.0.0.1 (10.0.0.1)  4.1 ms" or " 7  *"
            val hops = out.lines().filter { Regex("""^\s*\d+\s""").containsMatchIn(it) }
            val last = hops.lastOrNull()
            if (last == null || last.substringAfter(' ').trim().trimStart { it.isDigit() }.trim().startsWith("*")) {
                (if (hops.isEmpty()) firstLine.ifEmpty { s.traceFailed } else s.traceFailed) to false
            } else s.traceHops(hops.size) to true
        }
        2 -> {
            // busybox nslookup: the answer comes after "Name:"; "Address 1: 1.2.3.4 host" or "Address: 1.2.3.4"
            val answer = out.substringAfter("Name:", "")
            val ips = Regex("""Address(?: \d+)?:\s*([0-9a-fA-F:.]+)""").findAll(answer).map { it.groupValues[1] }.toList()
            if (ips.isEmpty()) s.dnsFailed to false
            else s.dnsResolved(ips.take(2).joinToString(", ") + if (ips.size > 2) " …" else "") to true
        }
        else -> COMMAND_PRESETS.getOrNull(preset)?.summary?.invoke(out, s)
            ?: (s.cmdDone(if (out == s.noOutput) 0 else out.trimEnd().lines().size) to true)
    }
}

@Composable
private fun ToolResult(running: Boolean, summary: Pair<String, Boolean>?, onOpen: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    if (!running && summary == null) return
    val dark = cs.background.luminance() < 0.5f
    val green = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    Card(
        onClick = onOpen,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow, disabledContainerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running || summary == null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(s.toolRunning, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            } else {
                val (text, ok) = summary
                val color = if (ok) green else cs.error
                Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, Modifier.size(24.dp), tint = color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                    Text(s.tapForDetails, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
            }
        }
    }
}

/** The full command output in a popup (terminal text is always left to right). */
@Composable
private fun OutputDialog(title: String, output: String, titleSuffix: Boolean = true, onClose: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (titleSuffix) "$title · ${s.toolOutput}" else title) },
        text = {
            Card(
                Modifier.fillMaxWidth().heightIn(max = 420.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        Text(
                            output,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                // command output keeps its columns (scroll sideways); a command line just wraps
                                .then(if (titleSuffix) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                                .padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.close) } },
    )
}

/** Command tool: "custom command" entry in the preset list. */
private const val CUSTOM = -1

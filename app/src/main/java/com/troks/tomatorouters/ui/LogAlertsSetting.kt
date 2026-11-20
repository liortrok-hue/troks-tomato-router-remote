package com.troks.tomatorouters.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.troks.tomatorouters.data.LogAlerts
import com.troks.tomatorouters.data.LogLevel
import com.troks.tomatorouters.data.alertStrings

/**
 * Settings: notifications about new log lines of chosen levels, in a frame marked "Beta".
 * Checking while the app is open and while it's closed are switched on separately.
 */
@Composable
fun LogAlertsSetting(vm: AppViewModel) {
    val a = alertStrings(vm.language)
    SettingsFrame(badge = a.beta) { LogAlertsContent(vm, a) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogAlertsContent(vm: AppViewModel, a: com.troks.tomatorouters.data.LogAlertStrings) {
    val ctx = LocalContext.current
    val levelNames = vm.language.strings.mon.levelNames
    var blocked by remember { mutableStateOf(false) }
    // the switch that asked for permission, turned on once it's granted
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Android 13+ asks the user before an app may show notifications
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        if (granted) pending?.invoke()
        pending = null
    }
    fun switchOn(on: Boolean, apply: (Boolean) -> Unit) {
        if (on && Build.VERSION.SDK_INT >= 33 && !LogAlerts.canNotify(ctx)) {
            pending = { apply(true) }
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            blocked = false
            apply(on)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(a.setting, style = MaterialTheme.typography.bodyLarge)
            Text(a.settingDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (blocked) Text(a.blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

    // ---- while the app is open
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    SwitchRow(a.openTitle, a.openDesc, vm.logAlertsOpen) { on -> switchOn(on, vm::changeLogAlertsOpen) }
    if (vm.logAlertsOpen) {
        DropdownField(
            label = a.intervalLabel,
            options = LogAlerts.OPEN_MINUTES.mapIndexed { i, m -> m.toString() to a.openIntervals[i] },
            selected = vm.logAlertOpenMinutes.toString(),
            onSelect = { v -> vm.changeLogAlertOpenMinutes(v.toInt()) },
        )
    }

    // ---- while the app is closed
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    SwitchRow(a.closedTitle, a.closedDesc, vm.logAlertsClosed) { on -> switchOn(on, vm::changeLogAlertsClosed) }
    if (vm.logAlertsClosed) {
        ClosedInterval(vm, a)
        BatteryRow(a)
        if (!vm.login.remember) {
            Text(a.needRemember, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ---- which levels (up to 3), shared by both
    if (vm.logAlertsOpen || vm.logAlertsClosed) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        SubLabel(a.levelsLabel)
        val chosen = vm.logAlertLevels
        val full = chosen.size >= LogAlerts.MAX_LEVELS
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogLevel.entries.forEach { lv ->
                val on = lv in chosen
                FilterChip(
                    selected = on,
                    onClick = { vm.toggleLogAlertLevel(lv) },
                    label = { Text(levelNames[lv.ordinal]) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Outlined.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    // a 4th can't be added; the last one can't be removed
                    enabled = if (on) chosen.size > 1 else !full,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** How often to check when the app is closed: presets, or a custom value (60+ min). */
@Composable
private fun ClosedInterval(vm: AppViewModel, a: com.troks.tomatorouters.data.LogAlertStrings) {
    val minutes = vm.logAlertMinutes
    var customMode by remember { mutableStateOf(vm.logAlertCustom) }
    DropdownField(
        label = a.intervalLabel,
        options = LogAlerts.PRESET_MINUTES.mapIndexed { i, m -> m.toString() to a.intervals[i] } + ("custom" to a.custom),
        selected = if (customMode) "custom" else minutes.toString(),
        onSelect = { v ->
            if (v == "custom") customMode = true
            else { customMode = false; vm.changeLogAlertMinutes(v.toInt(), custom = false) }
        },
    )
    if (customMode) {
        val focus = LocalFocusManager.current
        var text by remember { mutableStateOf(if (vm.logAlertCustom) minutes.toString() else "") }
        val value = text.toIntOrNull()
        val valid = value != null && value in LogAlerts.MIN_CUSTOM_MINUTES..LogAlerts.MAX_CUSTOM_MINUTES
        // saved only on "Save" (or the keyboard's Done), so half-typed numbers never take effect
        val saved = vm.logAlertCustom && value == minutes
        fun save() {
            if (valid && vm.changeLogAlertMinutes(value!!, custom = true)) focus.clearFocus()
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter(Char::isDigit).take(4) },
                label = { Text(a.minutes(LogAlerts.MIN_CUSTOM_MINUTES)) },
                isError = text.isNotEmpty() && !valid,
                supportingText = when {
                    text.isNotEmpty() && !valid -> { { Text(a.minutesError(LogAlerts.MIN_CUSTOM_MINUTES)) } }
                    saved -> { { Text(a.saved(minutes), color = MaterialTheme.colorScheme.primary) } }
                    else -> null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = ::save,
                enabled = valid && !saved,
                modifier = Modifier.padding(top = 8.dp).height(56.dp),
            ) { Text(a.save) }
        }
    }
}

/** Shows whether Android lets the app check in the background, with a shortcut to change it. */
@Composable
private fun BatteryRow(a: com.troks.tomatorouters.data.LogAlertStrings) {
    val ctx = LocalContext.current
    // re-read when coming back from the system settings
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val battery = remember(tick) { LogAlerts.battery(ctx) }
    val cs = MaterialTheme.colorScheme
    val ok = battery == LogAlerts.Battery.UNRESTRICTED
    Surface(
        color = if (ok) cs.secondaryContainer else cs.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert, null,
                    tint = if (ok) cs.onSecondaryContainer else cs.onErrorContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    when (battery) {
                        LogAlerts.Battery.UNRESTRICTED -> a.batteryOk
                        LogAlerts.Battery.OPTIMIZED -> a.batteryOptimized
                        LogAlerts.Battery.RESTRICTED -> a.batteryRestricted
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) cs.onSecondaryContainer else cs.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!ok) {
                Text(a.batteryHow, style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer)
                OutlinedButton(onClick = { LogAlerts.openBatterySettings(ctx) }) { Text(a.batteryButton) }
            }
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

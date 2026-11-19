package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    val l = vm.login
    var showPass by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var portText by remember { mutableStateOf(l.port.toString()) }
    val activity = LocalContext.current as FragmentActivity

    fun unlock() {
        showBiometricPrompt(
            activity,
            title = s.biometricTitle,
            subtitle = "${l.username}@${l.host}",
            negativeText = s.usePassword,
            onSuccess = vm::biometricUnlocked,
        )
    }
    // remembered login: ask for fingerprint / face as soon as the app opens (once per launch)
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (vm.biometricLocked && !prompted) {
            prompted = true
            unlock()
        }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            IconBadge(
                Icons.Outlined.Router,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 88.dp,
            )
            Text("Troks Tomato Router Remote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(s.appSubtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                Modifier.fillMaxWidth().widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = l.host,
                        onValueChange = { vm.update(l.copy(host = it.trim())) },
                        label = { Text(s.hostLabel) },
                        placeholder = { Text("192.168.1.1") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { t ->
                                portText = t.filter(Char::isDigit).take(5)
                                vm.update(l.copy(port = portText.toIntOrNull() ?: 0))
                            },
                            label = { Text(s.portLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("HTTPS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = l.https,
                            onCheckedChange = { on ->
                                // follow the usual port when it's still on the other default (80 <-> 443)
                                val port = when {
                                    on && l.port == 80 -> 443
                                    !on && l.port == 443 -> 80
                                    else -> l.port
                                }
                                portText = port.toString()
                                vm.update(l.copy(https = on, port = port))
                            },
                        )
                    }
                    OutlinedTextField(
                        value = l.username,
                        onValueChange = { vm.update(l.copy(username = it)) },
                        label = { Text(s.usernameLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.biometricLocked) {
                        // saved password is waiting for fingerprint / face; typing below replaces it
                        FilledTonalButton(onClick = ::unlock, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(s.unlockBiometric)
                        }
                    }
                    OutlinedTextField(
                        value = l.password,
                        onValueChange = { vm.update(l.copy(password = it)) },
                        label = { Text(s.password) },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.connect() }),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPass) s.hide else s.show,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = l.remember, onCheckedChange = { vm.update(l.copy(remember = it)) })
                        Column {
                            Text(s.rememberPassword, style = MaterialTheme.typography.bodyMedium)
                            if (vm.biometricAvailable) {
                                Text(
                                    s.rememberBiometricNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    vm.loginError?.let { err ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(err(s), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    vm.certChanged?.let { fp ->
                        Text(s.certChangedInfo(fp), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = vm::trustNewCertificate) { Text(s.trustNewCert) }
                    }

                    Button(
                        onClick = vm::connect,
                        enabled = !vm.connecting,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (vm.connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text(s.connect, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            TextButton(onClick = vm::startDemo) {
                Icon(Icons.Outlined.PlayCircle, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(s.tryDemo)
            }
            Text(
                s.httpsNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        // About and Settings in the top corner, same order as in the app's top bar
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { showAbout = true }) {
                Icon(Icons.Outlined.Info, contentDescription = s.about)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = s.settings)
            }
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
}

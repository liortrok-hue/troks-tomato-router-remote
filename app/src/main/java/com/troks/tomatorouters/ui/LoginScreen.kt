package com.troks.tomatorouters.ui

import com.troks.tomatorouters.data.cleanHost
import com.troks.tomatorouters.data.SavedLogin
import com.troks.tomatorouters.data.Profile
import com.troks.tomatorouters.data.MAX_PROFILES
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.key
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.Public
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
    val x = loginExtraStrings(vm.language)
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
                Column(Modifier.padding(20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // saved profiles: picking a name fills in everything below
                    ProfileCard(vm, x) { if (vm.biometricLocked) unlock() }

                    // addresses, ports and user name: folded into a one-line summary once a profile is
                    // set up, opened for a new one, on "Edit", or when signing in fails
                    var detailsOpen by rememberSaveable(vm.activeProfileId) {
                        mutableStateOf(vm.activeProfileId == null || l.username.isBlank())
                    }
                    LaunchedEffect(vm.loginError) { if (vm.loginError != null) detailsOpen = true }
                    if (!detailsOpen) {
                        TextButton(onClick = { detailsOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(x.editDetails)
                        }
                    } else key(vm.activeProfileId) {
                    if (vm.activeProfileId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(x.details, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { detailsOpen = false }) {
                                Text(x.hideDetails)
                                Icon(Icons.Outlined.ExpandLess, null, Modifier.size(ButtonDefaults.IconSize))
                            }
                        }
                    }
                    // local address: used first (at home)
                    OutlinedTextField(
                        value = l.host,
                        onValueChange = { vm.update(l.copy(host = it.trim())) },
                        label = { Text(x.localLabel) },
                        placeholder = { Text("192.168.1.1") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PortHttpsRow(l.port, l.https) { port, https -> vm.update(vm.login.copy(port = port, https = https)) }

                    // external address: tried when the local one can't be reached (away from home)
                    OutlinedTextField(
                        value = l.remoteHost,
                        onValueChange = { vm.update(l.copy(remoteHost = it.trim())) },
                        label = { Text(x.remoteLabel) },
                        placeholder = { Text("myrouter.ddns.net") },
                        leadingIcon = { Icon(Icons.Outlined.Public, null) },
                        supportingText = { Text(x.remoteHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // port + HTTPS for the external address: always shown, it often differs from the local one
                    PortHttpsRow(l.remotePort, l.remoteHttps) { port, https ->
                        vm.update(vm.login.copy(remotePort = port, remoteHttps = https))
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
                    } // details
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

/**
 * Port + HTTPS switch for one address. Turning HTTPS on/off moves the port along when it's
 * still on the other default (80 <-> 443).
 */
@Composable
private fun PortHttpsRow(port: Int, https: Boolean, onChange: (port: Int, https: Boolean) -> Unit) {
    val s = LocalStrings.current
    // kept as text so the field can be empty while typing
    var portText by remember { mutableStateOf(port.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = portText,
            onValueChange = { t ->
                portText = t.filter(Char::isDigit).take(5)
                onChange(portText.toIntOrNull() ?: 0, https)
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
            checked = https,
            onCheckedChange = { on ->
                val p = when {
                    on && port == 80 -> 443
                    !on && port == 443 -> 80
                    else -> port
                }
                portText = p.toString()
                onChange(p, on)
            },
        )
    }
}

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    x: LoginExtraStrings,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String) -> Unit,
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Person, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text(x.nameLabel) },
                placeholder = { Text(x.namePlaceholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(x.save) } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(x.delete, color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
        },
    )
}

internal class LoginExtraStrings(
    val localLabel: String,
    val remoteLabel: String,
    val remoteHint: String,
    val profiles: String,
    val add: String,
    /** name shown for a profile that was never named (the login from before profiles existed) */
    val defaultName: String,
    val newProfile: String,
    val editProfile: String,
    val nameLabel: String,
    val namePlaceholder: String,
    val save: String,
    val delete: String,
    val deleteConfirm: (String) -> String,
    val edit: String,
    val details: String,
    val hideDetails: String,
    val chooseProfile: String,
    val addProfile: String,
    val editDetails: String,
    val saveAsProfile: String,
    val saveAsProfileHint: String,
)

internal fun loginExtraStrings(lang: AppLanguage) = when (lang) {
    AppLanguage.HEBREW -> LoginExtraStrings(
        localLabel = "כתובת מקומית (IP / דומיין)",
        remoteLabel = "כתובת חיצונית (IP / דומיין)",
        remoteHint = "לא חובה. משמשת כשהכתובת המקומית לא זמינה, למשל מחוץ לבית",
        profiles = "פרופילים",
        add = "הוסף",
        defaultName = "הראוטר שלי",
        newProfile = "פרופיל חדש",
        editProfile = "עריכת פרופיל",
        nameLabel = "שם",
        namePlaceholder = "למשל: בית, משרד",
        save = "שמור",
        delete = "מחק",
        deleteConfirm = { "למחוק את הפרופיל \"$it\"? הכתובות והסיסמה השמורה שלו יימחקו." },
        edit = "ערוך",
        details = "פרטי חיבור",
        hideDetails = "הסתר",
        chooseProfile = "בחירת פרופיל",
        addProfile = "הוספת פרופיל",
        editDetails = "עריכת פרטי החיבור",
        saveAsProfile = "שמירה כפרופיל",
        saveAsProfileHint = "תן שם לחיבור הזה, כדי לעבור בין ראוטרים בלחיצה",
    )
    AppLanguage.RUSSIAN -> LoginExtraStrings(
        localLabel = "Локальный адрес (IP / домен)",
        remoteLabel = "Внешний адрес (IP / домен)",
        remoteHint = "Необязательно. Используется, если локальный адрес недоступен, например вне дома",
        profiles = "Профили",
        add = "Добавить",
        defaultName = "Мой роутер",
        newProfile = "Новый профиль",
        editProfile = "Изменить профиль",
        nameLabel = "Имя",
        namePlaceholder = "Например: дом, офис",
        save = "Сохранить",
        delete = "Удалить",
        deleteConfirm = { "Удалить профиль «$it»? Его адреса и сохранённый пароль будут удалены." },
        edit = "Изменить",
        details = "Параметры подключения",
        hideDetails = "Скрыть",
        chooseProfile = "Выбор профиля",
        addProfile = "Добавить профиль",
        editDetails = "Изменить параметры подключения",
        saveAsProfile = "Сохранить как профиль",
        saveAsProfileHint = "Дайте подключению имя, чтобы переключаться между роутерами одним касанием",
    )
    AppLanguage.ARABIC -> LoginExtraStrings(
        localLabel = "العنوان المحلي (IP / النطاق)",
        remoteLabel = "العنوان الخارجي (IP / النطاق)",
        remoteHint = "اختياري. يُستخدم عندما يتعذر الوصول إلى العنوان المحلي، مثلاً خارج المنزل",
        profiles = "الملفات الشخصية",
        add = "إضافة",
        defaultName = "الراوتر الخاص بي",
        newProfile = "ملف شخصي جديد",
        editProfile = "تعديل الملف الشخصي",
        nameLabel = "الاسم",
        namePlaceholder = "مثلاً: المنزل، المكتب",
        save = "حفظ",
        delete = "حذف",
        deleteConfirm = { "حذف الملف الشخصي \"$it\"؟ سيتم حذف عناوينه وكلمة المرور المحفوظة." },
        edit = "تعديل",
        details = "تفاصيل الاتصال",
        hideDetails = "إخفاء",
        chooseProfile = "اختيار ملف شخصي",
        addProfile = "إضافة ملف شخصي",
        editDetails = "تعديل تفاصيل الاتصال",
        saveAsProfile = "حفظ كملف شخصي",
        saveAsProfileHint = "سمِّ هذا الاتصال للتبديل بين أجهزة الراوتر بلمسة واحدة",
    )
    AppLanguage.ENGLISH -> LoginExtraStrings(
        localLabel = "Local address (IP / domain)",
        remoteLabel = "External address (IP / domain)",
        remoteHint = "Optional. Used when the local address can't be reached, e.g. away from home",
        profiles = "Profiles",
        add = "Add",
        defaultName = "My router",
        newProfile = "New profile",
        editProfile = "Edit profile",
        nameLabel = "Name",
        namePlaceholder = "e.g. Home, Office",
        save = "Save",
        delete = "Delete",
        deleteConfirm = { "Delete the profile \"$it\"? Its addresses and saved password will be removed." },
        edit = "Edit",
        details = "Connection details",
        hideDetails = "Hide",
        chooseProfile = "Choose a profile",
        addProfile = "Add profile",
        editDetails = "Edit connection details",
        saveAsProfile = "Save as a profile",
        saveAsProfileHint = "Name this connection to switch between routers with one tap",
    )
}

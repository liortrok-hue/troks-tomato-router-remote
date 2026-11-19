package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.BuildConfig

private const val SUPPORT_EMAIL = "TomatoAppSupport@troks.net"
private const val GITHUB_URL = "https://github.com/liortrok-hue/troks-tomato-router-remote"

/** About: app name, version, developer, license, support e-mail and privacy. */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    val s = LocalStrings.current
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            IconBadge(
                Icons.Outlined.Router,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 56.dp,
            )
        },
        title = { Text("Troks Tomato Router Remote", textAlign = TextAlign.Center) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.aboutVersion("⁦${BuildConfig.VERSION_NAME}⁩"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(s.aboutDesc, textAlign = TextAlign.Center)
                Text("${s.aboutDeveloper}: Lior Trok", style = MaterialTheme.typography.bodyMedium)
                Text(s.aboutLicense, style = MaterialTheme.typography.bodyMedium)
                // source code on GitHub
                TextButton(onClick = { runCatching { uri.openUri(GITHUB_URL) } }) {
                    Icon(Icons.Outlined.Code, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("GitHub")
                }
                // support e-mail: tap to write (opens the mail app)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.aboutSupport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { runCatching { uri.openUri("mailto:$SUPPORT_EMAIL") } }) {
                        Icon(Icons.Outlined.Email, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(SUPPORT_EMAIL)
                    }
                }
                Text(
                    s.aboutPrivacy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.close) } },
    )
}

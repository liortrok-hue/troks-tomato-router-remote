package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.Donations

/** Small tonal "$ Donate" pill for the top bar (same height as the status pill); opens the donation sheet. */
@Composable
fun DonateButton(vm: AppViewModel, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    var open by rememberSaveable { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { open = true; vm.openDonations() },
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
        modifier = modifier.height(32.dp),
    ) {
        Icon(Icons.Outlined.Paid, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(s.donate, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (open) DonateSheet(vm, toast) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonateSheet(vm: AppViewModel, toast: (String) -> Unit, onClose: () -> Unit) {
    val s = LocalStrings.current
    val products by vm.donations.products.collectAsState()
    val result by vm.donations.result.collectAsState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(result) {
        when (result) {
            Donations.Result.THANKS -> { toast(s.donateThanks); vm.donations.clearResult(); onClose() }
            Donations.Result.PENDING -> { toast(s.donatePending); vm.donations.clearResult(); onClose() }
            Donations.Result.ERROR -> { toast(s.donateError); vm.donations.clearResult() }
            Donations.Result.CANCELED -> vm.donations.clearResult()
            null -> {}
        }
    }

    // fixed insets: see SettingsSheet (the default top inset changes while dragging and makes the sheet jitter)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(
                Icons.Outlined.Paid,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 64.dp,
            )
            Text(s.donateTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                s.donateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val list = products
            when {
                list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(s.donateLoading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list.isEmpty() || activity == null -> Text(
                    s.donateUnavailable,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEachIndexed { i, p ->
                        val price = p.oneTimePurchaseOfferDetails?.formattedPrice ?: p.name
                        val modifier = Modifier.weight(1f).height(56.dp)
                        // the middle amount is highlighted as the suggested one
                        if (i == list.size / 2) {
                            Button(onClick = { vm.donations.donate(activity, p) }, modifier = modifier) {
                                Text("⁨$price⁩", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            FilledTonalButton(onClick = { vm.donations.donate(activity, p) }, modifier = modifier) {
                                Text("⁨$price⁩", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(ButtonDefaults.IconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(s.donateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

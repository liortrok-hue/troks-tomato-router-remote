package com.troks.tomatorouters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troks.tomatorouters.data.MAX_PROFILES
import com.troks.tomatorouters.data.Profile
import com.troks.tomatorouters.data.SavedLogin
import com.troks.tomatorouters.data.cleanHost

/** Display name: the one given, or "My router" (in the app's language) for an unnamed profile. */
internal fun Profile.displayName(x: LoginExtraStrings) = name.ifBlank { x.defaultName }

/** "192.168.1.1 · admin" (plus the external address, when there is one). */
internal fun SavedLogin.summary(): String = buildList {
    add(cleanHost(host))
    cleanHost(remoteHost).takeIf { it.isNotEmpty() }?.let { add(it) }
    username.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(" · ") { "⁨$it⁩" }

/**
 * Round avatar with the profile's first letter, like a Google account. Each profile gets
 * a different tonal color from the current theme (by its position), so it follows the palette.
 */
@Composable
internal fun ProfileAvatar(name: String, index: Int, size: Dp = 40.dp) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (index.mod(4)) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.tertiaryContainer to cs.onTertiaryContainer
        2 -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.inversePrimary to cs.onSurface
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * The login screen's profile card: avatar, name and address of the chosen profile.
 * Tapping it opens the profile list (choose / edit / add). With no profiles yet it offers to save one.
 */
@Composable
internal fun ProfileCard(vm: AppViewModel, x: LoginExtraStrings, onSelected: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val index = vm.profiles.indexOfFirst { it.id == vm.activeProfileId }
    val active = vm.profiles.getOrNull(index)

    Surface(
        onClick = { if (active == null) naming = true else picking = true },
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active != null) ProfileAvatar(active.displayName(x), index, 44.dp)
            else Box(Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = cs.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    active?.displayName(x) ?: x.saveAsProfile,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (active != null) vm.login.summary() else x.saveAsProfileHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ExpandMore, x.chooseProfile, tint = cs.onSurfaceVariant)
            }
        }
    }

    if (picking) {
        ProfilePickerSheet(vm, x, onDismiss = { picking = false }) { id ->
            picking = false
            if (id != vm.activeProfileId) { vm.selectProfile(id); onSelected() }
        }
    }
    if (naming) {
        NameDialog(x.newProfile, "", x, onDismiss = { naming = false }) { name ->
            vm.addProfile(name)
            naming = false
        }
    }
}

/**
 * Bottom sheet listing the saved profiles, Google account-switcher style: tap one to use it,
 * the pencil renames or deletes it, and "Add profile" adds one (up to [MAX_PROFILES]).
 * [onPick] gets the chosen profile's id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilePickerSheet(vm: AppViewModel, x: LoginExtraStrings, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // fixed insets, so the sheet doesn't jitter while dragged (see SettingsSheet)
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(
                x.chooseProfile,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            vm.profiles.forEachIndexed { i, p ->
                val selected = p.id == vm.activeProfileId
                Surface(
                    onClick = { onPick(p.id) },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) cs.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                p.displayName(x),
                                fontWeight = if (selected) FontWeight.SemiBold else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(p.login.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { ProfileAvatar(p.displayName(x), i) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary)
                                IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, x.editProfile) }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            if (vm.profiles.size < MAX_PROFILES) {
                Surface(
                    onClick = { adding = true },
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(x.addProfile, color = cs.primary) },
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, null, tint = cs.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (adding) {
        NameDialog(x.newProfile, "", x, onDismiss = { adding = false }) { name ->
            adding = false
            if (vm.addProfile(name)) vm.activeProfileId?.let(onPick)
        }
    }
    editing?.let { p ->
        NameDialog(
            x.editProfile, p.displayName(x), x,
            onDismiss = { editing = null },
            onDelete = { editing = null; deleting = p },
        ) { name ->
            vm.renameProfile(p.id, name)
            editing = null
        }
    }
    deleting?.let { p ->
        ConfirmDialog(
            text = x.deleteConfirm(p.displayName(x)),
            confirmLabel = x.delete,
            onConfirm = { vm.deleteProfile(p.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

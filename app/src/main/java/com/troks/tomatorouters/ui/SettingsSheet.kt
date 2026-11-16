package com.troks.tomatorouters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.settings, style = MaterialTheme.typography.headlineSmall)

            SettingLabel(s.language)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { i, lang ->
                    SegmentedButton(
                        selected = vm.language == lang,
                        onClick = { vm.changeLanguage(lang) },
                        shape = segmentShape(i, AppLanguage.entries.size),
                        icon = {},
                    ) { Text(lang.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            SettingLabel(s.theme)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = vm.themeMode == mode,
                        onClick = { vm.changeTheme(mode) },
                        shape = segmentShape(i, ThemeMode.entries.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = vm.themeMode == mode) {
                                Icon(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.padding(0.dp),
                                )
                            }
                        },
                    ) { Text(mode.label(s), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            SettingLabel(s.textSize)
            Text(s.textSizeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TEXT_SCALES.forEachIndexed { i, scale ->
                    SegmentedButton(
                        selected = vm.textScale == scale,
                        onClick = { vm.changeTextScale(scale) },
                        shape = segmentShape(i, TEXT_SCALES.size),
                        icon = {},
                    ) {
                        // each option's "A" is drawn at the size it gives, so the choice is visible without reading
                        Text(
                            "A",
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * (scale / vm.textScale),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.semantics { contentDescription = "${(scale * 100).roundToInt()}%" },
                        )
                    }
                }
            }

            if (dynamicColorSupported) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.dynamicColor, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            s.dynamicColorDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.dynamicColor, onCheckedChange = vm::changeDynamicColor)
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

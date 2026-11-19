package com.troks.tomatorouters.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection

/** The last 10 addresses used in Tools (Ping / Traceroute / DNS), newest first. Stored on this phone only. */
object HostHistory {
    private const val PREFS = "tools_history"
    private const val KEY = "hosts"
    private const val MAX = 10

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    /** Puts [host] first (no duplicates), keeps 10, saves, and returns the new list. */
    fun add(context: Context, host: String): List<String> {
        val h = host.trim()
        if (h.isEmpty()) return load(context)
        val list = (listOf(h) + load(context).filterNot { it.equals(h, ignoreCase = true) }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, list.joinToString("\n")).apply()
        return list
    }
}

/**
 * Address field that you can type in, with a drop-down of recent addresses.
 * Empty by default; the arrow opens the history (hidden when there's none yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostField(
    value: String,
    onChange: (String) -> Unit,
    history: List<String>,
    label: String,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && history.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.trim()) },
            label = { Text(label) },
            placeholder = { Text("8.8.8.8") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { expanded = false; onGo() }),
            trailingIcon = if (history.isEmpty()) null else ({
                // only the arrow opens the list; tapping the text just types
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                ) { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            }),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = false),
        )
        ExposedDropdownMenu(
            expanded = expanded && history.isNotEmpty(),
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            history.forEach { h ->
                DropdownMenuItem(
                    text = { Text(h, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)) },
                    leadingIcon = { Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { onChange(h); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

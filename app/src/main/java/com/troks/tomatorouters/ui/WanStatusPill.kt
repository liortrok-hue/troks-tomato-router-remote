package com.troks.tomatorouters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.troks.tomatorouters.data.WanState

/** Internet status for the top bar: green = connected, yellow = connecting, red = disconnected. */
@Composable
fun WanStatusPill(state: WanState, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = when (state) {
        WanState.UP -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        WanState.CONNECTING -> if (dark) Color(0xFFFFCA28) else Color(0xFFB26A00)
        WanState.DOWN -> MaterialTheme.colorScheme.error
    }
    val label = when (state) {
        WanState.UP -> s.online
        WanState.CONNECTING -> s.connecting
        WanState.DOWN -> s.offline
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

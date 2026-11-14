package com.troks.tomatorouters.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun label(s: Strings) = when (this) {
        SYSTEM -> s.themeSystem
        LIGHT -> s.themeLight
        DARK -> s.themeDark
    }

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

// Material 3 tonal palette built from the tomato-red seed (#E53935) with a leaf-green tertiary.
private val LightColors = lightColorScheme(
    primary = Color(0xFFAF2B22), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5), onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF386A20), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F397), onTertiaryContainer = Color(0xFF042100),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857371), outlineVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7), surfaceContainerHigh = Color(0xFFF6E4E1),
    surfaceContainerHighest = Color(0xFFF1DEDC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A9), onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF8D150F), onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6), onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B), onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFF9DD67D), onTertiary = Color(0xFF0B3900),
    tertiaryContainer = Color(0xFF205107), onTertiaryContainer = Color(0xFFB8F397),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110), onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1110), onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8A), outlineVariant = Color(0xFF534341),
    surfaceContainerLowest = Color(0xFF140C0B), surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C), surfaceContainerHigh = Color(0xFF322826),
    surfaceContainerHighest = Color(0xFF3D3231),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun TomatoTheme(dark: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && dynamicColorSupported -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

/** Status color for "good" states (online, ping ok). */
val ColorScheme.success: Color
    get() = tertiary

/**
 * Shape for a segmented button that is correct in both LTR and RTL:
 * corners are rounded by the segment's on-screen position, not its index.
 */
@Composable
fun segmentShape(index: Int, count: Int): Shape {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val visual = if (rtl) count - 1 - index else index
    return when {
        count == 1 -> RoundedCornerShape(50)
        visual == 0 -> AbsoluteRoundedCornerShape(topLeftPercent = 50, bottomLeftPercent = 50)
        visual == count - 1 -> AbsoluteRoundedCornerShape(topRightPercent = 50, bottomRightPercent = 50)
        else -> RectangleShape
    }
}

/** Icon inside a tonal circle, used as a leading visual in cards and lists. */
@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        // first-strong isolate: "106 / 249 MB" stays left-to-right inside Hebrew/Arabic layouts,
        // while Hebrew/Arabic values keep their own direction
        Text(
            "\u2068" + value.ifBlank { "—" } + "\u2069",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(error: Throwable, onRetry: () -> Unit) {
    val s = LocalStrings.current
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                friendlyError(error, s),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onRetry) { Text(s.retry) }
        }
    }
}

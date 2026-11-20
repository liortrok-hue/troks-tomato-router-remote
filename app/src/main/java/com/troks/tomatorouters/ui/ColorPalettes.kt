package com.troks.tomatorouters.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * App color palettes. [TOMATO] is the original hand-tuned scheme; the others are built from a
 * hue by [toneScheme]. The tertiary (green) colors stay the same in all of them, since they
 * mean "good / online" across the app.
 */
enum class ColorPalette(val hue: Float, val saturation: Float, val primaryTone: Float = 40f) {
    TOMATO(4f, 0.67f),
    OCEAN(215f, 0.60f),
    TEAL(178f, 0.60f),
    FOREST(135f, 0.42f),
    VIOLET(268f, 0.45f),
    ROSE(335f, 0.55f),

    // brighter ones: a lighter main color, with dark text on it
    ORANGE(28f, 0.90f, 55f),
    SUNFLOWER(46f, 0.90f, 52f),
    MINT(155f, 0.55f, 55f),
    SKY(198f, 0.80f, 58f),
    LAVENDER(258f, 0.70f, 72f);

    /** Brighter palettes put dark text and icons on the main color instead of white. */
    val bright: Boolean get() = primaryTone >= 50f

    /** Color of this palette's swatch in Settings. */
    val swatch: Color get() = if (this == TOMATO) Color(0xFFAF2B22) else tone(hue, saturation, primaryTone)

    fun label(lang: AppLanguage): String = paletteNames(lang)[ordinal]

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: TOMATO
    }
}

/** WCAG contrast ratio between two colors (1 to 21). */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

private fun tone(h: Float, s: Float, t: Float) = Color.hsl(h, s.coerceIn(0f, 1f), (t / 100f).coerceIn(0f, 1f))

/** A Material 3 scheme from one hue: primary at tone 40 (light) / 80 (dark), with tinted neutrals. */
internal fun toneScheme(p: ColorPalette, dark: Boolean): ColorScheme {
    val h = p.hue
    val s = p.saturation
    fun t(sat: Float, tone: Float) = tone(h, sat, tone)
    // In light mode the main color is also used for text (links, labels), so a bright one is
    // darkened just enough to stay readable on the light background; the bright tone itself
    // goes to the containers (tonal buttons, selected chips), so the palette still looks bright.
    var lightTone = p.primaryTone
    while (lightTone > 20f && contrast(t(s, lightTone), t(0.25f, 98.5f)) < 3.6f) lightTone -= 1f
    val onLight = if (contrast(t(s, lightTone), Color.White) >= 3f) Color.White else t(s, 10f)
    return if (!dark) lightColorScheme(
        primary = t(s, lightTone), onPrimary = onLight,
        primaryContainer = if (p.bright) t(s, maxOf(p.primaryTone, 72f)) else t(s, 90f),
        onPrimaryContainer = t(s, 12f),
        secondary = t(0.18f, 40f), onSecondary = Color.White,
        secondaryContainer = if (p.bright) t(s * 0.8f, 86f) else t(0.30f, 90f),
        onSecondaryContainer = t(0.30f, 12f),
        tertiary = Color(0xFF386A20), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFB8F397), onTertiaryContainer = Color(0xFF042100),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = t(0.25f, 98.5f), onBackground = t(0.10f, 11f),
        surface = t(0.25f, 98.5f), onSurface = t(0.10f, 11f),
        surfaceVariant = t(0.25f, 90f), onSurfaceVariant = t(0.08f, 30f),
        outline = t(0.06f, 48f), outlineVariant = t(0.15f, 80f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = t(0.25f, 96.5f),
        surfaceContainer = t(0.20f, 95f), surfaceContainerHigh = t(0.18f, 93f),
        surfaceContainerHighest = t(0.15f, 90.5f),
    ) else darkColorScheme(
        primary = t(s, 78f), onPrimary = t(s, 20f),
        primaryContainer = t(s, 30f), onPrimaryContainer = t(s, 90f),
        secondary = t(0.25f, 78f), onSecondary = t(0.20f, 20f),
        secondaryContainer = t(0.18f, 28f), onSecondaryContainer = t(0.30f, 90f),
        tertiary = Color(0xFF9DD67D), onTertiary = Color(0xFF0B3900),
        tertiaryContainer = Color(0xFF205107), onTertiaryContainer = Color(0xFFB8F397),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = t(0.15f, 7f), onBackground = t(0.12f, 90f),
        surface = t(0.15f, 7f), onSurface = t(0.12f, 90f),
        surfaceVariant = t(0.10f, 28f), onSurfaceVariant = t(0.15f, 80f),
        outline = t(0.07f, 58f), outlineVariant = t(0.10f, 28f),
        surfaceContainerLowest = t(0.15f, 5f), surfaceContainerLow = t(0.12f, 10f),
        surfaceContainer = t(0.12f, 12f), surfaceContainerHigh = t(0.10f, 16f),
        surfaceContainerHighest = t(0.08f, 20f),
    )
}

private fun paletteNames(lang: AppLanguage): List<String> = when (lang) {
    AppLanguage.HEBREW -> listOf("עגבנייה", "אוקיינוס", "טורקיז", "יער", "סגול", "ורוד", "כתום", "חמנייה", "מנטה", "שמיים", "לבנדר")
    AppLanguage.RUSSIAN -> listOf("Томат", "Океан", "Бирюза", "Лес", "Фиалка", "Роза", "Апельсин", "Подсолнух", "Мята", "Небо", "Лаванда")
    AppLanguage.ARABIC -> listOf("طماطم", "محيط", "فيروزي", "غابة", "بنفسجي", "وردي", "برتقالي", "دوار الشمس", "نعناع", "سماء", "خزامى")
    AppLanguage.ENGLISH -> listOf("Tomato", "Ocean", "Teal", "Forest", "Violet", "Rose", "Orange", "Sunflower", "Mint", "Sky", "Lavender")
}

fun paletteTitle(lang: AppLanguage): String = when (lang) {
    AppLanguage.HEBREW -> "ערכת צבעים"
    AppLanguage.RUSSIAN -> "Цветовая схема"
    AppLanguage.ARABIC -> "نظام الألوان"
    AppLanguage.ENGLISH -> "Color palette"
}

/** Row of color swatches; none is marked while dynamic color is on (it overrides them). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PalettePicker(lang: AppLanguage, selected: ColorPalette?, onSelect: (ColorPalette) -> Unit) {
    // two rows: the original six, then the brighter five
    FlowRow(
        Modifier.fillMaxWidth(),
        // fixed gaps, so the second row's swatches line up under the first row's
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 6,
    ) {
        ColorPalette.entries.forEach { p ->
            val on = p == selected
            Surface(
                shape = CircleShape,
                color = p.swatch,
                border = if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                modifier = Modifier
                    .size(44.dp)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(p) }
                    .semantics { contentDescription = p.label(lang) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (on) Icon(Icons.Outlined.Check, null, tint = if (p.bright) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

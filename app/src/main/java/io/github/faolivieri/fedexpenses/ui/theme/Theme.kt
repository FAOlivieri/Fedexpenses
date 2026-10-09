package io.github.faolivieri.fedexpenses.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

@Immutable
data class MoneyColors(val expense: Color, val income: Color, val gridLine: Color)

private val LightMoney = MoneyColors(expense = Color(0xFFC62828), income = Color(0xFF1B7F3B), gridLine = Color(0xFFE4E3DF))
private val DarkMoney = MoneyColors(expense = Color(0xFFFF8A80), income = Color(0xFF7BD88F), gridLine = Color(0xFF34342F))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

/** A preset app palette, defined by one seed color. */
data class Palette(val id: String, val name: String, val seed: Color)

/** The user's palette choice: a preset id, [Palettes.WALLPAPER], or [Palettes.CUSTOM] with [customColor]. */
@Immutable
data class ThemeChoice(val paletteId: String = Palettes.DEFAULT, val customColor: Int = Palettes.presets[0].seed.toArgb())

object Palettes {
    const val DEFAULT = "ocean"
    /** Android 12+ colors taken from the wallpaper. */
    const val WALLPAPER = "wallpaper"
    const val CUSTOM = "custom"

    val presets = listOf(
        Palette(DEFAULT, "Ocean", Color(0xFF2A78D6)),
        Palette("teal", "Teal", Color(0xFF00897B)),
        Palette("forest", "Forest", Color(0xFF2E7D32)),
        Palette("violet", "Violet", Color(0xFF6A4FC4)),
        Palette("rose", "Rose", Color(0xFFC2185B)),
        Palette("sunset", "Sunset", Color(0xFFE65100)),
        Palette("amber", "Amber", Color(0xFFB7791F)),
        Palette("graphite", "Graphite", Color(0xFF546E7A)),
    )

    val wallpaperAvailable: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** The seed color a choice resolves to (wallpaper falls back to the default where unavailable). */
    fun seedOf(choice: ThemeChoice): Color = when (choice.paletteId) {
        CUSTOM -> Color(choice.customColor)
        else -> presets.firstOrNull { it.id == choice.paletteId }?.seed ?: presets[0].seed
    }
}

// The default palette keeps its hand-tuned schemes.
private val OceanLight = lightColorScheme(
    primary = Color(0xFF2A78D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FA),
    onPrimaryContainer = Color(0xFF0D366B),
    secondaryContainer = Color(0xFFE3E9F2),
    background = Color(0xFFFCFCFB),
    surface = Color(0xFFFCFCFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F4),
    surfaceContainer = Color(0xFFF0EFEC),
    surfaceContainerHigh = Color(0xFFEAE9E5),
    onSurface = Color(0xFF0B0B0B),
    onSurfaceVariant = Color(0xFF52514E),
    outlineVariant = Color(0xFFDAD9D4),
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF86B6EF),
    onPrimary = Color(0xFF0D366B),
    primaryContainer = Color(0xFF1C5CAB),
    onPrimaryContainer = Color(0xFFD6E6FA),
    secondaryContainer = Color(0xFF2E3440),
    background = Color(0xFF1A1A19),
    surface = Color(0xFF1A1A19),
    surfaceContainerLowest = Color(0xFF141413),
    surfaceContainerLow = Color(0xFF20201F),
    surfaceContainer = Color(0xFF262624),
    surfaceContainerHigh = Color(0xFF30302D),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFC3C2B7),
    outlineVariant = Color(0xFF3D3D39),
)

/** HSL helper: [h] 0..360, [s] and [l] 0..1. */
private fun hsl(h: Float, s: Float, l: Float): Color = Color.hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/**
 * The color with hue [h] and saturation [s] whose relative luminance (what contrast ratios are
 * computed from) is closest to [target]. Hues look very different in brightness at the same HSL
 * lightness (teal and yellow far brighter than blue), so roles are tuned by luminance instead.
 */
private fun withLuminance(h: Float, s: Float, target: Float): Color {
    var lo = 0f
    var hi = 1f
    repeat(20) {
        val mid = (lo + hi) / 2
        if (hsl(h, s, mid).luminance() < target) lo = mid else hi = mid
    }
    return hsl(h, s, (lo + hi) / 2)
}

/**
 * Builds a full scheme from one seed. Only hue and saturation come from the seed; every role has
 * a fixed luminance (matching the default Ocean palette), so any color gets the same contrast.
 */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), out)
    val h = out[0]
    val s = out[1]
    // Grays stay gray; vivid seeds are tamed a little so large areas don't glare.
    val sat = s.coerceAtMost(0.85f)
    val tint = (sat * 0.12f).coerceAtMost(0.08f) // faint hue in the neutral surfaces
    return if (!dark) {
        // Luminance 0.17 gives white text ≥ 4.5:1 contrast on buttons, like Ocean's #2A78D6.
        val primary = withLuminance(h, sat, 0.17f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = withLuminance(h, sat * 0.8f, 0.78f),
            onPrimaryContainer = withLuminance(h, sat, 0.035f),
            secondaryContainer = withLuminance(h, sat * 0.35f, 0.80f),
            onSecondaryContainer = withLuminance(h, sat * 0.4f, 0.035f),
            surfaceTint = primary,
            background = hsl(h, tint, 0.985f),
            surface = hsl(h, tint, 0.985f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, tint, 0.96f),
            surfaceContainer = hsl(h, tint, 0.94f),
            surfaceContainerHigh = hsl(h, tint, 0.915f),
            onSurface = Color(0xFF0B0B0B),
            onSurfaceVariant = Color(0xFF52514E),
            outlineVariant = hsl(h, tint, 0.85f),
        )
    } else {
        val primary = withLuminance(h, sat, 0.42f)
        darkColorScheme(
            primary = primary,
            onPrimary = withLuminance(h, sat, 0.035f),
            primaryContainer = withLuminance(h, sat * 0.75f, 0.10f),
            onPrimaryContainer = withLuminance(h, sat, 0.75f),
            secondaryContainer = withLuminance(h, sat * 0.25f, 0.045f),
            onSecondaryContainer = withLuminance(h, sat * 0.3f, 0.72f),
            surfaceTint = primary,
            background = hsl(h, tint, 0.10f),
            surface = hsl(h, tint, 0.10f),
            surfaceContainerLowest = hsl(h, tint, 0.08f),
            surfaceContainerLow = hsl(h, tint, 0.12f),
            surfaceContainer = hsl(h, tint, 0.145f),
            surfaceContainerHigh = hsl(h, tint, 0.18f),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFC3C2B7),
            outlineVariant = hsl(h, tint, 0.23f),
        )
    }
}

@Composable
fun ExpensesTheme(choice: ThemeChoice = ThemeChoice(), content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = remember(choice, dark) {
        when {
            choice.paletteId == Palettes.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            choice.paletteId == Palettes.DEFAULT -> if (dark) OceanDark else OceanLight
            else -> schemeFromSeed(Palettes.seedOf(choice), dark)
        }
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

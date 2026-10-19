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

/**
 * The user's colors: the accent palette (a preset id, [Palettes.WALLPAPER], or [Palettes.CUSTOM] with
 * [customColor]) and the background ([Backgrounds], with [backgroundColor] for a custom one).
 */
@Immutable
data class ThemeChoice(
    val paletteId: String = Palettes.DEFAULT,
    val customColor: Int = Palettes.presets[0].seed.toArgb(),
    val background: String = Backgrounds.PALETTE,
    val backgroundColor: Int = 0xFF8D6E63.toInt(),
)

object Backgrounds {
    /** Tinted with the palette's hue (or the wallpaper's surfaces). */
    const val PALETTE = "palette"
    /** Plain greys. */
    const val NEUTRAL = "neutral"
    /** Tinted with [ThemeChoice.backgroundColor]. */
    const val CUSTOM = "custom"
}

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

/**
 * Background, card, bar and dialog colors with the hue and saturation of [h]/[s] and a fixed
 * luminance per role, so text keeps its contrast whatever color is chosen.
 */
fun ColorScheme.withTintedSurfaces(h: Float, s: Float, dark: Boolean): ColorScheme {
    fun c(target: Float) = withLuminance(h, s, target)
    return if (!dark) copy(
        background = c(0.92f), surface = c(0.92f),
        surfaceContainerLowest = c(0.97f), surfaceContainerLow = c(0.885f), surfaceContainer = c(0.85f),
        surfaceContainerHigh = c(0.815f), surfaceContainerHighest = c(0.78f), surfaceVariant = c(0.815f),
        surfaceBright = c(0.95f), surfaceDim = c(0.80f), outlineVariant = c(0.62f),
    ) else copy(
        background = c(0.012f), surface = c(0.012f),
        surfaceContainerLowest = c(0.008f), surfaceContainerLow = c(0.017f), surfaceContainer = c(0.023f),
        surfaceContainerHigh = c(0.031f), surfaceContainerHighest = c(0.04f), surfaceVariant = c(0.031f),
        surfaceBright = c(0.05f), surfaceDim = c(0.010f), outlineVariant = c(0.07f),
    )
}

/** Plain grey surfaces (the original look). */
private fun ColorScheme.withNeutralSurfaces(dark: Boolean): ColorScheme {
    val n = if (dark) OceanDark else OceanLight
    return copy(
        background = n.background, surface = n.surface,
        surfaceContainerLowest = n.surfaceContainerLowest, surfaceContainerLow = n.surfaceContainerLow,
        surfaceContainer = n.surfaceContainer, surfaceContainerHigh = n.surfaceContainerHigh,
        surfaceContainerHighest = if (dark) Color(0xFF353532) else Color(0xFFE4E3DF),
        surfaceVariant = n.surfaceContainerHigh, outlineVariant = n.outlineVariant,
        surfaceBright = n.surfaceContainerLowest, surfaceDim = n.surfaceContainerHigh,
    )
}

private fun hueSat(color: Color): Pair<Float, Float> {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), out)
    return out[0] to out[1]
}

/** The complete scheme for a choice: accent colors first, then the chosen background. */
fun schemeFor(choice: ThemeChoice, dark: Boolean, wallpaper: ColorScheme?): ColorScheme {
    val accent = when {
        choice.paletteId == Palettes.WALLPAPER && wallpaper != null -> wallpaper
        choice.paletteId == Palettes.DEFAULT -> if (dark) OceanDark else OceanLight
        else -> schemeFromSeed(Palettes.seedOf(choice), dark)
    }
    return when (choice.background) {
        Backgrounds.NEUTRAL -> accent.withNeutralSurfaces(dark)
        Backgrounds.CUSTOM -> {
            val (h, s) = hueSat(Color(choice.backgroundColor))
            accent.withTintedSurfaces(h, s.coerceAtMost(0.9f), dark).withVisibleSelection(dark)
        }
        // Wallpaper colors come with their own matching surfaces.
        else -> if (accent === wallpaper) accent else {
            val (h, s) = hueSat(Palettes.seedOf(choice))
            accent.withTintedSurfaces(h, s.coerceAtMost(0.85f) * 0.55f, dark).withVisibleSelection(dark)
        }
    }
}

/**
 * Selected chips, segmented buttons and the bottom-bar indicator use secondaryContainer. On tinted
 * surfaces the pale default blends into dialogs and cards, so make it a clearly stronger tint of the
 * accent, still with dark text in light mode and light text in dark mode.
 */
private fun ColorScheme.withVisibleSelection(dark: Boolean): ColorScheme {
    val (h, s) = hueSat(primary)
    val sat = (s * 0.6f).coerceAtMost(0.6f)
    return if (!dark) copy(
        secondaryContainer = withLuminance(h, sat, 0.60f),
        onSecondaryContainer = withLuminance(h, sat, 0.025f),
    ) else copy(
        secondaryContainer = withLuminance(h, sat, 0.13f),
        onSecondaryContainer = withLuminance(h, sat * 0.5f, 0.80f),
    )
}

@Composable
fun ExpensesTheme(choice: ThemeChoice = ThemeChoice(), content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = remember(choice, dark) {
        val wallpaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
        schemeFor(choice, dark, wallpaper)
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

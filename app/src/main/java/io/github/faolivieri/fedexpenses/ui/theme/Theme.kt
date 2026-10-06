package io.github.faolivieri.fedexpenses.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MoneyColors(val expense: Color, val income: Color, val gridLine: Color)

private val LightMoney = MoneyColors(expense = Color(0xFFC62828), income = Color(0xFF1B7F3B), gridLine = Color(0xFFE4E3DF))
private val DarkMoney = MoneyColors(expense = Color(0xFFFF8A80), income = Color(0xFF7BD88F), gridLine = Color(0xFF34342F))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

private val Light = lightColorScheme(
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

private val Dark = darkColorScheme(
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

@Composable
fun ExpensesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

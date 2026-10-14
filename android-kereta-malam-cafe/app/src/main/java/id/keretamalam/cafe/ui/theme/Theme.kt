package id.keretamalam.cafe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// Cafe Flow Accent Palette — navy, soft blue, teal, and violet
private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF173B78),
    secondary = Color(0xFF0F8F83),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5F5EF),
    onSecondaryContainer = Color(0xFF064E49),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF0F4FA),
    onSurfaceVariant = Color(0xFF526176),
    outline = Color(0xFFDCE4EF),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF10213D),
    primaryContainer = Color(0xFF203A62),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF70D4C4),
    onSecondary = Color(0xFF073B37),
    secondaryContainer = Color(0xFF174A45),
    onSecondaryContainer = Color(0xFFBDF7EC),
    tertiary = Color(0xFFC4A7FF),
    onTertiary = Color(0xFF2B1556),
    background = Color(0xFF090D14),
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF111722),
    onSurface = Color(0xFFF3F6FC),
    surfaceVariant = Color(0xFF1A2432),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF2B394C),
    error = Color(0xFFFF6B72),
    onError = Color(0xFF39090D)
)

private val CafeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/**
 * Tema Kereta Malam Cafe dengan aksen warna yang konsisten.
 * Mendukung pengaturan skala tampilan / zoom (40%, 50%, 60%, 70%, 80%, 90%, 100%, 110%, 120%)
 * secara nyaman lewat `LocalDensity` tanpa merusak struktur layout maupun dialog.
 */
@Composable
fun KeretaMalamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiScalePercent: Int = 100,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
    val baseDensity = LocalDensity.current
    val clampedScale = uiScalePercent.coerceIn(40, 120) / 100f

    val scaledDensity = Density(
        density = baseDensity.density * clampedScale,
        fontScale = baseDensity.fontScale
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CafeTypography,
            content = content
        )
    }
}

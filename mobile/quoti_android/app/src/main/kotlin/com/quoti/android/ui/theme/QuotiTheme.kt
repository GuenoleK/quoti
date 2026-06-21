package com.quoti.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.WindowCompat

private val QuotiLightColors =
    lightColorScheme(
        primary = Color(0xFF7A442F),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF745B4D),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF8A6A2E),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF8F1E8),
        onBackground = Color(0xFF211A16),
        surface = Color(0xFFFFFBF6),
        onSurface = Color(0xFF211A16),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF8EF),
        surfaceContainer = Color(0xFFF4E9DD),
        surfaceContainerHigh = Color(0xFFEFE2D4),
        surfaceContainerHighest = Color(0xFFE6D7C8),
        surfaceVariant = Color(0xFFEADDD1),
        onSurfaceVariant = Color(0xFF51443B),
        outline = Color(0xFF857469),
        outlineVariant = Color(0xFFD6C6B8),
    )

private val QuotiDarkColors =
    darkColorScheme(
        primary = Color(0xFFFFB693),
        onPrimary = Color(0xFF4B210F),
        secondary = Color(0xFFE2BFAE),
        onSecondary = Color(0xFF422A1F),
        tertiary = Color(0xFFD9BC77),
        onTertiary = Color(0xFF3A2B06),
        background = Color(0xFF16110E),
        onBackground = Color(0xFFF1DFD2),
        surface = Color(0xFF211A16),
        onSurface = Color(0xFFF5E8DC),
        surfaceContainerLowest = Color(0xFF110D0B),
        surfaceContainerLow = Color(0xFF1A1411),
        surfaceContainer = Color(0xFF211A16),
        surfaceContainerHigh = Color(0xFF2D231E),
        surfaceContainerHighest = Color(0xFF392D27),
        surfaceVariant = Color(0xFF51443B),
        onSurfaceVariant = Color(0xFFD7C4B7),
        outline = Color(0xFFA18D80),
        outlineVariant = Color(0xFF51443B),
    )

private val QuotiBrandFontFamily = FontFamily.Serif
private val QuotiPlainFontFamily = FontFamily.SansSerif

private val QuotiTypography =
    Typography(
        displayLarge = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 57.sp, 64.sp),
        displayMedium = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 45.sp, 52.sp),
        displaySmall = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 36.sp, 44.sp),
        headlineLarge = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 32.sp, 40.sp),
        headlineMedium = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 28.sp, 36.sp),
        headlineSmall = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 24.sp, 32.sp),
        titleLarge = quotiType(QuotiPlainFontFamily, FontWeight.Medium, 22.sp, 28.sp),
        titleMedium = quotiType(QuotiPlainFontFamily, FontWeight.Medium, 16.sp, 24.sp),
        titleSmall = quotiType(QuotiPlainFontFamily, FontWeight.Medium, 14.sp, 20.sp),
        bodyLarge = quotiType(QuotiBrandFontFamily, FontWeight.Normal, 16.sp, 24.sp),
        bodyMedium = quotiType(QuotiPlainFontFamily, FontWeight.Normal, 14.sp, 20.sp),
        bodySmall = quotiType(QuotiPlainFontFamily, FontWeight.Normal, 12.sp, 16.sp),
        labelLarge = quotiType(QuotiPlainFontFamily, FontWeight.SemiBold, 14.sp, 20.sp),
        labelMedium = quotiType(QuotiPlainFontFamily, FontWeight.SemiBold, 12.sp, 16.sp),
        labelSmall = quotiType(QuotiPlainFontFamily, FontWeight.SemiBold, 11.sp, 16.sp),
    )

private fun quotiType(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    lineHeight: TextUnit,
): TextStyle =
    TextStyle(
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = 0.sp,
    )

private val QuotiShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(22.dp),
    )

@Composable
fun QuotiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) QuotiDarkColors else QuotiLightColors

    QuotiSystemBars(
        colorScheme = colorScheme,
        darkTheme = darkTheme,
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = QuotiTypography,
        shapes = QuotiShapes,
        content = content,
    )
}

@Composable
@Suppress("DEPRECATION")
private fun QuotiSystemBars(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val background = colorScheme.background.toArgb()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

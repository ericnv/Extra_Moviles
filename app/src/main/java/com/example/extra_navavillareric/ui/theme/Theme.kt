package com.example.extra_navavillareric.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class AppThemeType {
    GUINDA, AZUL
}

private val GuindaDarkColorScheme = darkColorScheme(
    primary = GuindaDarkPrimary,
    secondary = GuindaDarkSecondary,
    tertiary = GuindaDarkTertiary
)

private val GuindaLightColorScheme = lightColorScheme(
    primary = GuindaPrimary,
    secondary = GuindaSecondary,
    tertiary = GuindaTertiary
)

private val AzulDarkColorScheme = darkColorScheme(
    primary = AzulDarkPrimary,
    secondary = AzulDarkSecondary,
    tertiary = AzulDarkTertiary
)

private val AzulLightColorScheme = lightColorScheme(
    primary = AzulPrimary,
    secondary = AzulSecondary,
    tertiary = AzulTertiary
)

@Composable
fun Extra_NavaVillarEricTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeType: AppThemeType = AppThemeType.GUINDA,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeType == AppThemeType.GUINDA -> {
            if (darkTheme) GuindaDarkColorScheme else GuindaLightColorScheme
        }
        themeType == AppThemeType.AZUL -> {
            if (darkTheme) AzulDarkColorScheme else AzulLightColorScheme
        }
        else -> if (darkTheme) GuindaDarkColorScheme else GuindaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

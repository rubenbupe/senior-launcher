package com.seniorlauncher.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF427CB3),
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun SeniorLauncherTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        FallbackLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = Color.Black.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
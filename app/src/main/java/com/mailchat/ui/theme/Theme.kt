package com.mailchat.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import com.mailchat.util.SettingsUtils

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppDarkGreen,
    onPrimary = Color.White,
    primaryContainer = WhatsAppGreen,
    onPrimaryContainer = Color.White,
    secondary = WhatsAppDarkGreen,
    onSecondary = Color.White,
    background = WhatsAppBackground,
    onBackground = WhatsAppTextPrimary,
    surface = WhatsAppBubbleIn,
    onSurface = WhatsAppTextPrimary,
    surfaceVariant = WhatsAppChatBackground,
    onSurfaceVariant = WhatsAppTextSecondary
)

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppDarkAccent,
    onPrimary = Color.Black,
    primaryContainer = WhatsAppDarkAccent,
    onPrimaryContainer = Color.White,
    secondary = WhatsAppDarkAccent,
    onSecondary = Color.Black,
    background = WhatsAppDarkBackground,
    onBackground = WhatsAppDarkTextPrimary,
    surface = WhatsAppDarkBubbleIn,
    onSurface = WhatsAppDarkTextPrimary,
    surfaceVariant = WhatsAppDarkBackground,
    onSurfaceVariant = WhatsAppDarkTextSecondary
)

@Composable
private fun findActivity(): Activity? {
    var ctx = LocalContext.current
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun MailchatTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkMode = remember { SettingsUtils.getDarkMode(context) }
    val darkTheme = when (darkMode) {
        SettingsUtils.DARK_MODE_LIGHT -> false
        SettingsUtils.DARK_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }

    // Adapt the status bar / navigation bar icon appearance to the current theme
    // (dark icons on a light background, light icons on a dark background). This
    // makes the status bar behave like other apps and fixes Samsung devices where
    // the fixed theme color was overridden in edge-to-edge mode.
    val activity = findActivity()
    LaunchedEffect(darkTheme, activity) {
        activity?.window?.let { window ->
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

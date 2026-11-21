package com.example.smarthome

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Navy = Color(0xFF14213D)
    val Blue = Color(0xFF2563EB)
    val Teal = Color(0xFF0D9488)
    val Green = Color(0xFF16A34A)
    val Orange = Color(0xFFF59E0B)
    val Red = Color(0xFFDC2626)
    val Purple = Color(0xFF7C3AED)
}

private val Light = lightColorScheme(
    primary = Pal.Blue,
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF60A5FA),
    background = Color(0xFF0B1226),
    surface = Color(0xFF16213E),
    surfaceVariant = Color(0xFF22305A),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun SmartHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

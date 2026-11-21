package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.model.WidgetAccent

// FocusFlow Dark Palette
val DarkBackground = Color(0xFF0E1116)
val DarkSurface = Color(0xFF151A21)
val DarkSurfaceVariant = Color(0xFF1E2530)
val DarkSurfaceElevated = Color(0xFF1A2029)
val DarkOnBackground = Color(0xFFE6E8EC)
val DarkOnSurfaceVariant = Color(0xFFA9B1BE)
val DarkPrimary = Color(0xFF6EA8FF)
val DarkOnPrimary = Color(0xFF06214A)
val DarkPrimaryContainer = Color(0xFF1B3A6B)
val DarkOnPrimaryContainer = Color(0xFFD6E6FF)
val DarkSecondary = Color(0xFF7FD6C2)
val DarkOnSecondary = Color(0xFF00382E)
val DarkSecondaryContainer = Color(0xFF12483F)
val DarkOnSecondaryContainer = Color(0xFFB8F2E4)
val DarkOutline = Color(0xFF3A4352)
val DarkOutlineVariant = Color(0xFF252D3A)
val DarkError = Color(0xFFFF8A80)
val DarkOnError = Color(0xFF4A0805)
val DarkErrorContainer = Color(0xFF4A1818)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// FocusFlow Light Palette
val LightBackground = Color(0xFFF6F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EBF1)
val LightOnBackground = Color(0xFF11151B)
val LightOnSurfaceVariant = Color(0xFF4A5362)
val LightPrimary = Color(0xFF2F6FDB)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD6E6FF)
val LightOnPrimaryContainer = Color(0xFF0B2A5C)
val LightSecondary = Color(0xFF1B7F6C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC7F2E8)
val LightOnSecondaryContainer = Color(0xFF00382E)
val LightOutline = Color(0xFFB7BECB)
val LightOutlineVariant = Color(0xFFDCE1EA)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)

// Widget Accent Palette
val AccentIndigo = Color(0xFF5C6BC0)
val AccentTeal = Color(0xFF26A69A)
val AccentAmber = Color(0xFFFFB300)
val AccentRose = Color(0xFFEC407A)
val AccentGreen = Color(0xFF66BB6A)
val AccentSlate = Color(0xFF78909C)

val WarningAmber = Color(0xFFFFB74D)
val SuccessGreen = Color(0xFF66BB6A)

fun WidgetAccent.toColor(): Color = when (this) {
    WidgetAccent.INDIGO -> AccentIndigo
    WidgetAccent.TEAL -> AccentTeal
    WidgetAccent.AMBER -> AccentAmber
    WidgetAccent.ROSE -> AccentRose
    WidgetAccent.GREEN -> AccentGreen
    WidgetAccent.SLATE -> AccentSlate
}
